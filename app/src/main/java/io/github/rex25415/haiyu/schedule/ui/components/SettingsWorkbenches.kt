package io.github.rex25415.haiyu.schedule.ui.components

import android.appwidget.AppWidgetProvider
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rex25415.haiyu.schedule.BuildConfig
import io.github.rex25415.haiyu.schedule.model.AppSettings
import io.github.rex25415.haiyu.schedule.model.AppThemePalette
import io.github.rex25415.haiyu.schedule.model.DarkModeOption
import io.github.rex25415.haiyu.schedule.update.AppReleaseInfo
import io.github.rex25415.haiyu.schedule.update.AppUpdateManager
import io.github.rex25415.haiyu.schedule.update.SignatureTrustState
import io.github.rex25415.haiyu.schedule.update.SignatureVerificationResult
import io.github.rex25415.haiyu.schedule.widget.CompactSquareWidgetProvider
import io.github.rex25415.haiyu.schedule.widget.CountdownWidgetProvider
import io.github.rex25415.haiyu.schedule.widget.NextClassWidgetProvider
import io.github.rex25415.haiyu.schedule.widget.PinWidgetOutcome
import io.github.rex25415.haiyu.schedule.widget.TodayScheduleWidgetProvider
import io.github.rex25415.haiyu.schedule.widget.WidgetScheduleHelper
import kotlin.math.roundToInt

