package io.github.rex25415.haiyu.schedule.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import io.github.rex25415.haiyu.schedule.MainActivity
import io.github.rex25415.haiyu.schedule.R
import io.github.rex25415.haiyu.schedule.data.AppRepository
import io.github.rex25415.haiyu.schedule.data.GdouTimeRules
import io.github.rex25415.haiyu.schedule.data.TermWeekPhase
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object ClassReminderScheduler {

    const val CHANNEL_ID = "haiyu_class_reminders"
    const val ACTION_CLASS_REMINDER = "io.github.rex25415.haiyu.schedule.ACTION_CLASS_REMINDER"
    const val ACTION_DDL_REMINDER = "io.github.rex25415.haiyu.schedule.ACTION_DDL_REMINDER"

    const val EXTRA_COURSE_NAME = "course_name"
    const val EXTRA_LOCATION = "location"
    const val EXTRA_TIME_TEXT = "time_text"
    const val EXTRA_TEACHER = "teacher"
    const val EXTRA_STAGGERED = "staggered"

    const val EXTRA_DDL_TITLE = "ddl_title"
    const val EXTRA_DDL_COURSE = "ddl_course"
    const val EXTRA_DDL_DUE_DATE = "ddl_due_date"

    private const val REQUEST_CODE_NEXT = 202601
    private const val REQUEST_CODE_DDL = 202602

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "上课与作业 DDL 提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "海屿课表按广海三校区精准错峰作息发送上课提醒与临近作业 DDL 通知"
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }
    }

    fun rescheduleNextReminder(context: Context) {
        ensureChannel(context)
        val repo = AppRepository.getInstance(context)
        val snapshot = repo.state.value
        val settings = snapshot.settings
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        cancelExisting(context, am)
        cancelExistingDdl(context, am)
        if (!settings.reminderEnabled) return

        scheduleNextClassInternal(context, am)
        scheduleNextDdlInternal(context, am)
    }

    private fun scheduleNextClassInternal(context: Context, am: AlarmManager) {
        val snapshot = AppRepository.getInstance(context).state.value
        val settings = snapshot.settings
        if (snapshot.courses.isEmpty()) return

        val now = LocalDateTime.now()
        val minutesBefore = settings.reminderMinutesBefore.coerceIn(5, 60)

        var earliestTrigger: LocalDateTime? = null
        val earliestCourses = mutableListOf<Triple<String, String, Boolean>>() // (name, loc, staggered)
        var bestTimeText = ""
        var bestTeacher = ""

        // Look ahead up to 14 days, strictly respecting TermWeekPhase.IN_TERM (never fires during vacation!)
        for (dayOffset in 0L..14L) {
            val date = now.toLocalDate().plusDays(dayOffset)
            val weekInfo = GdouTimeRules.calculateTermWeekInfo(settings.semesterStartDate, settings.totalWeeks, date)
            if (weekInfo.phase != TermWeekPhase.IN_TERM) continue
            val weekNum = weekInfo.rawWeek
            val dow = date.dayOfWeek.value

            snapshot.courses
                .filter { it.dayOfWeek == dow && weekNum in it.weeks }
                .sortedWith(compareBy({ it.startSection }, { it.endSection }, { it.name }))
                .forEach { course ->
                    val resolved = GdouTimeRules.resolveCourseTime(course, settings.campus)
                    val startTime = GdouTimeRules.parseLocalTime(resolved.startTime)
                    val classDateTime = LocalDateTime.of(date, startTime)
                    val triggerAt = classDateTime.minusMinutes(minutesBefore.toLong())
                    if (triggerAt.isAfter(now)) {
                        val locText = course.location.ifBlank { "地点待定" }
                        if (earliestTrigger == null || triggerAt.isBefore(earliestTrigger)) {
                            earliestTrigger = triggerAt
                            earliestCourses.clear()
                            earliestCourses.add(Triple(course.name, locText, resolved.isSpecialStaggered))
                            bestTimeText = "${resolved.startTime}-${resolved.endTime}"
                            bestTeacher = course.teacher
                        } else if (triggerAt == earliestTrigger) {
                            earliestCourses.add(Triple(course.name, locText, resolved.isSpecialStaggered))
                        }
                    }
                }
            if (earliestTrigger != null) break
        }

        val triggerTime = earliestTrigger ?: return
        val first = earliestCourses.firstOrNull() ?: return
        val bestTitle = if (earliestCourses.size > 1) {
            "${first.first}（另有${earliestCourses.size - 1}门重课）"
        } else {
            first.first
        }
        val bestLoc = if (earliestCourses.size > 1) {
            earliestCourses.joinToString(" / ") { "${it.first}@${it.second}" }
        } else {
            first.second
        }
        val bestStaggered = earliestCourses.any { it.third }
        val triggerMillis = triggerTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val intent = Intent(context, ClassReminderReceiver::class.java).apply {
            action = ACTION_CLASS_REMINDER
            putExtra(EXTRA_COURSE_NAME, bestTitle)
            putExtra(EXTRA_LOCATION, bestLoc)
            putExtra(EXTRA_TIME_TEXT, bestTimeText)
            putExtra(EXTRA_TEACHER, bestTeacher)
            putExtra(EXTRA_STAGGERED, bestStaggered)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_NEXT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleAlarmCompat(am, triggerMillis, pi)
    }

    private fun scheduleNextDdlInternal(context: Context, am: AlarmManager) {
        val snapshot = AppRepository.getInstance(context).state.value
        val pending = snapshot.tasks.filter { !it.isDone && it.dueDate.isNotBlank() }
        if (pending.isEmpty()) return

        val now = LocalDateTime.now()
        var earliestTrigger: LocalDateTime? = null
        var targetTaskTitle = ""
        var targetCourse = ""
        var targetDueDate = ""

        for (task in pending) {
            val due = try {
                LocalDate.parse(task.dueDate.trim())
            } catch (_: Exception) {
                null
            } ?: continue

            // Gentle reminder: 20:00 the night before dueDate, or 09:00 on dueDate if already past eve 20:00
            val eveTrigger = LocalDateTime.of(due.minusDays(1), LocalTime.of(20, 0))
            val morningTrigger = LocalDateTime.of(due, LocalTime.of(9, 0))
            val chosen = when {
                eveTrigger.isAfter(now) -> eveTrigger
                morningTrigger.isAfter(now) -> morningTrigger
                else -> null
            } ?: continue

            if (earliestTrigger == null || chosen.isBefore(earliestTrigger)) {
                earliestTrigger = chosen
                targetTaskTitle = task.title
                targetCourse = task.courseName
                targetDueDate = task.dueDate
            }
        }

        val triggerTime = earliestTrigger ?: return
        val triggerMillis = triggerTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = Intent(context, ClassReminderReceiver::class.java).apply {
            action = ACTION_DDL_REMINDER
            putExtra(EXTRA_DDL_TITLE, targetTaskTitle)
            putExtra(EXTRA_DDL_COURSE, targetCourse)
            putExtra(EXTRA_DDL_DUE_DATE, targetDueDate)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_DDL,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleAlarmCompat(am, triggerMillis, pi)
    }

    private fun scheduleAlarmCompat(am: AlarmManager, triggerMillis: Long, pi: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
        }
    }

    private fun cancelExisting(context: Context, am: AlarmManager) {
        val intent = Intent(context, ClassReminderReceiver::class.java).apply {
            action = ACTION_CLASS_REMINDER
        }
        val pi = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_NEXT,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) am.cancel(pi)
    }

    private fun cancelExistingDdl(context: Context, am: AlarmManager) {
        val intent = Intent(context, ClassReminderReceiver::class.java).apply {
            action = ACTION_DDL_REMINDER
        }
        val pi = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_DDL,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) am.cancel(pi)
    }

    fun sendTestNotification(context: Context) {
        ensureChannel(context)
        val campus = AppRepository.getInstance(context).state.value.settings.campus
        val sampleLoc = when (campus) {
            io.github.rex25415.haiyu.schedule.model.GdouCampus.HUGUANG -> "广学楼 302（湖光错峰）"
            io.github.rex25415.haiyu.schedule.model.GdouCampus.HAIBIN -> "实验楼 301（海滨错峰）"
            io.github.rex25415.haiyu.schedule.model.GdouCampus.YANGJIANG -> "厚德楼 201（阳江错峰）"
        }
        val sampleTime = if (campus == io.github.rex25415.haiyu.schedule.model.GdouCampus.YANGJIANG) "10:10-11:40" else "10:05-11:35"
        showClassNotification(
            context = context,
            courseName = "海屿课表 · 提醒测试成功",
            location = sampleLoc,
            timeText = sampleTime,
            teacher = "广海全校区课表助手",
            isStaggered = true
        )
    }

    fun showClassNotification(
        context: Context,
        courseName: String,
        location: String,
        timeText: String,
        teacher: String,
        isStaggered: Boolean
    ) {
        ensureChannel(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPi = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val badge = if (isStaggered) "【错峰】" else ""
        val teacherPart = if (teacher.isNotBlank()) " · $teacher" else ""
        val body = "$badge$timeText · $location$teacherPart"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("即将上课：$courseName")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify((System.currentTimeMillis() % 100000).toInt(), notification)
    }

    fun showDdlNotification(
        context: Context,
        title: String,
        courseName: String,
        dueDate: String
    ) {
        ensureChannel(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPi = PendingIntent.getActivity(
            context,
            1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val body = buildString {
            if (courseName.isNotBlank()) append("[$courseName] ")
            append("截止日期：$dueDate，记得按时提交！")
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("作业 DDL 临近：$title")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(((System.currentTimeMillis() % 90000) + 10000).toInt(), notification)
    }
}
