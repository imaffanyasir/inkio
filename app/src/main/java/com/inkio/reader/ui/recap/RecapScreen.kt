package com.inkio.reader.ui.recap

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inkio.reader.data.Book
import com.inkio.reader.ui.components.GrayscaleFilter
import com.inkio.reader.ui.library.formatDuration
import com.inkio.reader.ui.reader.ReaderViewModel
import com.inkio.reader.ui.reader.RecapState
import com.inkio.reader.ui.theme.LocalInk
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** "Welcome back" recap of everything read so far, shown when returning to a book. */
@Composable
fun RecapScreen(book: Book, vm: ReaderViewModel, onContinue: () -> Unit) {
    val p = LocalInk.current
    val state by vm.recap.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.buildRecap() }
    BackHandler(onBack = onContinue)

    Box(
        Modifier.fillMaxSize()
            .background(p.bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
    ) {
        Aurora()
        Particles()
        when (val s = state) {
            is RecapState.Ready -> RecapContent(book, s, onContinue)
            is RecapState.Loading -> Loading(s)
            null -> Loading(RecapState.Loading(0f, "Opening your book…"))
        }
        IconButton(onClick = onContinue, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) {
            Icon(Icons.Outlined.Close, "Skip recap", tint = p.muted)
        }
    }
}

// ---------------------------------------------------------------------------- background

private fun accentColors(isEink: Boolean): List<Color> =
    if (isEink) listOf(Color(0x24000000), Color(0x1A000000), Color(0x14000000))
    else listOf(Color(0x66E8A657), Color(0x558A3E14), Color(0x404B2E83))

@Composable
private fun Aurora() {
    val p = LocalInk.current
    val inf = rememberInfiniteTransition(label = "aurora")
    val t by inf.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(18000, easing = LinearEasing)), label = "t")
    val colors = remember(p.isEink) { accentColors(p.isEink) }
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val blobs = listOf(
            Triple(Offset(w * (0.25f + 0.15f * sin(t)), h * (0.18f + 0.06f * cos(t * 2))), w * 0.75f, colors[0]),
            Triple(Offset(w * (0.8f + 0.1f * cos(t)), h * (0.42f + 0.1f * sin(t))), w * 0.7f, colors[1]),
            Triple(Offset(w * (0.4f + 0.2f * sin(t + 2)), h * (0.85f + 0.05f * cos(t))), w * 0.8f, colors[2]),
        )
        for ((c, r, col) in blobs) {
            drawCircle(Brush.radialGradient(listOf(col, Color.Transparent), center = c, radius = r), radius = r, center = c)
        }
    }
}

private class Particle(val x: Float, val y: Float, val r: Float, val speed: Float, val phase: Float)

@Composable
private fun Particles() {
    val p = LocalInk.current
    val parts = remember {
        val rnd = Random(42)
        List(46) { Particle(rnd.nextFloat(), rnd.nextFloat(), 0.6f + rnd.nextFloat() * 2.2f, 0.02f + rnd.nextFloat() * 0.05f, rnd.nextFloat() * 6.28f) }
    }
    val inf = rememberInfiniteTransition(label = "particles")
    val time by inf.animateFloat(0f, 100f, infiniteRepeatable(tween(400_000, easing = LinearEasing)), label = "time")
    val color = if (p.isEink) p.ink else Color(0xFFF6CF94)
    Canvas(Modifier.fillMaxSize()) {
        for (pt in parts) {
            val y = ((pt.y - time * pt.speed) % 1f + 1f) % 1f
            val x = pt.x + 0.02f * sin(time * 3 + pt.phase)
            val tw = 0.35f + 0.65f * ((sin(time * 8 + pt.phase) + 1f) / 2f)
            drawCircle(color.copy(alpha = (if (p.isEink) 0.25f else 0.7f) * tw), radius = pt.r * density, center = Offset(x * size.width, y * size.height))
        }
    }
}

// ---------------------------------------------------------------------------- loading

