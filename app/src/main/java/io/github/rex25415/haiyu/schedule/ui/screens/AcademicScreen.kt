package io.github.rex25415.haiyu.schedule.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rex25415.haiyu.schedule.data.GdouTimeRules
import io.github.rex25415.haiyu.schedule.model.AppDataSnapshot
import io.github.rex25415.haiyu.schedule.model.ExamItem
import io.github.rex25415.haiyu.schedule.model.GradeItem
import io.github.rex25415.haiyu.schedule.model.TodoTaskItem
import io.github.rex25415.haiyu.schedule.ui.components.QuickAddTaskDialog
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcademicScreen(
    snapshot: AppDataSnapshot,
    requestedSubTab: Int = 0,
    subTabRequestNonce: Int = 0,
    lastGradeSkipReason: String? = null,
    onJumpToSync: () -> Unit,
    onAddTask: (TodoTaskItem) -> Unit,
    onToggleTaskDone: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onClearCompletedTasks: () -> Unit,
    onAddGrade: (GradeItem) -> Unit,
    onDeleteGrade: (String) -> Unit,
    onAddExam: (ExamItem) -> Unit,
    onDeleteExam: (String) -> Unit,
    onToggleExamPin: (String) -> Unit = {},
    onCopyExamsText: () -> Unit = {}
) {
    var tabIndex by remember { mutableIntStateOf(requestedSubTab.coerceIn(0, 2)) } // 0 = Tasks DDL, 1 = Grades & GPA, 2 = Exams
    LaunchedEffect(subTabRequestNonce) {
        if (subTabRequestNonce > 0) {
            tabIndex = requestedSubTab.coerceIn(0, 2)
        }
    }

    // Auto-refresh local date/time on ON_RESUME and every minute so exam & DDL countdowns tick over midnight automatically!
    val lifecycleOwner = LocalLifecycleOwner.current
    var nowDateTime by remember { mutableStateOf(LocalDateTime.now()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                nowDateTime = LocalDateTime.now()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            nowDateTime = LocalDateTime.now()
        }
    }

    var showAddTaskDialog by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<TodoTaskItem?>(null) }
    var showAddGradeDialog by remember { mutableStateOf(false) }
    var showAddExamDialog by remember { mutableStateOf(false) }
    var editingExam by remember { mutableStateOf<ExamItem?>(null) }

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
                                        0 -> {
                                            editingTask = null
                                            showAddTaskDialog = true
                                        }
                                        1 -> showAddGradeDialog = true
                                        else -> {
                                            editingExam = null
                                            showAddExamDialog = true
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    when (tabIndex) {
                                        0 -> "记 DDL"
                                        1 -> "模拟算分"
                                        else -> "加倒计时"
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
                    today = nowDateTime.toLocalDate(),
                    onAddClick = {
                        editingTask = null
                        showAddTaskDialog = true
                    },
                    onEditTask = { task ->
                        editingTask = task
                        showAddTaskDialog = true
                    },
                    onToggleDone = onToggleTaskDone,
                    onDeleteTask = onDeleteTask,
                    onClearCompleted = onClearCompletedTasks
                )

                1 -> GradesContent(
                    grades = snapshot.grades,
                    lastGradeSkipReason = lastGradeSkipReason,
                    onJumpToSync = onJumpToSync,
                    onDeleteGrade = onDeleteGrade
                )

                else -> ExamsContent(
                    exams = snapshot.exams,
                    now = nowDateTime,
                    onJumpToSync = onJumpToSync,
                    onEditExam = { exam ->
                        editingExam = exam
                        showAddExamDialog = true
                    },
                    onDeleteExam = onDeleteExam,
                    onToggleExamPin = onToggleExamPin,
                    onCopyExamsText = onCopyExamsText
                )
            }
        }
    }

    if (showAddTaskDialog) {
        QuickAddTaskDialog(
            initialCourseName = "",
            availableCourses = courseNames,
            existingTask = editingTask,
            onDismiss = {
                showAddTaskDialog = false
                editingTask = null
            },
            onSave = { task ->
                onAddTask(task)
                showAddTaskDialog = false
                editingTask = null
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
            io.github.rex25415.haiyu.schedule.model.GdouCampus.HUGUANG -> "湖光校区广学楼"
            io.github.rex25415.haiyu.schedule.model.GdouCampus.HAIBIN -> "海滨校区教学楼"
            io.github.rex25415.haiyu.schedule.model.GdouCampus.YANGJIANG -> "阳江校区慎思楼"
        }
        AddExamDialog(
            defaultLocation = defaultExamLoc,
            existingExam = editingExam,
            onDismiss = {
                showAddExamDialog = false
                editingExam = null
            },
            onSave = {
                onAddExam(it)
                showAddExamDialog = false
                editingExam = null
            }
        )
    }
}

