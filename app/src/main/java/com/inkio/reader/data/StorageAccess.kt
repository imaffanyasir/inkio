package com.inkio.reader.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File

object StorageAccess {

    fun hasAllFilesAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    /** Intent that opens the "All files access" screen for Inkio (Android 11+). */
    fun allFilesAccessIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        }

    fun fallbackAllFilesIntent(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION) else null

    val booksDir: File get() = File(Environment.getExternalStorageDirectory(), "Books")

    val downloadsDir: File
        get() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    fun dirFor(source: LibrarySource): File? = when (source) {
        LibrarySource.BOOKS -> booksDir
        LibrarySource.DOWNLOADS -> downloadsDir
        LibrarySource.CUSTOM -> null
    }

    /** Hint for the system folder picker so it opens in the Books folder. */
    fun initialPickerUri(): Uri =
        DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Books")

    fun describe(source: LibrarySource, treeUri: String?): String = when (source) {
        LibrarySource.BOOKS -> "Internal storage / Books"
        LibrarySource.DOWNLOADS -> "Internal storage / Download"
        LibrarySource.CUSTOM -> treeUri?.let {
            Uri.decode(it).substringAfterLast(':').ifBlank { "Custom folder" }.let { p -> "Folder: $p" }
        } ?: "Custom folder (not chosen)"
    }
}
