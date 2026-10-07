package com.inkio.reader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.inkio.reader.data.Book
import com.inkio.reader.data.CoverSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Book covers come from three places: the first page of the PDF (offline, default),
 * an online lookup (Open Library / Google Books, only when asked), or an image the user picks.
 */
class CoverManager(private val context: Context) {

    private val dir = File(context.filesDir, "covers").apply { mkdirs() }

    private fun save(book: Book, bmp: Bitmap): String {
        dir.listFiles { f -> f.name.startsWith(book.id + "_") }?.forEach { it.delete() }
        val f = File(dir, "${book.id}_${System.currentTimeMillis()}.jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return f.absolutePath
    }

    suspend fun autoCover(book: Book, tryOnline: Boolean): Pair<String, CoverSource>? {
        if (tryOnline) {
            val url = runCatching { searchOnline(book.title, book.author).firstOrNull() }.getOrNull()
            if (url != null) download(book, url)?.let { return it to CoverSource.ONLINE }
        }
        return fromPdf(book, 0)?.let { it to CoverSource.PDF }
    }

    suspend fun fromPdf(book: Book, page: Int): String? = withContext(Dispatchers.IO) {
        runCatching {
            PdfSession.open(context, book.uri).use { s ->
                val bmp = s.renderThumb(page.coerceIn(0, s.pageCount - 1), 480)
                save(book, bmp).also { bmp.recycle() }
            }
        }.getOrNull()
    }

    suspend fun fromImage(book: Book, image: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(image)!!.use { BitmapFactory.decodeStream(it, null, opts) }
            var sample = 1
            while (opts.outWidth / (sample * 2) >= 600) sample *= 2
            val bmp = context.contentResolver.openInputStream(image)!!.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }!!
            save(book, bmp).also { bmp.recycle() }
        }.getOrNull()
    }

    suspend fun download(book: Book, url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = httpGet(url, binary = true) as ByteArray
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)!!
            if (bmp.width < 40) return@runCatching null // placeholder pixel
            save(book, bmp).also { bmp.recycle() }
        }.getOrNull()
    }

    /** Candidate cover image URLs for a title. Needs an internet connection. */
    suspend fun searchOnline(title: String, author: String = ""): List<String> = withContext(Dispatchers.IO) {
        val out = LinkedHashSet<String>()
        val q = URLEncoder.encode(title, "UTF-8")
        runCatching {
            val a = if (author.isNotBlank()) "&author=" + URLEncoder.encode(author, "UTF-8") else ""
            val json = JSONObject(httpGet("https://openlibrary.org/search.json?title=$q$a&limit=8&fields=cover_i") as String)
            val docs = json.optJSONArray("docs")
            if (docs != null) for (i in 0 until docs.length()) {
                val id = docs.getJSONObject(i).optLong("cover_i", 0)
                if (id > 0) out += "https://covers.openlibrary.org/b/id/$id-L.jpg"
            }
        }
        runCatching {
            val json = JSONObject(httpGet("https://www.googleapis.com/books/v1/volumes?q=intitle:$q&maxResults=8") as String)
            val items = json.optJSONArray("items")
            if (items != null) for (i in 0 until items.length()) {
                val links = items.getJSONObject(i).optJSONObject("volumeInfo")?.optJSONObject("imageLinks") ?: continue
                val u = links.optString("thumbnail").ifBlank { links.optString("smallThumbnail") }
                if (u.isNotBlank()) out += u.replace("http://", "https://").replace("&edge=curl", "")
            }
        }
        out.take(12)
    }

    private fun httpGet(url: String, binary: Boolean = false): Any {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 12000
        c.setRequestProperty("User-Agent", "Inkio/1.0 (Android e-reader)")
        c.instanceFollowRedirects = true
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            val bytes = c.inputStream.use { it.readBytes() }
            return if (binary) bytes else String(bytes)
        } finally {
            c.disconnect()
        }
    }
}
