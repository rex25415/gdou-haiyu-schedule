package cn.gdou.haiyu.schedule.ui

import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.zIndex
import cn.gdou.haiyu.schedule.data.AppRepository
import cn.gdou.haiyu.schedule.data.GdouTimeRules
import cn.gdou.haiyu.schedule.data.IcsExporter
import cn.gdou.haiyu.schedule.data.ScheduleImageExporter
import cn.gdou.haiyu.schedule.reminder.ClassReminderScheduler
import cn.gdou.haiyu.schedule.ui.screens.AcademicScreen
import cn.gdou.haiyu.schedule.ui.screens.ScheduleScreen
import cn.gdou.haiyu.schedule.ui.screens.SettingsScreen
import cn.gdou.haiyu.schedule.ui.screens.SyncPortalScreen
import cn.gdou.haiyu.schedule.ui.theme.HaiyuTheme
import cn.gdou.haiyu.schedule.widget.TodayScheduleWidgetProvider
import kotlinx.coroutines.launch

private data class NavTab(
    val label: String,
    val icon: ImageVector
)

@Composable
fun HaiyuApp(
    onRequestNotificationPermission: () -> Unit,
    onSystemBarThemeChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val repo = remember { AppRepository.getInstance(context) }
    val snapshot by repo.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var currentTab by rememberSaveable { mutableIntStateOf(0) }
    var hasOpenedSyncTab by rememberSaveable { mutableStateOf(false) }
    var syncSessionGeneration by rememberSaveable { mutableIntStateOf(0) }
    var isJwLoggedIn by remember { mutableStateOf(false) }
    var isJwSyncing by remember { mutableStateOf(false) }
    var quickRefreshTrigger by remember { mutableIntStateOf(0) }
    var academicRequestedSubTab by rememberSaveable { mutableIntStateOf(0) }
    var academicSubTabNonce by rememberSaveable { mutableIntStateOf(0) }

    if (currentTab == 1 && !hasOpenedSyncTab) {
        hasOpenedSyncTab = true
    }

    val tabs = remember {
        listOf(
            NavTab("课表", Icons.Rounded.CalendarMonth),
            NavTab("教务", Icons.Rounded.CloudSync),
            NavTab("学业", Icons.Rounded.School),
            NavTab("设置", Icons.Rounded.Settings)
        )
    }

    fun showToast(msg: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(msg)
        }
    }

    fun syncWidgetsAndReminders() {
        TodayScheduleWidgetProvider.refreshAllWidgets(context)
        ClassReminderScheduler.rescheduleNextReminder(context)
    }

    fun copyShareCode(code: String) {
        clipboard.setText(AnnotatedString(code))
        showToast("已复制课表分享码，直接粘贴发给同班同学即可")
    }

    fun importShareCode(raw: String) {
        val result = repo.importScheduleShareCode(raw)
        result.onSuccess { count ->
            syncWidgetsAndReminders()
            showToast("导入成功：已导入 $count 条课程并同步校历")
        }.onFailure { err ->
            showToast(err.message ?: "分享码导入失败")
        }
    }

    fun exportScheduleImage(weekNumber: Int) {
        try {
            val res = ScheduleImageExporter.exportWeekScheduleImageAndShare(context, snapshot, weekNumber)
            when {
                !res.ok -> showToast("当前没有课程可生成图片，请先同步课表")
                res.savedToGallery -> showToast("高清课表图已保存至手机相册（Pictures/海屿课表）")
                else -> showToast("已生成第 $weekNumber 周高清课表图")
            }
        } catch (e: Exception) {
            showToast("生成课表图失败：${e.message}")
        }
    }

    fun triggerQuickRefreshSchedule() {
        if (hasOpenedSyncTab && isJwLoggedIn) {
            showToast("正在后台刷新最新课表与调课变化...")
            quickRefreshTrigger++
        } else {
            hasOpenedSyncTab = true
            currentTab = 1
            showToast("请先确认已登录教务，登录后可点「刷课表」或回课表页一键刷新")
        }
    }

    HaiyuTheme(
        palette = snapshot.settings.themePalette,
        darkModeOption = snapshot.settings.darkMode
    ) { isDark ->
        LaunchedEffect(isDark) {
            onSystemBarThemeChanged(isDark)
        }

        // Smooth Predictive-Back friendly navigation: Back from Academic/Settings returns to Schedule
        BackHandler(enabled = currentTab == 2 || currentTab == 3) {
            currentTab = 0
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                NavigationBar {
                    tabs.forEachIndexed { idx, tab ->
                        NavigationBarItem(
                            selected = currentTab == idx,
                            onClick = { currentTab = idx },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Keep SyncPortalScreen alive once opened, reset cleanly on logout via generation key
                if (hasOpenedSyncTab) {
                    key(syncSessionGeneration) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(if (currentTab == 1) 1f else 0f)
                        ) {
                            SyncPortalScreen(
                                isVisible = currentTab == 1,
                                settings = snapshot.settings,
                                quickRefreshTrigger = quickRefreshTrigger,
                                onLoginStateChanged = { isJwLoggedIn = it },
                                onSyncStateChanged = { isJwSyncing = it },
                                onQuickRefreshNeedsLogin = { reason ->
                                    currentTab = 1
                                    showToast(reason)
                                },
                                onUpdateChannelPrefs = { offCampus, desktopUa ->
                                    repo.updateSettings {
                                        it.copy(
                                            preferOffCampusChannel = offCampus,
                                            preferDesktopUa = desktopUa
                                        )
                                    }
                                },
                                onCoursesSynced = { courses, startDate, totalWeeks, termLabel, yearCode, semCode, detectedCampusId ->
                                    val detected = repo.replaceSyncedCourses(
                                        courses = courses,
                                        semesterStartDate = startDate,
                                        totalWeeks = totalWeeks,
                                        academicYearLabel = termLabel,
                                        yearCode = yearCode,
                                        semesterCode = semCode,
                                        detectedCampusId = detectedCampusId
                                    )
                                    syncWidgetsAndReminders()
                                    if (detected != null) {
                                        showToast("已自动识别「${detected.label}」并匹配对应错峰作息")
                                    }
                                },
                                onGradesSynced = { grades ->
                                    repo.replaceSyncedGrades(grades)
                                },
                                onExamsSynced = { exams ->
                                    repo.replaceSyncedExams(exams)
                                },
                                onShowMessage = { showToast(it) },
                                onNavigateBackToSchedule = { currentTab = 0 },
                                onSyncFinishedJumpSchedule = { currentTab = 0 }
                            )
                        }
                    }
                }

                if (currentTab != 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(2f)
                    ) {
                        when (currentTab) {
                            0 -> ScheduleScreen(
                                snapshot = snapshot,
                                isDark = isDark,
                                isRefreshingSchedule = isJwSyncing,
                                onQuickRefreshSchedule = { triggerQuickRefreshSchedule() },
                                onJumpToSync = { currentTab = 1 },
                                onJumpToTasks = {
                                    academicRequestedSubTab = 0
                                    academicSubTabNonce++
                                    currentTab = 2
                                },
                                onSelectCampus = { campus ->
                                    repo.updateSettings { it.copy(campus = campus) }
                                    syncWidgetsAndReminders()
                                    showToast("已切换至${campus.label}作息规则")
                                },
                                onLoadDemoData = {
                                    repo.loadDemoData()
                                    syncWidgetsAndReminders()
                                    showToast("已加载${snapshot.settings.campus.label}示例课表、作业、成绩与考场")
                                },
                                onToggleShowNonCurrentWeek = {
                                    val next = !snapshot.settings.showNonCurrentWeekCourses
                                    repo.updateSettings { it.copy(showNonCurrentWeekCourses = next) }
                                    showToast(if (next) "已显示非本周课程" else "已隐藏非本周课程（仅看本周）")
                                },
                                onSaveCourse = { course ->
                                    repo.upsertCourse(course)
                                    syncWidgetsAndReminders()
                                    showToast("已保存课程：${course.name}")
                                },
                                onDeleteCourse = { id ->
                                    repo.deleteCourse(id)
                                    syncWidgetsAndReminders()
                                    showToast("已删除该课程")
                                },
                                onRescheduleSingleWeek = { courseId, weekNum, newDay, newStart, newEnd, newLoc ->
                                    val ok = repo.rescheduleSingleWeek(courseId, weekNum, newDay, newStart, newEnd, newLoc)
                                    if (ok) {
                                        syncWidgetsAndReminders()
                                        showToast("已仅调整第 $weekNum 周上课安排，其他周次不受影响")
                                    } else {
                                        showToast("调整失败，请检查该课程周次")
                                    }
                                },
                                onAddTask = { task ->
                                    repo.upsertTask(task)
                                    showToast("已记入作业 DDL：${task.title}")
                                },
                                onExportWeekImage = { weekNum -> exportScheduleImage(weekNum) },
                                onGetShareCode = { repo.exportScheduleShareCode() },
                                onCopyShareCode = { copyShareCode(it) },
                                onImportShareCode = { importShareCode(it) },
                                onApplyHolidayShift = { sw, sd, tw, td, move ->
                                    val count = repo.applyHolidayShift(sw, sd, tw, td, move)
                                    syncWidgetsAndReminders()
                                    if (count > 0) {
                                        showToast("调休成功：已同步调整 $count 节课程")
                                    } else {
                                        showToast("源日期当天没有排课，未执行调休")
                                    }
                                }
                            )

                            2 -> AcademicScreen(
                                snapshot = snapshot,
                                requestedSubTab = academicRequestedSubTab,
                                subTabRequestNonce = academicSubTabNonce,
                                onJumpToSync = { currentTab = 1 },
                                onAddTask = { task ->
                                    repo.upsertTask(task)
                                    showToast("已添加作业 DDL：${task.title}")
                                },
                                onToggleTaskDone = { id ->
                                    repo.toggleTaskDone(id)
                                },
                                onDeleteTask = { id ->
                                    repo.deleteTask(id)
                                    showToast("已删除该作业任务")
                                },
                                onClearCompletedTasks = {
                                    val removed = repo.clearCompletedTasks()
                                    if (removed > 0) showToast("已清理 $removed 条已完成作业")
                                },
                                onAddGrade = { g ->
                                    repo.upsertGrade(g)
                                    showToast("已加入模拟成绩：${g.courseName}")
                                },
                                onDeleteGrade = { id ->
                                    repo.deleteGrade(id)
                                    showToast("已移除该记录")
                                },
                                onAddExam = { ex ->
                                    repo.upsertExam(ex)
                                    showToast("已添加考试倒计时：${ex.courseName}")
                                },
                                onDeleteExam = { id ->
                                    repo.deleteExam(id)
                                    showToast("已删除该考试")
                                }
                            )

                            3 -> SettingsScreen(
                                snapshot = snapshot,
                                onUpdateSettings = { transform ->
                                    repo.updateSettings(transform)
                                    syncWidgetsAndReminders()
                                },
                                onToggleReminder = { enabled ->
                                    if (enabled) onRequestNotificationPermission()
                                    repo.updateSettings { it.copy(reminderEnabled = enabled) }
                                    ClassReminderScheduler.rescheduleNextReminder(context)
                                    showToast(if (enabled) "已开启课前精准提醒" else "已关闭课前提醒")
                                },
                                onTestNotification = {
                                    onRequestNotificationPermission()
                                    ClassReminderScheduler.sendTestNotification(context)
                                    showToast("已发送测试通知，请查看手机通知栏")
                                },
                                onRefreshWidget = {
                                    TodayScheduleWidgetProvider.refreshAllWidgets(context)
                                    showToast("已立即刷新海屿桌面小组件")
                                },
                                onExportWeekImage = {
                                    val curWeek = GdouTimeRules.calculateCurrentWeek(
                                        snapshot.settings.semesterStartDate,
                                        snapshot.settings.totalWeeks
                                    )
                                    exportScheduleImage(curWeek)
                                },
                                onExportIcs = {
                                    val ok = IcsExporter.exportToIcsAndShare(context, snapshot)
                                    if (!ok) showToast("当前没有可导出的课程，请先同步课表")
                                },
                                onGetShareCode = { repo.exportScheduleShareCode() },
                                onCopyShareCode = { copyShareCode(it) },
                                onImportShareCode = { importShareCode(it) },
                                onLoadDemoData = {
                                    repo.loadDemoData()
                                    syncWidgetsAndReminders()
                                    showToast("已加载示例数据")
                                },
                                onLogoutJwSession = {
                                    CookieManager.getInstance().removeAllCookies {
                                        CookieManager.getInstance().flush()
                                    }
                                    WebStorage.getInstance().deleteAllData()
                                    hasOpenedSyncTab = false
                                    isJwLoggedIn = false
                                    isJwSyncing = false
                                    syncSessionGeneration++
                                    showToast("已彻底退出教务账号并清除浏览器缓存")
                                },
                                onClearAllData = {
                                    repo.clearAllAcademicData()
                                    syncWidgetsAndReminders()
                                    showToast("已清空所有本地课表、作业、成绩与考场")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
