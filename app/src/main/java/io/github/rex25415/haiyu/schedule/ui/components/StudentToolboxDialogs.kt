package io.github.rex25415.haiyu.schedule.ui.components

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rex25415.haiyu.schedule.data.GdouTimeRules
import io.github.rex25415.haiyu.schedule.model.GdouCampus
import io.github.rex25415.haiyu.schedule.model.TodoTaskItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * 广海三校区标准 & 错峰作息速查表
 */
@Composable
fun BellScheduleReferenceDialog(
    initialCampus: GdouCampus,
    onDismiss: () -> Unit
) {
    var selectedCampus by remember { mutableStateOf(initialCampus) }
    val slots = remember(selectedCampus) { GdouTimeRules.slotsForCampus(selectedCampus) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("广海作息时间速查表") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    GdouCampus.entries.forEach { campus ->
                        FilterChip(
                            selected = selectedCampus == campus,
                            onClick = { selectedCampus = campus },
                            label = { Text(campus.label.removeSuffix("校区"), fontSize = 12.sp) }
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "${selectedCampus.label}错峰提示",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = selectedCampus.shortDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Table header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("节次", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(42.dp))
                    Text("常规作息", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("错峰作息", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider()

                slots.forEach { slot ->
                    val staggeredNote = when (selectedCampus) {
                        GdouCampus.HAIBIN, GdouCampus.HUGUANG -> when (slot.number) {
                            3 -> "3-4连 10:05起"
                            4 -> "至 11:35"
                            7 -> "7-8连 16:25起"
                            8 -> "至 17:50"
                            else -> "同常规"
                        }
                        GdouCampus.YANGJIANG -> when (slot.number) {
                            3 -> "非慎思 10:10起"
                            4 -> "至 11:40"
                            else -> "同常规"
                        }
                    }
                    val isStaggered = staggeredNote != "同常规"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "第${slot.number}节",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(48.dp)
                        )
                        Text(
                            text = "${slot.startTime} - ${slot.endTime}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            color = if (isStaggered) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = staggeredNote,
                                fontSize = 11.sp,
                                fontWeight = if (isStaggered) FontWeight.Bold else FontWeight.Normal,
                                color = if (isStaggered) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier.padding(horizontal = if (isStaggered) 6.dp else 0.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (slot.number == 4 || slot.number == 8) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("知道了") }
        }
    )
}

/**
 * 同班/室友课表分享码互传弹窗（一键复制 / 粘贴导入，纯离线无账号隐私）
 */
@Composable
fun ScheduleShareCodeDialog(
    shareCode: String?,
    onCopyCode: (String) -> Unit,
    onImportCode: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var pasteInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("同班课表互传（分享码）") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("发给室友 / 同班同学", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "仅导出当前课表与校区作息，不含学号密码等任何隐私，发到微信/QQ即可让对方秒导课表。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = { shareCode?.let(onCopyCode) },
                            enabled = !shareCode.isNullOrBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (shareCode.isNullOrBlank()) "暂无课程可分享" else "复制我的课表分享码")
                        }
                    }
                }

                HorizontalDivider()

                Text("导入同学发来的分享码", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = pasteInput,
                    onValueChange = { pasteInput = it },
                    placeholder = { Text("粘贴海屿课表分享码...") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipText = clipboard.getText()?.text.orEmpty()
                            if (clipText.isNotBlank()) pasteInput = clipText
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("粘贴剪贴板", fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            if (pasteInput.isNotBlank()) {
                                onImportCode(pasteInput)
                                onDismiss()
                            }
                        },
                        enabled = pasteInput.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("一键导入", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

/**
 * 快捷记录或编辑作业 / 实验报告 DDL 弹窗（支持可视化日历选截止日期与课程关联）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddTaskDialog(
    initialCourseName: String = "",
    availableCourses: List<String> = emptyList(),
    existingTask: TodoTaskItem? = null,
    onDismiss: () -> Unit,
    onSave: (TodoTaskItem) -> Unit
) {
    val today = remember { LocalDate.now() }
    val fmt = remember { DateTimeFormatter.ISO_LOCAL_DATE }
    var title by remember(existingTask) { mutableStateOf(existingTask?.title.orEmpty()) }
    var courseName by remember(initialCourseName, existingTask) {
        mutableStateOf(existingTask?.courseName ?: initialCourseName)
    }
    var dueDate by remember(existingTask) {
        mutableStateOf(existingTask?.dueDate?.ifBlank { today.plusDays(2).format(fmt) } ?: today.plusDays(2).format(fmt))
    }
    var note by remember(existingTask) { mutableStateOf(existingTask?.note.orEmpty()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    val quickDates = remember(today) {
        listOf(
            "今天" to today.format(fmt),
            "明天" to today.plusDays(1).format(fmt),
            "3天后" to today.plusDays(3).format(fmt),
            "下周" to today.plusDays(7).format(fmt)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingTask == null) "记一条作业 / 实验 DDL" else "编辑作业 / 实验 DDL") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; errorMsg = null },
                    label = { Text("任务内容（如 实验报告 / 课后习题）*") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = courseName,
                    onValueChange = { courseName = it },
                    label = { Text("所属课程（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (availableCourses.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableCourses.take(12).forEach { cName ->
                            val selected = courseName == cName
                            FilterChip(
                                selected = selected,
                                onClick = { courseName = if (selected) "" else cName },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null,
                                label = { Text(cName.take(10), fontSize = 11.sp) }
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("截止日期", style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("从日历选", fontSize = 12.sp)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickDates.forEach { (label, dateStr) ->
                        val selected = dueDate == dateStr
                        FilterChip(
                            selected = selected,
                            onClick = { dueDate = dateStr },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null,
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
                OutlinedTextField(
                    value = dueDate,
                    onValueChange = { dueDate = it },
                    label = { Text("截止日期 (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（如 学习通提交 / 纸质版交学委）") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = title.trim()
                    if (trimmed.isEmpty()) {
                        errorMsg = "请先填写作业/任务内容"
                    } else {
                        val saved = if (existingTask != null) {
                            existingTask.copy(
                                title = trimmed,
                                courseName = courseName.trim(),
                                dueDate = dueDate.trim(),
                                note = note.trim(),
                                isDemo = false
                            )
                        } else {
                            TodoTaskItem(
                                title = trimmed,
                                courseName = courseName.trim(),
                                dueDate = dueDate.trim(),
                                note = note.trim()
                            )
                        }
                        onSave(saved)
                    }
                }
            ) { Text("保存 DDL") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )

    if (showDatePicker) {
        val initDate = try {
            LocalDate.parse(dueDate.trim())
        } catch (_: Exception) {
            today.plusDays(2)
        }
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                Button(
                    onClick = {
                        state.selectedDateMillis?.let { ms ->
                            val picked = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                            dueDate = picked.format(fmt)
                        }
                        showDatePicker = false
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

/**
 * 全机型 / 国产 ROM（小米、OPPO、vivo、华为鸿蒙4、荣耀、三星）课前提醒与桌面小组件保活指南
 */
@Composable
fun OemKeepAliveGuideDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val canExactAlarm = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            am?.canScheduleExactAlarms() ?: true
        } else {
            true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("多品牌手机提醒与小组件保活指南") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = if (canExactAlarm) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    else MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (canExactAlarm) "精准闹钟权限：已开启（支持准点课前提醒）"
                            else "精准闹钟权限：暂未授权（可能导致提醒延迟数分钟）",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (canExactAlarm) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onErrorContainer
                        )
                        if (!canExactAlarm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Spacer(modifier = Modifier.height(8.dp))
                            FilledTonalButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Rounded.AlarmOn, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("去开启精准闹钟权限", fontSize = 12.sp)
                            }
                        }
                    }
                }

                Text(
                    text = "国产手机系统默认会限制后台自启动和省电策略，如遇到课前提醒没响或桌面小组件未自动刷新，建议按机型开启「允许自启动」并设为「无限制省电」：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val tips = listOf(
                    "小米 / 红米 (HyperOS)" to "应用信息 → 省电策略选「无限制」+ 开启「自启动」",
                    "OPPO / 一加 / 真我 (ColorOS)" to "应用信息 → 耗电管理 → 允许「完全后台行为」与「自启动」",
                    "vivo / iQOO (OriginOS)" to "应用信息 → 电池 → 后台耗电管理选「允许后台高耗电」",
                    "华为 (鸿蒙4) / 荣耀 (MagicOS)" to "应用启动管理 → 关掉「自动管理」，勾选「允许自启动/后台活动」",
                    "三星 (One UI)" to "应用信息 → 电池 → 选「不受限制」"
                )
                tips.forEach { (brand, step) ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(brand, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(step, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.SettingsApplications, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("直达本应用系统权限 / 电池页", fontSize = 12.5.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("知道了") }
        }
    )
}
