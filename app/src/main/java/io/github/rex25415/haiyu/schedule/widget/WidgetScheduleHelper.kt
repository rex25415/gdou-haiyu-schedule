package io.github.rex25415.haiyu.schedule.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.rex25415.haiyu.schedule.data.GdouTimeRules
import io.github.rex25415.haiyu.schedule.data.OccurrenceState
import io.github.rex25415.haiyu.schedule.data.TermWeekPhase
import io.github.rex25415.haiyu.schedule.model.AppDataSnapshot
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 极简桌面小组件（4×1 与 2×2）共用的纯视图模型，保证两者文案与判定逻辑完全一致且可独立单测。
 */
data class FocusClassSummary(
    val badge: String,
    val weekTag: String,
    val compactHeaderMeta: String,
    val primaryTitle: String,
    val highlightLocation: String,
    val compactSectionMeta: String,
    val compactTimeOnly: String,
    val timeDetail: String,
    val footerBadge: String,
    val oneLineTitle: String,
    val oneLineSubtitle: String
)

enum class PinWidgetOutcome {
    REQUESTED,
    UNSUPPORTED_LAUNCHER,
    FAILED
}

object WidgetScheduleHelper {

    fun hasAnyWidget(context: Context): Boolean {
        return TodayScheduleWidgetProvider.hasAnyWidget(context) ||
            CountdownWidgetProvider.hasAnyWidget(context) ||
            NextClassWidgetProvider.hasAnyWidget(context) ||
            CompactSquareWidgetProvider.hasAnyWidget(context)
    }

    fun refreshAllWidgets(context: Context) {
        TodayScheduleWidgetProvider.refreshAllWidgets(context)
        CountdownWidgetProvider.refreshAllWidgets(context)
        NextClassWidgetProvider.refreshAllWidgets(context)
        CompactSquareWidgetProvider.refreshAllWidgets(context)
    }

