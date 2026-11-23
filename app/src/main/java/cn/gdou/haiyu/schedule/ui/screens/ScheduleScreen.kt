package cn.gdou.haiyu.schedule.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.gdou.haiyu.schedule.data.GdouTimeRules
import cn.gdou.haiyu.schedule.data.OccurrenceState
import cn.gdou.haiyu.schedule.model.AppDataSnapshot
import cn.gdou.haiyu.schedule.model.CourseItem
import cn.gdou.haiyu.schedule.model.GdouCampus
import cn.gdou.haiyu.schedule.model.TodoTaskItem
import cn.gdou.haiyu.schedule.ui.components.BellScheduleReferenceDialog
import cn.gdou.haiyu.schedule.ui.components.QuickAddTaskDialog
import cn.gdou.haiyu.schedule.ui.components.ScheduleShareCodeDialog
import cn.gdou.haiyu.schedule.ui.theme.coursePaletteFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    snapshot: AppDataSnapshot,
    isDark: Boolean,
    isRefreshingSchedule: Boolean,
    onQuickRefreshSchedule: () -> Unit,
    onJumpToSync: () -> Unit,
    onJumpToTasks: () -> Unit,
    onSelectCampus: (GdouCampus) -> Unit,
    onLoadDemoData: () -> Unit,
    onToggleShowNonCurrentWeek: () -> Unit,
    onSaveCourse: (CourseItem) -> Unit,
    onDeleteCourse: (String) -> Unit,
    onRescheduleSingleWeek: (courseId: String, weekNumber: Int, newDay: Int, newStartSection: Int, newEndSection: Int, newLocation: String) -> Unit,
    onAddTask: (TodoTaskItem) -> Unit,
    onExportWeekImage: (weekNumber: Int) -> Unit,
    onGetShareCode: () -> String?,
    onCopyShareCode: (String) -> Unit,
    onImportShareCode: (String) -> Unit,
    onApplyHolidayShift: (sourceWeek: Int, sourceDay: Int, targetWeek: Int, targetDay: Int, move: Boolean) -> Unit
) {
    val settings = snapshot.settings
    // Live 30s clock ticker so Hero countdown, Today pill, & current-section highlight stay accurate!
    var nowDateTime by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            nowDateTime = LocalDateTime.now()
        }
    }
    val today = nowDateTime.toLocalDate()
    val nowTime = nowDateTime.toLocalTime()
    val totalWeeks = settings.totalWeeks.coerceIn(1, 30)
    val actualCurrentWeek = remember(settings.semesterStartDate, totalWeeks, today) {
        GdouTimeRules.calculateCurrentWeek(settings.semesterStartDate, totalWeeks, today)
    }

    val pagerState = rememberPagerState(
        initialPage = (actualCurrentWeek - 1).coerceIn(0, totalWeeks - 1),
        pageCount = { totalWeeks }
    )
    val scope = rememberCoroutineScope()

    // Keep pager aligned if semesterStartDate changes externally
    LaunchedEffect(actualCurrentWeek) {
        val target = (actualCurrentWeek - 1).coerceIn(0, totalWeeks - 1)
        if (pagerState.currentPage != target) {
            pagerState.scrollToPage(target)
        }
    }

    val selectedWeek = (pagerState.currentPage + 1).coerceIn(1, totalWeeks)
    var isDayViewMode by remember { mutableStateOf(false) }
    var selectedDayOfWeek by remember { mutableIntStateOf(today.dayOfWeek.value) }
    var showWeekPicker by remember { mutableStateOf(false) }
    var showToolsMenu by remember { mutableStateOf(false) }
    var showBellRefDialog by remember { mutableStateOf(false) }
    var showShareCodeDialog by remember { mutableStateOf(false) }
    var detailCourse by remember { mutableStateOf<CourseItem?>(null) }
    var conflictSheetData by remember { mutableStateOf<Pair<Int, List<CourseItem>>?>(null) }
    var showAllWeekConflictsSheet by remember { mutableStateOf(false) }
    var singleWeekShiftCourse by remember { mutableStateOf<CourseItem?>(null) }
    var quickTaskPrefillCourse by remember { mutableStateOf<String?>(null) }
    var editingCourse by remember { mutableStateOf<CourseItem?>(null) }
    var showAddCourseSheet by remember { mutableStateOf(false) }
    var addPrefillDay by remember { mutableIntStateOf(today.dayOfWeek.value) }
    var addPrefillSection by remember { mutableIntStateOf(1) }
    var showHolidayShiftDialog by remember { mutableStateOf(false) }

    val weekConflictClusters = remember(snapshot.courses, selectedWeek) {
        GdouTimeRules.findConflictClustersForWeek(snapshot.courses, selectedWeek)
    }

    val todayStatuses = remember(snapshot.courses, settings.semesterStartDate, totalWeeks, settings.campus, nowDateTime) {
        GdouTimeRules.getTodayCoursesWithStatus(
            courses = snapshot.courses,
            startDateStr = settings.semesterStartDate,
            totalWeeks = totalWeeks,
            campus = settings.campus,
            now = nowDateTime
        )
    }

    val urgentTasks = remember(snapshot.tasks, today) {
        snapshot.tasks
            .filter { !it.isDone }
            .mapNotNull { task ->
                val d = try { LocalDate.parse(task.dueDate) } catch (_: Exception) { null }
                val days = d?.let { ChronoUnit.DAYS.between(today, it) }
                if (days != null && days <= 3L) task to days else null
            }
            .sortedBy { it.second }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Sleek Single-Tier Top Header Bar (reclaims ~44dp vertical space for timetable!)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showWeekPicker = true }
                                .padding(end = 2.dp)
                        ) {
                            Text(
                                text = "第 $selectedWeek 周",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false
                            )
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "切换教学周",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable { showBellRefDialog = true }
                        ) {
                            Text(
                                text = settings.campus.label.removeSuffix("校区"),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
                            )
                        }
                    }
                    Text(
                        text = if (selectedWeek == actualCurrentWeek) {
                            "${settings.academicYearLabel} · $actualCurrentWeek/${totalWeeks}周"
                        } else {
                            "当前第${actualCurrentWeek}周 · 预览第${selectedWeek}周"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (selectedWeek != actualCurrentWeek) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable {
                                    scope.launch {
                                        pagerState.animateScrollToPage((actualCurrentWeek - 1).coerceIn(0, totalWeeks - 1))
                                    }
                                }
                        ) {
                            Text(
                                text = "回本周",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(2.dp))
                    }
                    // Uniform 20.dp action icons with harmonious visual weight
                    IconButton(
                        onClick = { if (!isRefreshingSchedule) onQuickRefreshSchedule() },
                        modifier = Modifier.size(34.dp)
                    ) {
                        if (isRefreshingSchedule) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(17.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "一键刷新课表",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onToggleShowNonCurrentWeek,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (settings.showNonCurrentWeekCourses) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                            contentDescription = "显示/隐藏非本周课程",
                            tint = if (settings.showNonCurrentWeekCourses) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { isDayViewMode = !isDayViewMode },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (isDayViewMode) Icons.Rounded.GridView else Icons.AutoMirrored.Rounded.ViewList,
                            contentDescription = "切换周/日视图",
                            tint = if (isDayViewMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { showToolsMenu = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "课表更多工具",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showToolsMenu,
                            onDismissRequest = { showToolsMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("手动添加课程") },
                                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                                onClick = {
                                    showToolsMenu = false
                                    addPrefillDay = selectedDayOfWeek
                                    addPrefillSection = 1
                                    editingCourse = null
                                    showAddCourseSheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("生成本周高清课表图") },
                                leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null) },
                                onClick = {
                                    showToolsMenu = false
                                    onExportWeekImage(selectedWeek)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("节假日一键调休") },
                                leadingIcon = { Icon(Icons.Rounded.SwapHoriz, contentDescription = null) },
                                onClick = {
                                    showToolsMenu = false
                                    showHolidayShiftDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("广海作息时间速查表") },
                                leadingIcon = { Icon(Icons.Rounded.Schedule, contentDescription = null) },
                                onClick = {
                                    showToolsMenu = false
                                    showBellRefDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("同班课表分享码（互传）") },
                                leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                                onClick = {
                                    showToolsMenu = false
                                    showShareCodeDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }

        // Empty State Onboarding OR Active Schedule Views
        if (snapshot.courses.isEmpty()) {
            EmptyScheduleOnboarding(
                selectedCampus = settings.campus,
                onSelectCampus = onSelectCampus,
                onJumpToSync = onJumpToSync,
                onLoadDemoData = onLoadDemoData,
                onManualAdd = {
                    editingCourse = null
                    showAddCourseSheet = true
                }
            )
        } else {
            NextClassHeroBanner(
                todayStatuses = todayStatuses,
                isDark = isDark,
                onCourseClick = { detailCourse = it },
                onConflictClick = { overlapped ->
                    conflictSheetData = today.dayOfWeek.value to overlapped
                }
            )

            // Unified Smart Status Pill Row (compact side-by-side DDL + Conflict chips, zero vertical bloat!)
            if (urgentTasks.isNotEmpty() || weekConflictClusters.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (weekConflictClusters.isNotEmpty()) {
                        Surface(
                            color = if (isDark) Color(0xFF4A2516) else Color(0xFFFFEFE2),
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable { showAllWeekConflictsSheet = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WarningAmber,
                                    contentDescription = null,
                                    tint = if (isDark) Color(0xFFFFB085) else Color(0xFFC84B00),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${weekConflictClusters.size}处重课冲突",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDark) Color(0xFFFFD8C2) else Color(0xFF8C3200),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    if (urgentTasks.isNotEmpty()) {
                        val (topTask, daysLeft) = urgentTasks.first()
                        val dueTag = when {
                            daysLeft < 0L -> "逾期${-daysLeft}天"
                            daysLeft == 0L -> "今天截止"
                            daysLeft == 1L -> "明天截止"
                            else -> "${daysLeft}天后"
                        }
                        val isCritical = daysLeft <= 1L
                        Surface(
                            color = when {
                                isCritical && isDark -> Color(0xFF4E1B1A)
                                isCritical -> Color(0xFFFFE7E5)
                                else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.78f)
                            },
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable { onJumpToTasks() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Assignment,
                                    contentDescription = null,
                                    tint = when {
                                        isCritical && isDark -> Color(0xFFFFB4AB)
                                        isCritical -> Color(0xFFBA1A1A)
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = buildString {
                                        val raw = topTask.title.trim()
                                        val shortTitle = if (raw.length > 10) {
                                            raw.take(9).trimEnd('（', '(', '【', '[', ' ', '·') + "…"
                                        } else raw
                                        append("DDL $shortTitle · $dueTag")
                                        if (urgentTasks.size > 1) append(" +${urgentTasks.size - 1}")
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = when {
                                        isCritical && isDark -> Color(0xFFFFDAD6)
                                        isCritical -> Color(0xFF93000A)
                                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                                    },
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            if (isDayViewMode) {
                DayTimelineScheduleView(
                    courses = snapshot.courses,
                    selectedWeek = selectedWeek,
                    selectedDay = selectedDayOfWeek,
                    onSelectDay = { selectedDayOfWeek = it },
                    startDateStr = settings.semesterStartDate,
                    campus = settings.campus,
                    isDark = isDark,
                    onCourseClick = { detailCourse = it }
                )
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1
                ) { pageIdx ->
                    val pageWeek = pageIdx + 1
                    WeekGridScheduleView(
                        courses = snapshot.courses,
                        selectedWeek = pageWeek,
                        today = today,
                        nowTime = nowTime,
                        startDateStr = settings.semesterStartDate,
                        campus = settings.campus,
                        showWeekend = settings.showWeekend,
                        showNonCurrentWeek = settings.showNonCurrentWeekCourses,
                        isDark = isDark,
                        onCourseClick = { detailCourse = it },
                        onConflictClusterClick = { day, overlapped ->
                            conflictSheetData = day to overlapped
                        },
                        onEmptyCellClick = { day, section ->
                            addPrefillDay = day
                            addPrefillSection = section
                            editingCourse = null
                            showAddCourseSheet = true
                        }
                    )
                }
            }
        }
    }

    // Smart Week Picker Dialog (shows per-week course density at a glance!)
    if (showWeekPicker) {
        AlertDialog(
            onDismissRequest = { showWeekPicker = false },
            title = { Text("快速切换教学周") },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.height(250.dp)
                ) {
                    items(totalWeeks) { idx ->
                        val w = idx + 1
                        val isSelected = w == selectedWeek
                        val isNow = w == actualCurrentWeek
                        val weekCourseCount = remember(snapshot.courses, w) {
                            snapshot.courses.count { w in it.weeks }
                        }
                        Surface(
                            shape = RoundedCornerShape(11.dp),
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.primary
                                isNow -> MaterialTheme.colorScheme.primaryContainer
                                weekCourseCount == 0 -> MaterialTheme.colorScheme.surfaceContainerLow
                                else -> MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            modifier = Modifier
                                .height(50.dp)
                                .clickable {
                                    showWeekPicker = false
                                    scope.launch { pagerState.animateScrollToPage(w - 1) }
                                }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "$w",
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.onPrimary
                                        isNow -> MaterialTheme.colorScheme.onPrimaryContainer
                                        weekCourseCount == 0 -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                    fontWeight = if (isSelected || isNow) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.5.sp,
                                    lineHeight = 15.sp
                                )
                                Text(
                                    text = if (isNow) "本周" else if (weekCourseCount > 0) "${weekCourseCount}门" else "无课",
                                    fontSize = 8.5.sp,
                                    lineHeight = 10.sp,
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                                        isNow -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (selectedWeek != actualCurrentWeek) {
                    Button(
                        onClick = {
                            showWeekPicker = false
                            scope.launch { pagerState.animateScrollToPage(actualCurrentWeek - 1) }
                        }
                    ) { Text("回到本周") }
                } else {
                    TextButton(onClick = { showWeekPicker = false }) { Text("关闭") }
                }
            },
            dismissButton = {
                if (selectedWeek != actualCurrentWeek) {
                    TextButton(onClick = { showWeekPicker = false }) { Text("取消") }
                }
            }
        )
    }

    // Course Detail Bottom Sheet (Color-Matched Pastel Header!)
    val currentDetail = detailCourse
    if (currentDetail != null) {
        val resolved = GdouTimeRules.resolveCourseTime(currentDetail, settings.campus)
        val detailPalette = coursePaletteFor(currentDetail.colorIndex, isDark)
        ModalBottomSheet(
            onDismissRequest = { detailCourse = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 6.dp)
            ) {
                Surface(
                    color = detailPalette.container,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = currentDetail.name,
                                style = MaterialTheme.typography.titleLarge,
                                color = detailPalette.onContainer,
                                modifier = Modifier.weight(1f)
                            )
                            if (resolved.isSpecialStaggered) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = detailPalette.accent,
                                    shape = RoundedCornerShape(999.dp)
                                ) {
                                    Text(
                                        text = "错峰作息",
                                        color = if (isDark) Color(0xFF0A1614) else Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${GdouTimeRules.dayOfWeekLabel(currentDetail.dayOfWeek)} 第${currentDetail.startSection}-${currentDetail.endSection}节 · ${resolved.startTime}-${resolved.endTime}" +
                                if (currentDetail.credit.isNotBlank()) " · ${currentDetail.credit}学分" else "",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = detailPalette.onContainer.copy(alpha = 0.85f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // If this course conflicts with another course in selectedWeek, surface a helpful callout
                val peersInConflict = remember(snapshot.courses, currentDetail, selectedWeek) {
                    if (selectedWeek !in currentDetail.weeks) emptyList()
                    else snapshot.courses.filter { other ->
                        other.id != currentDetail.id &&
                            other.dayOfWeek == currentDetail.dayOfWeek &&
                            selectedWeek in other.weeks &&
                            other.startSection <= currentDetail.endSection &&
                            currentDetail.startSection <= other.endSection
                    }
                }
                if (peersInConflict.isNotEmpty()) {
                    Surface(
                        color = if (isDark) Color(0xFF4A2516) else Color(0xFFFFEFE2),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = if (isDark) Color(0xFFFFB085) else Color(0xFFC84B00),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "第${selectedWeek}周与「${peersInConflict.joinToString("、") { it.name }}」时间重叠，可点下方「仅调第${selectedWeek}周」错开。",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDark) Color(0xFFFFD8C2) else Color(0xFF8C3200)
                            )
                        }
                    }
                }

                DetailInfoRow(
                    icon = Icons.Rounded.AccessTime,
                    label = "上课时间",
                    value = "${GdouTimeRules.dayOfWeekLabel(currentDetail.dayOfWeek)} 第${currentDetail.startSection}-${currentDetail.endSection}节  (${resolved.startTime} - ${resolved.endTime})"
                )
                if (resolved.isSpecialStaggered && resolved.reason != null) {
                    Text(
                        text = "已自动命中：${resolved.reason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 32.dp, bottom = 8.dp)
                    )
                }
                DetailInfoRow(
                    icon = Icons.Rounded.LocationOn,
                    label = "上课地点",
                    value = currentDetail.location.ifBlank { "未标注地点" }
                )
                DetailInfoRow(
                    icon = Icons.Rounded.Person,
                    label = "任课教师",
                    value = currentDetail.teacher.ifBlank { "未标注教师" }
                )
                DetailInfoRow(
                    icon = Icons.Rounded.CalendarMonth,
                    label = "上课周次",
                    value = GdouTimeRules.formatWeeksCompact(currentDetail.weeks)
                )
                if (currentDetail.credit.isNotBlank()) {
                    DetailInfoRow(
                        icon = Icons.Rounded.EventRepeat,
                        label = "课程学分",
                        value = "${currentDetail.credit} 学分"
                    )
                }
                if (currentDetail.note.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "备注：${currentDetail.note}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                // Student-friendly Quick Actions: Single-Week Reschedule & Homework DDL
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (selectedWeek in currentDetail.weeks) {
                        FilledTonalButton(
                            onClick = {
                                singleWeekShiftCourse = currentDetail
                                detailCourse = null
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("仅调第${selectedWeek}周", fontSize = 12.5.sp)
                        }
                    }
                    FilledTonalButton(
                        onClick = {
                            quickTaskPrefillCourse = currentDetail.name
                            detailCourse = null
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Assignment, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("记作业 DDL", fontSize = 12.5.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onDeleteCourse(currentDetail.id)
                            detailCourse = null
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("删除")
                    }
                    Button(
                        onClick = {
                            editingCourse = currentDetail
                            detailCourse = null
                            showAddCourseSheet = true
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("编辑全部周次")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Conflict Picker BottomSheet (when tapping an overlapping course stack in Week Grid)
    conflictSheetData?.let { (conflictDay, overlappedCourses) ->
        ModalBottomSheet(
            onDismissRequest = { conflictSheetData = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFFFFB085) else Color(0xFFC84B00),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${GdouTimeRules.dayOfWeekLabel(conflictDay)} 时段重叠（共 ${overlappedCourses.size} 门课）",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "第 $selectedWeek 周该时间段存在多门重课，点击任意一门可查看详情、编辑或「仅调本周」错开：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                overlappedCourses.forEach { c ->
                    val resolved = GdouTimeRules.resolveCourseTime(c, settings.campus)
                    val palette = coursePaletteFor(c.colorIndex, isDark)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = palette.container),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .clickable {
                                conflictSheetData = null
                                detailCourse = c
                            }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = c.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.onContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    color = palette.accent,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "第${c.startSection}-${c.endSection}节 ${resolved.startTime}-${resolved.endTime}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) Color(0xFF0A1614) else Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(5.dp))
                            Text(
                                text = buildString {
                                    append(c.location.ifBlank { "地点待定" })
                                    if (c.teacher.isNotBlank()) append(" · ${c.teacher}")
                                    append(" · ${GdouTimeRules.formatWeeksCompact(c.weeks)}")
                                    if (resolved.isSpecialStaggered) append(" · 错峰")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.onContainer.copy(alpha = 0.85f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        conflictSheetData = null
                                        detailCourse = c
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) { Text("查看详情", fontSize = 12.sp) }
                                FilledTonalButton(
                                    onClick = {
                                        conflictSheetData = null
                                        singleWeekShiftCourse = c
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) { Text("仅调第${selectedWeek}周", fontSize = 12.sp) }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    // All Conflicts Summary BottomSheet for selectedWeek
    if (showAllWeekConflictsSheet && weekConflictClusters.isNotEmpty()) {
        ModalBottomSheet(
            onDismissRequest = { showAllWeekConflictsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "第 $selectedWeek 周重课冲突一览（共 ${weekConflictClusters.size} 处）",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                weekConflictClusters.forEach { (day, cluster) ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .clickable {
                                showAllWeekConflictsSheet = false
                                conflictSheetData = day to cluster.courses
                            }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "${GdouTimeRules.dayOfWeekLabel(day)} 第${cluster.minStartSection}-${cluster.maxEndSection}节（${cluster.courses.size} 门课重叠）",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color(0xFFFFB085) else Color(0xFFC84B00)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            cluster.courses.forEach { c ->
                                val r = GdouTimeRules.resolveCourseTime(c, settings.campus)
                                Text(
                                    text = "• ${c.name}（${r.startTime}-${r.endTime} @${c.location.ifBlank { "待定" }}）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }

    // Single-Week Temporary Reschedule Dialog ("老师临时把这周这节课调走了")
    singleWeekShiftCourse?.let { target ->
        SingleWeekRescheduleDialog(
            course = target,
            weekNumber = selectedWeek,
            onDismiss = { singleWeekShiftCourse = null },
            onConfirm = { newDay, newStart, newEnd, newLoc ->
                onRescheduleSingleWeek(target.id, selectedWeek, newDay, newStart, newEnd, newLoc)
                singleWeekShiftCourse = null
            }
        )
    }

    // Quick Add Homework DDL Dialog from Course Detail
    quickTaskPrefillCourse?.let { courseName ->
        QuickAddTaskDialog(
            initialCourseName = courseName,
            availableCourses = remember(snapshot.courses) { snapshot.courses.map { it.name }.distinct() },
            onDismiss = { quickTaskPrefillCourse = null },
            onSave = { task ->
                onAddTask(task)
                quickTaskPrefillCourse = null
            }
        )
    }

    // Bell Schedule Reference Dialog
    if (showBellRefDialog) {
        BellScheduleReferenceDialog(
            initialCampus = settings.campus,
            onDismiss = { showBellRefDialog = false }
        )
    }

    // Share / Import Classmate Schedule Code Dialog
    if (showShareCodeDialog) {
        ScheduleShareCodeDialog(
            shareCode = onGetShareCode(),
            onCopyCode = onCopyShareCode,
            onImportCode = onImportShareCode,
            onDismiss = { showShareCodeDialog = false }
        )
    }

    // Full-Featured Keyboard-Safe Course Editor BottomSheet (never clips off screen!)
    if (showAddCourseSheet) {
        CourseEditorBottomSheet(
            initial = editingCourse,
            defaultDay = addPrefillDay,
            defaultStartSection = addPrefillSection,
            totalWeeks = totalWeeks,
            onDismiss = { showAddCourseSheet = false },
            onSave = { course ->
                onSaveCourse(course)
                showAddCourseSheet = false
            }
        )
    }

    // Scrollable Holiday Shift Dialog
    if (showHolidayShiftDialog) {
        HolidayShiftDialog(
            currentWeek = selectedWeek,
            totalWeeks = totalWeeks,
            onDismiss = { showHolidayShiftDialog = false },
            onConfirm = { sw, sd, tw, td, move ->
                onApplyHolidayShift(sw, sd, tw, td, move)
                showHolidayShiftDialog = false
            }
        )
    }
}

@Composable
private fun DetailInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun EmptyScheduleOnboarding(
    selectedCampus: GdouCampus,
    onSelectCampus: (GdouCampus) -> Unit,
    onJumpToSync: () -> Unit,
    onLoadDemoData: () -> Unit,
    onManualAdd: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .widthIn(max = 540.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "欢迎使用海屿课表",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "广东海洋大学全校区纯净课表助手：无需校园网，支持校外流量/Wi-Fi 免 VPN 一键同步正方课表、精准识别分楼错峰作息，零广告离线秒开。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(14.dp))

                // Quick campus selection for public release across all 3 campuses
                Text(
                    text = "选择你的所在校区（自动匹配错峰作息）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    GdouCampus.entries.forEach { campus ->
                        FilterChip(
                            selected = selectedCampus == campus,
                            onClick = { onSelectCampus(campus) },
                            label = { Text(campus.label, fontSize = 12.sp, maxLines = 1, softWrap = false) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onJumpToSync,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("连接广海教务 · 一键同步课表", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = onLoadDemoData,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("先看示例")
                    }
                    OutlinedButton(
                        onClick = onManualAdd,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("手动加课")
                    }
                }
            }
        }
    }
}

@Composable
private fun NextClassHeroBanner(
    todayStatuses: List<cn.gdou.haiyu.schedule.data.CourseOccurrenceStatus>,
    isDark: Boolean,
    onCourseClick: (CourseItem) -> Unit,
    onConflictClick: (List<CourseItem>) -> Unit
) {
    val ongoingList = todayStatuses.filter { it.state == OccurrenceState.ONGOING }
    val activeGroup = if (ongoingList.isNotEmpty()) {
        ongoingList
    } else {
        val nextUpcoming = todayStatuses.firstOrNull { it.state == OccurrenceState.UPCOMING }
        if (nextUpcoming != null) {
            todayStatuses.filter {
                it.state == OccurrenceState.UPCOMING &&
                    (it.course.startSection <= nextUpcoming.course.endSection &&
                        nextUpcoming.course.startSection <= it.course.endSection)
            }
        } else {
            emptyList()
        }
    }
    val active = activeGroup.firstOrNull()
    val hasActiveConflict = activeGroup.size > 1

    val startBg = if (isDark) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary
    val endBg = if (isDark) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.tertiary
    val fgColor = if (isDark) MaterialTheme.colorScheme.onPrimaryContainer else Color.White

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Brush.horizontalGradient(listOf(startBg, endBg)))
            .clickable(enabled = active != null) {
                if (hasActiveConflict) {
                    onConflictClick(activeGroup.map { it.course })
                } else {
                    active?.let { onCourseClick(it.course) }
                }
            }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        if (active == null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = fgColor,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (todayStatuses.isEmpty()) "今天没有排课，好好享受校园时光" else "今日 ${todayStatuses.size} 节课已全部上完",
                    color = fgColor,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            val statusBadge = when (active.state) {
                OccurrenceState.ONGOING -> "上课中 · 剩${active.minutesDelta}m"
                OccurrenceState.UPCOMING -> {
                    val h = active.minutesDelta / 60
                    val m = active.minutesDelta % 60
                    if (h > 0) "下一节 · ${h}h${m}m后" else "下一节 · ${m}m后"
                }
                OccurrenceState.FINISHED -> "已结束"
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = fgColor.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = statusBadge,
                                color = fgColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                            )
                        }
                        if (hasActiveConflict) {
                            Spacer(modifier = Modifier.width(5.dp))
                            Surface(
                                color = Color(0xFFD94A1E),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = "重×${activeGroup.size}",
                                    color = Color.White,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                        if (active.time.isSpecialStaggered) {
                            Spacer(modifier = Modifier.width(5.dp))
                            Surface(
                                color = Color(0xFFFFD580),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = "错峰",
                                    color = Color(0xFF4A2500),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (hasActiveConflict) {
                            "${active.course.name} 等 ${activeGroup.size} 门重课"
                        } else {
                            active.course.name
                        },
                        color = fgColor,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${active.time.startTime}-${active.time.endTime}",
                        color = fgColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    )
                    Text(
                        text = if (hasActiveConflict) "点此切换查看" else active.course.location.ifBlank { "地点待定" },
                        color = fgColor.copy(alpha = 0.88f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun DayTimelineScheduleView(
    courses: List<CourseItem>,
    selectedWeek: Int,
    selectedDay: Int,
    onSelectDay: (Int) -> Unit,
    startDateStr: String,
    campus: GdouCampus,
    isDark: Boolean,
    onCourseClick: (CourseItem) -> Unit
) {
    val weekDates = remember(startDateStr, selectedWeek) {
        GdouTimeRules.getWeekDates(startDateStr, selectedWeek)
    }
    val today = remember { LocalDate.now() }
    val dayCourses = remember(courses, selectedWeek, selectedDay, campus) {
        courses
            .filter { it.dayOfWeek == selectedDay && selectedWeek in it.weeks }
            .sortedBy { it.startSection }
            .map { it to GdouTimeRules.resolveCourseTime(it, campus) }
    }
    val conflictingIds = remember(courses, selectedWeek) {
        GdouTimeRules.findConflictingCourseIds(courses, selectedWeek)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Mon-Sun Quick Day Selector Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (d in 1..7) {
                val date = weekDates[d - 1]
                val isSelected = d == selectedDay
                val isToday = date == today
                val count = remember(courses, selectedWeek, d) {
                    courses.count { it.dayOfWeek == d && selectedWeek in it.weeks }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        isToday -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelectDay(d) }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 7.dp)
                    ) {
                        Text(
                            text = GdouTimeRules.dayOfWeekLabel(d).removePrefix("周"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${date.dayOfMonth}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        count == 0 -> Color.Transparent
                                        isSelected -> MaterialTheme.colorScheme.onPrimary
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                )
                        )
                    }
                }
            }
        }

        if (dayCourses.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "${GdouTimeRules.dayOfWeekLabel(selectedDay)}没有排课，好好充电或休息吧 ~",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(dayCourses, key = { it.first.id }) { (course, resolved) ->
                    val palette = coursePaletteFor(course.colorIndex, isDark)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = palette.container),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCourseClick(course) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(64.dp)
                            ) {
                                Text(
                                    text = resolved.startTime,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = palette.accent
                                )
                                Text(
                                    text = resolved.endTime,
                                    fontSize = 12.sp,
                                    color = palette.onContainer.copy(alpha = 0.75f)
                                )
                                Text(
                                    text = "${course.startSection}-${course.endSection}节",
                                    fontSize = 10.sp,
                                    color = palette.onContainer.copy(alpha = 0.65f)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(palette.accent)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = course.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = palette.onContainer,
                                        modifier = Modifier.weight(1f, fill = false),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (course.id in conflictingIds) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = Color(0xFFD94A1E),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "时段冲突",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    if (resolved.isSpecialStaggered) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = palette.accent,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "错峰",
                                                color = if (isDark) Color(0xFF0A1614) else Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = buildString {
                                        append(course.location.ifBlank { "地点待定" })
                                        if (course.teacher.isNotBlank()) append(" · ${course.teacher}")
                                    },
                                    fontSize = 12.sp,
                                    color = palette.onContainer.copy(alpha = 0.82f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekGridScheduleView(
    courses: List<CourseItem>,
    selectedWeek: Int,
    today: LocalDate,
    nowTime: LocalTime,
    startDateStr: String,
    campus: GdouCampus,
    showWeekend: Boolean,
    showNonCurrentWeek: Boolean,
    isDark: Boolean,
    onCourseClick: (CourseItem) -> Unit,
    onConflictClusterClick: (day: Int, overlappedCourses: List<CourseItem>) -> Unit,
    onEmptyCellClick: (day: Int, section: Int) -> Unit
) {
    // Always honor showWeekend (default true) so Saturday & Sunday NEVER vanish across weeks!
    val hasAnyWeekendCourse = remember(courses) { courses.any { it.dayOfWeek >= 6 } }
    val dayCount = if (showWeekend || hasAnyWeekendCourse) 7 else 5
    val maxSection = remember(courses) {
        (courses.maxOfOrNull { it.endSection } ?: 10).coerceIn(10, 12)
    }
    val slots = remember(campus, maxSection) { GdouTimeRules.slotsForCampus(campus, maxSection) }
    val weekDates = remember(startDateStr, selectedWeek) {
        GdouTimeRules.getWeekDates(startDateStr, selectedWeek)
    }
    val weekContainsToday = remember(weekDates, today) { weekDates.any { it == today } }
    val activeSectionNumber: Int? = remember(slots, nowTime, weekContainsToday) {
        if (!weekContainsToday) null
        else slots.firstOrNull { slot ->
            val st = GdouTimeRules.parseLocalTime(slot.startTime)
            val et = GdouTimeRules.parseLocalTime(slot.endTime)
            !nowTime.isBefore(st) && !nowTime.isAfter(et)
        }?.number
    }
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.45f)
    val baseTimeW = if (dayCount == 7) 36f else 42f
    val timeColumnWidth = (baseTimeW * (1f + (fontScale - 1f) * 0.65f)).dp
    val rowHeight = (62f * (1f + (fontScale - 1f) * 0.55f)).dp
    val breakGap = 14.dp

    fun yOffsetForSection(section: Int): Dp {
        val breaksBefore = (if (section > 4) 1 else 0) + (if (section > 8) 1 else 0)
        return rowHeight * (section - 1) + breakGap * breaksBefore
    }

    fun heightForSpan(startSection: Int, endSection: Int): Dp {
        val top = yOffsetForSection(startSection)
        val bottom = yOffsetForSection(endSection) + rowHeight
        return bottom - top
    }

    val totalBreaks = (if (slots.size > 4) 1 else 0) + (if (slots.size > 8) 1 else 0)
    val totalGridHeight = rowHeight * slots.size + breakGap * totalBreaks

    // Calculate live current-time line Y-position (during campus hours 07:50 ~ 22:30)
    val liveTimeY: Dp? = remember(slots, nowTime, rowHeight) {
        val firstStart = GdouTimeRules.parseLocalTime(slots.first().startTime).minusMinutes(15)
        val lastEnd = GdouTimeRules.parseLocalTime(slots.last().endTime).plusMinutes(15)
        if (nowTime.isBefore(firstStart) || nowTime.isAfter(lastEnd)) {
            null
        } else {
            var computed: Dp? = null
            for (i in slots.indices) {
                val s = slots[i]
                val st = GdouTimeRules.parseLocalTime(s.startTime)
                val et = GdouTimeRules.parseLocalTime(s.endTime)
                val secTop = yOffsetForSection(s.number)
                if (!nowTime.isBefore(st) && !nowTime.isAfter(et)) {
                    val totalMin = ChronoUnit.MINUTES.between(st, et).coerceAtLeast(1)
                    val passed = ChronoUnit.MINUTES.between(st, nowTime).coerceIn(0, totalMin)
                    computed = secTop + rowHeight * (passed.toFloat() / totalMin.toFloat())
                    break
                }
                if (i < slots.lastIndex) {
                    val nxt = slots[i + 1]
                    val nxtSt = GdouTimeRules.parseLocalTime(nxt.startTime)
                    if (nowTime.isAfter(et) && nowTime.isBefore(nxtSt)) {
                        val gapTop = secTop + rowHeight
                        val gapBottom = yOffsetForSection(nxt.number)
                        val gapMin = ChronoUnit.MINUTES.between(et, nxtSt).coerceAtLeast(1)
                        val passed = ChronoUnit.MINUTES.between(et, nowTime).coerceIn(0, gapMin)
                        computed = gapTop + (gapBottom - gapTop) * (passed.toFloat() / gapMin.toFloat())
                        break
                    }
                }
            }
            computed
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Sleek Day-of-Week Header Row (integrated Today pill & month indicator)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(top = 3.dp, bottom = 5.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.width(timeColumnWidth),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${weekDates.first().monthValue}月",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            for (day in 1..dayCount) {
                val date = weekDates[day - 1]
                val isToday = date == today
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = GdouTimeRules.dayOfWeekLabel(day).removePrefix("周"),
                        fontSize = 11.5.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val dateCircleSize = (22f * fontScale).dp
                    Box(
                        modifier = Modifier
                            .size(dateCircleSize)
                            .clip(CircleShape)
                            .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${date.dayOfMonth}",
                            fontSize = 11.sp,
                            maxLines = 1,
                            softWrap = false,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

        // Scrollable Grid Body
        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(end = 3.dp, bottom = 20.dp)
        ) {
            // Left Time Slots Column (highlights current section cleanly — zero line slashing over course cards!)
            Column(modifier = Modifier.width(timeColumnWidth)) {
                slots.forEach { slot ->
                    val isCurrentSlot = activeSectionNumber == slot.number
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isCurrentSlot) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "${slot.number}",
                                    fontSize = 10.5.sp,
                                    lineHeight = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        } else {
                            Text(
                                text = "${slot.number}",
                                fontSize = 11.sp,
                                lineHeight = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(1.5.dp))
                        Text(
                            text = slot.startTime,
                            fontSize = 8.5.sp,
                            lineHeight = 10.sp,
                            maxLines = 1,
                            softWrap = false,
                            fontWeight = if (isCurrentSlot) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isCurrentSlot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = slot.endTime,
                            fontSize = 8.5.sp,
                            lineHeight = 10.sp,
                            maxLines = 1,
                            softWrap = false,
                            color = if (isCurrentSlot) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )
                    }
                    if (slot.number == 4 || slot.number == 8) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(breakGap),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (slot.number == 4) "午" else "晚",
                                fontSize = 8.sp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // Day Columns (with Today tint, live time bar, hairline grid, & Overlap Clustering!)
            for (day in 1..dayCount) {
                val isTodayCol = weekDates[day - 1] == today
                val dayData = remember(courses, day, selectedWeek, showNonCurrentWeek) {
                    val allDay = courses.filter { it.dayOfWeek == day }
                    val inWeek = allDay.filter { selectedWeek in it.weeks }
                    val activeClusters = GdouTimeRules.groupOverlappingCourses(inWeek)
                    val outWeek = if (showNonCurrentWeek) {
                        val placed = mutableListOf<CourseItem>()
                        val candidates = allDay
                            .filter { selectedWeek !in it.weeks }
                            .sortedBy { c -> c.weeks.minOfOrNull { kotlin.math.abs(it - selectedWeek) } ?: 99 }
                        for (candidate in candidates) {
                            val hitsInWeek = inWeek.any { active ->
                                active.startSection <= candidate.endSection && candidate.startSection <= active.endSection
                            }
                            val hitsOutWeek = placed.any { prev ->
                                prev.startSection <= candidate.endSection && candidate.startSection <= prev.endSection
                            }
                            if (!hitsInWeek && !hitsOutWeek) placed.add(candidate)
                        }
                        placed
                    } else emptyList()
                    outWeek to activeClusters
                }
                val outWeekCourses = dayData.first
                val activeClusters = dayData.second

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .height(totalGridHeight)
                        .background(
                            if (isTodayCol) MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.075f else 0.045f)
                            else Color.Transparent
                        )
                ) {
                    val colWidth = maxWidth
                    val canSideBySide = colWidth >= 96.dp

                    // Clean hairline slot grid (modern calendar feel, zero clunky Excel boxes!)
                    Column(modifier = Modifier.fillMaxSize()) {
                        slots.forEach { slot ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(rowHeight)
                                    .clickable { onEmptyCellClick(day, slot.number) }
                            ) {
                                HorizontalDivider(
                                    modifier = Modifier.align(Alignment.BottomCenter),
                                    thickness = 0.45.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.25f else 0.35f)
                                )
                            }
                            if (slot.number == 4 || slot.number == 8) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(breakGap)
                                        .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f))
                                )
                            }
                        }
                    }

                    // Subtle Current-Time Hairline BEHIND course cards (shows on free slots, NEVER cuts across course text!)
                    if (isTodayCol && liveTimeY != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = liveTimeY.coerceAtLeast(0.dp))
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.48f else 0.36f))
                        )
                    }

                    // 1. Non-current-week preview cards (clean muted style)
                    outWeekCourses.forEach { course ->
                        val clampedStart = course.startSection.coerceIn(1, slots.size)
                        val clampedEnd = course.endSection.coerceIn(clampedStart, slots.size)
                        val span = (clampedEnd - clampedStart + 1)
                        val topOffset = yOffsetForSection(clampedStart)
                        val blockHeight = heightForSpan(clampedStart, clampedEnd)
                        val resolved = GdouTimeRules.resolveCourseTime(course, campus)
                        val palette = coursePaletteFor(course.colorIndex, isDark)
                        WeekGridCourseCard(
                            course = course,
                            resolved = resolved,
                            palette = palette,
                            isDark = isDark,
                            isInCurrentWeek = false,
                            conflictCount = 0,
                            showInlineConflictTag = false,
                            dayCount = dayCount,
                            span = span,
                            fontScale = fontScale,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(blockHeight)
                                .offset(y = topOffset)
                                .padding(horizontal = 1.5.dp, vertical = 1.5.dp),
                            onClick = { onCourseClick(course) }
                        )
                    }

                    // 2. Active week clusters (single cards OR overlap-adapted conflict stacks/splits!)
                    activeClusters.forEach { cluster ->
                        if (!cluster.hasConflict) {
                            val course = cluster.courses.first()
                            val clampedStart = course.startSection.coerceIn(1, slots.size)
                            val clampedEnd = course.endSection.coerceIn(clampedStart, slots.size)
                            val span = (clampedEnd - clampedStart + 1)
                            val topOffset = yOffsetForSection(clampedStart)
                            val blockHeight = heightForSpan(clampedStart, clampedEnd)
                            val resolved = GdouTimeRules.resolveCourseTime(course, campus)
                            val palette = coursePaletteFor(course.colorIndex, isDark)
                            WeekGridCourseCard(
                                course = course,
                                resolved = resolved,
                                palette = palette,
                                isDark = isDark,
                                isInCurrentWeek = true,
                                conflictCount = 0,
                                showInlineConflictTag = false,
                                dayCount = dayCount,
                                span = span,
                                fontScale = fontScale,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(blockHeight)
                                    .offset(y = topOffset)
                                    .padding(horizontal = 1.5.dp, vertical = 1.5.dp),
                                onClick = { onCourseClick(course) }
                            )
                        } else if (canSideBySide) {
                            // Wide screen (Tablet / Foldable): display overlapping courses side-by-side!
                            val count = cluster.courses.size.coerceAtMost(3)
                            val subW = colWidth / count
                            cluster.courses.take(count).forEachIndexed { idx, course ->
                                val clampedStart = course.startSection.coerceIn(1, slots.size)
                                val clampedEnd = course.endSection.coerceIn(clampedStart, slots.size)
                                val span = (clampedEnd - clampedStart + 1)
                                val topOffset = yOffsetForSection(clampedStart)
                                val blockHeight = heightForSpan(clampedStart, clampedEnd)
                                val resolved = GdouTimeRules.resolveCourseTime(course, campus)
                                val palette = coursePaletteFor(course.colorIndex, isDark)
                                WeekGridCourseCard(
                                    course = course,
                                    resolved = resolved,
                                    palette = palette,
                                    isDark = isDark,
                                    isInCurrentWeek = true,
                                    conflictCount = if (idx == count - 1 && cluster.courses.size > count) cluster.courses.size else 0,
                                    showInlineConflictTag = true,
                                    dayCount = 7,
                                    span = span,
                                    fontScale = fontScale,
                                    modifier = Modifier
                                        .width(subW)
                                        .height(blockHeight)
                                        .offset(x = subW * idx, y = topOffset)
                                        .padding(1.dp),
                                    onClick = { onConflictClusterClick(day, cluster.courses) }
                                )
                            }
                        } else {
                            // Narrow phone 7-col: unmistakable stacked-cards visual + coral 重×N badge
                            val primary = cluster.courses.first()
                            val secondary = cluster.courses[1]
                            val clampedStart = cluster.minStartSection.coerceIn(1, slots.size)
                            val clampedEnd = cluster.maxEndSection.coerceIn(clampedStart, slots.size)
                            val span = (clampedEnd - clampedStart + 1)
                            val topOffset = yOffsetForSection(clampedStart)
                            val blockHeight = heightForSpan(clampedStart, clampedEnd)
                            val primaryResolved = GdouTimeRules.resolveCourseTime(primary, campus)
                            val primaryPalette = coursePaletteFor(primary.colorIndex, isDark)
                            val secondaryPalette = coursePaletteFor(secondary.colorIndex, isDark)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(blockHeight)
                                    .offset(y = topOffset)
                                    .padding(1.2.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = secondaryPalette.container,
                                    border = BorderStroke(1.dp, Color(0xFFD94A1E).copy(alpha = 0.72f)),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(start = 3.5.dp, top = 3.5.dp)
                                ) {}
                                WeekGridCourseCard(
                                    course = primary,
                                    resolved = primaryResolved,
                                    palette = primaryPalette,
                                    isDark = isDark,
                                    isInCurrentWeek = true,
                                    conflictCount = cluster.courses.size,
                                    showInlineConflictTag = false,
                                    dayCount = dayCount,
                                    span = span,
                                    fontScale = fontScale,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(end = 3.dp, bottom = 3.dp),
                                    onClick = { onConflictClusterClick(day, cluster.courses) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekGridCourseCard(
    course: CourseItem,
    resolved: cn.gdou.haiyu.schedule.data.ResolvedCourseTime,
    palette: cn.gdou.haiyu.schedule.ui.theme.CourseCardPalette,
    isDark: Boolean,
    isInCurrentWeek: Boolean,
    conflictCount: Int,
    showInlineConflictTag: Boolean,
    dayCount: Int,
    span: Int,
    fontScale: Float,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val isConflict = conflictCount > 1 || showInlineConflictTag
    val bgColor = if (isInCurrentWeek) {
        palette.container
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (isDark) 0.45f else 0.52f)
    }
    val textColor = if (isInCurrentWeek) {
        palette.onContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
    }
    val borderStroke = when {
        isConflict -> BorderStroke(1.2.dp, Color(0xFFD94A1E))
        isInCurrentWeek -> BorderStroke(0.6.dp, palette.accent.copy(alpha = if (isDark) 0.34f else 0.2f))
        else -> BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    }

    // Modern borderless pastel card: +15% horizontal room for 7-col Chinese course names, zero Excel feel!
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        border = borderStroke,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (dayCount == 5) 5.5.dp else 3.5.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.Top
        ) {
            if (conflictCount > 1) {
                Surface(
                    color = Color(0xFFD94A1E),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    val badgeLabel = when {
                        resolved.isSpecialStaggered && fontScale <= 1.15f && dayCount == 5 -> "重×$conflictCount · ${resolved.startTime}"
                        fontScale <= 1.15f -> "重×$conflictCount"
                        else -> "×$conflictCount"
                    }
                    Text(
                        text = badgeLabel,
                        fontSize = 8.sp,
                        lineHeight = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 3.5.dp, vertical = 1.dp)
                    )
                }
                if (resolved.isSpecialStaggered && (dayCount == 7 || fontScale > 1.15f)) {
                    Spacer(modifier = Modifier.height(1.5.dp))
                    Text(
                        text = "错峰${resolved.startTime}",
                        fontSize = 7.2.sp,
                        lineHeight = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accent,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
            } else if (showInlineConflictTag) {
                Surface(
                    color = Color(0xFFD94A1E),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "冲突",
                        fontSize = 7.5.sp,
                        lineHeight = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 3.5.dp, vertical = 1.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
            } else if (!isInCurrentWeek) {
                Text(
                    text = "非本周",
                    fontSize = 7.5.sp,
                    lineHeight = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(1.5.dp))
            } else if (resolved.isSpecialStaggered) {
                Surface(
                    color = palette.accent,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (dayCount == 5 && fontScale <= 1.15f) "错峰${resolved.startTime}" else resolved.startTime,
                        fontSize = 7.5.sp,
                        lineHeight = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color(0xFF0A1614) else Color.White,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 3.5.dp, vertical = 1.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
            }

            Text(
                text = course.name,
                fontSize = if (dayCount == 5) 11.5.sp else 10.5.sp,
                lineHeight = if (dayCount == 5) 14.sp else 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = when {
                    span <= 1 -> 2
                    span >= 3 -> 5
                    else -> 4
                },
                overflow = TextOverflow.Ellipsis
            )

            if (course.location.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.5.dp))
                Text(
                    text = course.location,
                    fontSize = if (dayCount == 5) 9.5.sp else 8.8.sp,
                    lineHeight = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor.copy(alpha = 0.76f),
                    maxLines = if (span <= 1) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseEditorBottomSheet(
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
private fun SingleWeekRescheduleDialog(
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
private fun HolidayShiftDialog(
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
private fun DaySelectorRow(selectedDay: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        for (d in 1..7) {
            val selected = selectedDay == d
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clickable { onSelect(d) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = GdouTimeRules.dayOfWeekLabel(d).removePrefix("周"),
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private fun parseWeeksExpression(expr: String, maxWeeks: Int): List<Int> {
    val result = mutableListOf<Int>()
    val groups = expr.replace("，", ",").split(",")
    for (g in groups) {
        val item = g.trim()
        val range = Regex("(\\d+)\\s*[-~～]\\s*(\\d+)").find(item)
        val single = Regex("(\\d+)").find(item)
        val odd = item.contains("单")
        val even = item.contains("双")
        if (range != null) {
            val s = range.groupValues[1].toIntOrNull() ?: continue
            val e = range.groupValues[2].toIntOrNull() ?: continue
            for (w in s.coerceAtLeast(1)..e.coerceAtMost(maxWeeks.coerceAtLeast(30))) {
                if (odd && w % 2 == 0) continue
                if (even && w % 2 == 1) continue
                result.add(w)
            }
        } else if (single != null) {
            val w = single.groupValues[1].toIntOrNull() ?: continue
            if (w in 1..30) result.add(w)
        }
    }
    return result.distinct().sorted()
}
