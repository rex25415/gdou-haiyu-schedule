package io.github.rex25415.haiyu.schedule.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import io.github.rex25415.haiyu.schedule.MainActivity
import io.github.rex25415.haiyu.schedule.R
import io.github.rex25415.haiyu.schedule.data.AppRepository
import io.github.rex25415.haiyu.schedule.data.GdouTimeRules
import io.github.rex25415.haiyu.schedule.data.OccurrenceState
import io.github.rex25415.haiyu.schedule.data.TermWeekPhase
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 桌面小组件 3：「海屿下一节课（极简 4×1）」— 单行深浅自适应，不占桌面空间，一眼看清下一节课与教室
 */
class NextClassWidgetProvider : AppWidgetProvider() {

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
        const val ACTION_REFRESH = "io.github.rex25415.haiyu.schedule.ACTION_NEXT_CLASS_WIDGET_REFRESH"

        fun hasAnyWidget(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            val cn = ComponentName(context, NextClassWidgetProvider::class.java)
            return manager.getAppWidgetIds(cn).isNotEmpty()
        }

        fun refreshAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val cn = ComponentName(context, NextClassWidgetProvider::class.java)
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
            val snapshot = AppRepository.getInstance(context).state.value
            val settings = snapshot.settings
            val today = LocalDate.now()
            val weekInfo = GdouTimeRules.calculateTermWeekInfo(
                startDateStr = settings.semesterStartDate,
                totalWeeks = settings.totalWeeks,
                today = today
            )
            val views = RemoteViews(context.packageName, R.layout.widget_next_class)

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val launchPi = PendingIntent.getActivity(
                context,
                20,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.nc_widget_root, launchPi)

            val refreshIntent = Intent(context, NextClassWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPi = PendingIntent.getBroadcast(
                context,
                21,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.nc_refresh, refreshPi)

            if (snapshot.courses.isEmpty()) {
                views.setTextViewText(R.id.nc_status_badge, "待同步")
                views.setTextViewText(R.id.nc_title, "点击进入海屿空间同步课表")
                views.setTextViewText(R.id.nc_subtitle, "支持广海三校区 · 校外免校园网一键导入")
                appWidgetManager.updateAppWidget(appWidgetId, views)
                return
            }

            if (weekInfo.phase != TermWeekPhase.IN_TERM) {
                val badge = if (weekInfo.phase == TermWeekPhase.BEFORE_TERM) "未开学" else "假期中"
                val title = if (weekInfo.phase == TermWeekPhase.BEFORE_TERM) {
                    "距开学还有 ${weekInfo.daysUntilStart} 天"
                } else {
                    "本学期已结课 · 假期愉快~"
                }
                views.setTextViewText(R.id.nc_status_badge, badge)
                views.setTextViewText(R.id.nc_title, title)
                views.setTextViewText(R.id.nc_subtitle, "海屿空间 · ${settings.campus.label}")
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
            val ongoing = todayCourses.firstOrNull { it.state == OccurrenceState.ONGOING }
            val upcoming = todayCourses.firstOrNull { it.state == OccurrenceState.UPCOMING }
            val active = ongoing ?: upcoming

            if (active != null) {
                val badge = if (active.state == OccurrenceState.ONGOING) "上课中" else "下一节"
                val loc = active.course.location.ifBlank { "地点待定" }
                val stag = if (active.time.isSpecialStaggered) " · 错峰" else ""
                views.setTextViewText(R.id.nc_status_badge, badge)
                views.setTextViewText(R.id.nc_title, "${active.course.name} · $loc")
                views.setTextViewText(
                    R.id.nc_subtitle,
                    "${active.time.startTime}-${active.time.endTime} · 第${active.course.startSection}-${active.course.endSection}节$stag"
                )
            } else {
                val tomorrow = today.plusDays(1)
                val tmrCourses = GdouTimeRules.getCoursesForDate(
                    courses = snapshot.courses,
                    startDateStr = settings.semesterStartDate,
                    totalWeeks = settings.totalWeeks,
                    campus = settings.campus,
                    date = tomorrow,
                    pinnedKeys = pinnedSet
                )
                val firstTmr = tmrCourses.firstOrNull()
                if (firstTmr != null) {
                    val loc = firstTmr.course.location.ifBlank { "地点待定" }
                    val tmrLabel = tomorrow.format(DateTimeFormatter.ofPattern("M月d日"))
                    views.setTextViewText(R.id.nc_status_badge, "明日早课")
                    views.setTextViewText(R.id.nc_title, "${firstTmr.course.name} · $loc")
                    views.setTextViewText(
                        R.id.nc_subtitle,
                        "明天($tmrLabel) ${firstTmr.time.startTime}-${firstTmr.time.endTime} · 共${tmrCourses.size}节"
                    )
                } else {
                    views.setTextViewText(R.id.nc_status_badge, "已下课")
                    views.setTextViewText(R.id.nc_title, "今天与明天都没有排课~")
                    views.setTextViewText(R.id.nc_subtitle, "第${weekInfo.rawWeek}周 · ${settings.campus.label} · 海屿空间")
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
