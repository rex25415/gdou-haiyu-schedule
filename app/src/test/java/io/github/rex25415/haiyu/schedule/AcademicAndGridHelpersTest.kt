package io.github.rex25415.haiyu.schedule

import io.github.rex25415.haiyu.schedule.model.ClassroomSlotStatus
import io.github.rex25415.haiyu.schedule.model.ExamItem
import io.github.rex25415.haiyu.schedule.model.GradeItem
import io.github.rex25415.haiyu.schedule.ui.components.GradeNatureFilter
import io.github.rex25415.haiyu.schedule.ui.components.filterAcademicGrades
import io.github.rex25415.haiyu.schedule.ui.components.findLongestContinuousFreeStreak
import io.github.rex25415.haiyu.schedule.ui.components.sortAcademicExams
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class AcademicAndGridHelpersTest {

    @Test
    fun filterAcademicGrades_identifiesRetakeFailedHighScoreAndQueryMatches() {
        val sampleGrades = listOf(
            GradeItem(
                id = "g1",
                academicYear = "2024-2025",
                semester = "第一学期",
                courseName = "大学物理",
                score = "52",
                gradePoint = 0.0,
                credit = 3.0,
                courseType = "必修课",
                examNature = "正常考试"
            ),
            GradeItem(
                id = "g2",
                academicYear = "2024-2025",
                semester = "第二学期",
                courseName = "大学物理",
                score = "78",
                gradePoint = 2.8,
                credit = 3.0,
                courseType = "必修课",
                examNature = "重修"
            ),
            GradeItem(
                id = "g3",
                academicYear = "2025-2026",
                semester = "第一学期",
                courseName = "海洋文化概论",
                score = "93",
                gradePoint = 4.3,
                credit = 1.5,
                courseType = "公共选修课",
                examNature = "正常考试"
            )
        )

        val retakeOrFailed = filterAcademicGrades(
            grades = sampleGrades,
            selectedTerm = "全部学期",
            natureFilter = GradeNatureFilter.RETAKE_OR_FAILED,
            searchQuery = ""
        )
        assertEquals(listOf("g1", "g2"), retakeOrFailed.map { it.id })

        val highScore = filterAcademicGrades(
            grades = sampleGrades,
            selectedTerm = "全部学期",
            natureFilter = GradeNatureFilter.HIGH_SCORE,
            searchQuery = ""
        )
        assertEquals(listOf("g3"), highScore.map { it.id })

        val natureQuery = filterAcademicGrades(
            grades = sampleGrades,
            selectedTerm = "全部学期",
            natureFilter = GradeNatureFilter.ALL,
            searchQuery = "重修"
        )
        assertEquals(listOf("g2"), natureQuery.map { it.id })
    }

    @Test
    fun sortAcademicExams_ordersUpcomingPinnedUndatedAndPastCleanly() {
        val now = LocalDateTime.of(2026, 6, 15, 10, 0)
        val exams = listOf(
            ExamItem(id = "past", courseName = "已考完", examTimeRaw = "2026-06-10(09:00-11:00)"),
            ExamItem(id = "undated", courseName = "待定科目", examTimeRaw = "待定"),
            ExamItem(id = "later", courseName = "较晚科目", examTimeRaw = "2026-06-25(09:00-11:00)", isPinned = false),
            ExamItem(id = "pinnedLater", courseName = "置顶科目", examTimeRaw = "2026-06-28(09:00-11:00)", isPinned = true)
        )

        val sortedIds = sortAcademicExams(exams, now).map { it.exam.id }
        assertEquals(listOf("pinnedLater", "later", "undated", "past"), sortedIds)
    }

    @Test
    fun findLongestContinuousFreeStreak_findsMaxConsecutiveSpanAcrossDay() {
        val slots = listOf(
            ClassroomSlotStatus(1, 2, isFree = true),
            ClassroomSlotStatus(3, 4, isFree = false),
            ClassroomSlotStatus(5, 6, isFree = true),
            ClassroomSlotStatus(7, 8, isFree = true),
            ClassroomSlotStatus(9, 10, isFree = true)
        )
        val best = findLongestContinuousFreeStreak(slots)
        assertEquals(Triple(5, 10, 6), best)
    }
}
