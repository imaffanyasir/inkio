package com.inkio.reader.ui.reader

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inkio.reader.InkioApp
import com.inkio.reader.data.Book
import com.inkio.reader.data.Bookmark
import com.inkio.reader.data.CoverSource
import com.inkio.reader.data.OutlineItem
import com.inkio.reader.pdf.DisplayProfile
import com.inkio.reader.pdf.EinkProcessor
import com.inkio.reader.pdf.PdfSession
import com.inkio.reader.summary.Recap
import com.inkio.reader.summary.Summarizer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface RecapState {
    data class Loading(val progress: Float, val label: String) : RecapState
    data class Ready(
        val recap: Recap,
        val fromPage: Int,
        val toPage: Int,
        val awayMs: Long,
        val thumbs: List<ImageBitmap>,
    ) : RecapState
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModel(private val app: InkioApp, val bookId: String) : ViewModel() {

    val book: StateFlow<Book?> = app.library.bookFlow().map { it[bookId] }
        .stateIn(viewModelScope, SharingStarted.Eagerly, app.library.book(bookId))

    private val _page = MutableStateFlow(app.library.book(bookId)?.currentPage ?: 0)
    val page: StateFlow<Int> = _page.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _pageCount = MutableStateFlow(app.library.book(bookId)?.pageCount ?: 0)
    val pageCount: StateFlow<Int> = _pageCount.asStateFlow()

    private val _outline = MutableStateFlow<List<OutlineItem>>(emptyList())
    val outline: StateFlow<List<OutlineItem>> = _outline.asStateFlow()

    private val _recap = MutableStateFlow<RecapState?>(null)
    val recap: StateFlow<RecapState?> = _recap.asStateFlow()

    /** Whether the "welcome back" recap should greet the reader on open. */
    val showRecapOnOpen: Boolean
    private val previousReadAt: Long

    private val sessionReady = CompletableDeferred<PdfSession?>()
    private var session: PdfSession? = null
    private val renderDispatcher = Dispatchers.Default.limitedParallelism(2)

    private var width = 0
    private var height = 0
    private var profile: DisplayProfile? = null
    private var trim = true
    private val cache = LinkedHashMap<Int, Deferred<ImageBitmap?>>()

    private var activeSince = System.currentTimeMillis()
    private var textJob: Job? = null

    init {
        val b = app.library.book(bookId)
        val s = app.settings.current
        previousReadAt = b?.lastReadAt ?: 0
        val away = System.currentTimeMillis() - previousReadAt
        showRecapOnOpen = b != null && s.recapEnabled && previousReadAt > 0 && !b.finished &&
            b.currentPage + 1 >= s.recapMinPages && away >= s.recapAfterHours * 3_600_000L

        viewModelScope.launch(Dispatchers.IO) {
            val bk = b ?: run { _error.value = "Book not found"; sessionReady.complete(null); return@launch }
            try {
                val sess = PdfSession.open(app, bk.uri)
                session = sess
                _pageCount.value = sess.pageCount
                if (bk.pageCount != sess.pageCount) app.library.update(bookId) { it.copy(pageCount = sess.pageCount) }
                _page.value = bk.currentPage.coerceIn(0, (sess.pageCount - 1).coerceAtLeast(0))
                sessionReady.complete(sess)
            } catch (t: Throwable) {
                _error.value = when (t) {
                    is SecurityException -> "This PDF is password-protected."
                    else -> "Couldn't open this PDF (${t.message ?: t.javaClass.simpleName})."
                }
                sessionReady.complete(null)
            }
            app.library.update(bookId) { it.copy(lastReadAt = System.currentTimeMillis()) }
            app.settings.update { it.copy(lastReadAt = System.currentTimeMillis()) }
            runCatching { _outline.value = app.text.outline(bk) }
        }
    }

    // ------------------------------------------------------------------ rendering

    /** Called by the UI whenever the viewport or display processing changes. */
    fun configure(w: Int, h: Int, p: DisplayProfile, trimMargins: Boolean): Boolean {
        if (w == width && h == height && p == profile && trimMargins == trim) return false
        width = w; height = h; profile = p; trim = trimMargins
        synchronized(cache) { cache.values.forEach { it.cancel() }; cache.clear() }
        return true
    }

    suspend fun pageBitmap(index: Int): ImageBitmap? {
        val sess = sessionReady.await() ?: return null
        if (index !in 0 until sess.pageCount || width <= 0) return null
        val d = synchronized(cache) {
            cache.getOrPut(index) {
                val w = width; val h = height; val p = profile; val t = trim
                viewModelScope.async(renderDispatcher) {
                    runCatching {
                        val bmp: Bitmap = sess.renderPage(index, w, h, t)
                        if (p != null) EinkProcessor.process(bmp, p)
                        bmp.asImageBitmap()
                    }.getOrNull()
                }
            }.also {
                // keep a small window of pages around the current one
                val cur = _page.value
                cache.keys.filter { k -> k !in (cur - 3)..(cur + 3) && k != index }.forEach { k -> cache.remove(k)?.cancel() }
            }
        }
        return runCatching { d.await() }.getOrNull()
    }

    // ------------------------------------------------------------------ navigation

    fun goTo(index: Int) {
        val count = _pageCount.value
        if (count <= 0) return
        val i = index.coerceIn(0, count - 1)
        if (i == _page.value) return
        _page.value = i
        commitTime()
        app.library.update(bookId) {
            it.copy(
                currentPage = i,
                lastReadAt = System.currentTimeMillis(),
                finished = it.finished || i >= count - 1,
            )
        }
        app.settings.update { it.copy(lastReadAt = System.currentTimeMillis()) }
        cacheTextSoon(i)
    }

    /** Text of pages you read is cached in the background so recaps are instant later. */
    private fun cacheTextSoon(i: Int) {
        textJob?.cancel()
        textJob = viewModelScope.launch(Dispatchers.IO) {
            delay(2500)
            val b = book.value ?: return@launch
            val pages = (maxOf(0, i - 1)..i).filterNot { app.text.isCached(bookId, it) }
            if (pages.isNotEmpty()) runCatching { app.text.pages(b, pages) }
        }
    }

    private fun commitTime() {
        val now = System.currentTimeMillis()
        val delta = (now - activeSince).coerceIn(0, 10 * 60_000L) // ignore idle stretches > 10 min
        activeSince = now
        if (delta > 0) app.library.update(bookId) { it.copy(readingTimeMs = it.readingTimeMs + delta) }
    }

    fun onPause() = commitTime()
    fun onResume() { activeSince = System.currentTimeMillis() }

    // ------------------------------------------------------------------ bookmarks & cover

    fun toggleBookmark(note: String = "") {
        val p = _page.value
        app.library.update(bookId) { b ->
            if (b.isBookmarked(p)) b.copy(bookmarks = b.bookmarks.filterNot { it.page == p })
            else b.copy(bookmarks = (b.bookmarks + Bookmark(p, System.currentTimeMillis(), note)).sortedBy { it.page })
        }
    }

    fun updateBookmarkNote(page: Int, note: String) = app.library.update(bookId) { b ->
        b.copy(bookmarks = b.bookmarks.map { if (it.page == page) it.copy(note = note) else it })
    }

    fun removeBookmark(page: Int) = app.library.update(bookId) { b ->
        b.copy(bookmarks = b.bookmarks.filterNot { it.page == page })
    }

    fun setCurrentPageAsCover() {
        val b = book.value ?: return
        val p = _page.value
        app.appScope.launch {
            app.covers.fromPdf(b, p)?.let { path -> app.library.update(bookId) { it.copy(coverPath = path, coverSource = CoverSource.PDF) } }
        }
    }

    // ------------------------------------------------------------------ recap

    fun buildRecap() {
        if (_recap.value is RecapState.Ready) return
        val b = book.value ?: return
        _recap.value = RecapState.Loading(0f, "Opening your book…")
        viewModelScope.launch {
            val sess = sessionReady.await()
            val to = b.currentPage.coerceAtLeast(0)
            val all = (0..to).toList()
            val pages = if (all.size <= 160) all else {
                val tail = all.takeLast(40)
                val head = all.dropLast(40)
                val step = head.size / 120f
                (0 until 120).map { head[(it * step).toInt()] }.distinct() + tail
            }
            val texts = runCatching {
                app.text.pages(b, pages) { done, total ->
                    _recap.value = RecapState.Loading(done / total.toFloat(), "Recalling page ${pages.getOrNull(done - 1)?.plus(1) ?: ""}…")
                }
            }.getOrDefault(emptyMap())
            _recap.value = RecapState.Loading(1f, "Weaving your story together…")
            val outline = _outline.value.ifEmpty { runCatching { app.text.outline(b) }.getOrDefault(emptyList()) }
            val recap = withContext(Dispatchers.Default) { Summarizer.summarize(texts, 0, to, outline) }
            val thumbs = if (sess == null) emptyList() else (maxOf(0, to - 2)..to).mapNotNull { i ->
                runCatching { sess.renderThumb(i, 360).asImageBitmap() }.getOrNull()
            }
            val away = if (previousReadAt > 0) System.currentTimeMillis() - previousReadAt else 0L
            _recap.value = RecapState.Ready(recap, 0, to, away, thumbs)
        }
    }

    override fun onCleared() {
        commitTime()
        val s = session
        app.appScope.launch(NonCancellable) {
            s?.closeSafely()
            app.text.release()
        }
        super.onCleared()
    }
}
