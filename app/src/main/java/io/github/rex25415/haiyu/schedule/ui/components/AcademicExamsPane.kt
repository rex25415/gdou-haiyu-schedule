package io.github.rex25415.haiyu.schedule.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rex25415.haiyu.schedule.data.GdouTimeRules
import io.github.rex25415.haiyu.schedule.model.ExamItem
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

internal data class PreparedExamItem(
    val exam: ExamItem,
    val startDateTime: LocalDateTime?
)

internal fun sortAcademicExams(exams: List<ExamItem>, now: LocalDateTime): List<PreparedExamItem> {
    return exams.map { ex ->
        PreparedExamItem(ex, GdouTimeRules.parseExamStartDateTime(ex.examTimeRaw))
    }.sortedWith(
        compareBy<PreparedExamItem> {
            val dt = it.startDateTime
            if (dt == null) 1 else if (dt.isBefore(now)) 2 else 0
        }.thenByDescending {
            it.exam.isPinned
        }.thenBy {
            it.startDateTime?.toString() ?: "9999"
        }
    )
}

@Composable
internal fun AcademicExamsPane(
    exams: List<ExamItem>,
    now: LocalDateTime,
    onJumpToSync: () -> Unit,
    onEditExam: (ExamItem) -> Unit,
    onDeleteExam: (String) -> Unit,
    onToggleExamPin: (String) -> Unit,
    onCopyExamsText: () -> Unit
) {
    if (exams.isEmpty()) {
        EmptyAcademicHint(
            title = "暂无考试或校园倒数日",
            desc = "前往「教务」页点击「一键全同步」可自动获取学校公布的考试时间与座位号，或点右上角「+ 加倒计时」添加四六级、体测、放假倒计时。",
            onJumpToSync = onJumpToSync
        )
        return
    }

    val today = now.toLocalDate()
    val sortedExams = remember(exams, now) { sortAcademicExams(exams, now) }

    LazyColumn(
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 84.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "共 ${sortedExams.size} 项 · 点图钉可置顶到桌面倒计时组件首行",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onCopyExamsText,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("复制清单", fontSize = 12.sp)
                }
            }
        }
        items(sortedExams, key = { it.exam.id }) { prepared ->
            val exam = prepared.exam
            val dt = prepared.startDateTime
            val daysLeft = remember(dt, today) {
                dt?.let { ChronoUnit.DAYS.between(today, it.toLocalDate()) }
            }
            val isPast = dt != null && dt.isBefore(now)

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isPast) MaterialTheme.colorScheme.surfaceContainerLow
                    else MaterialTheme.colorScheme.surfaceContainerLowest
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = when {
                            isPast -> MaterialTheme.colorScheme.surfaceContainerHigh
                            daysLeft != null && daysLeft <= 3 -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.primaryContainer
                        },
                        modifier = Modifier.width(60.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                        ) {
                            when {
                                isPast -> {
                                    Text("已结束", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                daysLeft == null -> {
                                    Text("待定", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                daysLeft == 0L -> {
                                    Text("今天", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onErrorContainer)
                                    Text("加油!", fontSize = 10.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                                else -> {
                                    Text(
                                        text = "$daysLeft",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (daysLeft <= 3) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "天后",
                                        fontSize = 10.sp,
                                        color = if (daysLeft <= 3) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = exam.courseName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (exam.isPinned) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "置顶",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Event, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(4.dp))
                            val prettyTime = exam.examTimeRaw
                                .ifBlank { "时间未公布" }
                                .replace("(", " ")
                                .replace("（", " ")
                                .replace(")", "")
                                .replace("）", "")
                                .trim()
                            Text(
                                text = prettyTime,
                                fontSize = 11.5.sp,
                                lineHeight = 15.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (exam.location.isNotBlank() || exam.seatNumber.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = buildString {
                                        append(exam.location.ifBlank { "考场待定" })
                                        if (exam.seatNumber.isNotBlank()) append(" · ${exam.seatNumber}号座")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { onToggleExamPin(exam.id) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Rounded.PushPin,
                            contentDescription = if (exam.isPinned) "取消置顶" else "置顶到小组件首行",
                            tint = if (exam.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { onEditExam(exam) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = "编辑",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { onDeleteExam(exam.id) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddExamDialog(
    defaultLocation: String,
    existingExam: ExamItem? = null,
    onDismiss: () -> Unit,
    onSave: (ExamItem) -> Unit
) {
    val fmt = remember { DateTimeFormatter.ISO_LOCAL_DATE }
    var name by remember(existingExam) { mutableStateOf(existingExam?.courseName.orEmpty()) }
    var examType by remember(existingExam) { mutableStateOf(existingExam?.examType ?: "期末考试") }
    var timeRaw by remember(existingExam) {
        mutableStateOf(existingExam?.examTimeRaw?.ifBlank { "${LocalDate.now().plusDays(7)}(09:00-11:00)" } ?: "${LocalDate.now().plusDays(7)}(09:00-11:00)")
    }
    var location by remember(existingExam, defaultLocation) {
        mutableStateOf(existingExam?.location ?: defaultLocation)
    }
    var seat by remember(existingExam) { mutableStateOf(existingExam?.seatNumber.orEmpty()) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingExam == null) "添加考试 / 校园倒数日" else "编辑考试与倒数日") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (existingExam == null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        listOf(
                            Triple("英语四级", "考证倒计时", defaultLocation),
                            Triple("英语六级", "考证倒计时", defaultLocation),
                            Triple("放假倒计时", "放假倒数日", ""),
                            Triple("体能测试", "体测倒计时", "田径场"),
                            Triple("计算机二级", "考证倒计时", defaultLocation),
                            Triple("考研初试", "考试倒计时", "")
                        ).forEach { (presetName, presetType, presetLoc) ->
                            val sel = name == presetName
                            Surface(
                                color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        name = presetName
                                        examType = presetType
                                        location = presetLoc
                                    }
                            ) {
                                Text(
                                    text = presetName,
                                    fontSize = 11.sp,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称（如 英语四级 / 放假倒计时）*") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("日期与时段", style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("从日历选日期", fontSize = 12.sp)
                    }
                }
                OutlinedTextField(
                    value = timeRaw,
                    onValueChange = { timeRaw = it },
                    label = { Text("时间（YYYY-MM-DD(HH:MM-HH:MM)）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("地点 / 考场（放假等倒数日可留空）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = seat,
                    onValueChange = { seat = it },
                    label = { Text("座位号（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isNotEmpty()) {
                        val saved = if (existingExam != null) {
                            existingExam.copy(
                                courseName = trimmed,
                                examTimeRaw = timeRaw.trim(),
                                location = location.trim(),
                                seatNumber = seat.trim(),
                                examType = examType,
                                isDemo = false
                            )
                        } else {
                            ExamItem(
                                courseName = trimmed,
                                examTimeRaw = timeRaw.trim(),
                                location = location.trim(),
                                seatNumber = seat.trim(),
                                examType = examType,
                                isManual = true
                            )
                        }
                        onSave(saved)
                    }
                }
            ) { Text(if (existingExam == null) "添加" else "保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )

    if (showDatePicker) {
        val initDate = GdouTimeRules.parseExamStartDateTime(timeRaw)?.toLocalDate() ?: LocalDate.now().plusDays(7)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                Button(
                    onClick = {
                        state.selectedDateMillis?.let { ms ->
                            val picked = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().format(fmt)
                            val timePart = Regex("\\([^)]*\\)").find(timeRaw)?.value ?: "(09:00-11:00)"
                            timeRaw = "$picked$timePart"
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
