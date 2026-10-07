package com.inkio.reader.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.inkio.reader.R
import com.inkio.reader.data.Book
import com.inkio.reader.ui.theme.LocalInk
import kotlinx.coroutines.delay
import java.io.File
import kotlin.random.Random

val GrayscaleFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

private val grainBitmap: ImageBitmap by lazy {
    val s = 160
    val r = Random(7)
    val px = IntArray(s * s) {
        val a = r.nextInt(0, 60)
        val v = if (r.nextBoolean()) 0 else 255
        (a shl 24) or (v shl 16) or (v shl 8) or v
    }
    Bitmap.createBitmap(px, s, s, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** Theme background; on e-ink it gets a faint paper grain like real e-paper. */
fun Modifier.paperBackground(): Modifier = this.then(Modifier.drawBehind { }).composedPaper()

private fun Modifier.composedPaper(): Modifier = androidx.compose.ui.composed {
    val p = LocalInk.current
    val brush = remember { ShaderBrush(ImageShader(grainBitmap, TileMode.Repeated, TileMode.Repeated)) }
    this.background(p.bg).drawBehind {
        if (p.isEink) drawRect(brush, alpha = 0.22f)
    }
}

@Composable
fun InkCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Int = 16,
    content: @Composable () -> Unit,
) {
    val p = LocalInk.current
    val shape = RoundedCornerShape(p.corner)
    Box(
        modifier
            .clip(shape)
            .background(p.surface)
            .border(BorderStroke(p.border, if (p.isEink) p.line else p.line), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding.dp),
    ) { content() }
}

@Composable
fun InkButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val p = LocalInk.current
    val shape = RoundedCornerShape(if (p.isEink) p.corner else 50.dp)
    val bg = when {
        !primary -> Color.Transparent
        p.isEink -> p.ink
        else -> p.accent
    }
    val fg = if (primary) p.onAccent else p.ink
    Row(
        modifier
            .clip(shape)
            .then(
                if (primary && !p.isEink) Modifier.background(
                    Brush.horizontalGradient(listOf(Color(0xFFF2C27F), p.accent, Color(0xFFD08A3C))),
                ) else Modifier.background(bg)
            )
            .border(p.border, if (primary) Color.Transparent else p.line, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    val p = LocalInk.current
    Column(modifier.padding(top = 20.dp, bottom = 8.dp)) {
        Text(
            text.uppercase(), color = if (p.isEink) p.ink else p.accent,
            style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp, fontWeight = FontWeight.Bold,
        )
        if (p.isEink) {
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalInk.current
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.ink, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, color = p.muted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = if (p.isEink) SwitchDefaults.colors(
                checkedThumbColor = p.surface, checkedTrackColor = p.ink, checkedBorderColor = p.ink,
                uncheckedThumbColor = p.ink, uncheckedTrackColor = p.surface, uncheckedBorderColor = p.ink,
            ) else SwitchDefaults.colors(
                checkedThumbColor = p.onAccent, checkedTrackColor = p.accent,
                uncheckedThumbColor = p.muted, uncheckedTrackColor = p.surfaceAlt, uncheckedBorderColor = p.line,
            ),
        )
    }
}

/** Row of mutually exclusive options, drawn as an e-ink style segmented control. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalInk.current
    val shape = RoundedCornerShape(if (p.isEink) p.corner else 50.dp)
    Row(modifier.fillMaxWidth().clip(shape).border(p.border, p.line, shape)) {
        options.forEachIndexed { i, (value, label) ->
            val sel = value == selected
            Box(
                Modifier.weight(1f)
                    .background(if (sel) (if (p.isEink) p.ink else p.accent) else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, color = if (sel) p.onAccent else p.ink, style = MaterialTheme.typography.labelMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                )
            }
            if (i < options.lastIndex) Box(Modifier.width(p.border).fillMaxHeight().background(p.line))
        }
    }
}

@Composable
fun InkProgress(progress: Float, modifier: Modifier = Modifier, height: Int = 6) {
    val p = LocalInk.current
    val shape = RoundedCornerShape(if (p.isEink) 1.dp else 50.dp)
    Box(
        modifier.height(height.dp).clip(shape)
            .background(if (p.isEink) p.surface else p.surfaceAlt)
            .then(if (p.isEink) Modifier.border(1.dp, p.line, shape) else Modifier),
    ) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(
                    if (p.isEink) Brush.horizontalGradient(listOf(p.ink, p.ink))
                    else Brush.horizontalGradient(listOf(Color(0xFFB8732E), p.accent, Color(0xFFF6CF94))),
                ),
        )
    }
}

/** Inkio wordmark: colour logo on AMOLED, greyscale like an e-ink panel would show it. */
@Composable
fun InkioLogo(modifier: Modifier = Modifier) {
    val p = LocalInk.current
    Image(
        painterResource(if (p.isEink) R.drawable.inkio_logo else R.drawable.inkio_icon),
        contentDescription = "Inkio",
        modifier = modifier,
        colorFilter = if (p.isEink) GrayscaleFilter else null,
        contentScale = ContentScale.Fit,
    )
}

@Composable
fun BookCover(book: Book, modifier: Modifier = Modifier) {
    val p = LocalInk.current
    val ctx = LocalContext.current
    val shape = RoundedCornerShape(if (p.isEink) 3.dp else 10.dp)
    Box(
        modifier.clip(shape).background(p.surfaceAlt)
            .border(if (p.isEink) 1.dp else 0.5.dp, p.line, shape),
    ) {
        val path = book.coverPath
        if (path != null && File(path).exists()) {
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(File(path)).crossfade(!p.isEink).build(),
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                colorFilter = if (p.isEink) GrayscaleFilter else null,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            GeneratedCover(book.title)
        }
        // subtle spine highlight
        Box(
            Modifier.fillMaxHeight().width(6.dp).background(
                Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent)),
            ),
        )
    }
}

