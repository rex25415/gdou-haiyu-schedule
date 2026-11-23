package io.github.rex25415.haiyu.schedule.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.rex25415.haiyu.schedule.widget.CountdownWidgetProvider
import io.github.rex25415.haiyu.schedule.widget.NextClassWidgetProvider
import io.github.rex25415.haiyu.schedule.widget.TodayScheduleWidgetProvider

class ClassReminderReceiver : BroadcastReceiver() {

    private fun refreshAllDesktopWidgets(context: Context) {
        TodayScheduleWidgetProvider.refreshAllWidgets(context)
        CountdownWidgetProvider.refreshAllWidgets(context)
        NextClassWidgetProvider.refreshAllWidgets(context)
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_DATE_CHANGED ||
            action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        ) {
            ClassReminderScheduler.rescheduleNextReminder(context)
            refreshAllDesktopWidgets(context)
            return
        }
        if (action == ClassReminderScheduler.ACTION_MIDNIGHT_WIDGET_REFRESH) {
            refreshAllDesktopWidgets(context)
            ClassReminderScheduler.scheduleMidnightWidgetRefresh(context)
            return
        }
        if (action == ClassReminderScheduler.ACTION_CLASS_REMINDER) {
            val name = intent.getStringExtra(ClassReminderScheduler.EXTRA_COURSE_NAME) ?: "课程提醒"
            val location = intent.getStringExtra(ClassReminderScheduler.EXTRA_LOCATION) ?: ""
            val timeText = intent.getStringExtra(ClassReminderScheduler.EXTRA_TIME_TEXT) ?: ""
            val teacher = intent.getStringExtra(ClassReminderScheduler.EXTRA_TEACHER) ?: ""
            val staggered = intent.getBooleanExtra(ClassReminderScheduler.EXTRA_STAGGERED, false)

            ClassReminderScheduler.showClassNotification(
                context = context,
                courseName = name,
                location = location,
                timeText = timeText,
                teacher = teacher,
                isStaggered = staggered
            )
            ClassReminderScheduler.rescheduleNextReminder(context)
            refreshAllDesktopWidgets(context)
            return
        }
        if (action == ClassReminderScheduler.ACTION_DDL_REMINDER) {
            val title = intent.getStringExtra(ClassReminderScheduler.EXTRA_DDL_TITLE) ?: "待办作业"
            val course = intent.getStringExtra(ClassReminderScheduler.EXTRA_DDL_COURSE) ?: ""
            val dueDate = intent.getStringExtra(ClassReminderScheduler.EXTRA_DDL_DUE_DATE) ?: ""

            ClassReminderScheduler.showDdlNotification(
                context = context,
                title = title,
                courseName = course,
                dueDate = dueDate
            )
            ClassReminderScheduler.rescheduleNextReminder(context)
            CountdownWidgetProvider.refreshAllWidgets(context)
            return
        }
        if (action == ClassReminderScheduler.ACTION_EXAM_REMINDER) {
            val course = intent.getStringExtra(ClassReminderScheduler.EXTRA_EXAM_COURSE) ?: "考试提醒"
            val examTime = intent.getStringExtra(ClassReminderScheduler.EXTRA_EXAM_TIME) ?: ""
            val locSeat = intent.getStringExtra(ClassReminderScheduler.EXTRA_EXAM_LOC_SEAT) ?: ""

            ClassReminderScheduler.showExamNotification(
                context = context,
                courseName = course,
                examTime = examTime,
                locSeat = locSeat
            )
            ClassReminderScheduler.rescheduleNextReminder(context)
            CountdownWidgetProvider.refreshAllWidgets(context)
        }
    }
}
