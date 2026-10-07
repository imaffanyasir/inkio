package com.inkio.reader.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

enum class AppTheme { EINK, AMOLED }
enum class PageAnim { CURL, SLIDE, FADE, NONE }
enum class DitherMode { NONE, ORDERED, FLOYD }
enum class LibrarySource { BOOKS, DOWNLOADS, CUSTOM }

data class Reminder(
    val id: Int,
    val hour: Int,
    val minute: Int,
    /** java.util.Calendar.DAY_OF_WEEK values (1 = Sunday .. 7 = Saturday). */
    val days: Set<Int> = (1..7).toSet(),
    val enabled: Boolean = true,
) {
    fun toJson() = JSONObject().put("id", id).put("hour", hour).put("minute", minute)
        .put("days", JSONArray(days.toList())).put("enabled", enabled)

    companion object {
        fun fromJson(o: JSONObject): Reminder {
            val d = o.optJSONArray("days") ?: JSONArray()
            return Reminder(
                o.getInt("id"), o.getInt("hour"), o.getInt("minute"),
                (0 until d.length()).map { d.getInt(it) }.toSet(), o.optBoolean("enabled", true),
            )
        }
    }
}

data class Settings(
    val onboarded: Boolean = false,
    val theme: AppTheme = AppTheme.EINK,
    val source: LibrarySource = LibrarySource.BOOKS,
    val customTreeUri: String? = null,
    // E-ink display processing
    val dither: DitherMode = DitherMode.FLOYD,
    val grayLevels: Int = 16,
    val contrast: Float = 1.15f,
    val paperTexture: Boolean = true,
    val fullRefreshEvery: Int = 6,
    val ghosting: Boolean = false,
    // Reading
    val pageAnim: PageAnim = PageAnim.CURL,
    val trimMargins: Boolean = true,
    val darkPagesInAmoled: Boolean = true,
    val volumeKeys: Boolean = true,
    val keepScreenOn: Boolean = true,
    // Recap
    val recapEnabled: Boolean = true,
    val recapAfterHours: Int = 24,
    val recapMinPages: Int = 5,
    // Reminders
    val reminders: List<Reminder> = emptyList(),
    val inactivityDays: Int = 2,
    val lastReadAt: Long = 0,
    val lastNudgeAt: Long = 0,
    // Covers
    val onlineCovers: Boolean = false,
    val sort: String = "recent",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("onboarded", onboarded); put("theme", theme.name); put("source", source.name)
        put("customTreeUri", customTreeUri ?: JSONObject.NULL)
        put("dither", dither.name); put("grayLevels", grayLevels); put("contrast", contrast.toDouble())
        put("paperTexture", paperTexture); put("fullRefreshEvery", fullRefreshEvery); put("ghosting", ghosting)
        put("pageAnim", pageAnim.name); put("trimMargins", trimMargins); put("darkPagesInAmoled", darkPagesInAmoled)
        put("volumeKeys", volumeKeys); put("keepScreenOn", keepScreenOn)
        put("recapEnabled", recapEnabled); put("recapAfterHours", recapAfterHours); put("recapMinPages", recapMinPages)
        put("reminders", JSONArray().apply { reminders.forEach { put(it.toJson()) } })
        put("inactivityDays", inactivityDays); put("lastReadAt", lastReadAt); put("lastNudgeAt", lastNudgeAt)
        put("onlineCovers", onlineCovers); put("sort", sort)
    }

    companion object {
        private inline fun <reified T : Enum<T>> JSONObject.enum(key: String, def: T): T =
            runCatching { enumValueOf<T>(getString(key)) }.getOrDefault(def)

        fun fromJson(o: JSONObject): Settings {
            val d = Settings()
            val rem = o.optJSONArray("reminders") ?: JSONArray()
            return Settings(
                onboarded = o.optBoolean("onboarded", d.onboarded),
                theme = o.enum("theme", d.theme),
                source = o.enum("source", d.source),
                customTreeUri = if (o.isNull("customTreeUri")) null else o.optString("customTreeUri"),
                dither = o.enum("dither", d.dither),
                grayLevels = o.optInt("grayLevels", d.grayLevels),
                contrast = o.optDouble("contrast", d.contrast.toDouble()).toFloat(),
                paperTexture = o.optBoolean("paperTexture", d.paperTexture),
                fullRefreshEvery = o.optInt("fullRefreshEvery", d.fullRefreshEvery),
                ghosting = o.optBoolean("ghosting", d.ghosting),
                pageAnim = o.enum("pageAnim", d.pageAnim),
                trimMargins = o.optBoolean("trimMargins", d.trimMargins),
                darkPagesInAmoled = o.optBoolean("darkPagesInAmoled", d.darkPagesInAmoled),
                volumeKeys = o.optBoolean("volumeKeys", d.volumeKeys),
                keepScreenOn = o.optBoolean("keepScreenOn", d.keepScreenOn),
                recapEnabled = o.optBoolean("recapEnabled", d.recapEnabled),
                recapAfterHours = o.optInt("recapAfterHours", d.recapAfterHours),
                recapMinPages = o.optInt("recapMinPages", d.recapMinPages),
                reminders = (0 until rem.length()).map { Reminder.fromJson(rem.getJSONObject(it)) },
                inactivityDays = o.optInt("inactivityDays", d.inactivityDays),
                lastReadAt = o.optLong("lastReadAt"),
                lastNudgeAt = o.optLong("lastNudgeAt"),
                onlineCovers = o.optBoolean("onlineCovers", d.onlineCovers),
                sort = o.optString("sort", d.sort),
            )
        }
    }
}

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("inkio_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()
    val current: Settings get() = _settings.value

    private fun load(): Settings = prefs.getString("json", null)
        ?.let { runCatching { Settings.fromJson(JSONObject(it)) }.getOrNull() }
        ?: Settings()

    fun update(block: (Settings) -> Settings) {
        _settings.update(block)
        prefs.edit().putString("json", _settings.value.toJson().toString()).apply()
    }
}
