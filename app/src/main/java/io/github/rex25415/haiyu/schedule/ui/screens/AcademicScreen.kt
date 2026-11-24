package io.github.rex25415.haiyu.schedule.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AssignmentTurnedIn
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.rex25415.haiyu.schedule.model.AppDataSnapshot
import io.github.rex25415.haiyu.schedule.model.ExamItem
import io.github.rex25415.haiyu.schedule.model.GradeItem
import io.github.rex25415.haiyu.schedule.model.GraduationCreditTargets
import io.github.rex25415.haiyu.schedule.model.TodoTaskItem
import io.github.rex25415.haiyu.schedule.ui.components.QuickAddTaskDialog
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcademicScreen(
    snapshot: AppDataSnapshot,
    isDark: Boolean = false,
    requestedSubTab: Int = -1,
    subTabRequestNonce: Int = 0,
    lastGradeSkipReason: String? = null,
    onSubTabChanged: (Int) -> Unit = {},
    onJumpToSync: () -> Unit,
    onAddTask: (TodoTaskItem) -> Unit,
    onToggleTaskDone: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onClearCompletedTasks: () -> Unit,
    onAddGrade: (GradeItem) -> Unit,
    onUpdateGrade: (GradeItem) -> Unit = {},
    onUpdateCreditTargets: (GraduationCreditTargets) -> Unit = {},
    onDeleteGrade: (String) -> Unit,
    onAddExam: (ExamItem) -> Unit,
    onDeleteExam: (String) -> Unit,
    onToggleExamPin: (String) -> Unit = {},
    onCopyExamsText: () -> Unit = {}
) {
    val smartDefaultTab = remember(snapshot.tasks, snapshot.grades.size, snapshot.exams.size) {
        val pending = snapshot.tasks.count { !it.isDone }
        when {
            pending > 0 -> 0
            snapshot.grades.isNotEmpty() -> 1
            snapshot.exams.isNotEmpty() -> 2
            else -> 0
        }
    }
    var tabIndex by remember {
        mutableIntStateOf(if (requestedSubTab in 0..2) requestedSubTab else smartDefaultTab)
    } // 0 = Tasks DDL, 1 = Grades & GPA, 2 = Exams
    LaunchedEffect(subTabRequestNonce) {
        if (subTabRequestNonce > 0 && requestedSubTab in 0..2) {
            tabIndex = requestedSubTab
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
                            onClick = { tabIndex = 0; onSubTabChanged(0) },
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
                            onClick = { tabIndex = 1; onSubTabChanged(1) },
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
                            onClick = { tabIndex = 2; onSubTabChanged(2) },
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

                1 -> io.github.rex25415.haiyu.schedule.ui.components.AcademicGradesPane(
                    grades = snapshot.grades,
                    isDark = isDark,
                    levelExams = snapshot.levelExams,
                    practiceCourses = snapshot.practiceCourses,
                    creditTargets = snapshot.settings.creditTargets,
                    lastGradeSkipReason = lastGradeSkipReason,
                    onJumpToSync = onJumpToSync,
                    onUpdateGrade = onUpdateGrade,
                    onUpdateCreditTargets = onUpdateCreditTargets,
                    onDeleteGrade = onDeleteGrade
                )

                else -> io.github.rex25415.haiyu.schedule.ui.components.AcademicExamsPane(
                    exams = snapshot.exams,
                    now = nowDateTime.withSecond(0).withNano(0),
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
        io.github.rex25415.haiyu.schedule.ui.components.AddSimulatedGradeDialog(
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
        io.github.rex25415.haiyu.schedule.ui.components.AddExamDialog(
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
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Text(
                                        text = task.courseName.take(12),
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            if (task.dueDate.isNotBlank()) {
                                Text(
                                    text = task.dueDate,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    softWrap = false,
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
                            maxLines = 1,
                            softWrap = false,
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