@Composable
private fun GeneratedCover(title: String) {
    val p = LocalInk.current
    val seed = title.hashCode()
    val tones = if (p.isEink) listOf(Color(0xFF2B2B2B), Color(0xFF4A4A4A), Color(0xFF3A3A3A))
    else listOf(Color(0xFF3B2A17), Color(0xFF1E2A33), Color(0xFF33202A), Color(0xFF1F2E22))
    val bg = tones[Math.floorMod(seed, tones.size)]
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bg, bg.copy(alpha = 0.85f), Color.Black))),
    ) {
        Column(
            Modifier.fillMaxSize().padding(10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.width(28.dp).height(1.dp).background(Color(0xFFE8D9C0)))
            Spacer(Modifier.height(8.dp))
            Text(
                title, color = Color(0xFFF2E6D2), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleSmall, maxLines = 5, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.width(28.dp).height(1.dp).background(Color(0xFFE8D9C0)))
        }
    }
}

/**
 * Full-screen e-ink "refresh": the panel flashes black, then paper, then shows the
 * new content — like a real e-reader doing a full waveform update.
 */
@Composable
fun EinkRefreshFlash(trigger: Any?, enabled: Boolean) {
    val p = LocalInk.current
    var first by remember { mutableStateOf(true) }
    val alpha = remember { Animatable(0f) }
    var dark by remember { mutableStateOf(true) }
    LaunchedEffect(trigger) {
        if (first) { first = false; return@LaunchedEffect }
        if (!enabled) return@LaunchedEffect
        dark = true; alpha.snapTo(1f); delay(70)
        dark = false; delay(60)
        alpha.animateTo(0f, tween(110))
    }
    if (alpha.value > 0f) {
        Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value }.background(if (dark) p.ink else p.bg))
    }
}

@Composable
fun IconText(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    val p = LocalInk.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = p.muted, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, color = p.muted, style = MaterialTheme.typography.labelSmall)
    }
}
