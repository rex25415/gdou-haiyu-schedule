package io.github.rex25415.haiyu.schedule.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rex25415.haiyu.schedule.BuildConfig
import io.github.rex25415.haiyu.schedule.data.GdouTimeRules
import io.github.rex25415.haiyu.schedule.model.AppDataSnapshot
import io.github.rex25415.haiyu.schedule.model.AppSettings
import io.github.rex25415.haiyu.schedule.model.AppThemePalette
import io.github.rex25415.haiyu.schedule.model.DarkModeOption
import io.github.rex25415.haiyu.schedule.model.GdouCampus
import io.github.rex25415.haiyu.schedule.ui.components.BellScheduleReferenceDialog
import io.github.rex25415.haiyu.schedule.ui.components.OemKeepAliveGuideDialog
import io.github.rex25415.haiyu.schedule.ui.components.ScheduleShareCodeDialog
import io.github.rex25415.haiyu.schedule.update.AppReleaseInfo
import io.github.rex25415.haiyu.schedule.update.AppSignatureVerifier
import io.github.rex25415.haiyu.schedule.update.SignatureTrustState
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    snapshot: AppDataSnapshot,
    availableUpdate: AppReleaseInfo?,
    isCheckingUpdate: Boolean,
    updateStatusSummary: String?,
    onCheckForUpdates: () -> Unit,
    onOpenUpdateDialog: () -> Unit,
    onOpenGitHubRepo: () -> Unit,
    onCopyDownloadPageLink: () -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onPickWallpaperUri: (Uri) -> Unit,
    onClearWallpaper: () -> Unit,
    onToggleReminder: (Boolean) -> Unit,
    onTestNotification: () -> Unit,
    onRefreshWidget: () -> Unit,
    onExportWeekImage: () -> Unit,
    onExportIcs: () -> Unit,
    onExportFullBackup: () -> Unit,
    onImportFullBackupUri: (Uri) -> Unit,
    onGetShareCode: () -> String?,
    onCopyShareCode: (String) -> Unit,
    onImportShareCode: (String) -> Unit,
    onLoadDemoData: () -> Unit,
    onLogoutJwSession: () -> Unit,
    onClearAllData: () -> Unit
) {
    val context = LocalContext.current
    val signatureInfo = remember { AppSignatureVerifier.verifyCurrentApp(context) }
    val settings = snapshot.settings
    var showDatePicker by remember { mutableStateOf(false) }
    var showTermMetaDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showBellRefDialog by remember { mutableStateOf(false) }
    var showShareCodeDialog by remember { mutableStateOf(false) }
    var showOemGuideDialog by remember { mutableStateOf(false) }

    val wallpaperPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
        ) {
        item {
            Column {
                Text("我的与设置", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "海屿课表 v${BuildConfig.VERSION_NAME} · 广东海洋大学全校区支持" +
                        if (settings.lastSyncTime.isNotBlank()) " · 上次同步 ${settings.lastSyncTime}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 0. Instant New-Version Banner (shown prominently whenever a newer GitHub Release exists!)
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

        // 1. Campus & Staggered Schedule Rules
        item {
            SettingsCard(title = "广海校区与错峰作息", icon = Icons.Rounded.School) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    GdouCampus.entries.forEach { campus ->
                        HaiyuChoiceChip(
                            label = campus.label,
                            selected = settings.campus == campus,
                            onClick = { onUpdateSettings { it.copy(campus = campus) } }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "当前已生效错峰规则（${settings.campus.label}）：",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = settings.campus.shortDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
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

        // 2. Theme & Appearance
        item {
            SettingsCard(title = "主题美学与外观", icon = Icons.Rounded.Palette) {
                Text("配色方案", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    AppThemePalette.entries.forEach { p ->
                        HaiyuChoiceChip(
                            label = p.label,
                            selected = settings.themePalette == p,
                            onClick = { onUpdateSettings { it.copy(themePalette = p) } }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("深浅模式", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    DarkModeOption.entries.forEach { m ->
                        HaiyuChoiceChip(
                            label = m.label,
                            selected = settings.darkMode == m,
                            onClick = { onUpdateSettings { it.copy(darkMode = m) } }
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                Text("自定义课表壁纸（零权限）", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "精选喜欢的风景或纯色图作为周课表底图，图片仅压缩保存在本机私有目录，不申请系统相册权限。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                val hasWallpaper = settings.scheduleBgFileName.isNotBlank()
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalButton(
                        onClick = {
                            wallpaperPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Wallpaper, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (hasWallpaper) "更换课表壁纸" else "选择课表壁纸", fontSize = 12.5.sp)
                    }
                    if (hasWallpaper) {
                        OutlinedButton(
                            onClick = onClearWallpaper,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.HideImage, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("恢复默认", fontSize = 12.5.sp)
                        }
                    }
                }
                if (hasWallpaper) {
                    var sliderAlpha by remember(settings.courseCardAlpha) {
                        mutableFloatStateOf(settings.courseCardAlpha.coerceIn(0.62f, 1.0f))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("课程卡片不透明度", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "${(sliderAlpha * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = sliderAlpha,
                        onValueChange = { sliderAlpha = it },
                        onValueChangeFinished = {
                            onUpdateSettings { it.copy(courseCardAlpha = sliderAlpha.coerceIn(0.62f, 1.0f)) }
                        },
                        valueRange = 0.62f..1.0f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 3. Term Calendar & Display Settings
        item {
            SettingsCard(title = "学期校历与课表显示", icon = Icons.Rounded.CalendarMonth) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
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
                    TextButton(onClick = { showDatePicker = true }) { Text("选日期") }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
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
                    TextButton(onClick = { showTermMetaDialog = true }) { Text("编辑") }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SettingSwitchRow(
                    title = "显示非本周课程",
                    subtitle = "在空闲时段以浅灰卡片预览其他周次的排课",
                    checked = settings.showNonCurrentWeekCourses,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(showNonCurrentWeekCourses = v) } }
                )
                SettingSwitchRow(
                    title = "每周固定显示周一至周日（含周末）",
                    subtitle = "默认开启，每一周页面均稳定显示 7 天，不会因周末没课而消失",
                    checked = settings.showWeekend,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(showWeekend = v) } }
                )
            }
        }

        // 4. Class Reminder Notifications
        item {
            SettingsCard(title = "课前精准提醒", icon = Icons.Rounded.NotificationsActive) {
                SettingSwitchRow(
                    title = "开启上课前自动弹窗通知",
                    subtitle = "自动按所在校区错峰时间提前推送教室提醒",
                    checked = settings.reminderEnabled,
                    onCheckedChange = onToggleReminder
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    listOf(10, 15, 20, 30).forEach { mins ->
                        HaiyuChoiceChip(
                            label = "${mins}分钟",
                            selected = settings.reminderMinutesBefore == mins,
                            onClick = { onUpdateSettings { it.copy(reminderMinutesBefore = mins) } }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onTestNotification,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("发送一条测试提醒（验证通知权限）")
                }
                Spacer(modifier = Modifier.height(6.dp))
                FilledTonalButton(
                    onClick = { showOemGuideDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.BatterySaver, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("收不到提醒 / 小组件不刷新？各品牌防杀后台指南", fontSize = 12.sp)
                }
            }
        }

        // 5. Desktop Widget & ICS Export Toolbox
        item {
            SettingsCard(title = "桌面小组件与数据工具", icon = Icons.Rounded.Widgets) {
                Text(
                    text = "在手机桌面长按空白处 → 点击「微件 / 小组件」→ 找到「海屿今日课表」即可添加到桌面，解锁一眼看当天课表！",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalButton(
                        onClick = onRefreshWidget,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("刷新桌面组件", fontSize = 12.5.sp)
                    }
                    FilledTonalButton(
                        onClick = onExportWeekImage,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("生成课表长图", fontSize = 12.5.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalButton(
                        onClick = onExportIcs,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("导出 .ics 日历", fontSize = 12.5.sp)
                    }
                    FilledTonalButton(
                        onClick = { showShareCodeDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("同班分享码", fontSize = 12.5.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalButton(
                        onClick = onExportFullBackup,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("导出全量备份", fontSize = 12.5.sp)
                    }
                    FilledTonalButton(
                        onClick = {
                            backupRestorePicker.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("从备份恢复", fontSize = 12.5.sp)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "全量备份包含课表、作业 DDL、成绩、考试与个性化设置（不含学号密码），换机一键还原。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onLoadDemoData,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("加载示例课表")
                    }
                    OutlinedButton(
                        onClick = { showClearConfirm = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Rounded.DeleteForever,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("清空课表数据", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // 6. In-App Self-Update & Open Source Repository
        item {
            SettingsCard(title = "应用更新与开源项目", icon = Icons.Rounded.SystemUpdate) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "当前版本：v${BuildConfig.VERSION_NAME}（build ${BuildConfig.VERSION_CODE}）",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = updateStatusSummary
                                ?: if (availableUpdate != null) "可升级至最新版本 v${availableUpdate.versionName}"
                                else "采用海屿开源统一发布签名打包，后续覆盖升级自动保留你的全部课表与设置。",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (availableUpdate != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            if (availableUpdate != null) onOpenUpdateDialog()
                            else onCheckForUpdates()
                        },
                        enabled = !isCheckingUpdate,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("检查中...", fontSize = 12.5.sp)
                        } else {
                            Icon(Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (availableUpdate != null) "更新至 v${availableUpdate.versionName}" else "检查新版本",
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onOpenGitHubRepo,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("GitHub 开源", fontSize = 12.5.sp, maxLines = 1, softWrap = false)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = onCopyDownloadPageLink,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("复制中文下载主页（发同学免翻 GitHub）", fontSize = 12.5.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                SettingSwitchRow(
                    title = "启动时自动检查更新",
                    subtitle = "每隔 12 小时静默检查 GitHub Releases，支持国内多线加速与 SHA-256 校验",
                    checked = settings.autoCheckUpdates,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(autoCheckUpdates = v) } }
                )
            }
        }

        // 7. Account Privacy, Runtime Signature Verification & Non-Commercial Open-Source Statement
        item {
            SettingsCard(title = "隐私安全、来源验真与非商业声明", icon = Icons.Rounded.Security) {
                val sigStyle = when (signatureInfo.state) {
                    SignatureTrustState.VERIFIED_ORIGINAL -> SigCardStyle(
                        bg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                        fg = MaterialTheme.colorScheme.onPrimaryContainer,
                        icon = Icons.Rounded.VerifiedUser,
                        title = "安装包签名验真通过（海屿原版纯净构建）",
                        desc = "证书指纹：${signatureInfo.shortFingerprint} · 包名 ${BuildConfig.APPLICATION_ID}"
                    )
                    SignatureTrustState.DEBUG_LOCAL_BUILD -> SigCardStyle(
                        bg = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                        fg = MaterialTheme.colorScheme.onSecondaryContainer,
                        icon = Icons.Rounded.Code,
                        title = "本地开发编译构建（Debug）",
                        desc = "证书指纹：${signatureInfo.shortFingerprint} · 源码自行编译版本"
                    )
                    SignatureTrustState.THIRD_PARTY_MODIFIED -> SigCardStyle(
                        bg = MaterialTheme.colorScheme.errorContainer,
                        fg = MaterialTheme.colorScheme.onErrorContainer,
                        icon = Icons.Rounded.WarningAmber,
                        title = "警告：非原作者发布签名（疑似第三方重签/修改包）",
                        desc = "指纹 ${signatureInfo.shortFingerprint} 与原版不符；如有广告或异常，与原作者无关，请前往开源主页下载纯净原版。"
                    )
                }
                Surface(
                    color = sigStyle.bg,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = sigStyle.icon,
                            contentDescription = null,
                            tint = sigStyle.fg,
                            modifier = Modifier.size(18.dp).padding(top = 1.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = sigStyle.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = sigStyle.fg
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = sigStyle.desc,
                                style = MaterialTheme.typography.labelSmall,
                                color = sigStyle.fg.copy(alpha = 0.88f)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "1. 非校方官方声明：海屿课表为个人学习与技术交流开发的独立纯本地开源客户端，与任何高校官方无隶属或授权关系。\n" +
                        "2. 非商业开源许可（PolyForm Noncommercial 1.0.0）：源码公开免费供个人自用与安全审计，严禁任何商业盈利、付费分发、植入广告或冒充原作者重打包。\n" +
                        "3. 零后端零中转：无自建服务器、无广告统计 SDK、不收集/不上传学号密码，教务登录仅在系统原生 WebView 中通过严格 HTTPS 直连 *.gdou.edu.cn，纯只读无选课/抢课/后台爬虫。\n" +
                        "4. 本地隔离：所有数据仅存于本机私有目录，已禁用系统云备份与换机导出；换号或借出设备前可随时点击下方按钮彻底清除登录态。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onLogoutJwSession,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("退出教务账号并清除浏览器缓存")
                }
            }
        }
    }
    }

    // Visual Material 3 Calendar DatePicker for Week 1 Monday
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

    if (showShareCodeDialog) {
        ScheduleShareCodeDialog(
            shareCode = onGetShareCode(),
            onCopyCode = onCopyShareCode,
            onImportCode = onImportShareCode,
            onDismiss = { showShareCodeDialog = false }
        )
    }

    if (showOemGuideDialog) {
        OemKeepAliveGuideDialog(
            onDismiss = { showOemGuideDialog = false }
        )
    }
}

private data class SigCardStyle(
    val bg: androidx.compose.ui.graphics.Color,
    val fg: androidx.compose.ui.graphics.Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val desc: String
)

@Composable
private fun HaiyuChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
            }
        } else null,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
        ),
        label = {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false
            )
        }
    )
}

@Composable
private fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
private fun SettingSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
