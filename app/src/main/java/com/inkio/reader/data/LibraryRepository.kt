package com.inkio.reader.data

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.inkio.reader.pdf.CoverManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class LibraryRepository(
    private val context: Context,
    private val settingsRepo: SettingsRepository,
    private val covers: CoverManager,
    private val scope: CoroutineScope,
) {
    enum class ScanProblem { NEEDS_PERMISSION, FOLDER_MISSING, NO_FOLDER_CHOSEN }

    private data class Found(val uri: String, val name: String, val size: Long)

    private val storeFile = File(context.filesDir, "library.json")
    private val writeLock = Mutex()
    private val scanLock = Mutex()

    private val all = MutableStateFlow<Map<String, Book>>(emptyMap())
    private val visibleIds = MutableStateFlow<List<String>>(emptyList())

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()
    private val _problem = MutableStateFlow<ScanProblem?>(null)
    val problem: StateFlow<ScanProblem?> = _problem.asStateFlow()

    val books: StateFlow<List<Book>> = combine(all, visibleIds) { map, ids -> ids.mapNotNull { map[it] } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        load()
    }

    fun book(id: String): Book? = all.value[id]
    fun bookFlow() = all

    private fun load() {
        runCatching {
            if (!storeFile.exists()) return
            val root = JSONObject(storeFile.readText())
            val arr = root.optJSONArray("books") ?: JSONArray()
            all.value = (0 until arr.length()).map { Book.fromJson(arr.getJSONObject(it)) }.associateBy { it.id }
            val vis = root.optJSONArray("visible") ?: JSONArray()
            visibleIds.value = (0 until vis.length()).map { vis.getString(it) }
        }
    }

    private fun persist() {
        val snapshotBooks = all.value.values.toList()
        val snapshotVisible = visibleIds.value
        scope.launch(Dispatchers.IO) {
            writeLock.withLock {
                val root = JSONObject()
                root.put("books", JSONArray().apply { snapshotBooks.forEach { put(it.toJson()) } })
                root.put("visible", JSONArray(snapshotVisible))
                val tmp = File(storeFile.parentFile, "library.json.tmp")
                tmp.writeText(root.toString())
                if (!tmp.renameTo(storeFile)) {
                    storeFile.writeText(root.toString()); tmp.delete()
                }
            }
        }
    }

    fun update(id: String, block: (Book) -> Book) {
        all.update { m -> m[id]?.let { m + (id to block(it)) } ?: m }
        persist()
    }

    // ---------------------------------------------------------------- scanning

    suspend fun scan() = withContext(Dispatchers.IO) {
        if (!scanLock.tryLock()) return@withContext
        try {
            _scanning.value = true
            val s = settingsRepo.current
            val found: List<Found>? = when (s.source) {
                LibrarySource.CUSTOM -> {
                    val tree = s.customTreeUri
                    if (tree == null) { _problem.value = ScanProblem.NO_FOLDER_CHOSEN; null }
                    else scanTree(Uri.parse(tree))
                }
                else -> {
                    if (!StorageAccess.hasAllFilesAccess(context)) {
                        _problem.value = ScanProblem.NEEDS_PERMISSION; null
                    } else {
                        val dir = StorageAccess.dirFor(s.source)!!
                        if (!dir.exists()) dir.mkdirs()
                        if (!dir.exists()) { _problem.value = ScanProblem.FOLDER_MISSING; null }
                        else scanDir(dir, 0)
                    }
                }
            }
            if (found == null) return@withContext
            _problem.value = null

            val now = System.currentTimeMillis()
            val current = all.value.toMutableMap()
            val ids = ArrayList<String>()
            for (f in found) {
                val id = idFor(f.name, f.size)
                if (id in ids) continue
                ids += id
                val existing = current[id]
                current[id] = when {
                    existing == null -> Book(
                        id = id, uri = f.uri, fileName = f.name, title = cleanTitle(f.name),
                        sizeBytes = f.size, pageCount = pageCountOf(f.uri), addedAt = now,
                    )
                    existing.uri != f.uri || existing.pageCount == 0 ->
                        existing.copy(uri = f.uri, pageCount = existing.pageCount.takeIf { it > 0 } ?: pageCountOf(f.uri))
                    else -> existing
                }
            }
            all.value = current
            visibleIds.value = ids
            persist()

            // Covers are produced in the background after the list is visible.
            for (id in ids) {
                val b = all.value[id] ?: continue
                if (b.coverPath != null && File(b.coverPath).exists()) continue
                val path = covers.autoCover(b, settingsRepo.current.onlineCovers)
                if (path != null) update(id) { it.copy(coverPath = path.first, coverSource = path.second) }
            }
        } finally {
            _scanning.value = false
            scanLock.unlock()
        }
    }

    private fun scanDir(dir: File, depth: Int): List<Found> {
        val out = ArrayList<Found>()
        val children = dir.listFiles() ?: return out
        for (f in children) {
            if (f.name.startsWith(".")) continue
            if (f.isDirectory && depth < 4) out += scanDir(f, depth + 1)
            else if (f.isFile && f.name.endsWith(".pdf", ignoreCase = true) && f.length() > 0) {
                out += Found(Uri.fromFile(f).toString(), f.name, f.length())
            }
        }
        return out
    }

    private fun scanTree(tree: Uri): List<Found> {
        val root = DocumentFile.fromTreeUri(context, tree) ?: return emptyList()
        if (!root.canRead()) { _problem.value = ScanProblem.NO_FOLDER_CHOSEN; return emptyList() }
        val out = ArrayList<Found>()
        fun walk(d: DocumentFile, depth: Int) {
            for (f in d.listFiles()) {
                val name = f.name ?: continue
                if (f.isDirectory && depth < 4) walk(f, depth + 1)
                else if (f.isFile && (name.endsWith(".pdf", true) || f.type == "application/pdf")) {
                    out += Found(f.uri.toString(), name, f.length())
                }
            }
        }
        walk(root, 0)
        return out
    }

    private fun pageCountOf(uri: String): Int = runCatching {
        context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")!!.use { pfd ->
            PdfRenderer(pfd).use { it.pageCount }
        }
    }.getOrDefault(0)

    companion object {
        fun idFor(name: String, size: Long): String {
            val md = MessageDigest.getInstance("SHA-1").digest("$name|$size".toByteArray())
            return md.take(10).joinToString("") { "%02x".format(it) }
        }

        private val junk = Regex(
            """[\(\[\{][^\)\]\}]*(z-lib|zlib|libgen|www\.|\.com|\.org|pdfdrive|ebook)[^\)\]\}]*[\)\]\}]""",
            RegexOption.IGNORE_CASE,
        )

        fun cleanTitle(fileName: String): String {
            var t = fileName.removeSuffix(".pdf").removeSuffix(".PDF")
            t = junk.replace(t, " ")
            t = t.replace('_', ' ').replace(Regex("""\.(?=\S)"""), " ").replace(Regex("""\s+"""), " ").trim()
            if (t.isNotEmpty() && t == t.lowercase()) {
                t = t.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
            }
            return t.ifBlank { fileName }
        }
    }
}
