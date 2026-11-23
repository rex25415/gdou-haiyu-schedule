package io.github.rex25415.haiyu.schedule.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import io.github.rex25415.haiyu.schedule.MainActivity
import io.github.rex25415.haiyu.schedule.R
import io.github.rex25415.haiyu.schedule.data.AppRepository
import io.github.rex25415.haiyu.schedule.data.GdouTimeRules
import io.github.rex25415.haiyu.schedule.data.OccurrenceState
import io.github.rex25415.haiyu.schedule.data.TermWeekPhase
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

    private data class WidgetCourseRow(
        val startTime: String,
        val endTime: String,
        val titleText: String,
        val subText: String
    )

    companion object {
        const val ACTION_REFRESH = "io.github.rex25415.haiyu.schedule.ACTION_WIDGET_REFRESH"

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
            val weekInfo = GdouTimeRules.calculateTermWeekInfo(
                settings.semesterStartDate,
                settings.totalWeeks,
                today
            )
            val dowLabel = GdouTimeRules.dayOfWeekLabel(today.dayOfWeek.value)
            val dateFmt = DateTimeFormatter.ofPattern("M/d")
            val dateStr = today.format(dateFmt)

            val views = RemoteViews(context.packageName, R.layout.widget_today_schedule)

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

            val rowIds = listOf(
                Triple(R.id.widget_row_1, R.id.widget_time_1, Pair(R.id.widget_name_1, R.id.widget_loc_1)),
                Triple(R.id.widget_row_2, R.id.widget_time_2, Pair(R.id.widget_name_2, R.id.widget_loc_2)),
                Triple(R.id.widget_row_3, R.id.widget_time_3, Pair(R.id.widget_name_3, R.id.widget_loc_3)),
                Triple(R.id.widget_row_4, R.id.widget_time_4, Pair(R.id.widget_name_4, R.id.widget_loc_4))
            )

            // 1. Vacation / Pre-Term / Empty Courses check
            if (snapshot.courses.isEmpty()) {
                views.setTextViewText(R.id.widget_title, "海屿课表 · $dateStr $dowLabel")
                views.setTextViewText(R.id.widget_week_badge, "待同步")
                views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                views.setTextViewText(R.id.widget_empty, "尚未同步课表，点此进入教务一键导入~")
                rowIds.forEach { views.setViewVisibility(it.first, View.GONE) }
                views.setViewVisibility(R.id.widget_more, View.GONE)
                appWidgetManager.updateAppWidget(appWidgetId, views)
                return
            }

            if (weekInfo.phase != TermWeekPhase.IN_TERM) {
                views.setTextViewText(R.id.widget_title, "海屿课表 · $dateStr $dowLabel")
                val badge = if (weekInfo.phase == TermWeekPhase.BEFORE_TERM) "未开学" else "假期中"
                val msg = if (weekInfo.phase == TermWeekPhase.BEFORE_TERM) {
                    "距离开学还有 ${weekInfo.daysUntilStart} 天，提前规划好新学期吧~"
                } else {
                    "本学期已结束，祝你在假期里轻松充电~"
                }
                views.setTextViewText(R.id.widget_week_badge, badge)
                views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                views.setTextViewText(R.id.widget_empty, msg)
                rowIds.forEach { views.setViewVisibility(it.first, View.GONE) }
                views.setViewVisibility(R.id.widget_more, View.GONE)
                appWidgetManager.updateAppWidget(appWidgetId, views)
                return
            }

            val pinnedSet = settings.pinnedConflictKeys.toSet()
            val todayCourses = GdouTimeRules.getTodayCoursesWithStatus(
                courses = snapshot.courses,
                startDateStr = settings.semesterStartDate,
                totalWeeks = settings.totalWeeks,
                campus = settings.campus,
                pinnedKeys = pinnedSet
            )
            val remainingToday = todayCourses.filter { it.state != OccurrenceState.FINISHED }
            val tomorrow = today.plusDays(1)
            val tomorrowCourses = if (remainingToday.isEmpty()) {
                GdouTimeRules.getCoursesForDate(
                    courses = snapshot.courses,
                    startDateStr = settings.semesterStartDate,
                    totalWeeks = settings.totalWeeks,
                    campus = settings.campus,
                    date = tomorrow,
                    pinnedKeys = pinnedSet
                )
            } else {
                emptyList()
            }

            val rowsToRender: List<WidgetCourseRow> = if (remainingToday.isNotEmpty()) {
                views.setTextViewText(R.id.widget_title, "海屿课表 · $dateStr $dowLabel")
                views.setTextViewText(R.id.widget_week_badge, "第${weekInfo.rawWeek}周")
                val src = if (todayCourses.size > 4) remainingToday else todayCourses
                src.map { item ->
                    val staggerTag = if (item.time.isSpecialStaggered) " 错峰" else ""
                    val prefix = when (item.state) {
                        OccurrenceState.ONGOING -> "● "
                        OccurrenceState.FINISHED -> "✓ "
                        OccurrenceState.UPCOMING -> ""
                    }
                    val locText = buildString {
                        append(item.course.location.ifBlank { "地点待定" })
                        if (item.course.teacher.isNotBlank()) append(" · ${item.course.teacher}")
                        append(staggerTag)
                    }
                    WidgetCourseRow(
                        startTime = item.time.startTime,
                        endTime = item.time.endTime,
                        titleText = "$prefix${item.course.name}",
                        subText = locText
                    )
                }
            } else if (tomorrowCourses.isNotEmpty()) {
                val tmrDate = tomorrow.format(dateFmt)
                val tmrDow = GdouTimeRules.dayOfWeekLabel(tomorrow.dayOfWeek.value)
                views.setTextViewText(R.id.widget_title, "明日预告 · $tmrDate $tmrDow")
                views.setTextViewText(R.id.widget_week_badge, "明日早课")
                tomorrowCourses.map { item ->
                    val staggerTag = if (item.time.isSpecialStaggered) " 错峰" else ""
                    val locText = buildString {
                        append(item.course.location.ifBlank { "地点待定" })
                        if (item.course.teacher.isNotBlank()) append(" · ${item.course.teacher}")
                        append(staggerTag)
                    }
                    WidgetCourseRow(
                        startTime = item.time.startTime,
                        endTime = item.time.endTime,
                        titleText = item.course.name,
                        subText = locText
                    )
                }
            } else {
                views.setTextViewText(R.id.widget_title, "海屿课表 · $dateStr $dowLabel")
                views.setTextViewText(R.id.widget_week_badge, "第${weekInfo.rawWeek}周")
                emptyList()
            }

            if (rowsToRender.isEmpty()) {
                views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                val emptyText = if (todayCourses.isEmpty()) {
                    "今天与明天都没有课，在${settings.campus.label}好好放松一下~"
                } else {
                    "今日 ${todayCourses.size} 节课已全部上完，明天也无课~"
                }
                views.setTextViewText(R.id.widget_empty, emptyText)
                rowIds.forEach { views.setViewVisibility(it.first, View.GONE) }
                views.setViewVisibility(R.id.widget_more, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_empty, View.GONE)
                rowIds.forEachIndexed { index, (rowId, timeId, nameLoc) ->
                    val row = rowsToRender.getOrNull(index)
                    if (row == null) {
                        views.setViewVisibility(rowId, View.GONE)
                    } else {
                        views.setViewVisibility(rowId, View.VISIBLE)
                        views.setTextViewText(timeId, "${row.startTime}\n${row.endTime}")
                        views.setTextViewText(nameLoc.first, row.titleText)
                        views.setTextViewText(nameLoc.second, row.subText)
                    }
                }
                if (rowsToRender.size > 4) {
                    views.setViewVisibility(R.id.widget_more, View.VISIBLE)
                    views.setTextViewText(R.id.widget_more, "还有 ${rowsToRender.size - 4} 节课，点击查看全部 →")
                } else {
                    views.setViewVisibility(R.id.widget_more, View.GONE)
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