@Composable
private fun Loading(s: RecapState.Loading) {
    val p = LocalInk.current
    val inf = rememberInfiniteTransition(label = "load")
    val pulse by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "pulse")
    val spin by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "spin")
    val accent = if (p.isEink) p.ink else p.accent
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                for (i in 0 until 3) {
                    val f = (pulse + i / 3f) % 1f
                    drawCircle(accent.copy(alpha = (1f - f) * 0.45f), radius = size.minDimension / 2 * (0.35f + 0.65f * f), style = Stroke(2.dp.toPx()))
                }
                rotate(spin) {
                    drawArc(
                        Brush.sweepGradient(listOf(Color.Transparent, accent)), 0f, 300f, false,
                        topLeft = Offset(size.width * 0.22f, size.height * 0.22f), size = Size(size.width * 0.56f, size.height * 0.56f),
                        style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
            Icon(Icons.Outlined.AutoStories, null, tint = accent, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("Recalling your journey", color = p.ink, style = MaterialTheme.typography.headlineSmall, fontStyle = FontStyle.Italic)
        Spacer(Modifier.height(8.dp))
        Text(s.label, color = p.muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
        Box(Modifier.width(200.dp).height(3.dp).clip(RoundedCornerShape(50)).background(p.surfaceAlt)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(s.progress.coerceIn(0.02f, 1f)).background(accent))
        }
    }
}

// ---------------------------------------------------------------------------- content

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecapContent(book: Book, s: RecapState.Ready, onContinue: () -> Unit) {
    val p = LocalInk.current
    val accent = if (p.isEink) p.ink else p.accent
    val r = s.recap
    var step = 0
    fun next(gap: Int = 160) = (step++) * gap

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
                .padding(horizontal = 22.dp).padding(top = 40.dp, bottom = 120.dp),
        ) {
            Reveal(next()) {
                Text(
                    "WELCOME BACK", color = accent, style = MaterialTheme.typography.labelLarge,
                    letterSpacing = 4.sp, fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(6.dp))
            LetterReveal(book.title, startDelay = 150)
            Spacer(Modifier.height(6.dp))
            Reveal(next(250)) {
                Text(
                    if (s.awayMs > 0) "It's been ${away(s.awayMs)} since you last turned a page. Here's where your story stands."
                    else "Here's a recap of everything you've read so far.",
                    color = p.muted, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic,
                )
            }

            Spacer(Modifier.height(22.dp))
            Reveal(next()) { ProgressPanel(book, s) }

            if (r.hasText && r.sections.isNotEmpty()) {
                Spacer(Modifier.height(26.dp))
                Reveal(next()) { Heading("The story so far") }
                Spacer(Modifier.height(12.dp))
                r.sections.forEachIndexed { i, sec ->
                    TimelineItem(sec.title, "p. ${sec.fromPage + 1}–${sec.toPage + 1}", sec.points, last = i == r.sections.lastIndex, delayMs = next(260))
                }
            }

            Spacer(Modifier.height(22.dp))
            Reveal(next()) { Heading("Where you left off") }
            Spacer(Modifier.height(12.dp))
            Reveal(next()) {
                GlowCard {
                    if (r.hasText && r.lastScene.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("“", color = accent, style = MaterialTheme.typography.displaySmall, modifier = Modifier.height(28.dp))
                            r.lastScene.forEachIndexed { i, line ->
                                TypeText(line, 900 + i * 500, italic = true)
                            }
                            Text("— around page ${s.toPage + 1}", color = p.muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.End))
                        }
                    } else {
                        Column {
                            Text(
                                "This PDF has no text layer (it's probably scanned), so here are the last pages you saw:",
                                color = p.muted, style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(12.dp))
                            ThumbFan(s)
                        }
                    }
                }
            }

            if (r.names.isNotEmpty() || r.keywords.isNotEmpty()) {
                Spacer(Modifier.height(26.dp))
                Reveal(next()) { Heading("Key threads") }
                Spacer(Modifier.height(12.dp))
                val base = next(0) + 200
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (r.names.take(6).map { it to true } + r.keywords.take(8).map { it to false }).forEachIndexed { i, (w, isName) ->
                        PopChip(w, isName, base + i * 70)
                    }
                }
            }
        }

        // pinned continue button with a soft fade behind it
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, p.bg.copy(alpha = 0.92f), p.bg)))
                .navigationBarsPadding().padding(horizontal = 22.dp, vertical = 16.dp),
        ) {
            Reveal(500) { ShimmerButton("Continue reading · page ${book.currentPage + 1}", onContinue) }
        }
    }
}

@Composable
private fun Heading(text: String) {
    val p = LocalInk.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = p.ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(p.line, Color.Transparent))))
    }
}

