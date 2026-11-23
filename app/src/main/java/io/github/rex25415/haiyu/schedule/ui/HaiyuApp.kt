package io.github.rex25415.haiyu.schedule.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.rex25415.haiyu.schedule.BuildConfig
import io.github.rex25415.haiyu.schedule.data.AppRepository
import io.github.rex25415.haiyu.schedule.data.GdouTimeRules
import io.github.rex25415.haiyu.schedule.data.IcsExporter
import io.github.rex25415.haiyu.schedule.data.ScheduleImageExporter
import io.github.rex25415.haiyu.schedule.reminder.ClassReminderScheduler
import io.github.rex25415.haiyu.schedule.ui.components.AppUpdateDialog
import io.github.rex25415.haiyu.schedule.ui.screens.AcademicScreen
import io.github.rex25415.haiyu.schedule.ui.screens.ScheduleScreen
import io.github.rex25415.haiyu.schedule.ui.screens.SettingsScreen
import io.github.rex25415.haiyu.schedule.ui.screens.SyncPortalScreen
import io.github.rex25415.haiyu.schedule.ui.theme.HaiyuTheme
import io.github.rex25415.haiyu.schedule.update.AppReleaseInfo
import io.github.rex25415.haiyu.schedule.update.AppUpdateManager
import io.github.rex25415.haiyu.schedule.update.LegacyPackageDetector
import io.github.rex25415.haiyu.schedule.update.UpdateCheckResult
import io.github.rex25415.haiyu.schedule.widget.CountdownWidgetProvider
import io.github.rex25415.haiyu.schedule.widget.NextClassWidgetProvider
import io.github.rex25415.haiyu.schedule.widget.TodayScheduleWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var lastGradeSkipReason by rememberSaveable { mutableStateOf<String?>(null) }

    // In-App GitHub Releases Updater state
    var availableUpdate by remember { mutableStateOf<AppReleaseInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateStatusSummary by remember { mutableStateOf<String?>(null) }

    // Zero-permission legacy package (cn.gdou.haiyu.schedule <= v2.7.0) detection, refreshed on every resume!
    val lifecycleOwner = LocalLifecycleOwner.current
    var legacyInstall by remember { mutableStateOf(LegacyPackageDetector.detectLegacyInstall(context)) }
    var showLegacyDialog by remember {
        mutableStateOf(legacyInstall != null && !snapshot.settings.legacyCleanupDismissed)
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val detected = LegacyPackageDetector.detectLegacyInstall(context)
                legacyInstall = detected
                if (detected == null) showLegacyDialog = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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

    fun checkForUpdates(manual: Boolean) {
        if (isCheckingUpdate) return
        isCheckingUpdate = true
        if (manual) updateStatusSummary = "正在连接 GitHub Releases 检查最新版本..."
        scope.launch {
            val result = AppUpdateManager.checkLatestRelease(BuildConfig.VERSION_NAME)
            isCheckingUpdate = false
            val nowMs = System.currentTimeMillis()
            when (result) {
                is UpdateCheckResult.UpdateAvailable -> {
                    availableUpdate = result.release
                    updateStatusSummary = "发现新版本 v${result.release.versionName}"
                    repo.updateSettings { it.copy(lastUpdateCheckEpochMs = nowMs) }
                    if (manual || result.release.versionName != snapshot.settings.ignoredUpdateVersion) {
                        showUpdateDialog = true
                    }
                }
                is UpdateCheckResult.UpToDate -> {
                    availableUpdate = null
                    updateStatusSummary = "当前已是最新版本（v${BuildConfig.VERSION_NAME}）"
                    repo.updateSettings { it.copy(lastUpdateCheckEpochMs = nowMs) }
                    if (manual) {
                        showToast("当前 v${BuildConfig.VERSION_NAME} 已是最新版本")
                    }
                }
                is UpdateCheckResult.Error -> {
                    updateStatusSummary = if (manual) result.message else null
                    if (manual) {
                        showToast(result.message)
                    }
                }
            }
        }
    }

    // Cold-start housekeeping: clean old update APKs + silent 12h auto-check
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            AppUpdateManager.cleanupCachedApks(context, BuildConfig.VERSION_NAME)
        }
        val st = snapshot.settings
        val due = System.currentTimeMillis() - st.lastUpdateCheckEpochMs >= AppUpdateManager.AUTO_CHECK_INTERVAL_MS
        if (st.autoCheckUpdates && due) {
            checkForUpdates(manual = false)
        }
    }

    fun syncWidgetsAndReminders() {
        TodayScheduleWidgetProvider.refreshAllWidgets(context)
        CountdownWidgetProvider.refreshAllWidgets(context)
        NextClassWidgetProvider.refreshAllWidgets(context)
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

    // Custom schedule wallpaper decoded off the UI thread (RGB_565, <=1440px)
    var scheduleBgBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(snapshot.settings.scheduleBgFileName) {
        val fileName = snapshot.settings.scheduleBgFileName
        scheduleBgBitmap = if (fileName.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                val file = repo.getScheduleBackgroundFile(fileName)
                if (file != null) {
                    try {
                        val opts = BitmapFactory.Options().apply {
                            inPreferredConfig = Bitmap.Config.RGB_565
                        }
                        BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
                    } catch (_: Exception) {
                        null
                    }
                } else {
                    null
                }
            }
        }
    }

    fun exportScheduleImage(weekNumber: Int) {
        val snap = snapshot
        scope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    ScheduleImageExporter.exportWeekScheduleImageAndShare(context, snap, weekNumber)
                }
                when {
                    !res.ok -> showToast("当前没有课程可生成图片，请先同步课表")
                    res.savedToGallery -> showToast("高清课表图已保存至手机相册（Pictures/海屿空间）")
                    else -> showToast("已生成第 $weekNumber 周高清课表图")
                }
            } catch (e: Exception) {
                showToast("生成课表图失败：${e.message}")
            }
        }
    }

    fun exportIcsAsync() {
        val snap = snapshot
        scope.launch {
            try {
                val ok = withContext(Dispatchers.IO) {
                    IcsExporter.exportToIcsAndShare(context, snap)
                }
                if (!ok) showToast("当前没有可导出的课程，请先同步课表")
            } catch (e: Exception) {
                showToast("导出日历失败：${e.message}")
            }
        }
    }

    fun exportFullBackupAsync() {
        scope.launch {
            try {
                val ok = withContext(Dispatchers.IO) {
                    repo.exportFullBackupAndShare(context)
                }
                if (ok) showToast("已生成全量备份文件，可保存本地或发给自己换机还原")
            } catch (e: Exception) {
                showToast("导出备份失败：${e.message}")
            }
        }
    }

    fun importFullBackupUri(uri: Uri) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bytes = stream.readBytes()
                        if (bytes.size > 5 * 1024 * 1024) {
                            return@withContext Result.failure<String>(IllegalArgumentException("备份文件过大（超过 5MB）"))
                        }
                        bytes.toString(Charsets.UTF_8)
                    } ?: return@withContext Result.failure<String>(IllegalArgumentException("无法读取所选备份文件"))
                    repo.importFullBackupJson(raw)
                } catch (e: Exception) {
                    Result.failure(IllegalArgumentException("读取备份失败：${e.message ?: "未知错误"}"))
                }
            }
            result.onSuccess { summary ->
                syncWidgetsAndReminders()
                showToast(summary)
            }.onFailure { err ->
                showToast(err.message ?: "备份恢复失败")
            }
        }
    }

    fun pickWallpaperUri(uri: Uri) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                repo.saveScheduleBackgroundFromUri(uri)
            }
            result.onSuccess {
                showToast("已应用自定义课表壁纸，可拖动下方滑块微调卡片透明度")
            }.onFailure { err ->
                showToast(err.message ?: "设置壁纸失败")
            }
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
                            icon = {
                                if (idx == 3 && (availableUpdate != null || legacyInstall != null)) {
                                    BadgedBox(badge = { Badge() }) {
                                        Icon(tab.icon, contentDescription = tab.label)
                                    }
                                } else {
                                    Icon(tab.icon, contentDescription = tab.label)
                                }
                            },
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
                                    lastGradeSkipReason = null
                                    repo.replaceSyncedGrades(grades)
                                },
                                onExamsSynced = { exams ->
                                    repo.replaceSyncedExams(exams)
                                    syncWidgetsAndReminders()
                                },
                                onShowMessage = { msg ->
                                    val skipMatch = Regex("成绩跳过\\(([^)]+)\\)").find(msg)
                                    if (skipMatch != null) {
                                        lastGradeSkipReason = skipMatch.groupValues[1].trim()
                                    }
                                    showToast(msg)
                                },
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
                                backgroundBitmap = scheduleBgBitmap,
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
                                onTogglePinConflictCourse = { target, peers ->
                                    val pinned = repo.togglePinConflictCourse(target, peers)
                                    syncWidgetsAndReminders()
                                    showToast(if (pinned) "已置顶显示「${target.name}」" else "已取消置顶「${target.name}」")
                                },
                                onUpdateCourseNote = { courseId, note ->
                                    repo.updateCourseNote(courseId, note)
                                    showToast(if (note.isBlank()) "已清空本课笔记" else "已保存课堂笔记")
                                },
                                onUpdateCourseAttendance = { courseId, absDelta, bonusDelta ->
                                    repo.updateCourseAttendance(courseId, absDelta, bonusDelta)
                                },
                                onAddTask = { task ->
                                    repo.upsertTask(task)
                                    syncWidgetsAndReminders()
                                    showToast("已记入作业 DDL：${task.title}")
                                },
                                onToggleTaskDone = { id ->
                                    repo.toggleTaskDone(id)
                                    syncWidgetsAndReminders()
                                },
                                onExportWeekImage = { weekNum -> exportScheduleImage(weekNum) },
                                onCopyWeekScheduleText = { weekNum ->
                                    val txt = GdouTimeRules.formatWeekScheduleAsText(
                                        courses = snapshot.courses,
                                        weekNumber = weekNum,
                                        campus = snapshot.settings.campus,
                                        termLabel = snapshot.settings.academicYearLabel,
                                        pinnedKeys = snapshot.settings.pinnedConflictKeys.toSet()
                                    )
                                    clipboard.setText(AnnotatedString(txt))
                                    showToast("已复制第 $weekNum 周课表文本，可直接发微信/QQ")
                                },
                                onGetShareCode = { repo.exportScheduleShareCode() },
                                onCopyShareCode = { copyShareCode(it) },
                                onImportShareCode = { importShareCode(it) },
                                onParsePartnerShareCode = { raw -> repo.parseScheduleShareCode(raw) },
                                onSavePartnerProfile = { name, code ->
                                    repo.updateSettings {
                                        it.copy(savedPartnerName = name, savedPartnerCode = code)
                                    }
                                },
                                onShowMessage = { showToast(it) },
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
                                lastGradeSkipReason = lastGradeSkipReason,
                                onJumpToSync = { currentTab = 1 },
                                onAddTask = { task ->
                                    repo.upsertTask(task)
                                    syncWidgetsAndReminders()
                                    showToast("已保存作业 DDL：${task.title}")
                                },
                                onToggleTaskDone = { id ->
                                    repo.toggleTaskDone(id)
                                    syncWidgetsAndReminders()
                                },
                                onDeleteTask = { id ->
                                    repo.deleteTask(id)
                                    syncWidgetsAndReminders()
                                    showToast("已删除该作业任务")
                                },
                                onClearCompletedTasks = {
                                    val removed = repo.clearCompletedTasks()
                                    syncWidgetsAndReminders()
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
                                    syncWidgetsAndReminders()
                                    showToast("已保存考试安排：${ex.courseName}")
                                },
                                onDeleteExam = { id ->
                                    repo.deleteExam(id)
                                    syncWidgetsAndReminders()
                                    showToast("已删除该项")
                                },
                                onToggleExamPin = { id ->
                                    val pinned = repo.toggleExamPin(id)
                                    syncWidgetsAndReminders()
                                    showToast(if (pinned) "已置顶，将优先显示在桌面倒计时组件首行" else "已取消置顶")
                                },
                                onCopyExamsText = {
                                    val txt = GdouTimeRules.formatExamsAsText(
                                        snapshot.exams,
                                        snapshot.settings.academicYearLabel
                                    )
                                    clipboard.setText(AnnotatedString(txt))
                                    showToast("已复制全部考试清单，可直接发微信/QQ")
                                }
                            )

                            3 -> SettingsScreen(
                                snapshot = snapshot,
                                availableUpdate = availableUpdate,
                                legacyInstall = legacyInstall,
                                isCheckingUpdate = isCheckingUpdate,
                                updateStatusSummary = updateStatusSummary,
                                onCheckForUpdates = { checkForUpdates(manual = true) },
                                onOpenUpdateDialog = {
                                    if (availableUpdate != null) showUpdateDialog = true
                                    else checkForUpdates(manual = true)
                                },
                                onLaunchLegacyApp = {
                                    if (!LegacyPackageDetector.launchLegacyApp(context)) {
                                        showToast("无法打开旧版应用，请从桌面手动打开复制分享码")
                                    }
                                },
                                onOpenLegacyUninstall = {
                                    if (!LegacyPackageDetector.openLegacyAppUninstallSettings(context)) {
                                        showToast("请前往系统设置 → 应用管理卸载旧版「海屿课表」")
                                    }
                                },
                                onOpenGitHubRepo = {
                                    if (!AppUpdateManager.openUrlInBrowser(context, AppUpdateManager.REPO_WEB_URL)) {
                                        showToast("未检测到可用浏览器，仓库地址：${AppUpdateManager.REPO_WEB_URL}")
                                    }
                                },
                                onOpenGitHubIssues = {
                                    if (!AppUpdateManager.openUrlInBrowser(context, AppUpdateManager.ISSUES_NEW_URL)) {
                                        clipboard.setText(AnnotatedString(AppUpdateManager.ISSUES_URL))
                                        showToast("未检测到浏览器，已复制反馈页地址至剪贴板")
                                    }
                                },
                                onCopyIssuesLink = {
                                    clipboard.setText(AnnotatedString(AppUpdateManager.ISSUES_URL))
                                    showToast("已复制 GitHub 问题反馈页链接")
                                },
                                onSystemShareApp = {
                                    val shareText = AppUpdateManager.buildAppShareText()
                                    if (!AppUpdateManager.sharePlainText(context, "分享海屿空间给同学", shareText)) {
                                        clipboard.setText(AnnotatedString(shareText))
                                        showToast("已复制推荐文案与下载链接，可直接粘贴发送")
                                    }
                                },
                                onCopyShareAppText = {
                                    clipboard.setText(AnnotatedString(AppUpdateManager.buildAppShareText()))
                                    showToast("已复制推荐文案与下载页链接，直接粘贴发给同学即可")
                                },
                                onCopyDownloadPageLink = {
                                    clipboard.setText(AnnotatedString(AppUpdateManager.DOWNLOAD_PAGE_URL))
                                    showToast("已复制中文多线下载主页链接（免翻 GitHub）")
                                },
                                onCopyDirectApkLink = { url ->
                                    clipboard.setText(AnnotatedString(url))
                                    showToast("已复制国内加速安装包直链")
                                },
                                onCopyDiagnosticInfo = { diag ->
                                    clipboard.setText(AnnotatedString(diag))
                                    showToast("已复制脱敏环境信息（不含任何个人隐私）")
                                },
                                onUpdateSettings = { transform ->
                                    repo.updateSettings(transform)
                                    syncWidgetsAndReminders()
                                },
                                onPickWallpaperUri = { uri -> pickWallpaperUri(uri) },
                                onClearWallpaper = {
                                    repo.clearScheduleBackground()
                                    showToast("已恢复默认清新课表背景")
                                },
                                onToggleReminder = { enabled ->
                                    if (enabled) onRequestNotificationPermission()
                                    repo.updateSettings { it.copy(reminderEnabled = enabled) }
                                    ClassReminderScheduler.rescheduleNextReminder(context)
                                    showToast(if (enabled) "已开启课前与考前精准提醒" else "已关闭提醒")
                                },
                                onTestNotification = {
                                    onRequestNotificationPermission()
                                    ClassReminderScheduler.sendTestNotification(context)
                                    showToast("已发送测试通知，请查看手机通知栏")
                                },
                                onRefreshWidget = {
                                    TodayScheduleWidgetProvider.refreshAllWidgets(context)
                                    CountdownWidgetProvider.refreshAllWidgets(context)
                                    NextClassWidgetProvider.refreshAllWidgets(context)
                                    ClassReminderScheduler.scheduleMidnightWidgetRefresh(context)
                                    showToast("已立即刷新全部 3 款海屿桌面小组件")
                                },
                                onExportWeekImage = {
                                    val curWeek = GdouTimeRules.calculateCurrentWeek(
                                        snapshot.settings.semesterStartDate,
                                        snapshot.settings.totalWeeks
                                    )
                                    exportScheduleImage(curWeek)
                                },
                                onExportIcs = { exportIcsAsync() },
                                onExportFullBackup = { exportFullBackupAsync() },
                                onImportFullBackupUri = { uri -> importFullBackupUri(uri) },
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

        val pendingRelease = availableUpdate
        if (showUpdateDialog && pendingRelease != null) {
            AppUpdateDialog(
                release = pendingRelease,
                onDismiss = { showUpdateDialog = false },
                onIgnoreVersion = { ver ->
                    repo.updateSettings { it.copy(ignoredUpdateVersion = ver) }
                    showToast("已忽略 v$ver 自动弹窗（随时可在「设置 → 应用更新」手动升级）")
                },
                onShowMessage = { showToast(it) }
            )
        }

        val activeLegacy = legacyInstall
        if (showLegacyDialog && activeLegacy != null) {
            val hasMigrated = snapshot.courses.isNotEmpty()
            val legacyVer = activeLegacy.versionName.removePrefix("v").removePrefix("V")
            AlertDialog(
                onDismissRequest = {
                    showLegacyDialog = false
                    repo.updateSettings { it.copy(legacyCleanupDismissed = true) }
                },
                title = { Text("检测到旧版海屿课表（v$legacyVer）") },
                text = {
                    Text(
                        text = if (hasMigrated) {
                            "因 v2.8.0 起升级开源协议并迁移独立应用标识（现已升级为「海屿空间」），无法直接覆盖旧版图标。\n\n检测到海屿空间已同步课表，你可以直接前往系统页面安全卸载旧版（卸载旧版不会影响当前的任何数据）。"
                        } else {
                            "因 v2.8.0 起升级开源协议并迁移独立应用标识（现已升级为「海屿空间」），无法直接覆盖旧版，桌面目前会有两个图标。\n\n建议先在海屿空间同步教务（或打开旧版复制「同班分享码」导入），确认课表无误后再卸载旧版。"
                        }
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showLegacyDialog = false
                            repo.updateSettings { it.copy(legacyCleanupDismissed = true) }
                            if (!LegacyPackageDetector.openLegacyAppUninstallSettings(context)) {
                                showToast("请前往系统设置 → 应用管理卸载旧版「海屿课表」")
                            }
                        }
                    ) { Text("去卸载旧版") }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showLegacyDialog = false
                            repo.updateSettings { it.copy(legacyCleanupDismissed = true) }
                            if (!hasMigrated) {
                                LegacyPackageDetector.launchLegacyApp(context)
                            }
                        }
                    ) { Text(if (hasMigrated) "稍后再说" else "先打开旧版") }
                }
            )
        }
    }
}
