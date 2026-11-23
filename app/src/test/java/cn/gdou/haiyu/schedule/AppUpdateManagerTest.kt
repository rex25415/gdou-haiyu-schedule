package cn.gdou.haiyu.schedule

import cn.gdou.haiyu.schedule.data.GdouTimeRules
import cn.gdou.haiyu.schedule.data.OccurrenceState
import cn.gdou.haiyu.schedule.model.AppDataSnapshot
import cn.gdou.haiyu.schedule.model.CourseItem
import cn.gdou.haiyu.schedule.model.GdouCampus
import cn.gdou.haiyu.schedule.update.AppUpdateManager
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class AppUpdateManagerTest {

    private val lenientJson = Json { ignoreUnknownKeys = true }

    @Test
    fun semanticVersionComparisonHandlesEdgeCases() {
        assertTrue(AppUpdateManager.isNewerVersion("v2.5.0", "2.4.0"))
        assertTrue(AppUpdateManager.isNewerVersion("2.10.0", "2.9.9"))
        assertTrue(AppUpdateManager.isNewerVersion("v3.0", "2.9.9"))
        assertTrue(AppUpdateManager.isNewerVersion("2.5.1", "2.5.0"))

        assertFalse(AppUpdateManager.isNewerVersion("v2.5.0", "2.5.0"))
        assertFalse(AppUpdateManager.isNewerVersion("2.4.9", "2.5.0"))
        assertFalse(AppUpdateManager.isNewerVersion("1.9.9", "2.0.0"))
        assertFalse(AppUpdateManager.isNewerVersion("", "2.5.0"))
    }

    @Test
    fun parseGitHubReleaseJsonExtractsApkAndApiDigest() {
        val sampleJson = """
            {
              "tag_name": "v2.5.0",
              "name": "海屿课表 v2.5.0",
              "html_url": "https://github.com/rex25415/gdou-haiyu-schedule/releases/tag/v2.5.0",
              "body": "## 更新日志\n- 新增应用内自动检查更新\n- 修复红线遮挡课程文字",
              "assets": [
                {
                  "name": "checksums.txt",
                  "size": 128,
                  "browser_download_url": "https://example.com/checksums.txt"
                },
                {
                  "name": "HaiyuSchedule-GDOU-v2.5.0.apk",
                  "size": 1750000,
                  "digest": "sha256:783ffb836dc2a65061d54414ead705a2f71fac3e6c9908f48ac4634f7f4997b7",
                  "browser_download_url": "https://github.com/rex25415/gdou-haiyu-schedule/releases/download/v2.5.0/HaiyuSchedule-GDOU-v2.5.0.apk"
                }
              ]
            }
        """.trimIndent()

        val info = AppUpdateManager.parseReleaseJson(sampleJson)
        assertNotNull(info)
        assertEquals("v2.5.0", info!!.tagName)
        assertEquals("2.5.0", info.versionName)
        assertEquals("HaiyuSchedule-GDOU-v2.5.0.apk", info.apkFileName)
        assertEquals(1750000L, info.apkSizeBytes)
        assertEquals(
            "783ffb836dc2a65061d54414ead705a2f71fac3e6c9908f48ac4634f7f4997b7",
            info.expectedSha256
        )
        assertTrue(info.releaseNotes.contains("新增应用内自动检查更新"))
    }

    @Test
    fun parseGitHubReleaseJsonFallsBackToBodySha256() {
        val sampleJson = """
            {
              "tag_name": "v2.6.0",
              "name": "v2.6.0",
              "body": "### 优化\n- 更流畅\n- SHA-256：`ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789`",
              "assets": [
                {
                  "name": "HaiyuSchedule-GDOU-v2.6.0.apk",
                  "size": 1800000,
                  "browser_download_url": "https://github.com/rex25415/gdou-haiyu-schedule/releases/download/v2.6.0/HaiyuSchedule-GDOU-v2.6.0.apk"
                }
              ]
            }
        """.trimIndent()

        val info = AppUpdateManager.parseReleaseJson(sampleJson)
        assertNotNull(info)
        assertEquals(
            "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
            info!!.expectedSha256
        )
    }

    @Test
    fun upgradeFromV240SettingsPreservesDataAndEnablesAutoUpdate() {
        val v240Json = """
            {
              "settings": {
                "campus": "HAIBIN",
                "showWeekend": true,
                "settingsVersion": 2,
                "semesterStartDate": "2026-08-31"
              }
            }
        """.trimIndent()
        val decoded = lenientJson.decodeFromString(AppDataSnapshot.serializer(), v240Json)
        assertEquals(GdouCampus.HAIBIN, decoded.settings.campus)
        assertTrue(decoded.settings.showWeekend)
        assertTrue(decoded.settings.autoCheckUpdates)
        assertEquals(0L, decoded.settings.lastUpdateCheckEpochMs)
        assertEquals("", decoded.settings.ignoredUpdateVersion)
    }

    @Test
    fun todayStatusesAndOverlapClustersShareConsistentOrdering() {
        val c1 = CourseItem(
            id = "c1",
            name = "高等数学 I x1",
            location = "教学楼121",
            dayOfWeek = 1,
            startSection = 3,
            endSection = 4,
            weeks = listOf(1)
        )
        val c2 = CourseItem(
            id = "c2",
            name = "工程数学",
            location = "实验楼405",
            dayOfWeek = 1,
            startSection = 3,
            endSection = 4,
            weeks = listOf(1)
        )
        val monday1037 = LocalDateTime.of(2026, 8, 31, 10, 37)
        val statuses = GdouTimeRules.getTodayCoursesWithStatus(
            courses = listOf(c1, c2),
            startDateStr = "2026-08-31",
            totalWeeks = 20,
            campus = GdouCampus.HAIBIN,
            now = monday1037
        )
        val clusters = GdouTimeRules.groupOverlappingCourses(listOf(c1, c2))
        assertEquals(2, statuses.count { it.state == OccurrenceState.ONGOING })
        assertEquals(clusters.first().courses.first().id, statuses.first().course.id)
    }
}
