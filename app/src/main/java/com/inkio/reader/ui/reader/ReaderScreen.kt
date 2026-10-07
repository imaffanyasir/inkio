package com.inkio.reader.ui.reader

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inkio.reader.InkioApp
import com.inkio.reader.KeyBus
import com.inkio.reader.data.DitherMode
import com.inkio.reader.data.PageAnim
import com.inkio.reader.pdf.DisplayProfile
import com.inkio.reader.ui.components.EinkRefreshFlash
import com.inkio.reader.ui.components.InkButton
import com.inkio.reader.ui.components.Segmented
import com.inkio.reader.ui.components.SwitchRow
import com.inkio.reader.ui.recap.RecapScreen
import com.inkio.reader.ui.theme.LocalInk
import kotlinx.coroutines.flow.MutableSharedFlow
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(bookId: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as InkioApp
    val vm: ReaderViewModel = viewModel(key = bookId) { ReaderViewModel(app, bookId) }
    val p = LocalInk.current
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val book by vm.book.collectAsStateWithLifecycle()
    val page by vm.page.collectAsStateWithLifecycle()
    val count by vm.pageCount.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val outline by vm.outline.collectAsStateWithLifecycle()

    var menu by remember { mutableStateOf(false) }
    var showRecap by remember { mutableStateOf(vm.showRecapOnOpen) }
    var sheet by remember { mutableStateOf<String?>(null) } // "contents" | "display"
    var generation by remember { mutableIntStateOf(0) }
    var zoom by remember { mutableStateOf<Offset?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var turnsSinceRefresh by remember { mutableIntStateOf(0) }
    var ghost by remember { mutableStateOf<ImageBitmap?>(null) }
    var lastShown by remember { mutableStateOf<ImageBitmap?>(null) }
    val turnRequests = remember { MutableSharedFlow<Int>(extraBufferCapacity = 4) }

    // ---- immersive, keep-awake, volume keys, lifecycle
    val view = LocalView.current
    val activity = ctx as? Activity
    DisposableEffect(menu, showRecap) {
        val window = activity?.window
        if (window != null) {
            val ctl = WindowCompat.getInsetsController(window, view)
            ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (menu || showRecap) ctl.show(WindowInsetsCompat.Type.systemBars()) else ctl.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { }
    }
    DisposableEffect(Unit) {
        KeyBus.active = true
        onDispose {
            KeyBus.active = false
            view.keepScreenOn = false
            activity?.window?.let { WindowCompat.getInsetsController(it, view).show(WindowInsetsCompat.Type.systemBars()) }
        }
    }
    LaunchedEffect(settings.keepScreenOn) { view.keepScreenOn = settings.keepScreenOn }
    LaunchedEffect(Unit) { KeyBus.events.collect { if (!showRecap && zoom == null) turnRequests.tryEmit(it) } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_PAUSE) vm.onPause()
            if (e == Lifecycle.Event.ON_RESUME) vm.onResume()
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    BackHandler(enabled = sheet != null || zoom != null || menu) {
        when {
            sheet != null -> sheet = null
            zoom != null -> zoom = null
            else -> menu = false
        }
    }

    // ---- display processing profile
    val profile = remember(settings, p) {
        if (p.isEink) DisplayProfile(
            settings.dither, settings.grayLevels, settings.contrast, p.pagePaper, p.pageInk, settings.paperTexture,
        ) else DisplayProfile(
            DitherMode.NONE, 64, 1.05f, p.pagePaper, p.pageInk, false, process = settings.darkPagesInAmoled,
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(p.bg)) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.roundToPx() }
        val hPx = with(density) { maxHeight.roundToPx() }
        LaunchedEffect(wPx, hPx, profile, settings.trimMargins) {
            if (vm.configure(wPx, hPx, profile, settings.trimMargins)) generation++
        }

        if (error != null) {
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error!!, color = p.ink, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                InkButton("Back to library", onClick = onBack)
            }
        } else if (count > 0) {
            if (zoom == null) {
                PageTurner(
                    index = page, count = count, anim = settings.pageAnim, paper = p.bg, generation = generation,
                    ghost = if (p.isEink && settings.ghosting) ghost else null,
                    loadPage = { i -> vm.pageBitmap(i).also { if (i == page) lastShown = it } },
                    onPageChanged = { i ->
                        if (p.isEink && settings.ghosting) ghost = lastShown
                        vm.goTo(i)
                        if (p.isEink && settings.fullRefreshEvery > 0) {
                            turnsSinceRefresh++
                            if (turnsSinceRefresh >= settings.fullRefreshEvery) {
                                turnsSinceRefresh = 0; refreshTick++; ghost = null
                            }
                        }
                    },
                    onCenterTap = { menu = !menu },
                    onDoubleTap = { zoom = it },
                    turnRequests = turnRequests,
                )
                LaunchedEffect(page, generation) { lastShown = vm.pageBitmap(page) }
            } else {
                ZoomedPage(vm, page, generation, zoom!!, onExit = { zoom = null })
            }
            if (book?.isBookmarked(page) == true) BookmarkRibbon(Modifier.align(Alignment.TopEnd).padding(end = 22.dp))
        }

        EinkRefreshFlash(trigger = refreshTick, enabled = p.isEink)

        // ---- top bar
        AnimatedVisibility(
            visible = menu && !showRecap, modifier = Modifier.align(Alignment.TopCenter),
            enter = if (p.isEink) EnterTransition.None else fadeIn() + slideInVertically(),
            exit = if (p.isEink) ExitTransition.None else fadeOut() + slideOutVertically(),
        ) {
            Row(
                Modifier.fillMaxWidth().background(p.surface).statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = p.ink) }
                Column(Modifier.weight(1f)) {
                    Text(book?.title.orEmpty(), color = p.ink, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        currentChapter(outline, page) ?: "${book?.percent ?: 0}% read",
                        color = p.muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { showRecap = true; menu = false }) { Icon(Icons.Outlined.AutoAwesome, "Recap", tint = p.ink) }
                IconButton(onClick = { vm.toggleBookmark() }) {
                    Icon(
                        if (book?.isBookmarked(page) == true) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                        "Bookmark", tint = if (book?.isBookmarked(page) == true && !p.isEink) p.accent else p.ink,
                    )
                }
            }
        }

        // ---- bottom bar
        AnimatedVisibility(
            visible = menu && !showRecap, modifier = Modifier.align(Alignment.BottomCenter),
            enter = if (p.isEink) EnterTransition.None else fadeIn() + slideInVertically { it },
            exit = if (p.isEink) ExitTransition.None else fadeOut() + slideOutVertically { it },
        ) {
            var dragValue by remember { mutableFloatStateOf(-1f) }
            Column(Modifier.fillMaxWidth().background(p.surface).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Page ${(if (dragValue >= 0) dragValue.toInt() else page) + 1} of $count",
                        color = p.ink, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${((page + 1) * 100 / count.coerceAtLeast(1))}%",
                        color = p.muted, style = MaterialTheme.typography.labelLarge,
                    )
                }
                if (count > 1) Slider(
                    value = if (dragValue >= 0) dragValue else page.toFloat(),
                    onValueChange = { dragValue = it },
                    onValueChangeFinished = { vm.goTo(dragValue.toInt()); dragValue = -1f },
                    valueRange = 0f..(count - 1).toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = if (p.isEink) p.ink else p.accent,
                        activeTrackColor = if (p.isEink) p.ink else p.accent,
                        inactiveTrackColor = p.surfaceAlt,
                    ),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    BarButton(Icons.AutoMirrored.Outlined.FormatListBulleted, "Contents") { sheet = "contents" }
                    BarButton(Icons.Outlined.Tune, "Display") { sheet = "display" }
                    BarButton(Icons.Outlined.Image, "Set cover") { vm.setCurrentPageAsCover() }
                    BarButton(Icons.Outlined.AutoAwesome, "Recap") { showRecap = true; menu = false }
                }
            }
        }

        // ---- recap overlay
        if (showRecap && book != null) {
            RecapScreen(book = book!!, vm = vm, onContinue = { showRecap = false })
        }
    }

    when (sheet) {
        "contents" -> ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = p.surface) {
            ContentsSheet(vm, outline, page) { sheet = null; menu = false; vm.goTo(it) }
        }
        "display" -> ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = p.surface) {
            DisplaySheet(app)
        }
    }
}

