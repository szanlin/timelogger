package com.y3lc.timelogger.ui

import android.content.Context
import java.time.DayOfWeek
import java.time.ZoneId

data class AppSettings(
    val fixedZoneId: String? = null,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
)

interface SettingsStore {
    fun load(): AppSettings

    fun save(settings: AppSettings)
}

class InMemorySettingsStore : SettingsStore {
    private var settings = AppSettings()

    override fun load(): AppSettings = settings

    override fun save(settings: AppSettings) {
        this.settings = settings
    }
}

class PreferencesSettingsStore(context: Context, name: String = "time_logger_settings") : SettingsStore {
    private val preferences = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun load(): AppSettings {
        val savedZone = preferences.getString("fixed_zone_id", null)
        val zone = savedZone?.takeIf { value -> runCatching { ZoneId.of(value) }.isSuccess }
        val firstDay = if (preferences.getString("first_day_of_week", null) == DayOfWeek.SUNDAY.name) {
            DayOfWeek.SUNDAY
        } else {
            DayOfWeek.MONDAY
        }
        return AppSettings(zone, firstDay)
    }

    override fun save(settings: AppSettings) {
        val editor = preferences.edit().putString("first_day_of_week", settings.firstDayOfWeek.name)
        if (settings.fixedZoneId == null) editor.remove("fixed_zone_id")
        else editor.putString("fixed_zone_id", settings.fixedZoneId)
        check(editor.commit()) { "无法保存设置" }
    }
}