    /**
     * 调用系统 AppWidgetManager.requestPinAppWidget（API 26+）请求将指定小组件直接添加到桌面。
     */
    fun requestPinWidget(
        context: Context,
        providerClass: Class<out AppWidgetProvider>,
        requestCode: Int
    ): PinWidgetOutcome {
        val manager = AppWidgetManager.getInstance(context)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !manager.isRequestPinAppWidgetSupported) {
            return PinWidgetOutcome.UNSUPPORTED_LAUNCHER
        }
        return try {
            val cn = ComponentName(context, providerClass)
            val callbackPi = PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, providerClass).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (manager.requestPinAppWidget(cn, null, callbackPi)) {
                PinWidgetOutcome.REQUESTED
            } else {
                PinWidgetOutcome.UNSUPPORTED_LAUNCHER
            }
        } catch (_: Exception) {
            PinWidgetOutcome.FAILED
        }
    }

    /**
     * 基于当前课表快照与时刻解析重点课程展示信息，服务 4×1 单行胶囊与 2×2 极简方卡。
     */
    fun resolveFocusClassSummary(
        snapshot: AppDataSnapshot,
        now: LocalDateTime = LocalDateTime.now()
    ): FocusClassSummary {
        val settings = snapshot.settings
        val today = now.toLocalDate()
        val weekInfo = GdouTimeRules.calculateTermWeekInfo(
            startDateStr = settings.semesterStartDate,
            totalWeeks = settings.totalWeeks,
            today = today
        )

        if (snapshot.courses.isEmpty()) {
            return FocusClassSummary(
                badge = "待同步",
                weekTag = settings.campus.label,
                compactHeaderMeta = settings.campus.label.removeSuffix("校区"),
                primaryTitle = "尚未同步课表",
                highlightLocation = "点此导入广海教务",
                compactSectionMeta = "支持校外免校园网",
                compactTimeOnly = "点击导入课表",
                timeDetail = "支持校外免校园网",
                footerBadge = "",
                oneLineTitle = "点击进入海屿空间同步课表",
                oneLineSubtitle = "支持广海三校区 · 校外免校园网一键导入"
            )
        }

        if (weekInfo.phase != TermWeekPhase.IN_TERM) {
            val badge = if (weekInfo.phase == TermWeekPhase.BEFORE_TERM) "未开学" else "假期中"
            val title = if (weekInfo.phase == TermWeekPhase.BEFORE_TERM) {
                "距开学 ${weekInfo.daysUntilStart} 天"
            } else {
                "本学期已结课"
            }
            val oneLineTitle = if (weekInfo.phase == TermWeekPhase.BEFORE_TERM) {
                "距开学还有 ${weekInfo.daysUntilStart} 天"
            } else {
                "本学期已结课 · 假期愉快~"
            }
            val campusShort = settings.campus.label.removeSuffix("校区")
            return FocusClassSummary(
                badge = badge,
                weekTag = campusShort,
                compactHeaderMeta = campusShort,
                primaryTitle = title,
                highlightLocation = if (weekInfo.phase == TermWeekPhase.BEFORE_TERM) "准备迎接新学期" else "假期愉快 · 放松充电",
                compactSectionMeta = settings.academicYearLabel.ifBlank { "海屿空间" },
                compactTimeOnly = "查看完整校历",
                timeDetail = settings.academicYearLabel.ifBlank { "海屿空间" },
                footerBadge = "",
                oneLineTitle = oneLineTitle,
                oneLineSubtitle = "海屿空间 · ${settings.campus.label}"
            )
        }

        val dowLabel = GdouTimeRules.dayOfWeekLabel(today.dayOfWeek.value)
        val weekTag = "第${weekInfo.rawWeek}周 $dowLabel"
        val compactWeekTag = "${weekInfo.rawWeek}周$dowLabel"
        val campusShort = settings.campus.label.removeSuffix("校区")
        val pinnedSet = settings.pinnedConflictKeys.toSet()
        val todayCourses = GdouTimeRules.getTodayCoursesWithStatus(
            courses = snapshot.courses,
            startDateStr = settings.semesterStartDate,
            totalWeeks = settings.totalWeeks,
            campus = settings.campus,
            now = now,
            pinnedKeys = pinnedSet
        )
        val ongoing = todayCourses.firstOrNull { it.state == OccurrenceState.ONGOING }
        val upcoming = todayCourses.firstOrNull { it.state == OccurrenceState.UPCOMING }
        val active = ongoing ?: upcoming

        if (active != null) {
            val badge = if (active.state == OccurrenceState.ONGOING) "上课中" else "下一节"
            val loc = active.course.location.ifBlank { "地点待定" }
            val stagSuffix = if (active.time.isSpecialStaggered) " · 错峰" else ""
            val sectionText = "第${active.course.startSection}-${active.course.endSection}节"
            val remainingUpcomingCount = todayCourses.count { it.state == OccurrenceState.UPCOMING }
            val footerBadge = when {
                active.state == OccurrenceState.ONGOING && remainingUpcomingCount > 0 -> "后余${remainingUpcomingCount}节"
                active.state == OccurrenceState.UPCOMING && remainingUpcomingCount > 1 -> "余${remainingUpcomingCount}节"
                active.time.isSpecialStaggered -> "错峰"
                else -> campusShort
            }
            return FocusClassSummary(
                badge = badge,
                weekTag = weekTag,
                compactHeaderMeta = compactWeekTag,
                primaryTitle = active.course.name,
                highlightLocation = loc,
                compactSectionMeta = "$sectionText · $campusShort$stagSuffix",
                compactTimeOnly = "${active.time.startTime}-${active.time.endTime}",
                timeDetail = "${active.time.startTime}-${active.time.endTime} · $sectionText",
                footerBadge = footerBadge,
                oneLineTitle = "${active.course.name} · $loc",
                oneLineSubtitle = "${active.time.startTime}-${active.time.endTime} · $sectionText$stagSuffix"
            )
        }

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
            val tmrDow = GdouTimeRules.dayOfWeekLabel(tomorrow.dayOfWeek.value)
            val sectionText = "第${firstTmr.course.startSection}-${firstTmr.course.endSection}节"
            val stagSuffix = if (firstTmr.time.isSpecialStaggered) " · 错峰" else ""
            return FocusClassSummary(
                badge = "明日早课",
                weekTag = "明$tmrDow",
                compactHeaderMeta = "明$tmrDow",
                primaryTitle = firstTmr.course.name,
                highlightLocation = loc,
                compactSectionMeta = "$sectionText · $campusShort$stagSuffix",
                compactTimeOnly = "${firstTmr.time.startTime}-${firstTmr.time.endTime}",
                timeDetail = "明${firstTmr.time.startTime}-${firstTmr.time.endTime} · ${firstTmr.course.startSection}-${firstTmr.course.endSection}节",
                footerBadge = "共${tmrCourses.size}节",
                oneLineTitle = "${firstTmr.course.name} · $loc",
                oneLineSubtitle = "明天($tmrLabel) ${firstTmr.time.startTime}-${firstTmr.time.endTime} · 共${tmrCourses.size}节"
            )
        }

        val noClassLoc = if (todayCourses.isEmpty()) "今天与明天均无排课" else "今日${todayCourses.size}节课已上完"
        return FocusClassSummary(
            badge = "已下课",
            weekTag = weekTag,
            compactHeaderMeta = compactWeekTag,
            primaryTitle = "暂无待上课程",
            highlightLocation = noClassLoc,
            compactSectionMeta = "$campusShort · 第${weekInfo.rawWeek}周",
            compactTimeOnly = "查看完整课表",
            timeDetail = "点此查看完整周课表",
            footerBadge = campusShort,
            oneLineTitle = "今天与明天都没有排课~",
            oneLineSubtitle = "第${weekInfo.rawWeek}周 · ${settings.campus.label} · 海屿空间"
        )
    }
}
