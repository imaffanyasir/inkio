package com.inkio.reader.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inkio.reader.InkioApp
import com.inkio.reader.data.AppTheme
import com.inkio.reader.data.DitherMode
import com.inkio.reader.data.LibrarySource
import com.inkio.reader.data.PageAnim
import com.inkio.reader.data.Reminder
import com.inkio.reader.data.Settings
import com.inkio.reader.data.StorageAccess
import com.inkio.reader.reminders.ReminderScheduler
import com.inkio.reader.ui.components.InkButton
import com.inkio.reader.ui.components.InkCard
import com.inkio.reader.ui.components.InkioLogo
import com.inkio.reader.ui.components.SectionHeader
import com.inkio.reader.ui.components.Segmented
import com.inkio.reader.ui.components.SwitchRow
import com.inkio.reader.ui.components.rememberFolderPicker
import com.inkio.reader.ui.components.rememberNotificationRequest
import com.inkio.reader.ui.components.rememberOnResume
import com.inkio.reader.ui.components.rememberStorageAccessRequest
import com.inkio.reader.ui.theme.LocalInk
import kotlinx.coroutines.launch
import java.util.Locale

private val dayLetters = listOf(2 to "M", 3 to "T", 4 to "W", 5 to "T", 6 to "F", 7 to "S", 1 to "S")

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as InkioApp
    val p = LocalInk.current
    val s by app.settings.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val update: ((Settings) -> Settings) -> Unit = { app.settings.update(it) }

    val hasAccess = rememberOnResume { StorageAccess.hasAllFilesAccess(ctx) }
    val notifOk = rememberOnResume { ReminderScheduler.canPostNotifications(ctx) }
    val exactOk = rememberOnResume { ReminderScheduler.canScheduleExact(ctx) }
    val requestAccess = rememberStorageAccessRequest()
    val pickFolder = rememberFolderPicker { scope.launch { app.library.scan() } }
    val requestNotif = rememberNotificationRequest()
    var editing by remember { mutableStateOf<Reminder?>(null) }

    fun applyReminders(newList: List<Reminder>, removed: Reminder? = null) {
        removed?.let { ReminderScheduler.cancel(ctx, it.id) }
        update { it.copy(reminders = newList) }
        ReminderScheduler.scheduleAll(ctx, app.settings.current)
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = p.ink) }
            Text("Settings", color = p.ink, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()) {

            // ---------------------------------------------------------- appearance
            SectionHeader("Appearance")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemePreview("E-Ink paper", AppTheme.EINK, s.theme == AppTheme.EINK, Modifier.weight(1f)) { update { it.copy(theme = AppTheme.EINK) } }
                ThemePreview("AMOLED black", AppTheme.AMOLED, s.theme == AppTheme.AMOLED, Modifier.weight(1f)) { update { it.copy(theme = AppTheme.AMOLED) } }
            }

            // ---------------------------------------------------------- library
            SectionHeader("Library")
            Label("Fetch books from")
            Segmented(
                listOf(LibrarySource.BOOKS to "Books", LibrarySource.DOWNLOADS to "Downloads", LibrarySource.CUSTOM to "Other…"),
                s.source,
                { v -> if (v == LibrarySource.CUSTOM) pickFolder() else update { it.copy(source = v) } },
            )
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Folder, null, tint = p.muted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(StorageAccess.describe(s.source, s.customTreeUri), color = p.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                if (s.source == LibrarySource.CUSTOM) TextButton(onClick = pickFolder) { Text("Change", color = p.ink) }
            }
            if (s.source != LibrarySource.CUSTOM && !hasAccess) {
                Spacer(Modifier.height(8.dp))
                InkButton("Grant storage access", icon = Icons.Outlined.LockOpen, primary = false, onClick = requestAccess)
            }
            Spacer(Modifier.height(8.dp))
            InkButton("Rescan library", icon = Icons.Outlined.Refresh, primary = false) { scope.launch { app.library.scan() } }
            SwitchRow(
                "Find covers online for new books",
                "Uses the internet only for covers (Open Library / Google Books). Off = covers come from the PDF.",
                s.onlineCovers,
            ) { v -> update { it.copy(onlineCovers = v) } }

            // ---------------------------------------------------------- reading
            SectionHeader("Reading")
            Label("Page turn animation")
            Segmented(
                listOf(PageAnim.CURL to "Page curl", PageAnim.SLIDE to "Slide", PageAnim.FADE to "Fade", PageAnim.NONE to "None"),
                s.pageAnim, { v -> update { it.copy(pageAnim = v) } },
            )
            SwitchRow("Trim page margins", "Enlarge text by cutting empty PDF borders", s.trimMargins) { v -> update { it.copy(trimMargins = v) } }
            SwitchRow("Volume keys turn pages", null, s.volumeKeys) { v -> update { it.copy(volumeKeys = v) } }
            SwitchRow("Keep screen on while reading", null, s.keepScreenOn) { v -> update { it.copy(keepScreenOn = v) } }
            SwitchRow("Dark pages in AMOLED theme", "Show pages as light text on pure black", s.darkPagesInAmoled) { v -> update { it.copy(darkPagesInAmoled = v) } }

            // ---------------------------------------------------------- e-ink
            SectionHeader("E-ink display")
            Label("Dithering")
            Segmented(
                listOf(DitherMode.NONE to "Off", DitherMode.ORDERED to "Ordered", DitherMode.FLOYD to "Floyd–Steinberg"),
                s.dither, { v -> update { it.copy(dither = v) } },
            )
            Spacer(Modifier.height(12.dp))
            Label("Grey levels")
            Segmented(listOf(4 to "4", 8 to "8", 16 to "16 (Carta)", 256 to "Smooth"), s.grayLevels, { v -> update { it.copy(grayLevels = v) } })
            Spacer(Modifier.height(12.dp))
            Label("Contrast  ${"%.2f".format(s.contrast)}")
            Slider(
                value = s.contrast, onValueChange = { v -> update { it.copy(contrast = v) } }, valueRange = 0.8f..1.8f,
                colors = SliderDefaults.colors(
                    thumbColor = if (p.isEink) p.ink else p.accent, activeTrackColor = if (p.isEink) p.ink else p.accent,
                    inactiveTrackColor = p.surfaceAlt,
                ),
            )
            Label("Full refresh flash")
            Segmented(
                listOf(0 to "Never", 1 to "Every page", 6 to "Every 6", 12 to "Every 12"),
                s.fullRefreshEvery, { v -> update { it.copy(fullRefreshEvery = v) } },
            )
            SwitchRow("Paper texture", "Subtle grain like real e-paper", s.paperTexture) { v -> update { it.copy(paperTexture = v) } }
            SwitchRow("Ghosting effect", "Faint traces of the previous page until the next full refresh", s.ghosting) { v -> update { it.copy(ghosting = v) } }
            Text(
                "These apply in the E-Ink theme.", color = p.muted, style = MaterialTheme.typography.labelSmall,
            )

            // ---------------------------------------------------------- recap
            SectionHeader("Recap")
            SwitchRow("Show a recap when I return", "An animated summary of what you've read so far", s.recapEnabled) { v -> update { it.copy(recapEnabled = v) } }
            if (s.recapEnabled) {
                Label("Show it after I've been away for")
                Segmented(
                    listOf(0 to "Always", 6 to "6 h", 24 to "1 day", 72 to "3 days", 168 to "1 week"),
                    s.recapAfterHours, { v -> update { it.copy(recapAfterHours = v) } },
                )
                Spacer(Modifier.height(12.dp))
                Label("Only once I've read at least")
                Segmented(
                    listOf(1 to "1 page", 5 to "5 pages", 15 to "15 pages", 30 to "30 pages"),
                    s.recapMinPages, { v -> update { it.copy(recapMinPages = v) } },
                )
            }

            // ---------------------------------------------------------- reminders
            SectionHeader("Reading reminders")
            if (!notifOk) {
                InkButton("Allow notifications", icon = Icons.Outlined.NotificationsActive, primary = false, onClick = requestNotif)
                Spacer(Modifier.height(10.dp))
            }
            if (s.reminders.isEmpty()) {
                Text("No reminders yet. Add one to build a daily reading habit.", color = p.muted, style = MaterialTheme.typography.bodySmall)
            }
            s.reminders.sortedBy { it.hour * 60 + it.minute }.forEach { r ->
                ReminderRow(
                    r,
                    onToggle = { en -> applyReminders(s.reminders.map { if (it.id == r.id) it.copy(enabled = en) else it }) },
                    onDays = { days -> applyReminders(s.reminders.map { if (it.id == r.id) it.copy(days = days) else it }) },
                    onEdit = { editing = r },
                    onDelete = { applyReminders(s.reminders.filterNot { it.id == r.id }, removed = r) },
                )
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(6.dp))
            InkButton("Add reminder", icon = Icons.Outlined.Add) {
                requestNotif()
                editing = Reminder(id = (s.reminders.maxOfOrNull { it.id } ?: 0) + 1, hour = 21, minute = 0)
            }
            if (s.reminders.isNotEmpty() && !exactOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                TextButton(onClick = {
                    runCatching {
                        ctx.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
                    }
                }) { Text("Allow on-time reminders (exact alarms)", color = p.ink) }
            }
            Spacer(Modifier.height(12.dp))
            Label("Nudge me when I haven't read for")
            Segmented(
                listOf(0 to "Off", 1 to "1 day", 2 to "2 days", 3 to "3 days", 7 to "1 week"),
                s.inactivityDays,
                { v -> update { it.copy(inactivityDays = v) }; ReminderScheduler.scheduleAll(ctx, app.settings.current) },
            )
            TextButton(onClick = {
                requestNotif()
                val book = ReminderScheduler.lastBook(ctx)
                val (t, body) = ReminderScheduler.reminderText(book)
                ReminderScheduler.notify(ctx, 98, t, body, book)
            }) { Text("Send a test notification", color = p.ink) }

            // ---------------------------------------------------------- about
            SectionHeader("About")
            InkCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InkioLogo(Modifier.size(64.dp))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Inkio 1.0", color = p.ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Works fully offline. Your books, progress, bookmarks and recaps never leave this device.",
                            color = p.muted, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    editing?.let { r -> ReminderDialog(r, onDismiss = { editing = null }) { saved ->
        val list = if (s.reminders.any { it.id == saved.id }) s.reminders.map { if (it.id == saved.id) saved else it } else s.reminders + saved
        applyReminders(list)
        editing = null
    } }
}

