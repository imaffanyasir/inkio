package com.inkio.reader.pdf

import android.graphics.Bitmap
import com.inkio.reader.data.DitherMode
import kotlin.math.roundToInt

/**
 * Turns a rendered page into what an e-paper panel would show: luminance only,
 * boosted contrast, a limited number of gray levels with dithering, a faint paper
 * grain, and finally mapped onto the theme's ink/paper colours.
 */
data class DisplayProfile(
    val dither: DitherMode,
    val levels: Int,
    val contrast: Float,
    /** Colour used for value 1.0 (page background). */
    val paper: Int,
    /** Colour used for value 0.0 (darkest ink). */
    val ink: Int,
    val texture: Boolean,
    /** When false, pages are shown in original colour (AMOLED "original" mode). */
    val process: Boolean = true,
)

object EinkProcessor {

    private val bayer4 = floatArrayOf(
        0f, 8f, 2f, 10f,
        12f, 4f, 14f, 6f,
        3f, 11f, 1f, 9f,
        15f, 7f, 13f, 5f,
    ).map { (it + 0.5f) / 16f - 0.5f }.toFloatArray()

    fun process(bmp: Bitmap, p: DisplayProfile) {
        if (!p.process) return
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)

        val lv = (p.levels.coerceIn(2, 256) - 1).toFloat()
        val v = FloatArray(w * h)
        for (i in px.indices) {
            val c = px[i]
            val a = (c ushr 24) and 0xFF
            var r = (c shr 16) and 0xFF
            var g = (c shr 8) and 0xFF
            var b = c and 0xFF
            if (a < 255) { // transparent areas count as paper
                r = 255 - ((255 - r) * a / 255); g = 255 - ((255 - g) * a / 255); b = 255 - ((255 - b) * a / 255)
            }
            var l = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            // contrast around a slightly-light midpoint so text gets darker and paper stays clean
            l = (l - 0.55f) * p.contrast + 0.55f
            if (l > 0.93f) l = 1f // clean paper white
            if (p.texture && l > 0.5f) l -= grain(i % w, i / w) * 0.035f
            v[i] = l.coerceIn(0f, 1f)
        }

        when (p.dither) {
            DitherMode.NONE -> for (i in v.indices) v[i] = (v[i] * lv).roundToInt() / lv
            DitherMode.ORDERED -> for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    val i = row + x
                    val t = bayer4[(y and 3) * 4 + (x and 3)] / lv
                    v[i] = ((v[i] + t).coerceIn(0f, 1f) * lv).roundToInt() / lv
                }
            }
            DitherMode.FLOYD -> for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    val i = row + x
                    val old = v[i]
                    val q = (old.coerceIn(0f, 1f) * lv).roundToInt() / lv
                    v[i] = q
                    val e = old - q
                    if (e == 0f) continue
                    if (x + 1 < w) v[i + 1] += e * 0.4375f
                    if (y + 1 < h) {
                        if (x > 0) v[i + w - 1] += e * 0.1875f
                        v[i + w] += e * 0.3125f
                        if (x + 1 < w) v[i + w + 1] += e * 0.0625f
                    }
                }
            }
        }

        // map onto ink -> paper gradient (palette of 256 entries)
        val pal = IntArray(256) { lerpColor(p.ink, p.paper, it / 255f) }
        for (i in px.indices) px[i] = pal[(v[i].coerceIn(0f, 1f) * 255f).roundToInt()]
        bmp.setPixels(px, 0, w, 0, 0, w, h)
    }

    /** Cheap deterministic value noise in 0..1 used as paper grain. */
    private fun grain(x: Int, y: Int): Float {
        var n = x * 374761393 + y * 668265263
        n = (n xor (n ushr 13)) * 1274126177
        return ((n xor (n ushr 16)) and 0xFF) / 255f
    }

    fun lerpColor(a: Int, b: Int, t: Float): Int {
        val ar = (a shr 16) and 0xFF; val ag = (a shr 8) and 0xFF; val ab = a and 0xFF
        val br = (b shr 16) and 0xFF; val bg = (b shr 8) and 0xFF; val bb = b and 0xFF
        val r = (ar + (br - ar) * t).roundToInt()
        val g = (ag + (bg - ag) * t).roundToInt()
        val bl = (ab + (bb - ab) * t).roundToInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }
}