private fun currentChapter(outline: List<com.inkio.reader.data.OutlineItem>, page: Int): String? =
    outline.filter { it.page <= page }.maxByOrNull { it.page }?.title

@Composable
private fun BarButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val p = LocalInk.current
    Column(
        Modifier.clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = p.ink, modifier = Modifier.size(22.dp))
        Text(label, color = p.muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun BookmarkRibbon(modifier: Modifier) {
    val p = LocalInk.current
    val color = if (p.isEink) p.ink else p.accent
    Canvas(modifier.size(width = 18.dp, height = 34.dp)) {
        val path = Path().apply {
            moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height)
            lineTo(size.width / 2, size.height * 0.74f); lineTo(0f, size.height); close()
        }
        drawPath(path, color)
    }
}

@Composable
private fun ZoomedPage(vm: ReaderViewModel, page: Int, generation: Int, focus: Offset, onExit: () -> Unit) {
    var bmp by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(page, generation) { bmp = vm.pageBitmap(page) }
    var scale by remember { mutableFloatStateOf(2.2f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        LaunchedEffect(Unit) { offset = Offset((w / 2 - focus.x) * (scale - 1), (h / 2 - focus.y) * (scale - 1)) }
        bmp?.let {
            Image(
                it, null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            val maxX = w * (scale - 1) / 2; val maxY = h * (scale - 1) / 2
                            offset = Offset((offset.x + pan.x).coerceIn(-maxX, maxX), (offset.y + pan.y).coerceIn(-maxY, maxY))
                            if (scale <= 1.01f) onExit()
                        }
                    }
                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onExit() }) }
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
                    },
            )
        }
    }
}

