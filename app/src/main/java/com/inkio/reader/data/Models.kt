package com.inkio.reader.data

import org.json.JSONArray
import org.json.JSONObject

enum class CoverSource { NONE, PDF, ONLINE, CUSTOM }

data class Bookmark(
    val page: Int,
    val createdAt: Long,
    val note: String = "",
)

data class Book(
    val id: String,
    val uri: String,
    val fileName: String,
    val title: String,
    val author: String = "",
    val sizeBytes: Long = 0,
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val lastReadAt: Long = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val readingTimeMs: Long = 0,
    val coverPath: String? = null,
    val coverSource: CoverSource = CoverSource.NONE,
    val bookmarks: List<Bookmark> = emptyList(),
    val finished: Boolean = false,
) {
    val progress: Float
        get() = when {
            finished -> 1f
            pageCount <= 0 || lastReadAt == 0L -> 0f
            else -> ((currentPage + 1).toFloat() / pageCount).coerceIn(0f, 1f)
        }
    val started: Boolean get() = lastReadAt > 0
    val percent: Int get() = (progress * 100).toInt()

    fun isBookmarked(page: Int) = bookmarks.any { it.page == page }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("uri", uri); put("fileName", fileName); put("title", title)
        put("author", author); put("sizeBytes", sizeBytes); put("pageCount", pageCount)
        put("currentPage", currentPage); put("lastReadAt", lastReadAt); put("addedAt", addedAt)
        put("readingTimeMs", readingTimeMs); put("coverPath", coverPath ?: JSONObject.NULL)
        put("coverSource", coverSource.name); put("finished", finished)
        put("bookmarks", JSONArray().apply {
            bookmarks.forEach {
                put(JSONObject().put("page", it.page).put("createdAt", it.createdAt).put("note", it.note))
            }
        })
    }

    companion object {
        fun fromJson(o: JSONObject): Book {
            val bms = o.optJSONArray("bookmarks") ?: JSONArray()
            return Book(
                id = o.getString("id"),
                uri = o.getString("uri"),
                fileName = o.optString("fileName"),
                title = o.optString("title"),
                author = o.optString("author"),
                sizeBytes = o.optLong("sizeBytes"),
                pageCount = o.optInt("pageCount"),
                currentPage = o.optInt("currentPage"),
                lastReadAt = o.optLong("lastReadAt"),
                addedAt = o.optLong("addedAt"),
                readingTimeMs = o.optLong("readingTimeMs"),
                coverPath = if (o.isNull("coverPath")) null else o.optString("coverPath"),
                coverSource = runCatching { CoverSource.valueOf(o.optString("coverSource")) }
                    .getOrDefault(CoverSource.NONE),
                finished = o.optBoolean("finished"),
                bookmarks = (0 until bms.length()).map {
                    val b = bms.getJSONObject(it)
                    Bookmark(b.getInt("page"), b.optLong("createdAt"), b.optString("note"))
                },
            )
        }
    }
}

/** Chapter entry from the PDF outline. */
data class OutlineItem(val title: String, val page: Int, val depth: Int)
