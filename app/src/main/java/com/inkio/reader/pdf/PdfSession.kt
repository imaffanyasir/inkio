package com.inkio.reader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable

/**
 * One open PDF. PdfRenderer is not thread-safe and only allows one open page,
 * so every access goes through [lock].
 */
class PdfSession private constructor(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : Closeable {

    private val lock = Mutex()
    private val trimCache = HashMap<Int, RectF>()
    val pageCount: Int = renderer.pageCount
    @Volatile private var closed = false

    /**
     * Renders [index] fitted into a [width]x[height] canvas (the reader's viewport),
     * with white everywhere that isn't ink. E-ink processing is applied afterwards.
     */
    suspend fun renderPage(index: Int, width: Int, height: Int, trimMargins: Boolean): Bitmap =
        withContext(Dispatchers.IO) {
            lock.withLock {
                check(!closed) { "closed" }
                renderer.openPage(index).use { page ->
                    val pw = page.width.toFloat()
                    val ph = page.height.toFloat()
                    val crop = if (trimMargins) trimCache.getOrPut(index) { findContent(page) } else RectF(0f, 0f, 1f, 1f)

                    val cw = crop.width() * pw
                    val ch = crop.height() * ph
                    val margin = (minOf(width, height) * 0.025f)
                    val scale = minOf((width - 2 * margin) / cw, (height - 2 * margin) / ch)
                    val dx = (width - cw * scale) / 2f
                    val dy = (height - ch * scale) / 2f

                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    val m = Matrix().apply {
                        postTranslate(-crop.left * pw, -crop.top * ph)
                        postScale(scale, scale)
                        postTranslate(dx, dy)
                    }
                    page.render(bmp, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        }

    /** Plain render of a page at a given width, used for covers and thumbnails. */
    suspend fun renderThumb(index: Int, width: Int): Bitmap = withContext(Dispatchers.IO) {
        lock.withLock {
            renderer.openPage(index).use { page ->
                val h = (width * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                val s = width / page.width.toFloat()
                page.render(bmp, null, Matrix().apply { setScale(s, s) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                flattenOnWhite(bmp)
                bmp
            }
        }
    }

    /** Detects the content box of a page (as fractions) so wide margins can be trimmed. */
    private fun findContent(page: PdfRenderer.Page): RectF {
        val w = 240
        val h = (w * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        val s = w / page.width.toFloat()
        page.render(bmp, null, Matrix().apply { setScale(s, s) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        flattenOnWhite(bmp)
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        bmp.recycle()
        var l = w; var t = h; var r = -1; var b = -1
        for (y in 0 until h) for (x in 0 until w) {
            val c = px[y * w + x]
            val lum = ((c shr 16 and 0xFF) + (c shr 8 and 0xFF) + (c and 0xFF)) / 3
            if (lum < 225) {
                if (x < l) l = x; if (x > r) r = x
                if (y < t) t = y; if (y > b) b = y
            }
        }
        if (r < 0 || (r - l) < w * 0.2f || (b - t) < h * 0.2f) return RectF(0f, 0f, 1f, 1f)
        val pad = 0.025f
        return RectF(
            (l.toFloat() / w - pad).coerceAtLeast(0f),
            (t.toFloat() / h - pad).coerceAtLeast(0f),
            ((r + 1).toFloat() / w + pad).coerceAtMost(1f),
            ((b + 1).toFloat() / h + pad).coerceAtMost(1f),
        )
    }

    /** PdfRenderer leaves transparent pixels where a page has no background. */
    private fun flattenOnWhite(bmp: Bitmap) {
        if (!bmp.hasAlpha()) return
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply { drawColor(Color.WHITE); drawBitmap(bmp, 0f, 0f, null) }
        val px = IntArray(bmp.width * bmp.height)
        out.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        bmp.setPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        out.recycle()
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }

    suspend fun closeSafely() = lock.withLock { close() }

    companion object {
        fun open(context: Context, uri: String): PdfSession {
            val pfd = context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")
                ?: error("Cannot open file")
            return try {
                PdfSession(pfd, PdfRenderer(pfd))
            } catch (t: Throwable) {
                pfd.close(); throw t
            }
        }
    }
}
