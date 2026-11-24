package io.github.rex25415.haiyu.schedule

import io.github.rex25415.haiyu.schedule.data.GdouTimeRules
import io.github.rex25415.haiyu.schedule.data.JointFreeState
import io.github.rex25415.haiyu.schedule.data.TermWeekPhase
import io.github.rex25415.haiyu.schedule.model.AppDataSnapshot
import io.github.rex25415.haiyu.schedule.model.CourseItem
import io.github.rex25415.haiyu.schedule.model.ExamItem
import io.github.rex25415.haiyu.schedule.model.GdouCampus
import io.github.rex25415.haiyu.schedule.model.ScheduleShareBundle
import io.github.rex25415.haiyu.schedule.model.TodoTaskItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

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

    @Test
    fun pinnedConflictCourseWinsClusterAndTodayOrder() {
        val c1 = CourseItem(
            id = "c1",
            name = "高等数学",
            location = "教学楼101",
            dayOfWeek = 1,
            startSection = 3,
            endSection = 4,
            weeks = listOf(1)
        )
        val c2 = CourseItem(
            id = "c2",
            name = "英语听力",
            location = "教学楼202",
            dayOfWeek = 1,
            startSection = 3,
            endSection = 4,
            weeks = listOf(1)
        )
        val defaultClusters = GdouTimeRules.groupOverlappingCourses(listOf(c1, c2))
        val initiallySecond = defaultClusters.first().courses.last()
        val pinnedKey = setOf(GdouTimeRules.courseConflictKey(initiallySecond))
        val pinnedClusters = GdouTimeRules.groupOverlappingCourses(listOf(c1, c2), pinnedKey)
        assertEquals(initiallySecond.id, pinnedClusters.first().courses.first().id)

        val todayStatuses = GdouTimeRules.getTodayCoursesWithStatus(
            courses = listOf(c1, c2),
            startDateStr = "2026-08-31",
            totalWeeks = 19,
            campus = GdouCampus.HAIBIN,
            now = java.time.LocalDateTime.of(2026, 8, 31, 10, 30),
            pinnedKeys = pinnedKey
        )
        assertEquals(initiallySecond.id, todayStatuses.first().course.id)
    }

    @Test
    fun cachedParseLocalTimeHandlesValidAndFallbackInputs() {
        val t1 = GdouTimeRules.parseLocalTime("10:05")
        val t2 = GdouTimeRules.parseLocalTime("10:05")
        assertEquals(10, t1.hour)
        assertEquals(5, t1.minute)
        assertEquals(t1, t2)

        val fallback = GdouTimeRules.parseLocalTime("invalid-time")
        assertEquals(8, fallback.hour)
        assertEquals(10, fallback.minute)
    }

    @Test
    fun resolveExamsForWeekProjectsDatesAndSectionsAccurately() {
        val e1 = ExamItem(
            courseName = "线性代数",
            examTimeRaw = "2026-12-28(08:30-10:30)",
            location = "教学楼204",
            seatNumber = "12"
        )
        val e2 = ExamItem(
            courseName = "大学物理",
            examTimeRaw = "2026-12-30 14:30-16:30",
            location = "教学楼305",
            seatNumber = "28"
        )
        val eOther = ExamItem(
            courseName = "下周考试",
            examTimeRaw = "2027-01-05(09:00-11:00)",
            location = "教学楼101"
        )
        // Start Monday 2026-08-31 -> Week 18 Monday is 2026-12-28
        val week18 = GdouTimeRules.resolveExamsForWeek(
            exams = listOf(e1, e2, eOther),
            startDateStr = "2026-08-31",
            weekNumber = 18,
            campus = GdouCampus.HAIBIN
        )
        assertEquals(2, week18.size)
        assertEquals(1, week18[0].dayOfWeek)
        assertEquals(1, week18[0].startSection)
        assertTrue(week18[0].endSection >= 3)
        assertEquals(3, week18[1].dayOfWeek)
        assertEquals(5, week18[1].startSection)
        assertEquals(6, week18[1].endSection)
    }

    @Test
    fun computeJointFreeMatrixDetectsAllFourStates() {
        val myCourses = listOf(
            CourseItem(name = "高数", dayOfWeek = 1, startSection = 1, endSection = 2, weeks = listOf(5)),
            CourseItem(name = "线代", dayOfWeek = 1, startSection = 3, endSection = 4, weeks = listOf(5))
        )
        val partnerCourses = listOf(
            CourseItem(name = "高数", dayOfWeek = 1, startSection = 1, endSection = 2, weeks = listOf(5)),
            CourseItem(name = "英语", dayOfWeek = 1, startSection = 5, endSection = 6, weeks = listOf(5))
        )
        val matrix = GdouTimeRules.computeJointFreeMatrix(myCourses, partnerCourses, weekNumber = 5)
        assertEquals(35, matrix.size)
        assertEquals(JointFreeState.BOTH_BUSY, matrix.first { it.dayOfWeek == 1 && it.blockIndex == 0 }.state)
        assertEquals(JointFreeState.ONLY_ME_BUSY, matrix.first { it.dayOfWeek == 1 && it.blockIndex == 1 }.state)
        assertEquals(JointFreeState.ONLY_PARTNER_BUSY, matrix.first { it.dayOfWeek == 1 && it.blockIndex == 2 }.state)
        assertEquals(JointFreeState.BOTH_FREE, matrix.first { it.dayOfWeek == 1 && it.blockIndex == 3 }.state)
    }

    @Test
    fun formatWeekScheduleAndExamsAsCleanTextWorks() {
        val c = CourseItem(name = "数据结构", location = "实验楼301", dayOfWeek = 1, startSection = 3, endSection = 4, weeks = listOf(1))
        val schedTxt = GdouTimeRules.formatWeekScheduleAsText(listOf(c), 1, GdouCampus.HAIBIN, "2026-2027 第一学期")
        assertTrue(schedTxt.contains("数据结构"))
        assertTrue(schedTxt.contains("10:05-11:35"))
        assertTrue(schedTxt.contains("[错峰]"))

        val ex = ExamItem(courseName = "数据结构", examTimeRaw = "2026-12-28(09:00-11:00)", location = "教学楼204", seatNumber = "15")
        val examTxt = GdouTimeRules.formatExamsAsText(listOf(ex), "2026-2027 第一学期")
        assertTrue(examTxt.contains("数据结构"))
        assertTrue(examTxt.contains("15号座"))
    }

    @Test
    fun calculateTargetGpaPlanHandlesAchievableReachedAndImpossibleCases() {
        // Current 3.0 over 40 credits, target 3.5 with 20 future credits -> needs (3.5*60 - 3.0*40)/20 = 4.5 (95分)
        val normal = GdouTimeRules.calculateTargetGpaPlan(
            currentGpa = 3.0,
            currentCredits = 40.0,
            targetGpa = 3.5,
            plannedFutureCredits = 20.0
        )
        assertTrue(normal.achievable)
        assertFalse(normal.alreadyReached)
        assertEquals(4.5, normal.requiredFutureGpa, 0.001)
        assertEquals(95.0, normal.estimatedScore, 0.1)

        // Already high enough (4.0 over 50 credits, target 3.0 with 10 credits -> neededGpa = -2.0 <= 0)
        val reached = GdouTimeRules.calculateTargetGpaPlan(
            currentGpa = 4.0,
            currentCredits = 50.0,
            targetGpa = 3.0,
            plannedFutureCredits = 10.0
        )
        assertTrue(reached.alreadyReached)
        assertTrue(reached.achievable)

        // Impossible in 10 credits (1.5 over 50 credits, target 3.5 -> needs 13.5 > 5.0)
        val impossible = GdouTimeRules.calculateTargetGpaPlan(
            currentGpa = 1.5,
            currentCredits = 50.0,
            targetGpa = 3.5,
            plannedFutureCredits = 10.0
        )
        assertFalse(impossible.achievable)
        assertTrue(impossible.summaryText.contains("超过满绩 5.0"))
        assertTrue(impossible.summaryText.contains("约需"))
    }

    @Test
    fun extractRoomFloorAndCourseLocationContextWorkAcrossCampuses() {
        assertEquals(5, GdouTimeRules.extractRoomFloor("教学楼505", "教学楼"))
        assertEquals(5, GdouTimeRules.extractRoomFloor("实506-1计算机(2)室", "实验楼"))
        assertEquals(2, GdouTimeRules.extractRoomFloor("钟海楼02005", "钟海楼"))
        assertEquals(2, GdouTimeRules.extractRoomFloor("广学楼208(多)", "广学楼"))
        val mingdeParsed = GdouTimeRules.extractBuildingAndFloor("明德楼B2302", GdouCampus.HUGUANG)
        assertNotNull(mingdeParsed)
        assertEquals("明德楼", mingdeParsed!!.first)
        assertEquals(3, mingdeParsed.second)

        val haibinLab = GdouTimeRules.extractBuildingAndFloor("实506-1", GdouCampus.HAIBIN)
        assertEquals("实验楼" to 5, haibinLab)
        val haibinTeach = GdouTimeRules.extractBuildingAndFloor("教学楼505", GdouCampus.HAIBIN)
        assertEquals("教学楼" to 5, haibinTeach)
        assertEquals("钟海楼" to 2, GdouTimeRules.extractBuildingAndFloor("钟海楼02005", GdouCampus.HUGUANG))
        assertEquals("广学楼" to 2, GdouTimeRules.extractBuildingAndFloor("广学楼208(多)", GdouCampus.HUGUANG))

        val haibinCourses = listOf(
            CourseItem(name = "数据结构实验", location = "实506-1", dayOfWeek = 1, startSection = 1, endSection = 2, weeks = listOf(1)),
            CourseItem(name = "线性代数", location = "教学楼505", dayOfWeek = 1, startSection = 3, endSection = 4, weeks = listOf(1))
        )
        // Monday 2026-09-07 09:50 -> 1-2 finished, 3-4 upcoming -> prefers upcoming 教学楼505
        val upcomingCtx = GdouTimeRules.inferCourseLocationContext(
            courses = haibinCourses,
            startDateStr = "2026-09-07",
            totalWeeks = 20,
            targetCampus = GdouCampus.HAIBIN,
            targetWeek = 1,
            targetDay = 1,
            queryStartSection = 3,
            now = LocalDateTime.of(2026, 9, 7, 9, 50)
        )
        assertNotNull(upcomingCtx)
        assertEquals("教学楼", upcomingCtx!!.building)
        assertEquals(5, upcomingCtx.floor)
        assertTrue(upcomingCtx.hintText.contains("教学楼505"))

        // Campus isolation: querying Huguang with Haibin-only courses returns null
        val isolatedHuguangCtx = GdouTimeRules.inferCourseLocationContext(
            courses = haibinCourses,
            startDateStr = "2026-09-07",
            totalWeeks = 20,
            targetCampus = GdouCampus.HUGUANG,
            targetWeek = 1,
            targetDay = 1,
            queryStartSection = 3,
            now = LocalDateTime.of(2026, 9, 7, 9, 50)
        )
        assertNull(isolatedHuguangCtx)

        val huguangCtx = GdouTimeRules.inferCourseLocationContext(
            courses = haibinCourses + CourseItem(
                name = "海洋科学导论",
                location = "钟海楼02005",
                dayOfWeek = 1,
                startSection = 3,
                endSection = 4,
                weeks = listOf(1)
            ),
            startDateStr = "2026-09-07",
            totalWeeks = 20,
            targetCampus = GdouCampus.HUGUANG,
            targetWeek = 1,
            targetDay = 1,
            queryStartSection = 3,
            now = LocalDateTime.of(2026, 9, 7, 10, 30)
        )
        assertNotNull(huguangCtx)
        assertEquals("钟海楼", huguangCtx!!.building)
        assertEquals(2, huguangCtx.floor)
        assertTrue(huguangCtx.hintText.contains("正在上课"))
    }
}
