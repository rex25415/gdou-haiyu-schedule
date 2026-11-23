package cn.gdou.haiyu.schedule.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import cn.gdou.haiyu.schedule.MainActivity
import cn.gdou.haiyu.schedule.R
import cn.gdou.haiyu.schedule.data.AppRepository
import cn.gdou.haiyu.schedule.data.GdouTimeRules
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object ClassReminderScheduler {

    const val CHANNEL_ID = "haiyu_class_reminders"
    const val ACTION_CLASS_REMINDER = "cn.gdou.haiyu.schedule.ACTION_CLASS_REMINDER"

    const val EXTRA_COURSE_NAME = "course_name"
    const val EXTRA_LOCATION = "location"
    const val EXTRA_TIME_TEXT = "time_text"
    const val EXTRA_TEACHER = "teacher"
    const val EXTRA_STAGGERED = "staggered"

    private const val REQUEST_CODE_NEXT = 202601

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "上课前提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "海屿课表在每节课前按广海三校区精准作息发送教室提醒"
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
        if (!settings.reminderEnabled || snapshot.courses.isEmpty()) return

        val now = LocalDateTime.now()
        val startMonday = GdouTimeRules.parseLocalDate(settings.semesterStartDate).with(DayOfWeek.MONDAY)
        val minutesBefore = settings.reminderMinutesBefore.coerceIn(5, 60)

        var earliestTrigger: LocalDateTime? = null
        var bestTitle = ""
        var bestLoc = ""
        var bestTimeText = ""
        var bestTeacher = ""
        var bestStaggered = false

        // Look ahead up to 14 days to find the very next scheduled class trigger
        for (dayOffset in 0L..14L) {
            val date = now.toLocalDate().plusDays(dayOffset)
            val weekNum = GdouTimeRules.calculateCurrentWeek(settings.semesterStartDate, settings.totalWeeks, date)
            val daysFromStart = java.time.temporal.ChronoUnit.DAYS.between(startMonday, date)
            if (daysFromStart < 0) continue
            val dow = date.dayOfWeek.value

            snapshot.courses
                .filter { it.dayOfWeek == dow && weekNum in it.weeks }
                .forEach { course ->
                    val resolved = GdouTimeRules.resolveCourseTime(course, settings.campus)
                    val startTime = GdouTimeRules.parseLocalTime(resolved.startTime)
                    val classDateTime = LocalDateTime.of(date, startTime)
                    val triggerAt = classDateTime.minusMinutes(minutesBefore.toLong())
                    if (triggerAt.isAfter(now)) {
                        if (earliestTrigger == null || triggerAt.isBefore(earliestTrigger)) {
                            earliestTrigger = triggerAt
                            bestTitle = course.name
                            bestLoc = course.location.ifBlank { "地点待定" }
                            bestTimeText = "${resolved.startTime}-${resolved.endTime}"
                            bestTeacher = course.teacher
                            bestStaggered = resolved.isSpecialStaggered
                        }
                    }
                }
            if (earliestTrigger != null) break
        }

        val triggerTime = earliestTrigger ?: return
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

    fun sendTestNotification(context: Context) {
        ensureChannel(context)
        val campus = AppRepository.getInstance(context).state.value.settings.campus
        val sampleLoc = when (campus) {
            cn.gdou.haiyu.schedule.model.GdouCampus.HUGUANG -> "广学楼 302（湖光错峰）"
            cn.gdou.haiyu.schedule.model.GdouCampus.HAIBIN -> "实验楼 301（海滨错峰）"
            cn.gdou.haiyu.schedule.model.GdouCampus.YANGJIANG -> "厚德楼 201（阳江错峰）"
        }
        val sampleTime = if (campus == cn.gdou.haiyu.schedule.model.GdouCampus.YANGJIANG) "10:10-11:40" else "10:05-11:35"
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
}