@Composable
private fun ContentsSheet(vm: ReaderViewModel, outline: List<com.inkio.reader.data.OutlineItem>, page: Int, onJump: (Int) -> Unit) {
    val p = LocalInk.current
    val book by vm.book.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(if (outline.isEmpty()) 1 else 0) }
    var editing by remember { mutableStateOf<com.inkio.reader.data.Bookmark?>(null) }
    Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
        Segmented(listOf(0 to "Chapters", 1 to "Bookmarks (${book?.bookmarks?.size ?: 0})"), tab, { tab = it })
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.fillMaxWidth().height(440.dp)) {
            if (tab == 0) {
                if (outline.isEmpty()) item {
                    Text("This PDF has no table of contents.", color = p.muted, modifier = Modifier.padding(16.dp))
                }
                items(outline) { o ->
                    val current = currentChapterItem(outline, page) == o
                    Row(
                        Modifier.fillMaxWidth().clickable { onJump(o.page) }
                            .background(if (current) p.surfaceAlt else Color.Transparent)
                            .padding(start = (8 + o.depth * 16).dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    ) {
                        Text(
                            o.title, color = p.ink, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                            fontWeight = if (o.depth == 0) FontWeight.SemiBold else FontWeight.Normal,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text("${o.page + 1}", color = p.muted, style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else {
                val bms = book?.bookmarks.orEmpty()
                if (bms.isEmpty()) item {
                    Text("No bookmarks yet. Tap the ribbon icon in the top bar to bookmark a page.", color = p.muted, modifier = Modifier.padding(16.dp))
                }
                items(bms) { b ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onJump(b.page) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Bookmark, null, tint = if (p.isEink) p.ink else p.accent)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Page ${b.page + 1}" + (currentChapter(outline, b.page)?.let { " · $it" } ?: ""), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                b.note.ifBlank { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(b.createdAt)) },
                                color = p.muted, style = MaterialTheme.typography.bodySmall, maxLines = 2,
                            )
                        }
                        IconButton(onClick = { editing = b }) { Icon(Icons.Outlined.Edit, "Note", tint = p.muted) }
                        IconButton(onClick = { vm.removeBookmark(b.page) }) { Icon(Icons.Outlined.Delete, "Delete", tint = p.muted) }
                    }
                }
            }
        }
    }
    editing?.let { b ->
        var note by remember(b) { mutableStateOf(b.note) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Note for page ${b.page + 1}") },
            text = { OutlinedTextField(note, { note = it }, minLines = 3) },
            confirmButton = { TextButton(onClick = { vm.updateBookmarkNote(b.page, note.trim()); editing = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}

private fun currentChapterItem(outline: List<com.inkio.reader.data.OutlineItem>, page: Int) =
    outline.filter { it.page <= page }.maxByOrNull { it.page }

@Composable
private fun DisplaySheet(app: InkioApp) {
    val p = LocalInk.current
    val s by app.settings.settings.collectAsStateWithLifecycle()
    Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp).verticalScroll(rememberScrollState())) {
        Text("Page turn", color = p.muted, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        Segmented(
            listOf(PageAnim.CURL to "Curl", PageAnim.SLIDE to "Slide", PageAnim.FADE to "Fade", PageAnim.NONE to "None"),
            s.pageAnim, { v -> app.settings.update { it.copy(pageAnim = v) } },
        )
        SwitchRow("Trim page margins", "Zoom past wide white borders", s.trimMargins) { v -> app.settings.update { it.copy(trimMargins = v) } }
        if (p.isEink) {
            Spacer(Modifier.height(6.dp))
            Text("Dithering", color = p.muted, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            Segmented(
                listOf(DitherMode.NONE to "Off", DitherMode.ORDERED to "Ordered", DitherMode.FLOYD to "Floyd–S."),
                s.dither, { v -> app.settings.update { it.copy(dither = v) } },
            )
            Spacer(Modifier.height(10.dp))
            Text("Contrast  ${"%.2f".format(s.contrast)}", color = p.muted, style = MaterialTheme.typography.labelMedium)
            Slider(
                value = s.contrast, onValueChange = { v -> app.settings.update { it.copy(contrast = v) } },
                valueRange = 0.8f..1.8f,
                colors = SliderDefaults.colors(thumbColor = p.ink, activeTrackColor = p.ink, inactiveTrackColor = p.surfaceAlt),
            )
            SwitchRow("Paper texture", null, s.paperTexture) { v -> app.settings.update { it.copy(paperTexture = v) } }
            SwitchRow("Ghosting", "Faint traces of the previous page, like real e-ink", s.ghosting) { v -> app.settings.update { it.copy(ghosting = v) } }
        } else {
            SwitchRow("Dark pages", "Invert pages to light text on true black", s.darkPagesInAmoled) { v -> app.settings.update { it.copy(darkPagesInAmoled = v) } }
        }
    }
}
