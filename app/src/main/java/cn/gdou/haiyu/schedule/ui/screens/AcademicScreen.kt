package cn.gdou.haiyu.schedule.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AssignmentTurnedIn
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.gdou.haiyu.schedule.data.GdouTimeRules
import cn.gdou.haiyu.schedule.model.AppDataSnapshot
import cn.gdou.haiyu.schedule.model.ExamItem
import cn.gdou.haiyu.schedule.model.GradeItem
import cn.gdou.haiyu.schedule.model.TodoTaskItem
import cn.gdou.haiyu.schedule.ui.components.QuickAddTaskDialog
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcademicScreen(
    snapshot: AppDataSnapshot,
    requestedSubTab: Int = 0,
    subTabRequestNonce: Int = 0,
    onJumpToSync: () -> Unit,
    onAddTask: (TodoTaskItem) -> Unit,
    onToggleTaskDone: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onClearCompletedTasks: () -> Unit,
    onAddGrade: (GradeItem) -> Unit,
    onDeleteGrade: (String) -> Unit,
    onAddExam: (ExamItem) -> Unit,
    onDeleteExam: (String) -> Unit
) {
    var tabIndex by remember { mutableIntStateOf(requestedSubTab.coerceIn(0, 2)) } // 0 = Tasks DDL, 1 = Grades & GPA, 2 = Exams
    LaunchedEffect(subTabRequestNonce) {
        if (subTabRequestNonce > 0) {
            tabIndex = requestedSubTab.coerceIn(0, 2)
        }
    }

    var showAddTaskDialog by remember { mutableStateOf(false) }
    var showAddGradeDialog by remember { mutableStateOf(false) }
    var showAddExamDialog by remember { mutableStateOf(false) }

    val pendingTaskCount = remember(snapshot.tasks) { snapshot.tasks.count { !it.isDone } }
    val courseNames = remember(snapshot.courses) { snapshot.courses.map { it.name }.distinct() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
        ) {
            // Top Header with 3-Segment Switch
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "学业中心",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    when (tabIndex) {
                                        0 -> showAddTaskDialog = true
                                        1 -> showAddGradeDialog = true
                                        else -> showAddExamDialog = true
                                    }
                                }
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    when (tabIndex) {
                                        0 -> "记 DDL"
                                        1 -> "模拟算分"
                                        else -> "加考试"
                                    }
                                )
                            }
                            TextButton(onClick = onJumpToSync) {
                                Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("同步")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = tabIndex == 0,
                            onClick = { tabIndex = 0 },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                        ) {
                            Text(
                                text = if (pendingTaskCount > 0) "作业($pendingTaskCount)" else "作业 DDL",
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        SegmentedButton(
                            selected = tabIndex == 1,
                            onClick = { tabIndex = 1 },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                        ) {
                            Text(
                                text = "成绩(${snapshot.grades.size})",
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        SegmentedButton(
                            selected = tabIndex == 2,
                            onClick = { tabIndex = 2 },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                        ) {
                            Text(
                                text = "考试(${snapshot.exams.size})",
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            when (tabIndex) {
                0 -> TasksContent(
                    tasks = snapshot.tasks,
                    onAddClick = { showAddTaskDialog = true },
                    onToggleDone = onToggleTaskDone,
                    onDeleteTask = onDeleteTask,
                    onClearCompleted = onClearCompletedTasks
                )

                1 -> GradesContent(
                    grades = snapshot.grades,
                    onJumpToSync = onJumpToSync,
                    onDeleteGrade = onDeleteGrade
                )

                else -> ExamsContent(
                    exams = snapshot.exams,
                    onJumpToSync = onJumpToSync,
                    onDeleteExam = onDeleteExam
                )
            }
        }
    }

    if (showAddTaskDialog) {
        QuickAddTaskDialog(
            initialCourseName = "",
            availableCourses = courseNames,
            onDismiss = { showAddTaskDialog = false },
            onSave = { task ->
                onAddTask(task)
                showAddTaskDialog = false
            }
        )
    }

    if (showAddGradeDialog) {
        AddSimulatedGradeDialog(
            onDismiss = { showAddGradeDialog = false },
            onSave = {
                onAddGrade(it)
                showAddGradeDialog = false
            }
        )
    }

    if (showAddExamDialog) {
        val defaultExamLoc = when (snapshot.settings.campus) {
            cn.gdou.haiyu.schedule.model.GdouCampus.HUGUANG -> "湖光校区广学楼"
            cn.gdou.haiyu.schedule.model.GdouCampus.HAIBIN -> "海滨校区教学楼"
            cn.gdou.haiyu.schedule.model.GdouCampus.YANGJIANG -> "阳江校区慎思楼"
        }
        AddExamDialog(
            defaultLocation = defaultExamLoc,
            onDismiss = { showAddExamDialog = false },
            onSave = {
                onAddExam(it)
                showAddExamDialog = false
            }
        )
    }
}

@Composable
private fun TasksContent(
    tasks: List<TodoTaskItem>,
    onAddClick: () -> Unit,
    onToggleDone: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onClearCompleted: () -> Unit
) {
    if (tasks.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.AssignmentTurnedIn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("暂无待办作业与实验 DDL", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "随时记下实验报告、课后作业或 PPT 汇报截止时间，也可以直接在课表点任意课程一键添加；临近截止前会在课表顶部贴心提醒你。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onAddClick, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("记一条作业 DDL")
            }
        }
        return
    }

    val today = remember { LocalDate.now() }
    val pendingCount = remember(tasks) { tasks.count { !it.isDone } }
    val doneCount = remember(tasks) { tasks.count { it.isDone } }

    val sortedTasks = remember(tasks) {
        tasks.sortedWith(
            compareBy<TodoTaskItem> { it.isDone }
                .thenBy { it.dueDate.ifBlank { "9999-12-31" } }
        )
    }

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
                    text = "待完成 $pendingCount 项 · 已完成 $doneCount 项",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (doneCount > 0) {
                    TextButton(
                        onClick = onClearCompleted,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Rounded.CleaningServices, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("清理已完成", fontSize = 12.sp)
                    }
                }
            }
        }

        items(sortedTasks, key = { it.id }) { task ->
            val dueDateObj = remember(task.dueDate) {
                try {
                    if (task.dueDate.isNotBlank()) LocalDate.parse(task.dueDate) else null
                } catch (_: Exception) {
                    null
                }
            }
            val daysLeft = remember(dueDateObj, today) {
                dueDateObj?.let { ChronoUnit.DAYS.between(today, it) }
            }
            val (badgeText, badgeBg, badgeFg) = when {
                task.isDone -> Triple("已完成", MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant)
                daysLeft == null -> Triple("无期限", MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant)
                daysLeft < 0L -> Triple("逾期${-daysLeft}天", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                daysLeft == 0L -> Triple("今天截止", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                daysLeft == 1L -> Triple("明天截止", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                daysLeft <= 3L -> Triple("剩${daysLeft}天", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                else -> Triple("剩${daysLeft}天", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (task.isDone) MaterialTheme.colorScheme.surfaceContainerLow
                    else MaterialTheme.colorScheme.surfaceContainerLowest
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (task.isDone) 0.72f else 1f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleDone(task.id) }
                        .padding(start = 6.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = task.isDone,
                        onCheckedChange = { onToggleDone(task.id) }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleSmall,
                            textDecoration = if (task.isDone) TextDecoration.LineThrough else TextDecoration.None,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (task.courseName.isNotBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = task.courseName.take(12),
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            if (task.dueDate.isNotBlank()) {
                                Text(
                                    text = task.dueDate,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (task.note.isNotBlank()) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = task.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = badgeBg,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeFg,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    IconButton(
                        onClick = { onDeleteTask(task.id) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "删除任务",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GradesContent(
    grades: List<GradeItem>,
    onJumpToSync: () -> Unit,
    onDeleteGrade: (String) -> Unit
) {
    if (grades.isEmpty()) {
        EmptyAcademicHint(
            title = "暂无离线成绩记录",
            desc = "前往「教务」页登录广海正方教务后点击「一键全同步」，即可离线查看全部成绩并自动计算加权 GPA；也可以点右上角「+ 模拟算分」手动测算。",
            onJumpToSync = onJumpToSync
        )
        return
    }

    val termTags = remember(grades) {
        listOf("全部学期") + grades.map { "${it.academicYear} ${it.semester}" }.distinct().sortedDescending()
    }
    var selectedTerm by remember(termTags) { mutableStateOf("全部学期") }
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(grades, selectedTerm, searchQuery) {
        grades.filter { g ->
            val termMatch = selectedTerm == "全部学期" || "${g.academicYear} ${g.semester}" == selectedTerm
            val queryMatch = searchQuery.isBlank() ||
                g.courseName.contains(searchQuery.trim(), ignoreCase = true) ||
                g.courseType.contains(searchQuery.trim(), ignoreCase = true)
            termMatch && queryMatch
        }
    }

    val stats = remember(filtered) {
        var totalCredits = 0.0
        var weightedGp = 0.0
        var weightedScore = 0.0
        var numericCredits = 0.0
        filtered.forEach { g ->
            if (g.credit > 0) {
                totalCredits += g.credit
                weightedGp += g.gradePoint * g.credit
                val numeric = parseScoreNumeric(g.score)
                if (numeric != null) {
                    weightedScore += numeric * g.credit
                    numericCredits += g.credit
                }
            }
        }
        val gpa = if (totalCredits > 0) weightedGp / totalCredits else 0.0
        val avgScore = if (numericCredits > 0) weightedScore / numericCredits else 0.0
        Triple(gpa, avgScore, totalCredits)
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 84.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // GPA Gradient Summary Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                        )
                    )
                    .padding(18.dp)
            ) {
                Column {
                    Text(
                        text = if (selectedTerm == "全部学期") "累计加权平均绩点 (GPA)" else "$selectedTerm · GPA",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.2f", stats.first),
                            color = Color.White,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            StatSmallColumn("加权均分", String.format(Locale.US, "%.1f", stats.second))
                            StatSmallColumn("总学分", String.format(Locale.US, "%.1f", stats.third))
                            StatSmallColumn("门数", "${filtered.size}")
                        }
                    }
                }
            }
        }

        // Term filter chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                termTags.forEach { tag ->
                    FilterChip(
                        selected = selectedTerm == tag,
                        onClick = { selectedTerm = tag },
                        label = { Text(tag, fontSize = 12.sp) }
                    )
                }
            }
        }

        // Search Box
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索课程名或课程性质...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        items(filtered, key = { it.id }) { grade ->
            val numeric = parseScoreNumeric(grade.score)
            val scoreColor = when {
                numeric == null -> MaterialTheme.colorScheme.primary
                numeric >= 90 -> Color(0xFF0E8A5F)
                numeric >= 80 -> Color(0xFF1A6FD4)
                numeric >= 60 -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.error
            }
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = grade.courseName,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (grade.isSimulated) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "模拟",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = buildString {
                                append("${grade.academicYear} ${grade.semester}")
                                if (grade.courseType.isNotBlank()) append(" · ${grade.courseType}")
                                if (grade.credit > 0) append(" · ${grade.credit}学分")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = grade.score.ifBlank { "--" },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = scoreColor
                        )
                        Text(
                            text = "绩点 ${String.format(Locale.US, "%.2f", grade.gradePoint)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (grade.isSimulated) {
                        IconButton(
                            onClick = { onDeleteGrade(grade.id) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = "删除模拟成绩",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatSmallColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.End) {
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
    }
}

@Composable
private fun ExamsContent(
    exams: List<ExamItem>,
    onJumpToSync: () -> Unit,
    onDeleteExam: (String) -> Unit
) {
    if (exams.isEmpty()) {
        EmptyAcademicHint(
            title = "暂无考试安排",
            desc = "前往「教务」页点击「一键全同步」可自动获取学校公布的考试时间与座位号，或点右上角「+ 加考试」手动添加四六级、期末考倒计时。",
            onJumpToSync = onJumpToSync
        )
        return
    }

    val now = remember { LocalDateTime.now() }
    val sortedExams = remember(exams, now) {
        exams.sortedWith(
            compareBy<ExamItem> {
                val dt = GdouTimeRules.parseExamStartDateTime(it.examTimeRaw)
                if (dt == null) 1 else if (dt.isBefore(now)) 2 else 0
            }.thenBy {
                GdouTimeRules.parseExamStartDateTime(it.examTimeRaw)?.toString() ?: "9999"
            }
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 84.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(sortedExams, key = { it.id }) { exam ->
            val dt = remember(exam.examTimeRaw) { GdouTimeRules.parseExamStartDateTime(exam.examTimeRaw) }
            val daysLeft = remember(dt) {
                dt?.let { ChronoUnit.DAYS.between(LocalDate.now(), it.toLocalDate()) }
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
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Countdown Badge
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = when {
                            isPast -> MaterialTheme.colorScheme.surfaceContainerHigh
                            daysLeft != null && daysLeft <= 3 -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.primaryContainer
                        },
                        modifier = Modifier.width(68.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                        ) {
                            when {
                                isPast -> {
                                    Text("已考完", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = exam.courseName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Event, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = exam.examTimeRaw.ifBlank { "时间未公布" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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

                    IconButton(
                        onClick = { onDeleteExam(exam.id) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "删除考试",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyAcademicHint(
    title: String,
    desc: String,
    onJumpToSync: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.School,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onJumpToSync, shape = RoundedCornerShape(12.dp)) {
            Text("前往广海教务同步")
        }
    }
}

@Composable
private fun AddSimulatedGradeDialog(
    onDismiss: () -> Unit,
    onSave: (GradeItem) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var score by remember { mutableStateOf("90") }
    var credit by remember { mutableStateOf("3.0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("模拟算分（测算 GPA）") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("课程名称 *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = score,
                        onValueChange = { score = it },
                        label = { Text("预计分数") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = credit,
                        onValueChange = { credit = it },
                        label = { Text("学分") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = "自动按广海 5 分制折算绩点（如 90分 = 4.0，85分 = 3.5，低于 60 = 0）。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isNotEmpty()) {
                        val s = score.toDoubleOrNull() ?: 80.0
                        val c = credit.toDoubleOrNull() ?: 2.0
                        val gp = if (s < 60.0) 0.0 else ((s - 50.0) / 10.0).coerceAtLeast(1.0)
                        onSave(
                            GradeItem(
                                academicYear = "模拟测算",
                                semester = "自定义",
                                courseName = trimmed,
                                score = score.trim(),
                                gradePoint = gp,
                                credit = c,
                                courseType = "模拟课程",
                                isSimulated = true
                            )
                        )
                    }
                }
            ) { Text("加入计算") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun AddExamDialog(
    defaultLocation: String,
    onDismiss: () -> Unit,
    onSave: (ExamItem) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var timeRaw by remember { mutableStateOf("${LocalDate.now().plusDays(7)}(09:00-11:00)") }
    var location by remember { mutableStateOf(defaultLocation) }
    var seat by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加自定义考试倒计时") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("考试科目（如 英语四级 / 期末考）*") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = timeRaw,
                    onValueChange = { timeRaw = it },
                    label = { Text("考试时间（YYYY-MM-DD(HH:MM-HH:MM)）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("考场地点") },
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
                        onSave(
                            ExamItem(
                                courseName = trimmed,
                                examTimeRaw = timeRaw.trim(),
                                location = location.trim(),
                                seatNumber = seat.trim(),
                                examType = "自定义考试",
                                isManual = true
                            )
                        )
                    }
                }
            ) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun parseScoreNumeric(score: String): Double? {
    score.trim().toDoubleOrNull()?.let { return it }
    return when (score.trim()) {
        "优秀", "优" -> 95.0
        "良好", "良" -> 85.0
        "中等", "中" -> 75.0
        "及格", "合格", "通过" -> 65.0
        "不及格", "不合格", "缺考" -> 0.0
        else -> null
    }
}
