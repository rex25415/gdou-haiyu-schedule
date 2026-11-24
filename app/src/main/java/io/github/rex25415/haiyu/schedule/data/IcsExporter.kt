package io.github.rex25415.haiyu.schedule.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import io.github.rex25415.haiyu.schedule.model.AppDataSnapshot
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object IcsExporter {

    private val DT_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    fun exportToIcsAndShare(context: Context, snapshot: AppDataSnapshot): Boolean {
        val icsContent = buildIcsCalendarContent(snapshot)
        if (icsContent.isEmpty()) return false

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val icsFile = File(exportDir, "海屿空间_广海_${System.currentTimeMillis()}.ics")
        icsFile.writeText(icsContent, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            icsFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/calendar"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "海屿空间课程日历 (.ics)")
            clipData = ClipData.newRawUri("海屿空间.ics", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(shareIntent, "分享/导入到系统日历").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
        return true
    }

    /**
     * 生成遵循 RFC 5545 规范的纯净 .ics 日历文本，方便在无 Android Context 下做单测验证。
     */
    internal fun buildIcsCalendarContent(
        snapshot: AppDataSnapshot,
        nowStamp: String = LocalDateTime.now().format(DT_FORMAT)
    ): String {
        if (snapshot.courses.isEmpty()) return ""
        val startMonday = GdouTimeRules.parseLocalDate(snapshot.settings.semesterStartDate)
            .with(DayOfWeek.MONDAY)
        val campus = snapshot.settings.campus
        val alarmMins = snapshot.settings.reminderMinutesBefore.coerceIn(5, 60)

        val sb = StringBuilder()
        sb.append("BEGIN:VCALENDAR\r\n")
        sb.append("VERSION:2.0\r\n")
        sb.append("PRODID:-//HaiyuSchedule//OpenSource//CN\r\n")
        sb.append("CALSCALE:GREGORIAN\r\n")
        sb.append("X-WR-CALNAME:海屿空间-${snapshot.settings.academicYearLabel}\r\n")
        sb.append("X-WR-TIMEZONE:Asia/Shanghai\r\n")

        snapshot.courses.forEach { course ->
            val resolved = GdouTimeRules.resolveCourseTime(course, campus)
            val startTime = GdouTimeRules.parseLocalTime(resolved.startTime)
            val endTime = GdouTimeRules.parseLocalTime(resolved.endTime)

            course.weeks.distinct().sorted().forEach { week ->
                val date = startMonday
                    .plusWeeks((week - 1).toLong())
                    .plusDays((course.dayOfWeek - 1).toLong())
                val dtStart = LocalDateTime.of(date, startTime).format(DT_FORMAT)
                val dtEnd = LocalDateTime.of(date, endTime).format(DT_FORMAT)
                val descParts = buildList {
                    if (course.teacher.isNotBlank()) add("教师：${course.teacher}")
                    add("节次：第${course.startSection}-${course.endSection}节")
                    if (resolved.isSpecialStaggered && resolved.reason != null) {
                        add("作息：${resolved.reason} (${resolved.startTime}-${resolved.endTime})")
                    }
                    if (course.note.isNotBlank()) add("备注：${course.note}")
                }
                sb.append("BEGIN:VEVENT\r\n")
                sb.append("UID:${course.id}-w${week}@haiyu-schedule.local\r\n")
                sb.append("DTSTAMP:${nowStamp}\r\n")
                sb.append("DTSTART;TZID=Asia/Shanghai:${dtStart}\r\n")
                sb.append("DTEND;TZID=Asia/Shanghai:${dtEnd}\r\n")
                sb.append("SUMMARY:${escapeIcs(course.name)}\r\n")
                if (course.location.isNotBlank()) {
                    sb.append("LOCATION:${escapeIcs(course.location)}\r\n")
                }
                sb.append("DESCRIPTION:${escapeIcs(descParts.joinToString("\n"))}\r\n")
                sb.append("BEGIN:VALARM\r\n")
                sb.append("TRIGGER:-PT${alarmMins}M\r\n")
                sb.append("ACTION:DISPLAY\r\n")
                sb.append("DESCRIPTION:${escapeIcs("即将上课：${course.name} @${course.location.ifBlank { "地点待定" }}")}\r\n")
                sb.append("END:VALARM\r\n")
                sb.append("END:VEVENT\r\n")
            }
        }
        sb.append("END:VCALENDAR\r\n")
        return sb.toString()
    }

    private fun escapeIcs(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace(",", "\\,")
            .replace(";", "\\;")
            .replace("\n", "\\n")
    }
}