@Composable
private fun ProgressPanel(book: Book, s: RecapState.Ready) {
    val p = LocalInk.current
    val accent = if (p.isEink) p.ink else p.accent
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(350); anim.animateTo(book.progress, tween(1600, easing = FastOutSlowInEasing)) }
    val shape = RoundedCornerShape(p.corner * 1.4f)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(p.surface.copy(alpha = if (p.isEink) 1f else 0.7f))
            .border(p.border, p.line, shape).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(110.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 10.dp.toPx()
                val inset = sw / 2
                drawArc(p.surfaceAlt, -90f, 360f, false, topLeft = Offset(inset, inset), size = Size(size.width - sw, size.height - sw), style = Stroke(sw))
                drawArc(
                    if (p.isEink) Brush.linearGradient(listOf(p.ink, p.ink))
                    else Brush.sweepGradient(listOf(Color(0xFFB8732E), accent, Color(0xFFF6CF94), Color(0xFFB8732E))),
                    -90f, 360f * anim.value, false, topLeft = Offset(inset, inset), size = Size(size.width - sw, size.height - sw),
                    style = Stroke(sw, cap = StrokeCap.Round),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${(anim.value * 100).toInt()}%", color = p.ink, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("read", color = p.muted, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.width(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CountStat(Icons.Outlined.MenuBook, s.toPage + 1, " of ${book.pageCount} pages")
            Stat(Icons.Outlined.Schedule, formatDuration(book.readingTimeMs) + " spent reading")
            if (s.recap.wordsRead > 0) CountStat(Icons.Outlined.TextSnippet, s.recap.wordsRead, " words behind you", approx = true)
        }
    }
}

@Composable
private fun Stat(icon: ImageVector, text: String) {
    val p = LocalInk.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (p.isEink) p.ink else p.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = p.ink, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CountStat(icon: ImageVector, target: Int, suffix: String, approx: Boolean = false) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(target) { delay(400); a.animateTo(target.toFloat(), tween(1500, easing = FastOutSlowInEasing)) }
    val v = a.value.toInt()
    val shown = if (approx && target >= 1000) "~" + "%,d".format((v / 100) * 100) else "%,d".format(v)
    Stat(icon, shown + suffix)
}

@Composable
private fun TimelineItem(title: String, range: String, points: List<String>, last: Boolean, delayMs: Int) {
    val p = LocalInk.current
    val accent = if (p.isEink) p.ink else p.accent
    val line = remember { Animatable(0f) }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        appear.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(Unit) { delay(delayMs + 200L); line.animateTo(1f, tween(900)) }
    val inf = rememberInfiniteTransition(label = "node")
    val glow by inf.animateFloat(0.4f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "glow")

    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).graphicsLayer { alpha = appear.value; translationY = (1 - appear.value) * 60f }) {
        Box(Modifier.width(26.dp).fillMaxHeight().padding(top = 6.dp)) {
            Canvas(Modifier.width(26.dp).fillMaxHeight()) {
                val cx = size.width / 2
                if (!last) drawLine(
                    Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.1f))),
                    Offset(cx, 12.dp.toPx()), Offset(cx, 12.dp.toPx() + (size.height - 12.dp.toPx()) * line.value), strokeWidth = 2.dp.toPx(),
                )
                drawCircle(accent.copy(alpha = 0.25f * glow), radius = 10.dp.toPx(), center = Offset(cx, 7.dp.toPx()))
                drawCircle(accent, radius = 5.dp.toPx(), center = Offset(cx, 7.dp.toPx()))
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).padding(bottom = 18.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(title, color = p.ink, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(8.dp))
                Text(range, color = p.muted, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(6.dp))
            val shape = RoundedCornerShape(p.corner)
            Column(
                Modifier.fillMaxWidth().clip(shape).background(p.surface.copy(alpha = if (p.isEink) 1f else 0.65f))
                    .border(if (p.isEink) 1.dp else 0.5.dp, p.line, shape).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                points.forEachIndexed { i, pt ->
                    Row {
                        Text("•", color = accent, modifier = Modifier.padding(end = 8.dp))
                        TypeText(pt, delayMs + 350 + i * 300)
                    }
                }
            }
        }
    }
}

@Composable
private fun GlowCard(content: @Composable () -> Unit) {
    val p = LocalInk.current
    val inf = rememberInfiniteTransition(label = "glowcard")
    val angle by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "angle")
    val shape = RoundedCornerShape(p.corner * 1.4f)
    val borderBrush = if (p.isEink) Brush.linearGradient(listOf(p.ink, p.ink))
    else Brush.sweepGradient(listOf(Color(0xFFF6CF94), Color(0x00E8A657), Color(0xFFE8A657), Color(0x008A3E14), Color(0xFFF6CF94)))
    Box(
        Modifier.fillMaxWidth()
            .drawWithContent {
                if (!p.isEink) rotate(angle) {
                    drawCircle(Brush.radialGradient(listOf(Color(0x33E8A657), Color.Transparent)), radius = size.maxDimension * 0.7f)
                }
                drawContent()
            }
            .clip(shape)
            .background(p.surface.copy(alpha = if (p.isEink) 1f else 0.75f))
            .border(if (p.isEink) 2.dp else 1.5.dp, borderBrush, shape)
            .padding(18.dp),
    ) { content() }
}

