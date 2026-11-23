package cn.gdou.haiyu.schedule.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import cn.gdou.haiyu.schedule.MainActivity
import cn.gdou.haiyu.schedule.R
import cn.gdou.haiyu.schedule.data.AppRepository
import cn.gdou.haiyu.schedule.data.GdouTimeRules
import cn.gdou.haiyu.schedule.data.OccurrenceState
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class TodayScheduleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id ->
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            refreshAllWidgets(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "cn.gdou.haiyu.schedule.ACTION_WIDGET_REFRESH"

        fun refreshAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val cn = ComponentName(context, TodayScheduleWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(cn)
            ids.forEach { id ->
                updateWidget(context, manager, id)
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val repo = AppRepository.getInstance(context)
            val snapshot = repo.state.value
            val settings = snapshot.settings
            val today = LocalDate.now()
            val currentWeek = GdouTimeRules.calculateCurrentWeek(
                settings.semesterStartDate,
                settings.totalWeeks,
                today
            )
            val dowLabel = GdouTimeRules.dayOfWeekLabel(today.dayOfWeek.value)
            val dateStr = today.format(DateTimeFormatter.ofPattern("M/d"))

            val views = RemoteViews(context.packageName, R.layout.widget_today_schedule)
            views.setTextViewText(R.id.widget_title, "海屿课表 · $dateStr $dowLabel")
            views.setTextViewText(R.id.widget_week_badge, "第${currentWeek}周")

            // Click root -> launch MainActivity
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val launchPi = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, launchPi)

            // Click refresh -> broadcast widget refresh
            val refreshIntent = Intent(context, TodayScheduleWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPi = PendingIntent.getBroadcast(
                context,
                1,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh, refreshPi)

            val todayCourses = GdouTimeRules.getTodayCoursesWithStatus(
                courses = snapshot.courses,
                startDateStr = settings.semesterStartDate,
                totalWeeks = settings.totalWeeks,
                campus = settings.campus
            )

            val rowIds = listOf(
                Triple(R.id.widget_row_1, R.id.widget_time_1, Pair(R.id.widget_name_1, R.id.widget_loc_1)),
                Triple(R.id.widget_row_2, R.id.widget_time_2, Pair(R.id.widget_name_2, R.id.widget_loc_2)),
                Triple(R.id.widget_row_3, R.id.widget_time_3, Pair(R.id.widget_name_3, R.id.widget_loc_3)),
                Triple(R.id.widget_row_4, R.id.widget_time_4, Pair(R.id.widget_name_4, R.id.widget_loc_4))
            )

            // Prefer showing ongoing/upcoming courses first if there are more than 4
            val prioritized = if (todayCourses.size > 4) {
                val active = todayCourses.filter { it.state != OccurrenceState.FINISHED }
                if (active.isNotEmpty()) active else todayCourses.takeLast(4)
            } else {
                todayCourses
            }

            if (prioritized.isEmpty()) {
                views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                val emptyText = if (snapshot.courses.isEmpty()) {
                    "尚未同步课表，点此进入广海教务一键导入~"
                } else {
                    "今天没有课，祝你在${settings.campus.label}度过轻松的一天~"
                }
                views.setTextViewText(R.id.widget_empty, emptyText)
                rowIds.forEach { views.setViewVisibility(it.first, View.GONE) }
                views.setViewVisibility(R.id.widget_more, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_empty, View.GONE)
                rowIds.forEachIndexed { index, (rowId, timeId, nameLoc) ->
                    val item = prioritized.getOrNull(index)
                    if (item == null) {
                        views.setViewVisibility(rowId, View.GONE)
                    } else {
                        views.setViewVisibility(rowId, View.VISIBLE)
                        val staggerTag = if (item.time.isSpecialStaggered) " 错峰" else ""
                        views.setTextViewText(timeId, "${item.time.startTime}\n${item.time.endTime}")
                        val statusPrefix = when (item.state) {
                            OccurrenceState.ONGOING -> "● "
                            OccurrenceState.FINISHED -> "✓ "
                            OccurrenceState.UPCOMING -> ""
                        }
                        views.setTextViewText(nameLoc.first, "$statusPrefix${item.course.name}")
                        val locText = buildString {
                            append(item.course.location.ifBlank { "地点待定" })
                            if (item.course.teacher.isNotBlank()) append(" · ${item.course.teacher}")
                            append(staggerTag)
                        }
                        views.setTextViewText(nameLoc.second, locText)
                    }
                }
                if (prioritized.size > 4) {
                    views.setViewVisibility(R.id.widget_more, View.VISIBLE)
                    views.setTextViewText(R.id.widget_more, "还有 ${prioritized.size - 4} 节课，点击查看全部 →")
                } else {
                    views.setViewVisibility(R.id.widget_more, View.GONE)
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
