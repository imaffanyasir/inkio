package com.inkio.reader.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.inkio.reader.InkioApp
import com.inkio.reader.data.Book
import com.inkio.reader.data.CoverSource
import com.inkio.reader.data.LibraryRepository
import com.inkio.reader.ui.components.BookCover
import com.inkio.reader.ui.components.GrayscaleFilter
import com.inkio.reader.ui.components.IconText
import com.inkio.reader.ui.components.InkButton
import com.inkio.reader.ui.components.InkCard
import com.inkio.reader.ui.components.InkProgress
import com.inkio.reader.ui.components.InkioLogo
import com.inkio.reader.ui.components.Segmented
import com.inkio.reader.ui.components.rememberFolderPicker
import com.inkio.reader.ui.components.rememberStorageAccessRequest
import com.inkio.reader.ui.theme.LocalInk
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

private enum class Filter { ALL, READING, UNREAD, DONE }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(onOpen: (String) -> Unit, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as InkioApp
    val p = LocalInk.current
    val scope = rememberCoroutineScope()
    val books by app.library.books.collectAsStateWithLifecycle()
    val scanning by app.library.scanning.collectAsStateWithLifecycle()
    val problem by app.library.problem.collectAsStateWithLifecycle()
    val settings by app.settings.settings.collectAsStateWithLifecycle()

    var filter by remember { mutableStateOf(Filter.ALL) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var optionsFor by remember { mutableStateOf<Book?>(null) }

    val rescan: () -> Unit = { scope.launch { app.library.scan() } }
    val requestAccess = rememberStorageAccessRequest(onResult = rescan)
    val pickFolder = rememberFolderPicker(onPicked = rescan)

    LaunchedEffect(settings.source, settings.customTreeUri) { app.library.scan() }

    val continueBook = books.filter { it.started && !it.finished }.maxByOrNull { it.lastReadAt }
    val shown = books
        .filter {
            when (filter) {
                Filter.ALL -> true
                Filter.READING -> it.started && !it.finished
                Filter.UNREAD -> !it.started
                Filter.DONE -> it.finished
            }
        }
        .filter { query.isBlank() || it.title.contains(query, true) || it.fileName.contains(query, true) }
        .let { list ->
            when (settings.sort) {
                "title" -> list.sortedBy { it.title.lowercase() }
                "progress" -> list.sortedByDescending { it.progress }
                else -> list.sortedWith(compareByDescending<Book> { it.lastReadAt }.thenByDescending { it.addedAt })
            }
        }

    PullToRefreshBox(isRefreshing = scanning, onRefresh = rescan, modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(108.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.statusBarsPadding().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    InkioLogo(Modifier.size(52.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Library", style = MaterialTheme.typography.headlineSmall, color = p.ink, fontWeight = FontWeight.Bold)
                        Text(
                            "${books.size} books · ${books.count { it.started && !it.finished }} in progress",
                            color = p.muted, style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                        Icon(Icons.Outlined.Search, "Search", tint = p.ink)
                    }
                    IconButton(onClick = rescan) {
                        if (scanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = p.ink)
                        else Icon(Icons.Outlined.Refresh, "Rescan", tint = p.ink)
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Settings", tint = p.ink) }
                }
            }

            if (searching) item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Search titles") }, modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = { IconButton(onClick = { query = ""; searching = false }) { Icon(Icons.Outlined.Close, null) } },
                )
            }

            problem?.let { pr ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ProblemCard(pr, onGrant = requestAccess, onPick = pickFolder)
                }
            }

            if (continueBook != null && query.isBlank()) item(span = { GridItemSpan(maxLineSpan) }) {
                ContinueCard(continueBook) { onOpen(continueBook.id) }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Segmented(
                        listOf(Filter.ALL to "All", Filter.READING to "Reading", Filter.UNREAD to "Unread", Filter.DONE to "Finished"),
                        filter, { filter = it },
                    )
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Sort:", color = p.muted, style = MaterialTheme.typography.labelMedium)
                        listOf("recent" to "Recent", "title" to "Title", "progress" to "Progress").forEach { (k, l) ->
                            Text(
                                l, color = if (settings.sort == k) p.ink else p.muted,
                                fontWeight = if (settings.sort == k) FontWeight.Bold else FontWeight.Normal,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.clip(RoundedCornerShape(4.dp))
                                    .clickable { app.settings.update { it.copy(sort = k) } }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            if (shown.isEmpty() && problem == null) item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(scanning, settings.source.name)
            }

            items(shown, key = { it.id }) { book ->
                BookTile(
                    book,
                    Modifier.combinedClickable(onClick = { onOpen(book.id) }, onLongClick = { optionsFor = book }),
                )
            }
        }
    }

    optionsFor?.let { b ->
        val live = books.find { it.id == b.id } ?: b
        BookOptionsSheet(live, onDismiss = { optionsFor = null }, onOpen = { optionsFor = null; onOpen(live.id) })
    }
}

@Composable
private fun ContinueCard(book: Book, onClick: () -> Unit) {
    val p = LocalInk.current
    InkCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BookCover(book, Modifier.width(78.dp).aspectRatio(0.68f))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "CONTINUE READING", color = if (p.isEink) p.muted else p.accent,
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(book.title, color = p.ink, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Page ${book.currentPage + 1} of ${book.pageCount} · ${lastReadLabel(book.lastReadAt)}",
                    color = p.muted, style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InkProgress(book.progress, Modifier.weight(1f), height = 7)
                    Spacer(Modifier.width(8.dp))
                    Text("${book.percent}%", color = p.ink, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun BookTile(book: Book, modifier: Modifier) {
    val p = LocalInk.current
    Column(modifier) {
        Box {
            BookCover(book, Modifier.fillMaxWidth().aspectRatio(0.68f))
            if (book.finished) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(50))
                        .background(if (p.isEink) p.ink else p.accent).padding(3.dp),
                ) { Icon(Icons.Outlined.TaskAlt, null, tint = p.onAccent, modifier = Modifier.size(14.dp)) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            book.title, color = p.ink, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2,
        )
        Spacer(Modifier.height(4.dp))
        InkProgress(book.progress, Modifier.fillMaxWidth(), height = 4)
        Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    book.finished -> "Finished"
                    !book.started -> "New · ${book.pageCount} p"
                    else -> "${book.percent}% read"
                },
                color = p.muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f),
            )
            if (book.bookmarks.isNotEmpty()) IconText(Icons.Outlined.Bookmark, "${book.bookmarks.size}")
        }
    }
}