@Composable
private fun TasksContent(
    tasks: List<TodoTaskItem>,
    today: LocalDate,
    onAddClick: () -> Unit,
    onEditTask: (TodoTaskItem) -> Unit,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
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
                        onClick = { onEditTask(task) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = "编辑任务",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { onDeleteTask(task.id) },
                        modifier = Modifier.size(30.dp)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GradesContent(
    grades: List<GradeItem>,
    lastGradeSkipReason: String?,
    onJumpToSync: () -> Unit,
    onDeleteGrade: (String) -> Unit
) {
    if (grades.isEmpty()) {
        EmptyAcademicHint(
            title = "暂无离线成绩记录",
            desc = "前往「教务」页登录广海正方教务后点击「一键全同步」，即可离线查看全部成绩并自动计算加权 GPA；也可以点右上角「+ 模拟算分」手动测算。",
            extraNotice = lastGradeSkipReason?.takeIf { it.isNotBlank() }?.let { reason ->
                "上次同步成绩提示：$reason。若教务首页有未读系统通知弹窗，先点击「查看详情 / 已阅读」后再点同步成绩即可。"
            },
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

    // Chronological per-term GPA trend & credit breakdown by course category (pure local math!)
    val termGpaSeries = remember(grades) {
        grades
            .filter { it.credit > 0.0 }
            .groupBy { "${it.academicYear} ${it.semester}" }
            .entries
            .sortedBy { (k, _) -> k }
            .mapNotNull { (termKey, list) ->
                val cr = list.sumOf { it.credit }
                if (cr <= 0.0) null else {
                    val gp = list.sumOf { it.gradePoint * it.credit } / cr
                    val shortLabel = termKey
                        .replace("20", "")
                        .replace(" 第一学期", "①")
                        .replace(" 第二学期", "②")
                        .replace(" 第三学期", "③")
                    shortLabel to gp
                }
            }
    }
    val creditByCategory = remember(filtered) {
        filtered
            .filter { it.credit > 0.0 }
            .groupBy { g ->
                val raw = g.courseType.trim()
                when {
                    raw.isBlank() -> "其他课程"
                    raw.contains("必修") -> "必修课"
                    raw.contains("选修") || raw.contains("任选") || raw.contains("限选") -> "选修课"
                    raw.contains("实践") || raw.contains("实验") -> "实践环节"
                    raw.contains("通识") || raw.contains("公选") -> "通识/公选"
                    else -> raw.take(6)
                }
            }
            .map { (cat, list) -> Triple(cat, list.sumOf { it.credit }, list.size) }
            .sortedByDescending { it.second }
    }
    var showInsightsCard by remember { mutableStateOf(false) }
    var targetGpaInput by remember { mutableStateOf("3.5") }
    var futureCreditsInput by remember { mutableStateOf("20") }
    var showLowScoreList by remember { mutableStateOf(false) }

    val allTermStats = remember(grades) {
        var totalCr = 0.0
        var weightedGp = 0.0
        grades.forEach { g ->
            if (g.credit > 0.0) {
                totalCr += g.credit
                weightedGp += g.gradePoint * g.credit
            }
        }
        val gpa = if (totalCr > 0.0) weightedGp / totalCr else 0.0
        gpa to totalCr
    }
    val lowScoreCourses = remember(grades) {
        grades.filter { g ->
            g.credit > 0.0 && !g.isSimulated && (
                g.gradePoint <= 0.0 || (parseScoreNumeric(g.score)?.let { it < 60.0 } == true)
            )
        }
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

        // Collapsible GPA Trend, Credit Breakdown & Target GPA Reverse Planner (clean & zero clutter by default!)
        if (termGpaSeries.size >= 2 || creditByCategory.isNotEmpty() || allTermStats.second > 0.0) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showInsightsCard = !showInsightsCard },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Insights,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "学分分布、GPA 走势与目标反推",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (showInsightsCard) "收起" else "展开查看",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = if (showInsightsCard) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (showInsightsCard) {
                            if (termGpaSeries.size >= 2) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "各学期 GPA 趋势（共 ${termGpaSeries.size} 学期）",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                val lineColor = MaterialTheme.colorScheme.primary
                                val dotFill = MaterialTheme.colorScheme.surface
                                val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                val minGpa = (termGpaSeries.minOf { it.second } - 0.35).coerceAtLeast(0.0)
                                val maxGpa = (termGpaSeries.maxOf { it.second } + 0.35).coerceAtMost(5.0)
                                val span = (maxGpa - minGpa).coerceAtLeast(0.5)

                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(82.dp)
                                        .padding(vertical = 8.dp)
                                ) {
                                    val w = size.width
                                    val h = size.height
                                    // Subtle baseline & midline
                                    drawLine(gridColor, Offset(0f, h), Offset(w, h), strokeWidth = 1.5f)
                                    drawLine(gridColor, Offset(0f, h * 0.5f), Offset(w, h * 0.5f), strokeWidth = 1f)

                                    val colW = w / termGpaSeries.size.coerceAtLeast(1)
                                    val pts = termGpaSeries.mapIndexed { idx, (_, gp) ->
                                        val x = colW * (idx + 0.5f)
                                        val ratio = ((gp - minGpa) / span).toFloat().coerceIn(0f, 1f)
                                        val y = h * (1f - ratio)
                                        Offset(x, y)
                                    }
                                    val path = Path().apply {
                                        pts.forEachIndexed { idx, pt ->
                                            if (idx == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                                        }
                                    }
                                    drawPath(
                                        path = path,
                                        color = lineColor,
                                        style = Stroke(width = 5f, cap = StrokeCap.Round)
                                    )
                                    pts.forEach { pt ->
                                        drawCircle(color = lineColor, radius = 9f, center = pt)
                                        drawCircle(color = dotFill, radius = 4.5f, center = pt)
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    termGpaSeries.forEach { (label, gp) ->
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = String.format(Locale.US, "%.2f", gp),
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                            Text(
                                                text = label,
                                                fontSize = 9.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            if (creditByCategory.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                val creditedCount = remember(creditByCategory) { creditByCategory.sumOf { it.third } }
                                Text(
                                    text = "已修学分分类统计（计学分共 ${creditedCount} 门）",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    creditByCategory.forEach { (cat, cr, count) ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "$cat ${String.format(Locale.US, "%.1f", cr)}分 · ${count}门",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Subtle low-score / retake reminder (only when present, collapsible, zero pressure)
                            if (lowScoreCourses.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showLowScoreList = !showLowScoreList }
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "学业体检：${lowScoreCourses.size} 门课程成绩待留意（补考/重修参考）",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = if (showLowScoreList) "收起" else "查看",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                        if (showLowScoreList) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = lowScoreCourses.joinToString(" · ") {
                                                    "${it.courseName}(${it.score.ifBlank { "0" }}/${it.credit}分)"
                                                },
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                                            )
                                        }
                                    }
                                }
                            }

                            // Target GPA Reverse Calculator (pure local offline math!)
                            if (allTermStats.second > 0.0) {
                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "目标 GPA 反推测算（按全部已修 ${String.format(Locale.US, "%.1f", allTermStats.second)} 学分）",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                ) {
                                    listOf("2.5", "3.0", "3.5", "3.8", "4.0").forEach { preset ->
                                        val sel = targetGpaInput.trim() == preset
                                        Surface(
                                            color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { targetGpaInput = preset }
                                        ) {
                                            Text(
                                                text = "目标 $preset",
                                                fontSize = 11.sp,
                                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                                color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = targetGpaInput,
                                        onValueChange = { targetGpaInput = it.filter { ch -> ch.isDigit() || ch == '.' }.take(4) },
                                        label = { Text("目标总绩点", fontSize = 11.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = futureCreditsInput,
                                        onValueChange = { futureCreditsInput = it.filter { ch -> ch.isDigit() || ch == '.' }.take(5) },
                                        label = { Text("后续预计学分", fontSize = 11.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                val targetVal = targetGpaInput.toDoubleOrNull()
                                val futureCrVal = futureCreditsInput.toDoubleOrNull()
                                if (targetVal != null && futureCrVal != null && futureCrVal > 0.0) {
                                    val plan = remember(allTermStats, targetVal, futureCrVal) {
                                        GdouTimeRules.calculateTargetGpaPlan(
                                            currentGpa = allTermStats.first,
                                            currentCredits = allTermStats.second,
                                            targetGpa = targetVal,
                                            plannedFutureCredits = futureCrVal
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        color = if (plan.achievable) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                        else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = plan.summaryText,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (plan.achievable) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
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
                    val selected = selectedTerm == tag
                    FilterChip(
                        selected = selected,
                        onClick = { selectedTerm = tag },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        label = {
                            Text(
                                tag,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
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
    val sortedExams = remember(exams, now) {
        exams.sortedWith(
            compareBy<ExamItem> {
                val dt = GdouTimeRules.parseExamStartDateTime(it.examTimeRaw)
                if (dt == null) 1 else if (dt.isBefore(now)) 2 else 0
            }.thenByDescending {
                it.isPinned
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
        items(sortedExams, key = { it.id }) { exam ->
            val dt = remember(exam.examTimeRaw) { GdouTimeRules.parseExamStartDateTime(exam.examTimeRaw) }
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

                    Spacer(modifier = Modifier.width(12.dp))

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
                            Icon(Icons.Rounded.Event, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = exam.examTimeRaw.ifBlank { "时间未公布" },
                                style = MaterialTheme.typography.bodySmall,
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

@Composable
private fun EmptyAcademicHint(
    title: String,
    desc: String,
    extraNotice: String? = null,
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
        if (!extraNotice.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.78f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(16.dp).padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = extraNotice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExamDialog(
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
