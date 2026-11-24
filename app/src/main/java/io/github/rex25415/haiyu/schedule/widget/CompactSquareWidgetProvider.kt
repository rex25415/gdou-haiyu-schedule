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

/**
 * 桌面小组件 4：「海屿极简方卡（2×2）」— 不占桌面宽幅，一眼看清状态、课名、教室与上下课时间
 */
class CompactSquareWidgetProvider : AppWidgetProvider() {

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
        const val ACTION_REFRESH = "io.github.rex25415.haiyu.schedule.ACTION_COMPACT_SQUARE_WIDGET_REFRESH"

        fun hasAnyWidget(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            val cn = ComponentName(context, CompactSquareWidgetProvider::class.java)
            return manager.getAppWidgetIds(cn).isNotEmpty()
        }

        fun refreshAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val cn = ComponentName(context, CompactSquareWidgetProvider::class.java)
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
            val summary = WidgetScheduleHelper.resolveFocusClassSummary(snapshot)
            val views = RemoteViews(context.packageName, R.layout.widget_compact_square)

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val launchPi = PendingIntent.getActivity(
                context,
                30,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.cs_widget_root, launchPi)

            val refreshIntent = Intent(context, CompactSquareWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPi = PendingIntent.getBroadcast(
                context,
                31,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.cs_refresh, refreshPi)

            views.setTextViewText(R.id.cs_status_badge, summary.badge)
            views.setTextViewText(R.id.cs_week_label, summary.compactHeaderMeta)
            views.setTextViewText(R.id.cs_course_name, summary.primaryTitle)
            views.setTextViewText(R.id.cs_location, summary.highlightLocation)
            views.setTextViewText(R.id.cs_section_meta, summary.compactSectionMeta)
            views.setTextViewText(R.id.cs_time_text, summary.compactTimeOnly)
            if (summary.footerBadge.isNotBlank()) {
                views.setViewVisibility(R.id.cs_footer_tag, View.VISIBLE)
                views.setTextViewText(R.id.cs_footer_tag, summary.footerBadge)
            } else {
                views.setViewVisibility(R.id.cs_footer_tag, View.GONE)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
