package com.y3lc.timelogger

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @Test
    fun entryShowsCoreStatusInsteadOfTemplate() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val activity = instrumentation.startActivitySync(intent)

        try {
            val root = activity.findViewById<ViewGroup>(R.id.main)
            val statusViews = arrayListOf<View>()
            root.findViewsWithText(statusViews, "记录", View.FIND_VIEWS_WITH_TEXT)
            assertTrue(statusViews.filterIsInstance<TextView>().any { it.isShown })

            val templateViews = arrayListOf<View>()
            root.findViewsWithText(templateViews, "Hello World", View.FIND_VIEWS_WITH_TEXT)
            assertFalse(templateViews.any { it.isShown })
        } finally {
            activity.finish()
        }
    }
}