@Composable
internal fun HaiyuCompactSegmentButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) null else BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.height(36.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
            }
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun SettingSwitchRow(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceWorkbenchSheet(
    settings: AppSettings,
    isImportingWallpaper: Boolean = false,
    wallpaperStatus: Pair<Boolean, String>? = null,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onPickWallpaper: () -> Unit,
    onPickWallpaperFromFiles: () -> Unit = {},
    onClearWallpaper: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("主题装扮与课表壁纸", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("配色方案", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppThemePalette.entries.forEach { p ->
                        HaiyuCompactSegmentButton(
                            label = p.label,
                            selected = settings.themePalette == p,
                            modifier = Modifier.weight(1f),
                            onClick = { onUpdateSettings { it.copy(themePalette = p) } }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("深浅模式", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DarkModeOption.entries.forEach { m ->
                        HaiyuCompactSegmentButton(
                            label = m.label,
                            selected = settings.darkMode == m,
                            modifier = Modifier.weight(1f),
                            onClick = { onUpdateSettings { it.copy(darkMode = m) } }
                        )
                    }
                }
            }

            SettingSwitchRow(
                title = "课表顶部使用微缩单行预告条",
                subtitle = "开启后收起大渐变卡片，仅保留一行极简「下一节 + 状态徽章」，为周课表留出更多空间",
                checked = settings.compactScheduleBanner,
                onCheckedChange = { v -> onUpdateSettings { it.copy(compactScheduleBanner = v) } }
            )

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("自定义课表壁纸（零权限）", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "精选喜欢的风景或纯色图作为周课表底图，图片仅压缩保存在本机私有目录，不申请系统相册权限。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val hasWallpaper = settings.scheduleBgFileName.isNotBlank()
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalButton(
                        onClick = onPickWallpaper,
                        enabled = !isImportingWallpaper,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isImportingWallpaper) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("正在导入处理...", fontSize = 12.5.sp)
                        } else {
                            Icon(Icons.Rounded.Wallpaper, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (hasWallpaper) "更换课表壁纸" else "选择课表壁纸", fontSize = 12.5.sp)
                        }
                    }
                    if (hasWallpaper) {
                        OutlinedButton(
                            onClick = onClearWallpaper,
                            enabled = !isImportingWallpaper,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.HideImage, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("恢复默认", fontSize = 12.5.sp)
                        }
                    }
                }
                OutlinedButton(
                    onClick = onPickWallpaperFromFiles,
                    enabled = !isImportingWallpaper,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "相册找不到或导入异常？改用系统文件管理器选图",
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (wallpaperStatus != null) {
                    val (isOk, msg) = wallpaperStatus
                    Surface(
                        color = if (isOk) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        } else {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = if (isOk) Icons.Rounded.Check else Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = if (isOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = msg,
                                fontSize = 11.5.sp,
                                color = if (isOk) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                if (hasWallpaper) {
                    var sliderAlpha by remember(settings.courseCardAlpha) {
                        mutableFloatStateOf(settings.courseCardAlpha.coerceIn(0.62f, 1.0f))
                    }
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
    }
}

private data class DesktopWidgetCatalogEntry(
    val title: String,
    val sizeBadge: String,
    val subtitle: String,
    val requestCode: Int,
    val providerClass: Class<out AppWidgetProvider>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetsBackupWorkbenchSheet(
    onRefreshWidget: () -> Unit,
    onExportIcs: () -> Unit,
    onExportFullBackup: () -> Unit,
    onLaunchBackupRestore: () -> Unit,
    onLoadDemoData: () -> Unit,
    onRequestClearData: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var pinFeedback by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val widgetCatalog = remember {
        listOf(
            DesktopWidgetCatalogEntry(
                title = "极简方卡",
                sizeBadge = "2×2",
                subtitle = "半屏小卡：状态、课程名、教室与时间",
                requestCode = 104,
                providerClass = CompactSquareWidgetProvider::class.java
            ),
            DesktopWidgetCatalogEntry(
                title = "下一节课",
                sizeBadge = "4×1",
                subtitle = "单行极简胶囊，不占桌面高度",
                requestCode = 103,
                providerClass = NextClassWidgetProvider::class.java
            ),
            DesktopWidgetCatalogEntry(
                title = "今日课表",
                sizeBadge = "4×2",
                subtitle = "今日全部课程列表与明日早课预告",
                requestCode = 101,
                providerClass = TodayScheduleWidgetProvider::class.java
            ),
            DesktopWidgetCatalogEntry(
                title = "考试与 DDL",
                sizeBadge = "4×2",
                subtitle = "考场座号、置顶倒数与待办截止",
                requestCode = 102,
                providerClass = CountdownWidgetProvider::class.java
            )
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Widgets, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("桌面小组件与数据备份", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("桌面微件支持（4 款深浅自适应）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = "可直接点右侧「放到桌面」快捷添置；若部分桌面拦截，也可长按桌面空白处 →「微件 / 小组件」手动添加。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        widgetCatalog.forEach { entry ->
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = entry.title,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = entry.sizeBadge,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = entry.subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            val outcome = WidgetScheduleHelper.requestPinWidget(
                                                context = context,
                                                providerClass = entry.providerClass,
                                                requestCode = entry.requestCode
                                            )
                                            pinFeedback = when (outcome) {
                                                PinWidgetOutcome.REQUESTED ->
                                                    true to "已向桌面发起添置「${entry.title}（${entry.sizeBadge}）」，请在系统弹窗确认。"
                                                PinWidgetOutcome.UNSUPPORTED_LAUNCHER,
                                                PinWidgetOutcome.FAILED ->
                                                    false to "当前桌面暂不支持应用内添置，请长按桌面空白处 →「小组件」查找「海屿${entry.title}」。"
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("放到桌面", fontSize = 11.5.sp, maxLines = 1, softWrap = false)
                                    }
                                }
                            }
                        }
                    }
                    pinFeedback?.let { (isOk, msg) ->
                        Surface(
                            color = if (isOk) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.78f)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = msg,
                                fontSize = 11.5.sp,
                                color = if (isOk) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilledTonalButton(
                            onClick = onRefreshWidget,
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("刷新全部小组件", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        FilledTonalButton(
                            onClick = onExportIcs,
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("导出 .ics 日历", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("换机迁移与本地备份", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = "全量备份包含课表、作业 DDL、成绩、考试与个性化设置（不含学号密码）；提示：课表高清长图与同班分享码请在「课表 → 右上工具箱」使用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilledTonalButton(
                            onClick = onExportFullBackup,
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Backup, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("导出全量备份", fontSize = 12.sp)
                        }
                        FilledTonalButton(
                            onClick = onLaunchBackupRestore,
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("从备份恢复", fontSize = 12.sp)
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { onDismiss(); onLoadDemoData() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("加载示例课表", fontSize = 12.5.sp)
                }
                OutlinedButton(
                    onClick = { onDismiss(); onRequestClearData() },
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
                    Text("清空数据", color = MaterialTheme.colorScheme.error, fontSize = 12.5.sp)
                }
            }
        }
    }
}

private data class WorkbenchSigStyle(
    val bg: Color,
    val fg: Color,
    val icon: ImageVector,
    val title: String,
    val desc: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutUpdateWorkbenchSheet(
    settings: AppSettings,
    signatureInfo: SignatureVerificationResult,
    availableUpdate: AppReleaseInfo?,
    isCheckingUpdate: Boolean,
    updateStatusSummary: String?,
    onCheckForUpdates: () -> Unit,
    onOpenUpdateDialog: () -> Unit,
    onOpenShareApp: () -> Unit,
    onOpenFeedback: () -> Unit,
    onOpenGitHubRepo: () -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onLogoutJwSession: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("应用更新、开源与隐私合规", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "海屿空间 v${BuildConfig.VERSION_NAME}（build ${BuildConfig.VERSION_CODE}）",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = updateStatusSummary
                            ?: if (availableUpdate != null) "可升级至最新版本 v${availableUpdate.versionName}"
                            else "采用海屿开源统一发布签名打包，覆盖升级自动保留全部数据。上线初期集中补核心功能与修紧急 Bug 曾高频连发，现核心已稳，转为每周 1～2 更（教务改版或重大故障随时加急修复）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (availableUpdate != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                if (availableUpdate != null) onOpenUpdateDialog()
                                else onCheckForUpdates()
                            },
                            enabled = !isCheckingUpdate,
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isCheckingUpdate) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(15.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("检查中...", fontSize = 12.sp)
                            } else {
                                Text(
                                    text = if (availableUpdate != null) "更新至 v${availableUpdate.versionName}" else "检查新版本",
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                        FilledTonalButton(
                            onClick = { onDismiss(); onOpenShareApp() },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("分享给同学", fontSize = 12.5.sp)
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { onDismiss(); onOpenFeedback() },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.BugReport, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("反馈与诊断", fontSize = 12.5.sp)
                        }
                        OutlinedButton(
                            onClick = { onDismiss(); onOpenGitHubRepo() },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("GitHub 开源", fontSize = 12.5.sp)
                        }
                    }
                    SettingSwitchRow(
                        title = "启动时自动检查更新",
                        subtitle = "每隔 12 小时静默检查 GitHub Releases，支持国内多线加速与 SHA-256 校验",
                        checked = settings.autoCheckUpdates,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(autoCheckUpdates = v) } }
                    )
                    SettingSwitchRow(
                        title = "接收抢先体验版（Beta）",
                        subtitle = "正式版通常每周 1～2 更；开启后可提前接收 GitHub Pre-release 预览版，关闭则仅接收正式版",
                        checked = settings.receiveBetaUpdates,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(receiveBetaUpdates = v) } }
                    )
                }
            }

            val sigStyle = when (signatureInfo.state) {
                SignatureTrustState.VERIFIED_ORIGINAL -> WorkbenchSigStyle(
                    bg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                    fg = MaterialTheme.colorScheme.onPrimaryContainer,
                    icon = Icons.Rounded.VerifiedUser,
                    title = "安装包签名验真通过（海屿空间原版构建）",
                    desc = "证书指纹：${signatureInfo.shortFingerprint}\n包名：${BuildConfig.APPLICATION_ID}"
                )
                SignatureTrustState.DEBUG_LOCAL_BUILD -> WorkbenchSigStyle(
                    bg = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                    fg = MaterialTheme.colorScheme.onSecondaryContainer,
                    icon = Icons.Rounded.Code,
                    title = "本地开发编译构建（Debug）",
                    desc = "证书指纹：${signatureInfo.shortFingerprint} · 源码自行编译版本"
                )
                SignatureTrustState.THIRD_PARTY_MODIFIED -> WorkbenchSigStyle(
                    bg = MaterialTheme.colorScheme.errorContainer,
                    fg = MaterialTheme.colorScheme.onErrorContainer,
                    icon = Icons.Rounded.WarningAmber,
                    title = "警告：非原作者发布签名（疑似第三方重签）",
                    desc = "指纹 ${signatureInfo.shortFingerprint} 与原版不符，请前往开源主页下载纯净原版。"
                )
            }
            Surface(
                color = sigStyle.bg,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = sigStyle.icon,
                        contentDescription = null,
                        tint = sigStyle.fg,
                        modifier = Modifier.size(18.dp).padding(top = 1.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(sigStyle.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = sigStyle.fg)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(sigStyle.desc, style = MaterialTheme.typography.labelSmall, color = sigStyle.fg.copy(alpha = 0.88f))
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("PolyForm 1.0.0 非商业开源与纯本地声明", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            Text(
                text = "• 非校方官方独立工具 · 零后端零中转 · 无广告统计 SDK；\n" +
                    "• 所有学号/Cookie 仅留存于系统 WebView 与本机私有目录，已禁用云端备份；支持随时退出并彻底清除登录态。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = { onDismiss(); onLogoutJwSession() },
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