@Composable
private fun ProblemCard(problem: LibraryRepository.ScanProblem, onGrant: () -> Unit, onPick: () -> Unit) {
    val p = LocalInk.current
    InkCard(Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.FolderOff, null, tint = p.ink)
                Spacer(Modifier.width(8.dp))
                Text(
                    when (problem) {
                        LibraryRepository.ScanProblem.NEEDS_PERMISSION -> "Inkio can't see your books yet"
                        LibraryRepository.ScanProblem.FOLDER_MISSING -> "That folder doesn't exist"
                        LibraryRepository.ScanProblem.NO_FOLDER_CHOSEN -> "Choose a folder for your library"
                    },
                    color = p.ink, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Allow file access to read PDFs from Books or Download, or pick any folder instead.",
                color = p.muted, style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InkButton("Grant access", icon = Icons.Outlined.LockOpen, onClick = onGrant)
                InkButton("Pick folder", primary = false, onClick = onPick)
            }
        }
    }
}

@Composable
private fun EmptyState(scanning: Boolean, source: String) {
    val p = LocalInk.current
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.AutoStories, null, tint = p.muted, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text(if (scanning) "Looking for books…" else "No PDFs found", color = p.ink, style = MaterialTheme.typography.titleMedium)
        Text(
            if (source == "DOWNLOADS") "Put PDF files in your Download folder and pull to refresh."
            else "Copy PDF files into your Books folder and pull to refresh.",
            color = p.muted, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic,
        )
    }
}

fun lastReadLabel(t: Long): String {
    if (t == 0L) return "not started"
    val d = System.currentTimeMillis() - t
    val m = TimeUnit.MILLISECONDS.toMinutes(d)
    return when {
        m < 2 -> "just now"
        m < 60 -> "$m min ago"
        m < 60 * 24 -> "${m / 60} h ago"
        m < 60 * 24 * 2 -> "yesterday"
        else -> "${m / (60 * 24)} days ago"
    }
}

