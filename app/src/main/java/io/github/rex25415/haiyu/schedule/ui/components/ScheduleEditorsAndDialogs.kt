package io.github.rex25415.haiyu.schedule.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rex25415.haiyu.schedule.data.GdouTimeRules
import io.github.rex25415.haiyu.schedule.data.ResolvedCourseTime
import io.github.rex25415.haiyu.schedule.model.CourseItem
import io.github.rex25415.haiyu.schedule.model.GdouCampus
import io.github.rex25415.haiyu.schedule.ui.theme.coursePaletteFor
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseSearchBottomSheet(
    courses: List<CourseItem>,
    resolvedTimeMap: Map<String, ResolvedCourseTime>,
    campus: GdouCampus,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onSelectCourse: (CourseItem) -> Unit,
    onJumpToWeek: (Int) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var groupByCourse by remember { mutableStateOf(true) }
    val distinctCourseCount = remember(courses) { courses.map { it.name.trim() }.distinct().size }
    val totalCredits = remember(courses) {
        val creditMap = LinkedHashMap<String, Double>()
        courses.forEach { c ->
            val key = c.name.trim()
            val cr = c.credit.trim().toDoubleOrNull()
            if (cr != null && cr > 0.0 && key !in creditMap) {
                creditMap[key] = cr
            }
        }
        creditMap.values.sum()
    }
    val filtered = remember(courses, query) {
        val q = query.trim()
        val list = if (q.isEmpty()) {
            courses
        } else {
            courses.filter { c ->
                c.name.contains(q, ignoreCase = true) ||
                    c.teacher.contains(q, ignoreCase = true) ||
                    c.location.contains(q, ignoreCase = true)
            }
        }
        list.sortedWith(
            compareBy<CourseItem> { it.dayOfWeek }
                .thenBy { it.startSection }
                .thenBy { it.name }
        )
    }
    val groupedCourses = remember(filtered) {
        filtered.groupBy { it.name.trim() }.values.toList()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "本学期课程检索",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = buildString {
                            append("共 $distinctCourseCount 门课 · ${courses.size} 条排课")
                            if (totalCredits > 0.0) {
                                append(" · ${String.format(Locale.US, "%.1f", totalCredits)}学分")
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = groupByCourse,
                    onClick = { groupByCourse = true },
                    leadingIcon = if (groupByCourse) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    label = { Text("按课程聚合 (${groupedCourses.size}门)", fontSize = 11.5.sp, fontWeight = if (groupByCourse) FontWeight.Bold else FontWeight.Medium) }
                )
                FilterChip(
                    selected = !groupByCourse,
                    onClick = { groupByCourse = false },
                    leadingIcon = if (!groupByCourse) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    label = { Text("按排课展开 (${filtered.size}条)", fontSize = 11.5.sp, fontWeight = if (!groupByCourse) FontWeight.Bold else FontWeight.Medium) }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜课程名、任课教师或教室...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "未找到匹配的课程记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (groupByCourse) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 22.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                ) {
                    items(groupedCourses, key = { it.first().name.trim() }) { group ->
                        val head = group.first()
                        val pal = coursePaletteFor(head.colorIndex, isDark)
                        val allWeeks = group.flatMap { it.weeks }.distinct().sorted()
                        val firstWeek = allWeeks.firstOrNull() ?: 1
                        val teachers = group.mapNotNull { it.teacher.trim().takeIf(String::isNotBlank) }.distinct().joinToString("/")
                        val locations = group.mapNotNull { it.location.trim().takeIf(String::isNotBlank) }.distinct().take(2).joinToString("、")
                        val positiveCredit = head.credit.trim().toDoubleOrNull()?.takeIf { it > 0.0 }

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectCourse(head) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(pal.accent)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = head.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${GdouTimeRules.formatWeeksCompact(allWeeks)} · 共 ${group.size} 条排课",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = buildString {
                                            append(locations.ifBlank { "地点待定" })
                                            if (teachers.isNotBlank()) append(" · $teachers")
                                            if (positiveCredit != null) append(" · ${head.credit}学分")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onJumpToWeek(firstWeek) }
                                ) {
                                    Text(
                                        text = "去第${firstWeek}周",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 22.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                ) {
                    items(filtered, key = { it.id }) { c ->
                        val resolved = resolvedTimeMap[c.id] ?: GdouTimeRules.resolveCourseTime(c, campus)
                        val pal = coursePaletteFor(c.colorIndex, isDark)
                        val firstWeek = c.weeks.minOrNull() ?: 1
                        val positiveCredit = c.credit.trim().toDoubleOrNull()?.takeIf { it > 0.0 }
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectCourse(c) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(pal.accent)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = c.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${GdouTimeRules.dayOfWeekLabel(c.dayOfWeek)} 第${c.startSection}-${c.endSection}节 (${resolved.startTime}-${resolved.endTime}) · ${GdouTimeRules.formatWeeksCompact(c.weeks)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = buildString {
                                            append(c.location.ifBlank { "地点待定" })
                                            if (c.teacher.isNotBlank()) append(" · ${c.teacher}")
                                            if (positiveCredit != null) append(" · ${c.credit}学分")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onJumpToWeek(firstWeek) }
                                ) {
                                    Text(
                                        text = "第${firstWeek}周",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseEditorBottomSheet(
    initial: CourseItem?,
    defaultDay: Int,
    defaultStartSection: Int,
    totalWeeks: Int,
    onDismiss: () -> Unit,
    onSave: (CourseItem) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var location by remember { mutableStateOf(initial?.location ?: "") }
    var teacher by remember { mutableStateOf(initial?.teacher ?: "") }
    var dayOfWeek by remember { mutableIntStateOf(initial?.dayOfWeek ?: defaultDay) }
    var startSection by remember { mutableStateOf((initial?.startSection ?: defaultStartSection).toString()) }
    var endSection by remember {
        mutableStateOf((initial?.endSection ?: (defaultStartSection + 1).coerceAtMost(12)).toString())
    }
    var weeksInput by remember {
        mutableStateOf(
            if (initial != null) GdouTimeRules.formatWeeksCompact(initial.weeks).removeSuffix("周")
            else "1-16"
        )
    }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = if (initial == null) "添加课程" else "编辑课程",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("课程名称 *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("上课教室（含实验楼/广学楼等自动识别错峰）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("任课教师") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("星期", style = MaterialTheme.typography.labelMedium)
                DaySelectorRow(selectedDay = dayOfWeek, onSelect = { dayOfWeek = it })

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = startSection,
                        onValueChange = { startSection = it.filter(Char::isDigit) },
                        label = { Text("开始节(1-12)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endSection,
                        onValueChange = { endSection = it.filter(Char::isDigit) },
                        label = { Text("结束节(1-12)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = weeksInput,
                    onValueChange = { weeksInput = it },
                    label = { Text("周次（如 1-16、1-15(单)、1,3,5）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsg != null) {
                    Text(text = errorMsg!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) { Text("取消") }
                Button(
                    onClick = {
                        val trimmedName = name.trim()
                        val s = startSection.toIntOrNull()
                        val e = endSection.toIntOrNull()
                        val parsedWeeks = parseWeeksExpression(weeksInput, totalWeeks)
                        when {
                            trimmedName.isEmpty() -> errorMsg = "请填写课程名称"
                            s == null || e == null || s !in 1..12 || e !in s..12 -> errorMsg = "节次范围需在 1~12 之间且开始不大于结束"
                            parsedWeeks.isEmpty() -> errorMsg = "周次格式无效，示例：1-16 或 1-15(单)"
                            else -> {
                                onSave(
                                    (initial ?: CourseItem(
                                        name = trimmedName,
                                        dayOfWeek = dayOfWeek,
                                        startSection = s,
                                        endSection = e,
                                        weeks = parsedWeeks,
                                        isManual = true
                                    )).copy(
                                        name = trimmedName,
                                        location = location.trim(),
                                        teacher = teacher.trim(),
                                        dayOfWeek = dayOfWeek,
                                        startSection = s,
                                        endSection = e,
                                        weeks = parsedWeeks,
                                        note = note.trim(),
                                        customStartTime = null,
                                        customEndTime = null
                                    )
                                )
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("保存课程") }
            }
        }
    }
}

@Composable
fun SingleWeekRescheduleDialog(
    course: CourseItem,
    weekNumber: Int,
    onDismiss: () -> Unit,
    onConfirm: (newDay: Int, newStartSection: Int, newEndSection: Int, newLocation: String) -> Unit
) {
    var newDay by remember { mutableIntStateOf(course.dayOfWeek) }
    var startSec by remember { mutableStateOf(course.startSection.toString()) }
    var endSec by remember { mutableStateOf(course.endSection.toString()) }
    var location by remember { mutableStateOf(course.location) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("仅调第 $weekNumber 周（临时调课）") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "课程：${course.name}\n仅修改第 $weekNumber 周的上课时间或教室，其他周次保持原样不变。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("调整到星期几", style = MaterialTheme.typography.labelMedium)
                DaySelectorRow(selectedDay = newDay, onSelect = { newDay = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startSec,
                        onValueChange = { startSec = it.filter(Char::isDigit); errorMsg = null },
                        label = { Text("开始节次") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endSec,
                        onValueChange = { endSec = it.filter(Char::isDigit); errorMsg = null },
                        label = { Text("结束节次") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("调整后教室/地点（自动匹配错峰）") },
                    singleLine = true,
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
                    val s = startSec.toIntOrNull()
                    val e = endSec.toIntOrNull()
                    if (s == null || e == null || s !in 1..12 || e !in s..12) {
                        errorMsg = "节次范围需在 1~12 之间且开始不大于结束"
                    } else {
                        onConfirm(newDay, s, e, location.trim())
                    }
                }
            ) { Text("确认仅调本周") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun HolidayShiftDialog(
    currentWeek: Int,
    totalWeeks: Int,
    onDismiss: () -> Unit,
    onConfirm: (sourceWeek: Int, sourceDay: Int, targetWeek: Int, targetDay: Int, move: Boolean) -> Unit
) {
    var sourceWeek by remember { mutableStateOf(currentWeek.toString()) }
    var sourceDay by remember { mutableIntStateOf(3) }
    var targetWeek by remember { mutableStateOf(currentWeek.toString()) }
    var targetDay by remember { mutableIntStateOf(7) }
    var moveInsteadOfCopy by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("节假日一键调休") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "例：放假调休“周日补周三的课”，选择源周三 → 目标周日即可一键完成。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sourceWeek,
                        onValueChange = { sourceWeek = it.filter(Char::isDigit) },
                        label = { Text("源第几周") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = targetWeek,
                        onValueChange = { targetWeek = it.filter(Char::isDigit) },
                        label = { Text("目标第几周") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text("把哪一天的课调走（源星期）", style = MaterialTheme.typography.labelMedium)
                DaySelectorRow(selectedDay = sourceDay, onSelect = { sourceDay = it })
                Text("调到哪一天上课（目标星期）", style = MaterialTheme.typography.labelMedium)
                DaySelectorRow(selectedDay = targetDay, onSelect = { targetDay = it })
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("同时清空原日期当周排课", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = moveInsteadOfCopy, onCheckedChange = { moveInsteadOfCopy = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sw = (sourceWeek.toIntOrNull() ?: currentWeek).coerceIn(1, totalWeeks)
                    val tw = (targetWeek.toIntOrNull() ?: currentWeek).coerceIn(1, totalWeeks)
                    onConfirm(sw, sourceDay, tw, targetDay, moveInsteadOfCopy)
                }
            ) { Text("执行调休") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun DaySelectorRow(selectedDay: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        for (d in 1..7) {
            val selected = selectedDay == d
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest,
                border = if (selected) null else BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(d) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = GdouTimeRules.dayOfWeekLabel(d).removePrefix("周"),
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * 解析手动加课输入的周次表达式（支持 "1-16"、"1-15(单)"、"2,4,6-8" 等格式）。
 */
internal fun parseWeeksExpression(expr: String, maxWeeks: Int): List<Int> {
    val result = mutableListOf<Int>()
    val groups = expr.replace("，", ",").split(",")
    val upperLimit = maxWeeks.coerceAtLeast(30)
    for (g in groups) {
        val item = g.trim()
        val range = Regex("(\\d+)\\s*[-~～]\\s*(\\d+)").find(item)
        val single = Regex("(\\d+)").find(item)
        val odd = item.contains("单")
        val even = item.contains("双")
        if (range != null) {
            val s = range.groupValues[1].toIntOrNull() ?: continue
            val e = range.groupValues[2].toIntOrNull() ?: continue
            for (w in s.coerceAtLeast(1)..e.coerceAtMost(upperLimit)) {
                if (odd && w % 2 == 0) continue
                if (even && w % 2 == 1) continue
                result.add(w)
            }
        } else if (single != null) {
            val w = single.groupValues[1].toIntOrNull() ?: continue
            if (w in 1..upperLimit) result.add(w)
        }
    }
    return result.distinct().sorted()
}
