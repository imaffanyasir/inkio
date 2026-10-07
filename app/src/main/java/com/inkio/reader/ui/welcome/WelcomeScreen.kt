package com.inkio.reader.ui.welcome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inkio.reader.InkioApp
import com.inkio.reader.data.AppTheme
import com.inkio.reader.data.LibrarySource
import com.inkio.reader.data.StorageAccess
import com.inkio.reader.reminders.ReminderScheduler
import com.inkio.reader.ui.components.InkButton
import com.inkio.reader.ui.components.InkioLogo
import com.inkio.reader.ui.components.SectionHeader
import com.inkio.reader.ui.components.rememberFolderPicker
import com.inkio.reader.ui.components.rememberNotificationRequest
import com.inkio.reader.ui.components.rememberOnResume
import com.inkio.reader.ui.components.rememberStorageAccessRequest
import com.inkio.reader.ui.theme.LocalInk
import kotlinx.coroutines.launch

@Composable
fun WelcomeScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as InkioApp
    val s by app.settings.settings.collectAsStateWithLifecycle()
    val p = LocalInk.current

    val hasAccess = rememberOnResume { StorageAccess.hasAllFilesAccess(ctx) }
    val notifOk = rememberOnResume { ReminderScheduler.canPostNotifications(ctx) }
    val requestAccess = rememberStorageAccessRequest()
    val pickFolder = rememberFolderPicker()
    val requestNotif = rememberNotificationRequest()

    val intro = remember { Animatable(0f) }
    LaunchedEffect(Unit) { intro.animateTo(1f, tween(900)) }

    val sourceReady = if (s.source == LibrarySource.CUSTOM) s.customTreeUri != null else hasAccess

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        InkioLogo(
            Modifier.size(180.dp).graphicsLayer {
                alpha = intro.value; scaleX = 0.9f + 0.1f * intro.value; scaleY = scaleX
            },
        )
        Text(
            "Your paper-like reading room.", color = p.muted, fontStyle = FontStyle.Italic,
            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { alpha = intro.value },
        )
        Spacer(Modifier.height(8.dp))

        SectionHeader("1 · Choose a look", Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChoiceTile(Icons.Outlined.WbSunny, "E-Ink paper", "Grey, calm, glare-free", s.theme == AppTheme.EINK, Modifier.weight(1f)) {
                app.settings.update { it.copy(theme = AppTheme.EINK) }
            }
            ChoiceTile(Icons.Outlined.DarkMode, "AMOLED black", "True black for night", s.theme == AppTheme.AMOLED, Modifier.weight(1f)) {
                app.settings.update { it.copy(theme = AppTheme.AMOLED) }
            }
        }

        SectionHeader("2 · Where are your PDFs?", Modifier.fillMaxWidth())
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceTile(Icons.Outlined.LibraryBooks, "Books folder", "Internal storage / Books", s.source == LibrarySource.BOOKS, Modifier.fillMaxWidth()) {
                app.settings.update { it.copy(source = LibrarySource.BOOKS) }
            }
            ChoiceTile(Icons.Outlined.Download, "Downloads folder", "Internal storage / Download", s.source == LibrarySource.DOWNLOADS, Modifier.fillMaxWidth()) {
                app.settings.update { it.copy(source = LibrarySource.DOWNLOADS) }
            }
            ChoiceTile(
                Icons.Outlined.FolderOpen, "Another folder…",
                if (s.source == LibrarySource.CUSTOM) StorageAccess.describe(s.source, s.customTreeUri) else "Pick any folder",
                s.source == LibrarySource.CUSTOM, Modifier.fillMaxWidth(),
            ) { pickFolder() }
        }
        if (s.source != LibrarySource.CUSTOM) {
            Spacer(Modifier.height(12.dp))
            if (hasAccess) {
                StatusLine(Icons.Outlined.CheckCircle, "Storage access granted")
            } else {
                Text(
                    "Inkio needs \"All files access\" to read PDFs from this folder. Nothing ever leaves your phone.",
                    color = p.muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                InkButton("Grant storage access", icon = Icons.Outlined.LockOpen, primary = false, onClick = requestAccess)
            }
        }

        SectionHeader("3 · Reading reminders", Modifier.fillMaxWidth())
        if (notifOk) StatusLine(Icons.Outlined.CheckCircle, "Notifications enabled — set reminders in Settings")
        else InkButton("Allow notifications", icon = Icons.Outlined.NotificationsActive, primary = false, onClick = requestNotif)

        Spacer(Modifier.height(28.dp))
        InkButton(
            "Open my library", modifier = Modifier.fillMaxWidth(), enabled = sourceReady,
            onClick = {
                app.settings.update { it.copy(onboarded = true) }
                app.appScope.launch { app.library.scan() }
                onDone()
            },
        )
        if (!sourceReady) {
            Text(
                "Grant access or pick a folder to continue", color = p.muted,
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ChoiceTile(
    icon: ImageVector, title: String, subtitle: String, selected: Boolean,
    modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    val p = LocalInk.current
    val shape = RoundedCornerShape(p.corner)
    val selColor = if (p.isEink) p.ink else p.accent
    Row(
        modifier.clip(shape)
            .background(if (selected) (if (p.isEink) p.surfaceAlt else p.accentSoft) else p.surface)
            .border(if (selected) 2.dp else p.border, if (selected) selColor else p.line, shape)
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) selColor else p.muted, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = p.ink, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, color = p.muted, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        }
    }
}

@Composable
private fun StatusLine(icon: ImageVector, text: String) {
    val p = LocalInk.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (p.isEink) p.ink else p.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = p.ink, style = MaterialTheme.typography.bodyMedium)
    }
}
