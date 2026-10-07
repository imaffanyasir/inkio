package com.inkio.reader

import android.app.Application
import com.inkio.reader.data.LibraryRepository
import com.inkio.reader.data.SettingsRepository
import com.inkio.reader.pdf.CoverManager
import com.inkio.reader.reminders.ReminderScheduler
import com.inkio.reader.summary.TextExtractor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class InkioApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var settings: SettingsRepository private set
    lateinit var covers: CoverManager private set
    lateinit var library: LibraryRepository private set
    lateinit var text: TextExtractor private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
        settings = SettingsRepository(this)
        covers = CoverManager(this)
        library = LibraryRepository(this, settings, covers, appScope)
        text = TextExtractor(this)
        ReminderScheduler.createChannel(this)
        ReminderScheduler.scheduleAll(this, settings.current)
    }
}
