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
            val summary = WidgetScheduleHelper.resolveFocusClassSummary(snapshot)
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

            views.setTextViewText(R.id.nc_status_badge, summary.badge)
            views.setTextViewText(R.id.nc_title, summary.oneLineTitle)
            views.setTextViewText(R.id.nc_subtitle, summary.oneLineSubtitle)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
