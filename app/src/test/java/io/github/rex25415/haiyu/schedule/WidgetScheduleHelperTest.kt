package io.github.rex25415.haiyu.schedule

import io.github.rex25415.haiyu.schedule.data.GdouTimeRules
import io.github.rex25415.haiyu.schedule.model.AppDataSnapshot
import io.github.rex25415.haiyu.schedule.model.AppSettings
import io.github.rex25415.haiyu.schedule.model.CourseItem
import io.github.rex25415.haiyu.schedule.model.GdouCampus
import io.github.rex25415.haiyu.schedule.widget.WidgetScheduleHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class WidgetScheduleHelperTest {

    private val baseCourse = CourseItem(
        name = "电路原理",
        teacher = "陈老师",
        location = "实验楼401",
        dayOfWeek = 1,
        startSection = 3,
        endSection = 4,
        weeks = (1..16).toList()
    )

    private val baseSettings = AppSettings(
        campus = GdouCampus.HAIBIN,
        semesterStartDate = "2026-08-31",
        totalWeeks = 20
    )

    @Test
    fun resolveFocusClassSummary_handlesUnsyncedAndOutOfTermStates() {
        val emptySummary = WidgetScheduleHelper.resolveFocusClassSummary(
            snapshot = AppDataSnapshot(settings = baseSettings),
            now = LocalDateTime.of(2026, 9, 7, 9, 0)
        )
        assertEquals("待同步", emptySummary.badge)
        assertEquals("尚未同步课表", emptySummary.primaryTitle)

        val beforeTerm = WidgetScheduleHelper.resolveFocusClassSummary(
            snapshot = AppDataSnapshot(courses = listOf(baseCourse), settings = baseSettings),
            now = LocalDateTime.of(2026, 8, 29, 10, 0)
        )
        assertEquals("未开学", beforeTerm.badge)
        assertTrue(beforeTerm.primaryTitle.contains("2 天"))

        val afterTerm = WidgetScheduleHelper.resolveFocusClassSummary(
            snapshot = AppDataSnapshot(courses = listOf(baseCourse), settings = baseSettings),
            now = LocalDateTime.of(2027, 2, 1, 10, 0)
        )
        assertEquals("假期中", afterTerm.badge)
    }

    @Test
    fun resolveFocusClassSummary_resolvesOngoingUpcomingAndRemainingCount() {
        val eveningCourse = baseCourse.copy(
            name = "大学英语",
            location = "教学楼202",
            startSection = 9,
            endSection = 10
        )
        val snapshot = AppDataSnapshot(
            courses = listOf(baseCourse, eveningCourse),
            settings = baseSettings
        )

        // Monday Week 2 at 09:00 -> upcoming 3-4 staggered Haibin (10:05-11:35)
        val beforeClass = WidgetScheduleHelper.resolveFocusClassSummary(
            snapshot = snapshot,
            now = LocalDateTime.of(2026, 9, 7, 9, 0)
        )
        assertEquals("下一节", beforeClass.badge)
        assertEquals("第2周 周一", beforeClass.weekTag)
        assertEquals("2周周一", beforeClass.compactHeaderMeta)
        assertEquals("电路原理", beforeClass.primaryTitle)
        assertEquals("实验楼401", beforeClass.highlightLocation)
        assertEquals("10:05-11:35", beforeClass.compactTimeOnly)
        assertEquals("余2节", beforeClass.footerBadge)
        assertTrue(beforeClass.oneLineSubtitle.contains("错峰"))

        // During class 10:20 -> ongoing + 后余1节
        val inClass = WidgetScheduleHelper.resolveFocusClassSummary(
            snapshot = snapshot,
            now = LocalDateTime.of(2026, 9, 7, 10, 20)
        )
        assertEquals("上课中", inClass.badge)
        assertEquals("电路原理", inClass.primaryTitle)
        assertEquals("后余1节", inClass.footerBadge)
    }

    @Test
    fun resolveFocusClassSummary_respectsTomorrowPreviewAndPinnedConflict() {
        val mondayA = baseCourse.copy(name = "高等数学", location = "教101")
        val mondayB = baseCourse.copy(name = "数据结构", location = "机房B")
        val tuesdayMorning = baseCourse.copy(
            name = "大学物理",
            location = "教学楼305",
            dayOfWeek = 2,
            startSection = 1,
            endSection = 2
        )
        val pinnedKey = GdouTimeRules.courseConflictKey(mondayB)
        val snapshot = AppDataSnapshot(
            courses = listOf(mondayA, mondayB, tuesdayMorning),
            settings = baseSettings.copy(pinnedConflictKeys = listOf(pinnedKey))
        )

        val upcomingPinned = WidgetScheduleHelper.resolveFocusClassSummary(
            snapshot = snapshot,
            now = LocalDateTime.of(2026, 9, 7, 8, 30)
        )
        assertEquals("数据结构", upcomingPinned.primaryTitle)
        assertEquals("机房B", upcomingPinned.highlightLocation)

        val mondayNight = WidgetScheduleHelper.resolveFocusClassSummary(
            snapshot = snapshot,
            now = LocalDateTime.of(2026, 9, 7, 21, 30)
        )
        assertEquals("明日早课", mondayNight.badge)
        assertEquals("大学物理", mondayNight.primaryTitle)
        assertEquals("教学楼305", mondayNight.highlightLocation)
        assertEquals("共1节", mondayNight.footerBadge)

        val tuesdayNightNoWed = WidgetScheduleHelper.resolveFocusClassSummary(
            snapshot = snapshot,
            now = LocalDateTime.of(2026, 9, 8, 21, 30)
        )
        assertEquals("已下课", tuesdayNightNoWed.badge)
        assertEquals("暂无待上课程", tuesdayNightNoWed.primaryTitle)
    }
}
