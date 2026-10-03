package com.y3lc.timelogger.ui

import androidx.test.platform.app.InstrumentationRegistry
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsStoreTest {
    @Test
    fun statisticsSettingsSurviveStoreRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "settings-store-test"
        context.getSharedPreferences(name, 0).edit().clear().commit()
        try {
            val first = PreferencesSettingsStore(context, name)
            first.save(AppSettings("America/New_York", DayOfWeek.SUNDAY))

            val reopened = PreferencesSettingsStore(context, name)
            assertEquals(AppSettings("America/New_York", DayOfWeek.SUNDAY), reopened.load())
            reopened.save(AppSettings(null, DayOfWeek.MONDAY))
            assertEquals(AppSettings(null, DayOfWeek.MONDAY), first.load())
        } finally {
            context.getSharedPreferences(name, 0).edit().clear().commit()
        }
    }
}
