package com.y3lc.timelogger

import android.content.pm.ApplicationInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xmlpull.v1.XmlPullParser

class BackupPolicyTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val backupDomains = setOf(
        "root", "file", "database", "sharedpref", "external",
        "device_root", "device_file", "device_database", "device_sharedpref",
    )

    @Test
    fun disableBackupInInstalledApplication() {
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test
    fun excludeAllDomainsFromLegacyBackup() {
        assertExcludedDomains("fullBackupContent", "full-backup-content", "full-backup-content")
    }

    @Test
    fun excludeAllDomainsFromCloudBackup() {
        assertExcludedDomains("dataExtractionRules", "data-extraction-rules", "cloud-backup")
    }

    @Test
    fun excludeAllDomainsFromDeviceTransfer() {
        assertExcludedDomains("dataExtractionRules", "data-extraction-rules", "device-transfer")
    }

    private fun assertExcludedDomains(attribute: String, root: String, section: String) {
        val excludedDomains = mutableSetOf<String>()
        var foundSection = false
        var sectionDepth = -1
        context.resources.getXml(getManifestResource(attribute)).use { parser ->
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    if (parser.depth == 1) assertEquals(root, parser.name)
                    if (parser.name == section) {
                        foundSection = true
                        sectionDepth = parser.depth
                    } else if (sectionDepth > 0 && parser.depth == sectionDepth + 1) {
                        assertFalse("$section 不得声明包含规则", parser.name == "include")
                        if (parser.name == "exclude") {
                            assertEquals(".", parser.getAttributeValue(null, "path"))
                            excludedDomains.add(parser.getAttributeValue(null, "domain"))
                        }
                    }
                } else if (parser.eventType == XmlPullParser.END_TAG && parser.depth == sectionDepth) {
                    sectionDepth = -1
                }
                parser.next()
            }
        }
        assertTrue("打包规则缺少 $section", foundSection)
        assertEquals("$section 必须排除全部存储域", backupDomains, excludedDomains)
    }

    private fun getManifestResource(attribute: String): Int {
        context.assets.openXmlResourceParser("AndroidManifest.xml").use { parser ->
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "application") {
                    val resourceId = parser.getAttributeResourceValue(
                        "http://schemas.android.com/apk/res/android", attribute, 0,
                    )
                    assertTrue("应用清单缺少 $attribute 资源引用", resourceId != 0)
                    return resourceId
                }
                parser.next()
            }
        }
        error("打包清单缺少 application")
    }
}