@Composable
private fun ThumbFan(s: RecapState.Ready) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        s.thumbs.forEachIndexed { i, img ->
            val a = remember { Animatable(0f) }
            LaunchedEffect(Unit) { delay(800L + i * 180); a.animateTo(1f, spring(dampingRatio = 0.6f)) }
            val rot = (i - (s.thumbs.size - 1) / 2f) * 7f
            val p = LocalInk.current
            Image(
                img, null, contentScale = ContentScale.Fit,
                colorFilter = if (p.isEink) GrayscaleFilter else null,
                modifier = Modifier.width(96.dp).aspectRatio(0.7f).padding(horizontal = 2.dp)
                    .graphicsLayer { rotationZ = rot * a.value; alpha = a.value; translationY = (1 - a.value) * 80f; shadowElevation = 12f }
                    .border(1.dp, p.line),
            )
        }
    }
}

@Composable
private fun PopChip(text: String, isName: Boolean, delayMs: Int) {
    val p = LocalInk.current
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(delayMs.toLong()); a.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) }
    val shape = RoundedCornerShape(50)
    val filled = isName
    Text(
        text,
        color = if (filled) p.onAccent else p.ink,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.graphicsLayer { scaleX = a.value; scaleY = a.value; alpha = a.value.coerceIn(0f, 1f) }
            .clip(shape)
            .background(if (filled) (if (p.isEink) p.ink else p.accent) else p.surface.copy(alpha = 0.8f))
            .border(1.dp, if (filled) Color.Transparent else p.line, shape)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun ShimmerButton(text: String, onClick: () -> Unit) {
    val p = LocalInk.current
    val inf = rememberInfiniteTransition(label = "shimmer")
    val x by inf.animateFloat(-1f, 2f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "x")
    val shape = RoundedCornerShape(if (p.isEink) p.corner else 50.dp)
    Box(
        Modifier.fillMaxWidth().height(56.dp).clip(shape)
            .background(if (p.isEink) Brush.linearGradient(listOf(p.ink, p.ink)) else Brush.horizontalGradient(listOf(Color(0xFFF2C27F), p.accent, Color(0xFFC57E35))))
            .drawWithContent {
                drawContent()
                val w = size.width
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = if (p.isEink) 0.18f else 0.45f), Color.Transparent),
                        start = Offset(w * x - 120f, 0f), end = Offset(w * x + 120f, size.height),
                    ),
                )
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AutoStories, null, tint = p.onAccent)
            Spacer(Modifier.width(10.dp))
            Text(text, color = p.onAccent, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
    }
}

// ---------------------------------------------------------------------------- text animation

@Composable
private fun Reveal(delayMs: Int, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(delayMs.toLong()); a.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    Box(Modifier.graphicsLayer { alpha = a.value; translationY = (1 - a.value) * 40f }) { content() }
}

@Composable
private fun LetterReveal(text: String, startDelay: Int) {
    val p = LocalInk.current
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(text) {
        delay(startDelay.toLong())
        val step = (900L / text.length.coerceAtLeast(1)).coerceIn(12L, 45L)
        while (shown < text.length) { shown++; delay(step) }
    }
    Box {
        // invisible full text reserves the final height so layout doesn't jump
        Text(text, style = MaterialTheme.typography.headlineMedium, color = Color.Transparent, fontWeight = FontWeight.Bold)
        Text(
            text.take(shown), color = p.ink, style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Start,
        )
    }
}

@Composable
private fun TypeText(text: String, startDelay: Int, italic: Boolean = false) {
    val p = LocalInk.current
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(text) {
        delay(startDelay.toLong())
        while (shown < text.length) { shown = (shown + 3).coerceAtMost(text.length); delay(14) }
    }
    Box {
        Text(text, color = Color.Transparent, style = MaterialTheme.typography.bodyMedium, lineHeight = 21.sp, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal)
        Text(
            text.take(shown), color = p.ink, style = MaterialTheme.typography.bodyMedium, lineHeight = 21.sp,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        )
    }
}

private fun away(ms: Long): String {
    val h = ms / 3_600_000
    val d = h / 24
    return when {
        d >= 14 -> "${d / 7} weeks"
        d >= 2 -> "$d days"
        d == 1L -> "a day"
        h >= 2 -> "$h hours"
        h == 1L -> "an hour"
        else -> "a little while"
    }
}
