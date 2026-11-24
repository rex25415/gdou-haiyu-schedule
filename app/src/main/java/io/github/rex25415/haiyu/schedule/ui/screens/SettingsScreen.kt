package io.github.rex25415.haiyu.schedule.ui.screens

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.rex25415.haiyu.schedule.BuildConfig
import io.github.rex25415.haiyu.schedule.data.GdouTimeRules
import io.github.rex25415.haiyu.schedule.model.AppDataSnapshot
import io.github.rex25415.haiyu.schedule.model.AppSettings
import io.github.rex25415.haiyu.schedule.model.GdouCampus
import io.github.rex25415.haiyu.schedule.model.GridRowDensity
import io.github.rex25415.haiyu.schedule.reminder.ClassReminderScheduler
import io.github.rex25415.haiyu.schedule.ui.components.AboutUpdateWorkbenchSheet
import io.github.rex25415.haiyu.schedule.ui.components.AppearanceWorkbenchSheet
import io.github.rex25415.haiyu.schedule.ui.components.BellScheduleReferenceDialog
import io.github.rex25415.haiyu.schedule.ui.components.FeedbackGuideDialog
import io.github.rex25415.haiyu.schedule.ui.components.HaiyuCompactSegmentButton
import io.github.rex25415.haiyu.schedule.ui.components.OemKeepAliveGuideDialog
import io.github.rex25415.haiyu.schedule.ui.components.SettingSwitchRow
import io.github.rex25415.haiyu.schedule.ui.components.ShareAppDialog
import io.github.rex25415.haiyu.schedule.ui.components.WidgetsBackupWorkbenchSheet
import io.github.rex25415.haiyu.schedule.update.AppReleaseInfo
import io.github.rex25415.haiyu.schedule.update.AppSignatureVerifier
import io.github.rex25415.haiyu.schedule.update.AppUpdateManager
import io.github.rex25415.haiyu.schedule.update.LegacyInstallInfo
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    snapshot: AppDataSnapshot,
    availableUpdate: AppReleaseInfo?,
    legacyInstall: LegacyInstallInfo?,
    isCheckingUpdate: Boolean,
    updateStatusSummary: String?,
    onCheckForUpdates: () -> Unit,
    onOpenUpdateDialog: () -> Unit,
    onLaunchLegacyApp: () -> Unit,
    onOpenLegacyUninstall: () -> Unit,
    onOpenGitHubRepo: () -> Unit,
    onOpenGitHubIssues: () -> Unit,
    onCopyIssuesLink: () -> Unit,
    onSystemShareApp: () -> Unit,
    onCopyShareAppText: () -> Unit,
    onCopyDownloadPageLink: () -> Unit,
    onCopyDirectApkLink: (String) -> Unit,
    onCopyDiagnosticInfo: (String) -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    isImportingWallpaper: Boolean = false,
    wallpaperStatus: Pair<Boolean, String>? = null,
    onClearWallpaperStatus: () -> Unit = {},
    onPickWallpaperUri: (Uri) -> Unit,
    onClearWallpaper: () -> Unit,
    onToggleReminder: (Boolean) -> Unit,
    onTestNotification: () -> Unit,
    onRefreshWidget: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onExportWeekImage: () -> Unit = {},
    onExportIcs: () -> Unit,
    onExportFullBackup: () -> Unit,
    onImportFullBackupUri: (Uri) -> Unit,
    @Suppress("UNUSED_PARAMETER") onGetShareCode: () -> String? = { null },
    @Suppress("UNUSED_PARAMETER") onCopyShareCode: (String) -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onImportShareCode: (String) -> Unit = {},
    onLoadDemoData: () -> Unit,
    onLogoutJwSession: () -> Unit,
    onClearAllData: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val signatureInfo = remember { AppSignatureVerifier.verifyCurrentApp(context) }
    var exactAlarmGranted by remember { mutableStateOf(ClassReminderScheduler.canScheduleExactAlarms(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exactAlarmGranted = ClassReminderScheduler.canScheduleExactAlarms(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val settings = snapshot.settings
    var showDatePicker by remember { mutableStateOf(false) }
    var showTermMetaDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showBellRefDialog by remember { mutableStateOf(false) }
    var showOemGuideDialog by remember { mutableStateOf(false) }
    var showShareAppDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }

    var showAppearanceSheet by remember { mutableStateOf(false) }
    var showWidgetsBackupSheet by remember { mutableStateOf(false) }
    var showAboutUpdateSheet by remember { mutableStateOf(false) }

    val wallpaperPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onPickWallpaperUri(uri)
    }
    val wallpaperDocPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onPickWallpaperUri(uri)
    }
    val backupRestorePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportFullBackupUri(uri)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
        ) {
            item {
                Column {
                    Text("设置与工作台", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "海屿空间 v${BuildConfig.VERSION_NAME} · 广东海洋大学全校区支持" +
                            if (settings.lastSyncTime.isNotBlank()) " · 上次同步 ${settings.lastSyncTime}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (availableUpdate != null) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenUpdateDialog() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                Text(
                                    text = "发现新版本 v${availableUpdate.versionName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "点击即可应用内一键下载升级，保留全部课表与作业记录",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                            }
                            Button(
                                onClick = onOpenUpdateDialog,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("立即更新", maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }

            if (legacyInstall != null) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.WarningAmber,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val legacyVer = legacyInstall.versionName.removePrefix("v").removePrefix("V")
                                Text(
                                    text = "检测到旧版海屿课表（v$legacyVer）仍留在手机上",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (snapshot.courses.isEmpty()) {
                                    "因 v2.8.0 起迁移独立应用标识，建议先打开旧版复制「同班分享码」导入海屿空间，确认课表就绪后再卸载旧版。"
                                } else {
                                    "海屿空间数据已就绪！点下方按钮可直接前往系统页安全卸载旧版图标（不影响当前数据）。"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.88f)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (snapshot.courses.isEmpty()) {
                                    OutlinedButton(
                                        onClick = onLaunchLegacyApp,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("打开旧版", fontSize = 12.sp, maxLines = 1, softWrap = false)
                                    }
                                }
                                Button(
                                    onClick = onOpenLegacyUninstall,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("前往卸载旧版", fontSize = 12.sp, maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                }
            }

            // 1. Campus & Semester Calendar
            item {
                SettingsCard(title = "广海校区与学期校历", icon = Icons.Rounded.School) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        GdouCampus.entries.forEach { campus ->
                            HaiyuCompactSegmentButton(
                                label = campus.label.removeSuffix("校区"),
                                selected = settings.campus == campus,
                                modifier = Modifier.weight(1f),
                                onClick = { onUpdateSettings { it.copy(campus = campus) } }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${settings.campus.label}：${settings.campus.shortDesc}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showDatePicker = true }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("开学第一周星期一", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "${settings.semesterStartDate}（自动从日历对齐周一）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text("选日期", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showTermMetaDialog = true }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("学期名称与总周数", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "${settings.academicYearLabel} · 共 ${settings.totalWeeks} 周",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("编辑", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { showBellRefDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("查看三校区作息速查表", maxLines = 1, softWrap = false)
                    }
                }
            }

            // 2. Schedule Grid Display & Row Density
            item {
                SettingsCard(title = "课表视图与行高密度", icon = Icons.Rounded.CalendarMonth) {
                    SettingSwitchRow(
                        title = "显示非本周课程",
                        subtitle = "空闲时段以浅灰卡片预览其他周次排课",
                        checked = settings.showNonCurrentWeekCourses,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(showNonCurrentWeekCourses = v) } }
                    )
                    SettingSwitchRow(
                        title = "每周固定显示周一至周日（含周末）",
                        subtitle = "关闭时若整学期周末均无排课则显示周一至周五",
                        checked = settings.showWeekend,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(showWeekend = v) } }
                    )
                    SettingSwitchRow(
                        title = "在周课表中显示考试安排",
                        subtitle = "期末周自动把已同步考试以醒目卡片投影到对应周",
                        checked = settings.showExamsOnTimetable,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(showExamsOnTimetable = v) } }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    Text("课表行高密度", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when (settings.gridRowDensity) {
                            GridRowDensity.COMPACT -> "当前已选「紧凑一屏」：按手机实际屏幕高度自适应铺满 1–10 节，一眼看全少滑动。"
                            GridRowDensity.STANDARD -> "当前已选「标准舒适」：字号与行高均衡，适合日常浏览。"
                            GridRowDensity.COMFORTABLE -> "当前已选「宽松大字」：加大格子行高并同步放大课表文字，阅读更省眼。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        GridRowDensity.entries.forEach { density ->
                            HaiyuCompactSegmentButton(
                                label = density.label,
                                selected = settings.gridRowDensity == density,
                                modifier = Modifier.weight(1f),
                                onClick = { onUpdateSettings { it.copy(gridRowDensity = density) } }
                            )
                        }
                    }
                }
            }

            // 3. Class & Exam Reminder Notifications
            item {
                SettingsCard(title = "课前与考前精准提醒", icon = Icons.Rounded.NotificationsActive) {
                    SettingSwitchRow(
                        title = "开启上课与考试前自动提醒",
                        subtitle = "按所在校区错峰时间推送教室提醒，考前 1 小时同步推送考场与座号",
                        checked = settings.reminderEnabled,
                        onCheckedChange = onToggleReminder
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(10, 15, 20, 30).forEach { mins ->
                            HaiyuCompactSegmentButton(
                                label = "${mins}分钟",
                                selected = settings.reminderMinutesBefore == mins,
                                modifier = Modifier.weight(1f),
                                onClick = { onUpdateSettings { it.copy(reminderMinutesBefore = mins) } }
                            )
                        }
                    }
                    if (settings.reminderEnabled && !exactAlarmGranted) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = "未开启系统「精准闹钟」权限",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Text(
                                        text = "Android 12+ 默认会延迟省电闹钟，开启后可按错峰时间准点提醒。",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                                    )
                                }
                                TextButton(
                                    onClick = { ClassReminderScheduler.openExactAlarmPermissionSettings(context) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("去开启", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = onTestNotification,
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("发一条测试提醒", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        FilledTonalButton(
                            onClick = { showOemGuideDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.weight(1.25f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.BatterySaver, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("防杀后台保活指南", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            // 4. Clean Sub-Workbench Directory (keeps main settings screen calm & under 2 swipes!)
            item {
                SettingsCard(title = "装扮、备份与关于工作台", icon = Icons.Rounded.Tune) {
                    val hasBg = settings.scheduleBgFileName.isNotBlank()
                    WorkbenchDirectoryRow(
                        icon = Icons.Rounded.Palette,
                        title = "主题装扮与课表壁纸",
                        subtitle = "${settings.themePalette.label} · ${settings.darkMode.label} · ${if (hasBg) "已设自定义壁纸" else "默认背景"}",
                        showBadge = false,
                        onClick = { showAppearanceSheet = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    WorkbenchDirectoryRow(
                        icon = Icons.Rounded.Widgets,
                        title = "桌面小组件与数据备份",
                        subtitle = "4 款微件（含 2×2）一键添置 · .ics · 全量备份",
                        showBadge = false,
                        onClick = { showWidgetsBackupSheet = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    WorkbenchDirectoryRow(
                        icon = Icons.Rounded.SystemUpdate,
                        title = "应用更新、分享与开源合规",
                        subtitle = if (availableUpdate != null) {
                            "发现新版本 v${availableUpdate.versionName} · 点击升级"
                        } else {
                            "v${BuildConfig.VERSION_NAME} · 签名验真 · PolyForm 非商业开源"
                        },
                        showBadge = availableUpdate != null,
                        onClick = { showAboutUpdateSheet = true }
                    )
                }
            }
        }
    }

    if (showAppearanceSheet) {
        AppearanceWorkbenchSheet(
            settings = settings,
            isImportingWallpaper = isImportingWallpaper,
            wallpaperStatus = wallpaperStatus,
            onUpdateSettings = onUpdateSettings,
            onPickWallpaper = {
                try {
                    wallpaperPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                } catch (_: Exception) {
                    try {
                        wallpaperDocPicker.launch(arrayOf("image/*"))
                    } catch (_: Exception) {}
                }
            },
            onPickWallpaperFromFiles = {
                try {
                    wallpaperDocPicker.launch(arrayOf("image/*"))
                } catch (_: Exception) {}
            },
            onClearWallpaper = onClearWallpaper,
            onDismiss = {
                onClearWallpaperStatus()
                showAppearanceSheet = false
            }
        )
    }

    if (showWidgetsBackupSheet) {
        WidgetsBackupWorkbenchSheet(
            onRefreshWidget = onRefreshWidget,
            onExportIcs = onExportIcs,
            onExportFullBackup = onExportFullBackup,
            onLaunchBackupRestore = {
                backupRestorePicker.launch(arrayOf("application/json", "text/plain", "*/*"))
            },
            onLoadDemoData = onLoadDemoData,
            onRequestClearData = { showClearConfirm = true },
            onDismiss = { showWidgetsBackupSheet = false }
        )
    }

    if (showAboutUpdateSheet) {
        AboutUpdateWorkbenchSheet(
            settings = settings,
            signatureInfo = signatureInfo,
            availableUpdate = availableUpdate,
            isCheckingUpdate = isCheckingUpdate,
            updateStatusSummary = updateStatusSummary,
            onCheckForUpdates = onCheckForUpdates,
            onOpenUpdateDialog = onOpenUpdateDialog,
            onOpenShareApp = { showShareAppDialog = true },
            onOpenFeedback = { showFeedbackDialog = true },
            onOpenGitHubRepo = onOpenGitHubRepo,
            onUpdateSettings = onUpdateSettings,
            onLogoutJwSession = onLogoutJwSession,
            onDismiss = { showAboutUpdateSheet = false }
        )
    }

    if (showDatePicker) {
        val initialMillis = remember(settings.semesterStartDate) {
            GdouTimeRules.parseLocalDate(settings.semesterStartDate)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                Button(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { ms ->
                            val picked = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                            val alignedMonday = picked.with(DayOfWeek.MONDAY)
                            val formatted = alignedMonday.format(DateTimeFormatter.ISO_LOCAL_DATE)
                            onUpdateSettings { it.copy(semesterStartDate = formatted) }
                        }
                        showDatePicker = false
                    }
                ) { Text("确定（对齐该周周一）") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTermMetaDialog) {
        var totalWeeksInput by remember { mutableStateOf(settings.totalWeeks.toString()) }
        var termLabelInput by remember { mutableStateOf(settings.academicYearLabel) }

        AlertDialog(
            onDismissRequest = { showTermMetaDialog = false },
            title = { Text("编辑学期名称与总周数") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = termLabelInput,
                        onValueChange = { termLabelInput = it },
                        label = { Text("学期名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = totalWeeksInput,
                        onValueChange = { totalWeeksInput = it.filter(Char::isDigit) },
                        label = { Text("总教学周数 (1-30)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateSettings {
                            it.copy(
                                academicYearLabel = termLabelInput.trim().ifBlank { it.academicYearLabel },
                                totalWeeks = (totalWeeksInput.toIntOrNull() ?: it.totalWeeks).coerceIn(1, 30)
                            )
                        }
                        showTermMetaDialog = false
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showTermMetaDialog = false }) { Text("取消") }
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("确认清空本地学术数据？") },
            text = { Text("将清除本地保存的课表、作业 DDL、成绩和考试记录（不会影响学校教务系统里的原始数据，随时可重新同步）。") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllData()
                        showClearConfirm = false
                    }
                ) { Text("确认清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            }
        )
    }

    if (showBellRefDialog) {
        BellScheduleReferenceDialog(
            initialCampus = settings.campus,
            onDismiss = { showBellRefDialog = false }
        )
    }

    if (showOemGuideDialog) {
        OemKeepAliveGuideDialog(
            onDismiss = { showOemGuideDialog = false }
        )
    }

    if (showShareAppDialog) {
        val targetVer = availableUpdate?.versionName ?: BuildConfig.VERSION_NAME
        val directMirrorUrl = remember(targetVer, availableUpdate) {
            AppUpdateManager.buildMirrorApkUrl(targetVer, availableUpdate?.apkDownloadUrl)
        }
        ShareAppDialog(
            targetVersionName = targetVer,
            directMirrorApkUrl = directMirrorUrl,
            onSystemShare = {
                showShareAppDialog = false
                onSystemShareApp()
            },
            onCopyShareText = {
                onCopyShareAppText()
            },
            onCopyDownloadPageUrl = {
                onCopyDownloadPageLink()
            },
            onCopyDirectApkUrl = {
                onCopyDirectApkLink(directMirrorUrl)
            },
            onDismiss = { showShareAppDialog = false }
        )
    }

    if (showFeedbackDialog) {
        val diagInfo = remember(settings.campus, settings.gridRowDensity, settings.darkMode) {
            AppUpdateManager.buildDiagnosticInfo(
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                androidRelease = Build.VERSION.RELEASE ?: "Unknown",
                sdkInt = Build.VERSION.SDK_INT,
                campusLabel = settings.campus.label,
                densityLabel = settings.gridRowDensity.label,
                darkModeLabel = settings.darkMode.label
            )
        }
        FeedbackGuideDialog(
            diagnosticInfo = diagInfo,
            onOpenGitHubIssues = {
                showFeedbackDialog = false
                onOpenGitHubIssues()
            },
            onCopyDiagnosticInfo = {
                onCopyDiagnosticInfo(diagInfo)
            },
            onCopyIssuesUrl = {
                onCopyIssuesLink()
            },
            onOpenGitHubRepo = {
                showFeedbackDialog = false
                onOpenGitHubRepo()
            },
            onDismiss = { showFeedbackDialog = false }
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun WorkbenchDirectoryRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    showBadge: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (showBadge) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0E9F6E))
                    )
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}
