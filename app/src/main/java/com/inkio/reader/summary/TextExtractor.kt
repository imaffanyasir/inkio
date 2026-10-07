package com.inkio.reader.summary

import android.content.Context
import android.net.Uri
import com.inkio.reader.data.Book
import com.inkio.reader.data.OutlineItem
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Extracts the text layer of PDF pages (fully offline, via PDFBox) and caches every
 * page on disk, so recaps of what you already read are instant later on.
 */
class TextExtractor(private val context: Context) {

    private val root = File(context.filesDir, "text")
    private val lock = Mutex()
    private var open: Pair<String, PDDocument>? = null

    private fun dirFor(bookId: String) = File(root, bookId).apply { mkdirs() }
    private fun pageFile(bookId: String, page: Int) = File(dirFor(bookId), "$page.txt")

    fun isCached(bookId: String, page: Int) = pageFile(bookId, page).exists()

    suspend fun pages(
        book: Book,
        pages: List<Int>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Map<Int, String> = withContext(Dispatchers.IO) {
        lock.withLock {
            val missing = pages.filterNot { isCached(book.id, it) }
            if (missing.isNotEmpty()) {
                val doc = docFor(book)
                val stripper = PDFTextStripper()
                missing.forEachIndexed { i, p ->
                    coroutineContext.ensureActive()
                    val text = if (doc == null || p >= doc.numberOfPages) "" else runCatching {
                        stripper.startPage = p + 1
                        stripper.endPage = p + 1
                        stripper.getText(doc)
                    }.getOrDefault("")
                    pageFile(book.id, p).writeText(text)
                    onProgress(i + 1, missing.size)
                }
            }
            pages.associateWith { runCatching { pageFile(book.id, it).readText() }.getOrDefault("") }
        }
    }

    suspend fun outline(book: Book): List<OutlineItem> = withContext(Dispatchers.IO) {
        val cache = File(dirFor(book.id), "outline.json")
        if (cache.exists()) {
            return@withContext runCatching {
                val arr = JSONArray(cache.readText())
                (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    OutlineItem(o.getString("t"), o.getInt("p"), o.getInt("d"))
                }
            }.getOrDefault(emptyList())
        }
        val items = lock.withLock {
            val doc = docFor(book) ?: return@withLock emptyList()
            val out = ArrayList<OutlineItem>()
            runCatching {
                val outline = doc.documentCatalog.documentOutline ?: return@runCatching
                fun walk(first: PDOutlineItem?, depth: Int) {
                    var cur = first
                    while (cur != null && out.size < 500) {
                        val idx = runCatching { cur.findDestinationPage(doc)?.let { doc.pages.indexOf(it) } }
                            .getOrNull() ?: -1
                        val title = cur.title?.trim().orEmpty()
                        if (idx >= 0 && title.isNotEmpty()) out += OutlineItem(title, idx, depth)
                        if (depth < 3 && cur.hasChildren()) walk(cur.firstChild, depth + 1)
                        cur = cur.nextSibling
                    }
                }
                walk(outline.firstChild, 0)
            }
            out
        }
        runCatching {
            cache.writeText(JSONArray().apply {
                items.forEach { put(JSONObject().put("t", it.title).put("p", it.page).put("d", it.depth)) }
            }.toString())
        }
        items
    }

    private fun docFor(book: Book): PDDocument? {
        open?.let { (id, d) -> if (id == book.id) return d }
        open?.second?.let { runCatching { it.close() } }
        open = null
        val doc = runCatching {
            val uri = Uri.parse(book.uri)
            if (uri.scheme == "file") {
                PDDocument.load(File(uri.path!!), MemoryUsageSetting.setupTempFileOnly())
            } else {
                context.contentResolver.openInputStream(uri)!!.use {
                    PDDocument.load(it, MemoryUsageSetting.setupTempFileOnly())
                }
            }
        }.getOrNull() ?: return null
        open = book.id to doc
        return doc
    }

    /** Frees the PDFBox document once the reader is closed. */
    suspend fun release() = withContext(Dispatchers.IO) {
        lock.withLock {
            open?.second?.let { runCatching { it.close() } }
            open = null
        }
    }
}