// ------------------------------------------------------------------ book options

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookOptionsSheet(book: Book, onDismiss: () -> Unit, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as InkioApp
    val p = LocalInk.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var onlineResults by remember { mutableStateOf<List<String>?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = "Saving cover…"
            app.covers.fromImage(book, uri)?.let { path -> app.library.update(book.id) { it.copy(coverPath = path, coverSource = CoverSource.CUSTOM) } }
            busy = null
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.surface) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BookCover(book, Modifier.width(64.dp).aspectRatio(0.68f))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(book.title, color = p.ink, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    Text(book.fileName, color = p.muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    Row {
                        IconText(Icons.Outlined.AutoStories, "${book.pageCount} pages")
                        Spacer(Modifier.width(10.dp))
                        IconText(Icons.Outlined.Schedule, formatDuration(book.readingTimeMs))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (busy != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = p.ink)
                    Spacer(Modifier.width(10.dp))
                    Text(busy!!, color = p.muted)
                }
            }
            OptionRow(Icons.Outlined.AutoStories, if (book.started) "Continue reading" else "Start reading", onOpen)
            Text("COVER", color = p.muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
            OptionRow(Icons.Outlined.PictureAsPdf, "Use first page of the PDF") {
                scope.launch {
                    busy = "Extracting cover…"
                    app.covers.fromPdf(book, 0)?.let { path -> app.library.update(book.id) { it.copy(coverPath = path, coverSource = CoverSource.PDF) } }
                    busy = null
                }
            }
            OptionRow(Icons.Outlined.CloudDownload, "Find cover online") {
                scope.launch {
                    busy = "Searching online…"
                    onlineResults = runCatching { app.covers.searchOnline(book.title, book.author) }.getOrDefault(emptyList())
                    busy = null
                }
            }
            OptionRow(Icons.Outlined.Image, "Upload my own image") {
                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            Text("BOOK", color = p.muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
            OptionRow(Icons.Outlined.Edit, "Rename") { renaming = true }
            OptionRow(Icons.Outlined.TaskAlt, if (book.finished) "Mark as not finished" else "Mark as finished") {
                app.library.update(book.id) { it.copy(finished = !it.finished, lastReadAt = maxOf(it.lastReadAt, System.currentTimeMillis())) }
            }
            OptionRow(Icons.Outlined.RestartAlt, "Reset reading progress") { confirmReset = true }
        }
    }

    onlineResults?.let { urls ->
        AlertDialog(
            onDismissRequest = { onlineResults = null },
            title = { Text("Choose a cover") },
            text = {
                if (urls.isEmpty()) Text("No covers found — check your internet connection or rename the book to its exact title.")
                else LazyVerticalGrid(GridCells.Fixed(3), Modifier.height(360.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(urls) { url ->
                        AsyncImage(
                            model = url, contentDescription = null, contentScale = ContentScale.Crop,
                            colorFilter = if (p.isEink) GrayscaleFilter else null,
                            modifier = Modifier.aspectRatio(0.68f).clip(RoundedCornerShape(4.dp))
                                .border(1.dp, p.line, RoundedCornerShape(4.dp))
                                .clickable {
                                    onlineResults = null
                                    scope.launch {
                                        busy = "Downloading cover…"
                                        app.covers.download(book, url)?.let { path ->
                                            app.library.update(book.id) { it.copy(coverPath = path, coverSource = CoverSource.ONLINE) }
                                        }
                                        busy = null
                                    }
                                },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onlineResults = null }) { Text("Close") } },
        )
    }

    if (renaming) {
        var title by remember { mutableStateOf(book.title) }
        var author by remember { mutableStateOf(book.author) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Edit details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
                    OutlinedTextField(author, { author = it }, label = { Text("Author (helps find covers)") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    app.library.update(book.id) { it.copy(title = title.trim().ifBlank { it.title }, author = author.trim()) }
                    renaming = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset progress?") },
            text = { Text("Your position and reading time for this book will be cleared. Bookmarks are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    app.library.update(book.id) { it.copy(currentPage = 0, lastReadAt = 0, readingTimeMs = 0, finished = false) }
                    confirmReset = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun OptionRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    val p = LocalInk.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(p.corner)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = p.ink, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, color = p.ink, style = MaterialTheme.typography.bodyLarge)
    }
}

fun formatDuration(ms: Long): String {
    val m = TimeUnit.MILLISECONDS.toMinutes(ms)
    return when {
        m < 1 -> "0 min"
        m < 60 -> "$m min"
        else -> "${m / 60} h ${m % 60} min"
    }
}
