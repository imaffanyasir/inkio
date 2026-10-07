package com.inkio.reader.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.inkio.reader.InkioApp
import com.inkio.reader.MainActivity
import com.inkio.reader.R
import com.inkio.reader.data.Book
import com.inkio.reader.data.Reminder
import com.inkio.reader.data.Settings
import java.util.Calendar
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    const val CHANNEL_ID = "reading_reminders"
    const val ACTION_REMIND = "com.inkio.reader.REMIND"
    const val EXTRA_ID = "reminder_id"
    const val EXTRA_OPEN_BOOK = "open_book"
    private const val NUDGE_WORK = "inkio_inactivity_nudge"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val ch = NotificationChannel(CHANNEL_ID, "Reading reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Daily reading reminders and gentle nudges when you haven't read for a while"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun pending(context: Context, id: Int): PendingIntent {
        val i = Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND).putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(context, 1000 + id, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun nextTrigger(r: Reminder, now: Calendar = Calendar.getInstance()): Long? {
        if (r.days.isEmpty()) return null
        val c = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, r.hour); set(Calendar.MINUTE, r.minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        for (i in 0..7) {
            if (c.timeInMillis > now.timeInMillis && c.get(Calendar.DAY_OF_WEEK) in r.days) return c.timeInMillis
            c.add(Calendar.DAY_OF_YEAR, 1)
        }
        return null
    }

    fun schedule(context: Context, r: Reminder) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context, r.id)
        am.cancel(pi)
        if (!r.enabled) return
        val at = nextTrigger(r) ?: return
        if (canScheduleExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun cancel(context: Context, id: Int) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, id))
    }

    fun scheduleAll(context: Context, s: Settings) {
        s.reminders.forEach { schedule(context, it) }
        val wm = WorkManager.getInstance(context)
        if (s.inactivityDays > 0) {
            wm.enqueueUniquePeriodicWork(
                NUDGE_WORK, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<InactivityWorker>(6, TimeUnit.HOURS).build(),
            )
        } else wm.cancelUniqueWork(NUDGE_WORK)
    }

    fun notify(context: Context, id: Int, title: String, text: String, book: Book?) {
        if (!canPostNotifications(context)) return
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            book?.let { putExtra(EXTRA_OPEN_BOOK, it.id) }
        }
        val pi = PendingIntent.getActivity(context, 2000 + id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_book)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setColor(0xFFE8A657.toInt())
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }

    /** Most recently read unfinished book, used to personalise reminders. */
    fun lastBook(context: Context): Book? {
        val app = context.applicationContext as InkioApp
        return app.library.books.value.filter { it.started && !it.finished }.maxByOrNull { it.lastReadAt }
            ?: app.library.books.value.filter { it.started }.maxByOrNull { it.lastReadAt }
    }

    fun reminderText(book: Book?): Pair<String, String> {
        val titles = listOf("Time to read", "Your reading moment", "A few pages tonight?", "Back to your book")
        val t = titles.random()
        val body = if (book != null) {
            "“${book.title}” is waiting for you on page ${book.currentPage + 1} — ${book.percent}% read."
        } else "Pick a book from your Inkio library and read a few pages."
        return t to body
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_REMIND) return
        val id = intent.getIntExtra(ReminderScheduler.EXTRA_ID, -1)
        val app = context.applicationContext as InkioApp
        val reminder = app.settings.current.reminders.find { it.id == id } ?: return
        val book = ReminderScheduler.lastBook(context)
        val (title, text) = ReminderScheduler.reminderText(book)
        ReminderScheduler.notify(context, 100 + id, title, text, book)
        ReminderScheduler.schedule(context, reminder) // queue the next occurrence
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as InkioApp
        ReminderScheduler.scheduleAll(context, app.settings.current)
    }
}

/** Sends a gentle nudge if nothing has been read for the configured number of days. */
class InactivityWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as InkioApp
        val s = app.settings.current
        if (s.inactivityDays <= 0) return Result.success()
        val now = System.currentTimeMillis()
        val day = TimeUnit.DAYS.toMillis(1)
        val since = s.lastReadAt
        if (since == 0L) return Result.success()
        val away = now - since
        if (away < s.inactivityDays * day || now - s.lastNudgeAt < day) return Result.success()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour < 9 || hour >= 22) return Result.success() // don't nudge at night

        val book = ReminderScheduler.lastBook(applicationContext)
        val days = (away / day).toInt()
        val text = if (book != null) {
            "It's been $days days. “${book.title}” is ${book.percent}% read — open it and Inkio will recap where you left off."
        } else "It's been $days days since you last read. A few pages is all it takes."
        ReminderScheduler.notify(applicationContext, 99, "We miss your reading streak", text, book)
        app.settings.update { it.copy(lastNudgeAt = now) }
        return Result.success()
    }
}