@Composable
private fun Label(text: String) {
    val p = LocalInk.current
    Text(text, color = p.muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun ThemePreview(name: String, theme: AppTheme, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalInk.current
    val bg = if (theme == AppTheme.EINK) Color(0xFFE8E5DE) else Color.Black
    val fg = if (theme == AppTheme.EINK) Color(0xFF191919) else Color(0xFFEFE8DC)
    val acc = if (theme == AppTheme.EINK) Color(0xFF191919) else Color(0xFFE8A657)
    val shape = RoundedCornerShape(p.corner)
    Column(
        modifier.clip(shape).border(if (selected) 2.5.dp else 1.dp, if (selected) (if (p.isEink) p.ink else p.accent) else p.line, shape)
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.fillMaxWidth().background(bg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.width(60.dp).height(8.dp).background(fg))
            repeat(4) { i -> Box(Modifier.fillMaxWidth(if (i == 3) 0.6f else 1f).height(4.dp).background(fg.copy(alpha = 0.55f))) }
            Box(Modifier.fillMaxWidth(0.7f).height(4.dp).clip(RoundedCornerShape(50)).background(acc))
        }
        Text(
            name, color = p.ink, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.background(p.surface).fillMaxWidth().padding(10.dp),
        )
    }
}

@Composable
private fun ReminderRow(r: Reminder, onToggle: (Boolean) -> Unit, onDays: (Set<Int>) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val p = LocalInk.current
    InkCard(Modifier.fillMaxWidth(), padding = 12) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Alarm, null, tint = if (r.enabled) (if (p.isEink) p.ink else p.accent) else p.muted)
                Spacer(Modifier.width(10.dp))
                Text(
                    formatTime(r.hour, r.minute), color = if (r.enabled) p.ink else p.muted,
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).clickable(onClick = onEdit),
                )
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete", tint = p.muted) }
                androidx.compose.material3.Switch(
                    checked = r.enabled, onCheckedChange = onToggle,
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedTrackColor = if (p.isEink) p.ink else p.accent, checkedThumbColor = p.onAccent,
                    ),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                dayLetters.forEach { (d, l) ->
                    val on = d in r.days
                    Box(
                        Modifier.size(34.dp).clip(CircleShape)
                            .background(if (on) (if (p.isEink) p.ink else p.accent) else Color.Transparent)
                            .border(1.dp, if (on) Color.Transparent else p.line, CircleShape)
                            .clickable { onDays(if (on) r.days - d else r.days + d) },
                        contentAlignment = Alignment.Center,
                    ) { Text(l, color = if (on) p.onAccent else p.ink, style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderDialog(r: Reminder, onDismiss: () -> Unit, onSave: (Reminder) -> Unit) {
    val p = LocalInk.current
    val state = rememberTimePickerState(initialHour = r.hour, initialMinute = r.minute)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        title = { Text("Remind me to read at") },
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = p.surfaceAlt,
                    selectorColor = if (p.isEink) p.ink else p.accent,
                    timeSelectorSelectedContainerColor = if (p.isEink) p.ink else p.accentSoft,
                    timeSelectorSelectedContentColor = if (p.isEink) p.onAccent else p.accent,
                    timeSelectorUnselectedContainerColor = p.surfaceAlt,
                    periodSelectorSelectedContainerColor = if (p.isEink) p.ink else p.accentSoft,
                    periodSelectorSelectedContentColor = if (p.isEink) p.onAccent else p.accent,
                ),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(r.copy(hour = state.hour, minute = state.minute, enabled = true)) }) { Text("Save", color = p.ink) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = p.muted) } },
    )
}

private fun formatTime(h: Int, m: Int): String {
    val hh = if (h % 12 == 0) 12 else h % 12
    return String.format(Locale.getDefault(), "%d:%02d %s", hh, m, if (h < 12) "AM" else "PM")
}
