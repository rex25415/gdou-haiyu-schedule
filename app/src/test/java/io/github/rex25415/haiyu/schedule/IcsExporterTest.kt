package io.github.rex25415.haiyu.schedule

import io.github.rex25415.haiyu.schedule.data.IcsExporter
import io.github.rex25415.haiyu.schedule.model.AppDataSnapshot
import io.github.rex25415.haiyu.schedule.model.AppSettings
import io.github.rex25415.haiyu.schedule.model.CourseItem
import io.github.rex25415.haiyu.schedule.model.GdouCampus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsExporterTest {

    @Test
    fun buildIcsCalendarReturnsEmptyWhenNoCoursesPresent() {
        val emptySnapshot = AppDataSnapshot(courses = emptyList())
        assertEquals("", IcsExporter.buildIcsCalendarContent(emptySnapshot, nowStamp = "20261124T120000"))
    }

    @Test
    fun buildIcsCalendarUsesRfc5545EscapingStaggeredTimesAndClampedAlarm() {
        val snapshot = AppDataSnapshot(
            courses = listOf(
                CourseItem(
                    id = "c1",
                    name = "电路原理, 第1讲;重点",
                    location = "实验楼401",
                    teacher = "王老师",
                    dayOfWeek = 1,
                    startSection = 3,
                    endSection = 4,
                    weeks = listOf(1, 3, 1),
                    note = "带万用表\\实验册\n注意安全"
                )
            ),
            settings = AppSettings(
                campus = GdouCampus.HAIBIN,
                semesterStartDate = "2026-08-31",
                academicYearLabel = "2026-2027 第一学期",
                reminderMinutesBefore = 2 // clamped cleanly to >= 5
            )
        )

        val ics = IcsExporter.buildIcsCalendarContent(snapshot, nowStamp = "20261124T120000")
        assertTrue(ics.contains("\r\n"))
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"))
        assertTrue(ics.contains("X-WR-CALNAME:海屿空间-2026-2027 第一学期\r\n"))
        // Haibin 实验楼 3-4 section is staggered 10:05-11:35
        assertTrue(ics.contains("DTSTART;TZID=Asia/Shanghai:20260831T100500\r\n"))
        assertTrue(ics.contains("DTEND;TZID=Asia/Shanghai:20260831T113500\r\n"))
        // Week 3 (2026-08-31 + 14 days = 2026-09-14) appears, duplicate week 1 deduplicated
        assertTrue(ics.contains("UID:c1-w1@haiyu-schedule.local\r\n"))
        assertTrue(ics.contains("UID:c1-w3@haiyu-schedule.local\r\n"))
        assertEquals(2, ics.split("BEGIN:VEVENT").size - 1)
        // Escaped special RFC 5545 characters
        assertTrue(ics.contains("SUMMARY:电路原理\\, 第1讲\\;重点\r\n"))
        assertTrue(ics.contains("带万用表\\\\实验册\\n注意安全"))
        // Clamped VALARM trigger
        assertTrue(ics.contains("TRIGGER:-PT5M\r\n"))
        assertFalse(ics.contains("TRIGGER:-PT2M"))
    }
}
