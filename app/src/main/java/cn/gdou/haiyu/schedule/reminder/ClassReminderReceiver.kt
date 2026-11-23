package cn.gdou.haiyu.schedule.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ClassReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED) {
            ClassReminderScheduler.rescheduleNextReminder(context)
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
            // Automatically chain the next upcoming class reminder
            ClassReminderScheduler.rescheduleNextReminder(context)
        }
    }
}
