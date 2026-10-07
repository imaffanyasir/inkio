package com.inkio.reader.ui.theme

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.inkio.reader.data.AppTheme

@Immutable
data class InkPalette(
    val isEink: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val corner: Dp,
    val border: Dp,
) {
    val pagePaper: Int get() = bg.toArgb()
    val pageInk: Int get() = if (isEink) 0xFF151515.toInt() else 0xFFDDD6CA.toInt()
}

val EinkPalette = InkPalette(
    isEink = true,
    bg = Color(0xFFE8E5DE),
    surface = Color(0xFFF0EDE7),
    surfaceAlt = Color(0xFFDCD8D0),
    ink = Color(0xFF191919),
    muted = Color(0xFF5F5B55),
    line = Color(0xFF2A2A2A),
    accent = Color(0xFF191919),
    onAccent = Color(0xFFF0EDE7),
    accentSoft = Color(0xFFC9C5BD),
    corner = 6.dp,
    border = 1.5.dp,
)

val AmoledPalette = InkPalette(
    isEink = false,
    bg = Color(0xFF000000),
    surface = Color(0xFF0B0B0B),
    surfaceAlt = Color(0xFF17140F),
    ink = Color(0xFFEFE8DC),
    muted = Color(0xFF8F877B),
    line = Color(0xFF2A251E),
    accent = Color(0xFFE8A657),
    onAccent = Color(0xFF120D06),
    accentSoft = Color(0xFF3A2A16),
    corner = 18.dp,
    border = 1.dp,
)

val LocalInk = staticCompositionLocalOf { EinkPalette }

private fun serifTypography(): Typography {
    val base = Typography()
    fun TextStyle.serif() = copy(fontFamily = FontFamily.Serif)
    return Typography(
        displayLarge = base.displayLarge.serif(), displayMedium = base.displayMedium.serif(),
        displaySmall = base.displaySmall.serif(), headlineLarge = base.headlineLarge.serif(),
        headlineMedium = base.headlineMedium.serif(), headlineSmall = base.headlineSmall.serif(),
        titleLarge = base.titleLarge.serif(), titleMedium = base.titleMedium.serif(),
        titleSmall = base.titleSmall.serif(), bodyLarge = base.bodyLarge.serif(),
        bodyMedium = base.bodyMedium.serif(), bodySmall = base.bodySmall.serif(),
        labelLarge = base.labelLarge.serif(), labelMedium = base.labelMedium.serif(),
        labelSmall = base.labelSmall.serif(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InkioTheme(theme: AppTheme, content: @Composable () -> Unit) {
    val p = if (theme == AppTheme.EINK) EinkPalette else AmoledPalette
    val scheme = if (p.isEink) lightColorScheme(
        primary = p.ink, onPrimary = p.onAccent, primaryContainer = p.surfaceAlt, onPrimaryContainer = p.ink,
        secondary = p.ink, onSecondary = p.onAccent, secondaryContainer = p.surfaceAlt, onSecondaryContainer = p.ink,
        tertiary = p.ink, background = p.bg, onBackground = p.ink, surface = p.surface, onSurface = p.ink,
        surfaceVariant = p.surfaceAlt, onSurfaceVariant = p.muted, outline = p.line, outlineVariant = p.accentSoft,
        surfaceContainerLowest = p.surface, surfaceContainerLow = p.surface, surfaceContainer = p.surface,
        surfaceContainerHigh = p.surface, surfaceContainerHighest = p.surfaceAlt, inverseSurface = p.ink,
        inverseOnSurface = p.surface, scrim = Color(0x66000000),
    ) else darkColorScheme(
        primary = p.accent, onPrimary = p.onAccent, primaryContainer = p.accentSoft, onPrimaryContainer = p.accent,
        secondary = p.accent, onSecondary = p.onAccent, secondaryContainer = p.surfaceAlt, onSecondaryContainer = p.ink,
        tertiary = p.accent, background = p.bg, onBackground = p.ink, surface = p.surface, onSurface = p.ink,
        surfaceVariant = p.surfaceAlt, onSurfaceVariant = p.muted, outline = p.line, outlineVariant = p.line,
        surfaceContainerLowest = p.bg, surfaceContainerLow = p.surface, surfaceContainer = p.surface,
        surfaceContainerHigh = Color(0xFF121110), surfaceContainerHighest = p.surfaceAlt,
        inverseSurface = p.ink, inverseOnSurface = p.bg,
    )
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(p.corner / 2), small = RoundedCornerShape(p.corner),
        medium = RoundedCornerShape(p.corner), large = RoundedCornerShape(p.corner * 1.3f),
        extraLarge = RoundedCornerShape(p.corner * 1.6f),
    )
    MaterialTheme(colorScheme = scheme, typography = serifTypography(), shapes = shapes) {
        if (p.isEink) {
            // E-paper has no ripples: taps simply invert / redraw.
            CompositionLocalProvider(LocalInk provides p, LocalRippleConfiguration provides null, content = content)
        } else {
            CompositionLocalProvider(
                LocalInk provides p,
                LocalRippleConfiguration provides RippleConfiguration(color = p.accent),
                content = content,
            )
        }
    }
}
