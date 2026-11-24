package io.github.rex25415.haiyu.schedule

import io.github.rex25415.haiyu.schedule.data.mergeSyncedCourseMetadata
import io.github.rex25415.haiyu.schedule.model.AppDataSnapshot
import io.github.rex25415.haiyu.schedule.model.CourseItem
import io.github.rex25415.haiyu.schedule.model.GdouCampus
import io.github.rex25415.haiyu.schedule.model.ScheduleShareBundle
import io.github.rex25415.haiyu.schedule.ui.components.parseWeeksExpression
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryAndMigrationTest {

    private val lenientJson = Json { ignoreUnknownKeys = true }

    @Test
    fun legacySnapshotJsonMigratesCleanlyWithAllNewSettingsDefaults() {
        val v240Payload = """
            {
              "courses": [
                {"id": "k1", "name": "高等数学A", "dayOfWeek": 2, "startSection": 1, "endSection": 2, "weeks": [1, 2, 3]}
              ],
              "settings": {
                "campus": "YANGJIANG",
                "semesterStartDate": "2026-08-31",
                "settingsVersion": 2
              }
            }
        """.trimIndent()

        val decoded = lenientJson.decodeFromString(AppDataSnapshot.serializer(), v240Payload)
        assertEquals(1, decoded.courses.size)
        assertEquals(GdouCampus.YANGJIANG, decoded.settings.campus)
        assertTrue(decoded.settings.favoriteBuildings.isEmpty())
        assertTrue(decoded.settings.favoriteClassrooms.isEmpty())
        assertFalse(decoded.settings.receiveBetaUpdates)
        assertTrue(decoded.settings.autoCheckUpdates)
        assertEquals(20, decoded.settings.totalWeeks)
    }

    @Test
    fun scheduleShareBundleRoundTripPreservesCampusTermAndCourses() {
        val bundle = ScheduleShareBundle(
            campus = GdouCampus.HAIBIN,
            semesterStartDate = "2026-08-31",
            totalWeeks = 19,
            academicYearLabel = "2026-2027 第一学期",
            courses = listOf(
                CourseItem(
                    id = "",
                    name = "船舶原理",
                    location = "教学楼203",
                    dayOfWeek = 4,
                    startSection = 5,
                    endSection = 6,
                    weeks = listOf(1, 2, 3)
                )
            )
        )
        val raw = lenientJson.encodeToString(ScheduleShareBundle.serializer(), bundle)
        val restored = lenientJson.decodeFromString(ScheduleShareBundle.serializer(), raw)
        assertEquals("HAIYU_GDOU_V1", restored.format)
        assertEquals(GdouCampus.HAIBIN, restored.campus)
        assertEquals(19, restored.totalWeeks)
        assertEquals("船舶原理", restored.courses.single().name)
    }

    @Test
    fun parseWeeksExpressionSupportsRangesOddEvenSingletonsAndChineseCommas() {
        assertEquals((1..16).toList(), parseWeeksExpression("1-16", 20))
        assertEquals(listOf(1, 3, 5, 7, 10, 12, 14), parseWeeksExpression("1-8(单)，10～15双", 20))
        assertEquals(listOf(2, 4, 5, 6, 9), parseWeeksExpression("2, 4, 5-6, 9", 18))
        assertTrue(parseWeeksExpression("未知周次", 20).isEmpty())
    }

    @Test
    fun mergeSyncedCourseMetadata_preservesUserNoteAndAttendanceWhileBackfillingRemarks() {
        val prevCourses = listOf(
            CourseItem(
                id = "old-1",
                name = "海洋气象学",
                teacher = "陈老师",
                location = "海博楼301",
                dayOfWeek = 1,
                startSection = 1,
                endSection = 2,
                weeks = (1..16).toList(),
                note = "记得带计算器",
                absenceCount = 1,
                bonusCount = 2
            ),
            CourseItem(
                id = "old-2",
                name = "数值分析",
                teacher = "李老师",
                location = "钟海楼02010",
                dayOfWeek = 3,
                startSection = 5,
                endSection = 6,
                weeks = (1..16).toList(),
                note = ""
            )
        )
        val freshSynced = listOf(
            CourseItem(
                id = "new-1",
                name = "海洋气象学",
                teacher = "陈老师",
                location = "海博楼301",
                dayOfWeek = 1,
                startSection = 1,
                endSection = 2,
                weeks = (1..16).toList(),
                note = "教务选课备注：实验班"
            ),
            CourseItem(
                id = "new-2",
                name = "数值分析",
                teacher = "李老师",
                location = "钟海楼02010",
                dayOfWeek = 3,
                startSection = 5,
                endSection = 6,
                weeks = (1..16).toList(),
                note = "上机自带电脑"
            )
        )

        val merged = mergeSyncedCourseMetadata(prevCourses, freshSynced)
        assertEquals("记得带计算器", merged[0].note)
        assertEquals(1, merged[0].absenceCount)
        assertEquals(2, merged[0].bonusCount)
        assertEquals("上机自带电脑", merged[1].note)
    }
}
