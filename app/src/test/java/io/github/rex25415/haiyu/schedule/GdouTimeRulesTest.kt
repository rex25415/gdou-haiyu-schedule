package io.github.rex25415.haiyu.schedule

import io.github.rex25415.haiyu.schedule.data.GdouTimeRules
import io.github.rex25415.haiyu.schedule.data.TermWeekPhase
import io.github.rex25415.haiyu.schedule.model.AppDataSnapshot
import io.github.rex25415.haiyu.schedule.model.CourseItem
import io.github.rex25415.haiyu.schedule.model.GdouCampus
import io.github.rex25415.haiyu.schedule.model.ScheduleShareBundle
import io.github.rex25415.haiyu.schedule.model.TodoTaskItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GdouTimeRulesTest {

    @Test
    fun haibinLabAndShortNameUseStaggeredSchedule() {
        val c1 = CourseItem(
            name = "数据结构实验",
            location = "实506-1计算机(2)室",
            dayOfWeek = 1,
            startSection = 3,
            endSection = 4,
            weeks = listOf(1, 2, 3)
        )
        val t1 = GdouTimeRules.resolveCourseTime(c1, GdouCampus.HAIBIN)
        assertTrue(t1.isSpecialStaggered)
        assertEquals("10:05", t1.startTime)
        assertEquals("11:35", t1.endTime)

        val c2 = CourseItem(
            name = "网球",
            location = "网球场（实验楼东面）",
            dayOfWeek = 2,
            startSection = 7,
            endSection = 8,
            weeks = listOf(1, 2)
        )
        val t2 = GdouTimeRules.resolveCourseTime(c2, GdouCampus.HAIBIN)
        assertTrue(t2.isSpecialStaggered)
        assertEquals("16:25", t2.startTime)
        assertEquals("17:50", t2.endTime)
    }

    @Test
    fun haibinRegularClassroomUsesStandardSchedule() {
        val c = CourseItem(
            name = "高等数学",
            location = "教学楼204",
            dayOfWeek = 1,
            startSection = 3,
            endSection = 4,
            weeks = listOf(1)
        )
        val t = GdouTimeRules.resolveCourseTime(c, GdouCampus.HAIBIN)
        assertFalse(t.isSpecialStaggered)
        assertEquals("10:15", t.startTime)
        assertEquals("11:50", t.endTime)
    }

    @Test
    fun huguangGuangxueUsesStaggeredSchedule() {
        val c = CourseItem(
            name = "大学物理",
            location = "广学楼302",
            dayOfWeek = 3,
            startSection = 3,
            endSection = 4,
            weeks = listOf(1)
        )
        val t = GdouTimeRules.resolveCourseTime(c, GdouCampus.HUGUANG)
        assertTrue(t.isSpecialStaggered)
        assertEquals("10:05", t.startTime)
        assertEquals("11:35", t.endTime)
    }

    @Test
    fun formatWeeksCompactHandlesOddEvenAndRanges() {
        assertEquals("1-15周(单)", GdouTimeRules.formatWeeksCompact((1..15 step 2).toList()))
        assertEquals("2-16周(双)", GdouTimeRules.formatWeeksCompact((2..16 step 2).toList()))
        assertEquals("1-8,10-16周", GdouTimeRules.formatWeeksCompact((1..8).toList() + (10..16).toList()))
    }

    @Test
    fun calculateCurrentWeekAccuracy() {
        val w1 = GdouTimeRules.calculateCurrentWeek("2026-08-31", 20, LocalDate.of(2026, 8, 31))
        val w2 = GdouTimeRules.calculateCurrentWeek("2026-08-31", 20, LocalDate.of(2026, 9, 7))
        assertEquals(1, w1)
        assertEquals(2, w2)
    }

    @Test
    fun yangjiangNonShensiUsesStaggeredSchedule() {
        val c1 = CourseItem(
            name = "电路分析",
            location = "厚德楼201",
            dayOfWeek = 2,
            startSection = 3,
            endSection = 4,
            weeks = listOf(1, 2)
        )
        val t1 = GdouTimeRules.resolveCourseTime(c1, GdouCampus.YANGJIANG)
        assertTrue(t1.isSpecialStaggered)
        assertEquals("10:10", t1.startTime)
        assertEquals("11:40", t1.endTime)

        val c2 = c1.copy(location = "慎思楼305")
        val t2 = GdouTimeRules.resolveCourseTime(c2, GdouCampus.YANGJIANG)
        assertFalse(t2.isSpecialStaggered)
        assertEquals("10:25", t2.startTime)
    }

    @Test
    fun shareBundleAndTodoTaskSerializationRoundtrip() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        val bundle = ScheduleShareBundle(
            campus = GdouCampus.HAIBIN,
            semesterStartDate = "2026-03-02",
            totalWeeks = 19,
            academicYearLabel = "2025-2026 第二学期",
            courses = GdouTimeRules.createHaibinDemoCourses()
        )
        val encoded = json.encodeToString(bundle)
        val decoded = json.decodeFromString(ScheduleShareBundle.serializer(), encoded)
        assertEquals(bundle.courses.size, decoded.courses.size)
        assertEquals(GdouCampus.HAIBIN, decoded.campus)

        val task = TodoTaskItem(title = "实验报告", courseName = "数据结构", dueDate = "2026-11-26")
        val taskJson = json.encodeToString(task)
        val decodedTask = json.decodeFromString(TodoTaskItem.serializer(), taskJson)
        assertEquals("实验报告", decodedTask.title)
        assertFalse(decodedTask.isDone)
    }

    @Test
    fun overlappingCoursesClusterExactPartialAndTriple() {
        val a = CourseItem(id = "a", name = "高等数学", dayOfWeek = 1, startSection = 1, endSection = 2, weeks = listOf(1, 2))
        val b = CourseItem(id = "b", name = "重修物理", dayOfWeek = 1, startSection = 2, endSection = 3, weeks = listOf(1, 2))
        val c = CourseItem(id = "c", name = "实验课", dayOfWeek = 1, startSection = 3, endSection = 4, weeks = listOf(1, 2))
        val d = CourseItem(id = "d", name = "英语", dayOfWeek = 1, startSection = 5, endSection = 6, weeks = listOf(1, 2))

        val clusters = GdouTimeRules.groupOverlappingCourses(listOf(d, c, a, b))
        assertEquals(2, clusters.size)
        assertTrue(clusters[0].hasConflict)
        assertEquals(3, clusters[0].courses.size)
        assertEquals(1, clusters[0].minStartSection)
        assertEquals(4, clusters[0].maxEndSection)
        assertFalse(clusters[1].hasConflict)
        assertEquals("d", clusters[1].courses.first().id)

        val conflictIds = GdouTimeRules.findConflictingCourseIds(listOf(a, b, c, d), weekNumber = 1)
        assertEquals(setOf("a", "b", "c"), conflictIds)
    }

    @Test
    fun allThreeCampusesDemoAndExtendedEveningSlots() {
        GdouCampus.entries.forEach { campus ->
            val demos = GdouTimeRules.createDemoCourses(campus)
            assertTrue("Demo courses empty for $campus", demos.isNotEmpty())
        }
        val late = CourseItem(name = "晚间实验", location = "钟海楼02016", dayOfWeek = 5, startSection = 9, endSection = 11, weeks = listOf(1))
        val tLate = GdouTimeRules.resolveCourseTime(late, GdouCampus.HUGUANG)
        assertEquals("19:30", tLate.startTime)
        assertEquals("22:00", tLate.endTime)
        assertEquals(12, GdouTimeRules.slotsForCampus(GdouCampus.HUGUANG, 12).size)
    }

    @Test
    fun legacySettingsWithoutVersionTriggerSevenDayMigration() {
        val legacyJson = """{"settings":{"campus":"HAIBIN","showWeekend":false}}"""
        val json = Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString(AppDataSnapshot.serializer(), legacyJson)
        assertEquals(0, decoded.settings.settingsVersion)
        assertFalse(decoded.settings.showWeekend)
        assertTrue(decoded.settings.settingsVersion < 2)
    }

    @Test
    fun vacationDatesReturnEmptyCoursesAndAccuratePhase() {
        val c = CourseItem(
            name = "高等数学",
            location = "教学楼204",
            dayOfWeek = 1,
            startSection = 1,
            endSection = 2,
            weeks = listOf(1, 19)
        )
        // Before term (7 days before 2026-08-31)
        val before = GdouTimeRules.calculateTermWeekInfo("2026-08-31", 19, LocalDate.of(2026, 8, 24))
        assertEquals(TermWeekPhase.BEFORE_TERM, before.phase)
        assertEquals(7L, before.daysUntilStart)
        assertTrue(
            GdouTimeRules.getTodayCoursesWithStatus(
                courses = listOf(c),
                startDateStr = "2026-08-31",
                totalWeeks = 19,
                campus = GdouCampus.HAIBIN,
                now = java.time.LocalDateTime.of(2026, 8, 24, 9, 0)
            ).isEmpty()
        )

        // After term (Week 20 Monday -> 2027-01-11)
        val after = GdouTimeRules.calculateTermWeekInfo("2026-08-31", 19, LocalDate.of(2027, 1, 11))
        assertEquals(TermWeekPhase.AFTER_TERM, after.phase)
        assertEquals(19, after.clampedWeek)
        assertTrue(
            GdouTimeRules.getTodayCoursesWithStatus(
                courses = listOf(c),
                startDateStr = "2026-08-31",
                totalWeeks = 19,
                campus = GdouCampus.HAIBIN,
                now = java.time.LocalDateTime.of(2027, 1, 11, 9, 0)
            ).isEmpty()
        )
        assertTrue(
            GdouTimeRules.getCoursesForDate(
                courses = listOf(c),
                startDateStr = "2026-08-31",
                totalWeeks = 19,
                campus = GdouCampus.HAIBIN,
                date = LocalDate.of(2027, 1, 11)
            ).isEmpty()
        )
    }

    @Test
    fun tomorrowCoursesCrossWeekBoundaryAndSortChronologically() {
        val lateNormal = CourseItem(
            name = "高等数学",
            location = "教学楼121",
            dayOfWeek = 1,
            startSection = 3,
            endSection = 4,
            weeks = listOf(2)
        )
        val earlyStaggered = CourseItem(
            name = "工程数学",
            location = "实验楼405",
            dayOfWeek = 1,
            startSection = 3,
            endSection = 4,
            weeks = listOf(2)
        )
        // Week 2 Monday is 2026-09-07 (previewed on Week 1 Sunday night!)
        val monCourses = GdouTimeRules.getCoursesForDate(
            courses = listOf(lateNormal, earlyStaggered),
            startDateStr = "2026-08-31",
            totalWeeks = 19,
            campus = GdouCampus.HAIBIN,
            date = LocalDate.of(2026, 9, 7)
        )
        assertEquals(2, monCourses.size)
        assertEquals("工程数学", monCourses.first().course.name)
        assertEquals("10:05", monCourses.first().time.startTime)
    }
}
