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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * 桌面小组件 2：「海屿考试与 DDL 倒计时」（自动深浅色适配，聚合展示最近 3 条考试与待办截止倒计时）
 */
class CountdownWidgetProvider : AppWidgetProvider() {

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

    private data class CountdownRow(
        val isPinned: Boolean,
        val sortDays: Long,
        val badgeText: String,
        val titleText: String,
        val subText: String
    )

    companion object {
        const val ACTION_REFRESH = "io.github.rex25415.haiyu.schedule.ACTION_COUNTDOWN_WIDGET_REFRESH"

        fun hasAnyWidget(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            val cn = ComponentName(context, CountdownWidgetProvider::class.java)
            return manager.getAppWidgetIds(cn).isNotEmpty()
        }

        fun refreshAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val cn = ComponentName(context, CountdownWidgetProvider::class.java)
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
            val today = LocalDate.now()
            val now = LocalDateTime.now()
            val views = RemoteViews(context.packageName, R.layout.widget_countdown)

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val launchPi = PendingIntent.getActivity(
                context,
                10,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.cd_widget_root, launchPi)

            val refreshIntent = Intent(context, CountdownWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPi = PendingIntent.getBroadcast(
                context,
                11,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.cd_widget_refresh, refreshPi)

            val items = mutableListOf<CountdownRow>()

            // 1. Upcoming exams
            snapshot.exams.forEach { ex ->
                val dt = GdouTimeRules.parseExamStartDateTime(ex.examTimeRaw) ?: return@forEach
                if (dt.isBefore(now)) return@forEach
                val days = ChronoUnit.DAYS.between(today, dt.toLocalDate())
                val badge = when {
                    days <= 0L -> "今日考"
                    days == 1L -> "明天考"
                    else -> "剩${days}天"
                }
                val sub = buildString {
                    append(ex.examTimeRaw.ifBlank { dt.toLocalDate().toString() })
                    if (ex.location.isNotBlank()) append(" · ${ex.location}")
                    if (ex.seatNumber.isNotBlank()) append(" ${ex.seatNumber}座")
                }
                val typeTag = when {
                    ex.isPinned -> "置顶"
                    ex.examType.contains("放假") || ex.examType.contains("倒数") -> "倒数"
                    else -> "考试"
                }
                items.add(
                    CountdownRow(
                        isPinned = ex.isPinned,
                        sortDays = days,
                        badgeText = badge,
                        titleText = "$typeTag｜${ex.courseName}",
                        subText = sub
                    )
                )
            }

            // 2. Pending homework DDLs
            snapshot.tasks.filter { !it.isDone && it.dueDate.isNotBlank() }.forEach { t ->
                val due = try { LocalDate.parse(t.dueDate.trim()) } catch (_: Exception) { null } ?: return@forEach
                val days = ChronoUnit.DAYS.between(today, due)
                val badge = when {
                    days < 0L -> "逾期${-days}天"
                    days == 0L -> "今天交"
                    days == 1L -> "明天交"
                    else -> "剩${days}天"
                }
                val sub = buildString {
                    if (t.courseName.isNotBlank()) append("${t.courseName} · ")
                    append("截止 ${t.dueDate}")
                }
                items.add(
                    CountdownRow(
                        isPinned = false,
                        sortDays = days,
                        badgeText = badge,
                        titleText = "DDL｜${t.title}",
                        subText = sub
                    )
                )
            }

            val sorted = items.sortedWith(
                compareByDescending<CountdownRow> { it.isPinned }.thenBy { it.sortDays }
            )
            views.setTextViewText(R.id.cd_widget_badge, "共 ${sorted.size} 项")

            val rowIds = listOf(
                Triple(R.id.cd_row_1, R.id.cd_badge_1, Pair(R.id.cd_title_1, R.id.cd_sub_1)),
                Triple(R.id.cd_row_2, R.id.cd_badge_2, Pair(R.id.cd_title_2, R.id.cd_sub_2)),
                Triple(R.id.cd_row_3, R.id.cd_badge_3, Pair(R.id.cd_title_3, R.id.cd_sub_3))
            )

            if (sorted.isEmpty()) {
                views.setViewVisibility(R.id.cd_widget_empty, View.VISIBLE)
                rowIds.forEach { views.setViewVisibility(it.first, View.GONE) }
            } else {
                views.setViewVisibility(R.id.cd_widget_empty, View.GONE)
                rowIds.forEachIndexed { idx, (rowId, badgeId, textPair) ->
                    val row = sorted.getOrNull(idx)
                    if (row == null) {
                        views.setViewVisibility(rowId, View.GONE)
                    } else {
                        views.setViewVisibility(rowId, View.VISIBLE)
                        views.setTextViewText(badgeId, row.badgeText)
                        views.setTextViewText(textPair.first, row.titleText)
                        views.setTextViewText(textPair.second, row.subText)
                    }
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
