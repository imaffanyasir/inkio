package com.inkio.reader.ui.components

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.inkio.reader.InkioApp
import com.inkio.reader.data.LibrarySource
import com.inkio.reader.data.StorageAccess
import com.inkio.reader.reminders.ReminderScheduler

/** Re-evaluates [check] every time the screen resumes (e.g. after returning from system settings). */
@Composable
fun rememberOnResume(check: () -> Boolean): Boolean {
    var value by remember { mutableStateOf(check()) }
    LifecycleResumeEffect(Unit) {
        value = check()
        onPauseOrDispose { }
    }
    return value
}

/** Launches whatever is needed for Inkio to read the Books / Download folders. */
@Composable
fun rememberStorageAccessRequest(onResult: () -> Unit = {}): () -> Unit {
    val ctx = LocalContext.current
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onResult() }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onResult() }
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { settingsLauncher.launch(StorageAccess.allFilesAccessIntent(ctx)) }
                .onFailure { StorageAccess.fallbackAllFilesIntent()?.let { settingsLauncher.launch(it) } }
        } else {
            permLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}

/** System folder picker; the chosen folder becomes the library source. */
@Composable
fun rememberFolderPicker(onPicked: () -> Unit = {}): () -> Unit {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as InkioApp
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            app.settings.update { it.copy(source = LibrarySource.CUSTOM, customTreeUri = uri.toString()) }
            onPicked()
        }
    }
    return { launcher.launch(StorageAccess.initialPickerUri()) }
}

@Composable
fun rememberNotificationRequest(onResult: (Boolean) -> Unit = {}): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onResult(it) }
    return {
        if (Build.VERSION.SDK_INT >= 33 && !ReminderScheduler.canPostNotifications(ctx)) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else onResult(true)
    }
}
