package io.github.rex25415.haiyu.schedule.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.rex25415.haiyu.schedule.data.GdouTimeRules
import io.github.rex25415.haiyu.schedule.data.ResolvedExamGridBlock
import io.github.rex25415.haiyu.schedule.data.TermWeekPhase
import io.github.rex25415.haiyu.schedule.model.AppDataSnapshot
import io.github.rex25415.haiyu.schedule.model.CourseItem
import io.github.rex25415.haiyu.schedule.model.GdouCampus
import io.github.rex25415.haiyu.schedule.model.GridRowDensity
import io.github.rex25415.haiyu.schedule.model.ScheduleShareBundle
import io.github.rex25415.haiyu.schedule.model.TodoTaskItem
import io.github.rex25415.haiyu.schedule.ui.components.AttendanceCounterPill
import io.github.rex25415.haiyu.schedule.ui.components.CourseEditorBottomSheet
import io.github.rex25415.haiyu.schedule.ui.components.CourseSearchBottomSheet
import io.github.rex25415.haiyu.schedule.ui.components.DayTimelineScheduleView
import io.github.rex25415.haiyu.schedule.ui.components.DetailInfoRow
import io.github.rex25415.haiyu.schedule.ui.components.EmptyScheduleOnboarding
import io.github.rex25415.haiyu.schedule.ui.components.HolidayShiftDialog
import io.github.rex25415.haiyu.schedule.ui.components.NextClassHeroBanner
import io.github.rex25415.haiyu.schedule.ui.components.SingleWeekRescheduleDialog
import io.github.rex25415.haiyu.schedule.ui.components.BellScheduleReferenceDialog
import io.github.rex25415.haiyu.schedule.ui.components.FreeSlotMatcherBottomSheet
import io.github.rex25415.haiyu.schedule.ui.components.QuickAddTaskDialog
import io.github.rex25415.haiyu.schedule.ui.components.ScheduleShareCodeDialog
import io.github.rex25415.haiyu.schedule.ui.components.ScheduleToolboxBottomSheet
import io.github.rex25415.haiyu.schedule.ui.theme.coursePaletteFor
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
    backgroundBitmap: ImageBitmap? = null,
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
    onTogglePinConflictCourse: (CourseItem, List<CourseItem>) -> Unit,
    onUpdateCourseNote: (courseId: String, note: String) -> Unit,
    onUpdateCourseAttendance: (courseId: String, absenceDelta: Int, bonusDelta: Int) -> Unit = { _, _, _ -> },
    onAddTask: (TodoTaskItem) -> Unit,
    onToggleTaskDone: (String) -> Unit,
    onExportWeekImage: (weekNumber: Int) -> Unit,
    onCopyWeekScheduleText: (weekNumber: Int) -> Unit,
    onGetShareCode: () -> String?,
    onCopyShareCode: (String) -> Unit,
    onImportShareCode: (String) -> Unit,
    onParsePartnerShareCode: (String) -> Result<ScheduleShareBundle>,
    onSavePartnerProfile: (name: String, code: String) -> Unit,
    onShowMessage: (String) -> Unit,
    onApplyHolidayShift: (sourceWeek: Int, sourceDay: Int, targetWeek: Int, targetDay: Int, move: Boolean) -> Unit,
    onOpenEmptyClassroom: () -> Unit = {},
    onToggleCompactBanner: () -> Unit = {},
    onOpenCampusServices: () -> Unit = {}
) {
    val settings = snapshot.settings
    // Live 30s clock ticker + immediate ON_RESUME refresh so overnight transitions update instantly!
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
            delay(30_000L)
            nowDateTime = LocalDateTime.now()
        }
    }
    val today = nowDateTime.toLocalDate()
    val nowTime = nowDateTime.toLocalTime()
    val totalWeeks = settings.totalWeeks.coerceIn(1, 30)
    val termWeekInfo = remember(settings.semesterStartDate, totalWeeks, today) {
        GdouTimeRules.calculateTermWeekInfo(settings.semesterStartDate, totalWeeks, today)
    }
    val actualCurrentWeek = termWeekInfo.clampedWeek

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
    var showCourseSearchSheet by remember { mutableStateOf(false) }
    var showFreeSlotMatcher by remember { mutableStateOf(false) }
    var selectedExamDetail by remember { mutableStateOf<ResolvedExamGridBlock?>(null) }
    var showAllWeekExamsSheet by remember { mutableStateOf(false) }
    var showPracticeCoursesDialog by remember { mutableStateOf(false) }

    val weekPracticeCourses = remember(snapshot.practiceCourses, selectedWeek) {
        snapshot.practiceCourses.filter { it.weeks.isEmpty() || selectedWeek in it.weeks }
    }

    val pinnedKeys = remember(settings.pinnedConflictKeys) { settings.pinnedConflictKeys.toSet() }
    val resolvedTimeMap = remember(snapshot.courses, settings.campus) {
        snapshot.courses.associate { it.id to GdouTimeRules.resolveCourseTime(it, settings.campus) }
    }
    val maxGridSection = remember(snapshot.courses) {
        (snapshot.courses.maxOfOrNull { it.endSection } ?: 10).coerceIn(10, 12)
    }
    val selectedWeekExams = remember(snapshot.exams, settings.semesterStartDate, selectedWeek, settings.campus, settings.showExamsOnTimetable, maxGridSection) {
        if (!settings.showExamsOnTimetable) emptyList()
        else GdouTimeRules.resolveExamsForWeek(snapshot.exams, settings.semesterStartDate, selectedWeek, settings.campus, maxGridSection)
    }

    val weekConflictClusters = remember(snapshot.courses, selectedWeek, pinnedKeys) {
        GdouTimeRules.findConflictClustersForWeek(snapshot.courses, selectedWeek, pinnedKeys)
    }

    val todayStatuses = remember(snapshot.courses, settings.semesterStartDate, totalWeeks, settings.campus, nowDateTime, pinnedKeys) {
        GdouTimeRules.getTodayCoursesWithStatus(
            courses = snapshot.courses,
            startDateStr = settings.semesterStartDate,
            totalWeeks = totalWeeks,
            campus = settings.campus,
            now = nowDateTime,
            pinnedKeys = pinnedKeys
        )
    }

    val tomorrowCourses = remember(snapshot.courses, settings.semesterStartDate, totalWeeks, settings.campus, today, pinnedKeys) {
        GdouTimeRules.getCoursesForDate(
            courses = snapshot.courses,
            startDateStr = settings.semesterStartDate,
            totalWeeks = totalWeeks,
            campus = settings.campus,
            date = today.plusDays(1),
            pinnedKeys = pinnedKeys
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
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (selectedWeek == actualCurrentWeek) {
                                "${settings.academicYearLabel} · $actualCurrentWeek/${totalWeeks}周"
                            } else {
                                "当前第${actualCurrentWeek}周 · 预览第${selectedWeek}周"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (selectedWeek != actualCurrentWeek) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
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
                                    text = "↩ 回第${actualCurrentWeek}周",
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Uniform 20.dp action icons with harmonious visual weight (never collides with week/campus badges!)
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
                    IconButton(
                        onClick = { showToolsMenu = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Apps,
                            contentDescription = "课表百宝箱",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
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
            val cfg = LocalConfiguration.current
            val isViewportShort = cfg.screenHeightDp < 600 ||
                cfg.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE ||
                settings.gridRowDensity == GridRowDensity.COMPACT
            val isCompactBannerActive = isViewportShort || settings.compactScheduleBanner
            NextClassHeroBanner(
                termWeekInfo = termWeekInfo,
                todayStatuses = todayStatuses,
                tomorrowCourses = tomorrowCourses,
                todayDayOfWeek = today.dayOfWeek.value,
                tomorrowDayOfWeek = today.plusDays(1).dayOfWeek.value,
                isCompactHeight = isCompactBannerActive,
                isBannerCollapsed = settings.compactScheduleBanner,
                onToggleBannerCollapse = if (!isViewportShort) onToggleCompactBanner else null,
                isDark = isDark,
                onCourseClick = { detailCourse = it },
                onConflictClick = { dayOfWeek, overlapped ->
                    conflictSheetData = dayOfWeek to overlapped
                }
            )

            // Unified Smart Status Pill Row (compact side-by-side Exam + Practice + Conflict + DDL chips, zero vertical bloat!)
            if (selectedWeekExams.isNotEmpty() || urgentTasks.isNotEmpty() || weekConflictClusters.isNotEmpty() || weekPracticeCourses.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (weekPracticeCourses.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable { showPracticeCoursesDialog = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.School,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (weekPracticeCourses.size == 1) {
                                        "实践课：${weekPracticeCourses.first().name.take(8)}"
                                    } else {
                                        "本周 ${weekPracticeCourses.size} 门整周实践课"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                    if (selectedWeekExams.isNotEmpty()) {
                        Surface(
                            color = if (isDark) Color(0xFF472C10) else Color(0xFFFFF0DB),
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable {
                                    if (selectedWeekExams.size == 1) {
                                        selectedExamDetail = selectedWeekExams.first()
                                    } else {
                                        showAllWeekExamsSheet = true
                                    }
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.EventNote,
                                    contentDescription = null,
                                    tint = if (isDark) Color(0xFFFFCA80) else Color(0xFFB45309),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "本周 ${selectedWeekExams.size} 场考试",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color(0xFFFFE1B3) else Color(0xFF92400E),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

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
                    weekExams = selectedWeekExams,
                    today = today,
                    selectedWeek = selectedWeek,
                    selectedDay = selectedDayOfWeek,
                    onSelectDay = { selectedDayOfWeek = it },
                    startDateStr = settings.semesterStartDate,
                    campus = settings.campus,
                    isDark = isDark,
                    onCourseClick = { detailCourse = it },
                    onExamClick = { selectedExamDetail = it }
                )
            } else {
                val coursesByDay = remember(snapshot.courses) {
                    snapshot.courses.groupBy { it.dayOfWeek }
                }
                val hasAnyWeekendCourse = remember(snapshot.courses) {
                    snapshot.courses.any { it.dayOfWeek >= 6 }
                }
                val gridTimeSlots = remember(settings.campus, maxGridSection) {
                    GdouTimeRules.slotsForCampus(settings.campus, maxGridSection)
                }
                val currentInSessionSection: Int? = remember(gridTimeSlots, nowTime, termWeekInfo.phase) {
                    if (termWeekInfo.phase != TermWeekPhase.IN_TERM) null
                    else gridTimeSlots.firstOrNull { slot ->
                        val st = GdouTimeRules.parseLocalTime(slot.startTime)
                        val et = GdouTimeRules.parseLocalTime(slot.endTime)
                        !nowTime.isBefore(st) && !nowTime.isAfter(et)
                    }?.number
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1
                ) { pageIdx ->
                    val pageWeek = pageIdx + 1
                    val isPageCurrentWeek = pageWeek == actualCurrentWeek && termWeekInfo.phase == TermWeekPhase.IN_TERM
                    val pageExams = remember(snapshot.exams, settings.semesterStartDate, pageWeek, settings.campus, settings.showExamsOnTimetable, maxGridSection) {
                        if (!settings.showExamsOnTimetable) emptyList()
                        else GdouTimeRules.resolveExamsForWeek(snapshot.exams, settings.semesterStartDate, pageWeek, settings.campus, maxGridSection)
                    }
                    io.github.rex25415.haiyu.schedule.ui.components.WeekGridScheduleView(
                        coursesByDay = coursesByDay,
                        hasAnyWeekendCourse = hasAnyWeekendCourse,
                        maxGridSection = maxGridSection,
                        weekExams = pageExams,
                        resolvedTimeMap = resolvedTimeMap,
                        pinnedKeys = pinnedKeys,
                        selectedWeek = pageWeek,
                        today = today,
                        activeSectionNumber = if (isPageCurrentWeek) currentInSessionSection else null,
                        startDateStr = settings.semesterStartDate,
                        campus = settings.campus,
                        rowDensity = settings.gridRowDensity,
                        showWeekend = settings.showWeekend,
                        showNonCurrentWeek = settings.showNonCurrentWeekCourses,
                        isDark = isDark,
                        backgroundBitmap = backgroundBitmap,
                        cardAlpha = if (backgroundBitmap != null) settings.courseCardAlpha.coerceIn(0.6f, 1f) else 1f,
                        onCourseClick = { detailCourse = it },
                        onExamClick = { selectedExamDetail = it },
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

    // Smart Week Picker Dialog (semester progress bar + per-week course density!)
    if (showWeekPicker) {
        val progressFraction = when (termWeekInfo.phase) {
            TermWeekPhase.BEFORE_TERM -> 0f
            TermWeekPhase.AFTER_TERM -> 1f
            TermWeekPhase.IN_TERM -> (actualCurrentWeek.toFloat() / totalWeeks.toFloat()).coerceIn(0f, 1f)
        }
        val progressPct = (progressFraction * 100f).toInt()
        val remainingWeeks = (totalWeeks - actualCurrentWeek).coerceAtLeast(0)
        val progressLabel = when (termWeekInfo.phase) {
            TermWeekPhase.BEFORE_TERM -> "尚未开学 · 距第 1 周还有 ${termWeekInfo.daysUntilStart} 天"
            TermWeekPhase.AFTER_TERM -> "本学期已结课 · 假期愉快"
            TermWeekPhase.IN_TERM -> "本学期进度 $progressPct% · 距结课还有 ${remainingWeeks} 周"
        }
        AlertDialog(
            onDismissRequest = { showWeekPicker = false },
            title = { Text("快速切换教学周") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = progressLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp))
                        )
                    }
                    val examCountByWeek = remember(snapshot.exams, settings.semesterStartDate, totalWeeks, settings.campus, settings.showExamsOnTimetable, maxGridSection) {
                        if (!settings.showExamsOnTimetable || snapshot.exams.isEmpty()) emptyMap()
                        else (1..totalWeeks).associateWith { w ->
                            GdouTimeRules.resolveExamsForWeek(
                                exams = snapshot.exams,
                                startDateStr = settings.semesterStartDate,
                                weekNumber = w,
                                campus = settings.campus,
                                maxSection = maxGridSection
                            ).size
                        }.filterValues { it > 0 }
                    }
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
                        val weekExamCount = examCountByWeek[w] ?: 0
                        val hasExam = weekExamCount > 0
                        val examTintBg = if (isDark) Color(0xFF472C10) else Color(0xFFFFF5E6)
                        val examTintBorder = if (isDark) Color(0xFFF59E0B) else Color(0xFFD97706)
                        val examTintText = if (isDark) Color(0xFFFFCA80) else Color(0xFFB45309)
                        val subLabel = when {
                            isNow && hasExam -> "本周·${weekExamCount}考"
                            isNow -> "本周"
                            hasExam && weekCourseCount > 0 -> "${weekCourseCount}门·${weekExamCount}考"
                            hasExam -> "${weekExamCount}场考"
                            weekCourseCount > 0 -> "${weekCourseCount}门"
                            else -> "无课"
                        }
                        Surface(
                            shape = RoundedCornerShape(11.dp),
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.primary
                                isNow -> MaterialTheme.colorScheme.primaryContainer
                                hasExam -> examTintBg
                                weekCourseCount == 0 -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.45f)
                                else -> MaterialTheme.colorScheme.surfaceContainerLowest
                            },
                            border = when {
                                isSelected -> null
                                isNow -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                                hasExam -> BorderStroke(1.dp, examTintBorder.copy(alpha = 0.72f))
                                weekCourseCount > 0 -> BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                                else -> BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
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
                                        hasExam -> examTintText
                                        weekCourseCount == 0 -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                    fontWeight = if (isSelected || isNow || hasExam) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.5.sp,
                                    lineHeight = 15.sp
                                )
                                Text(
                                    text = subLabel,
                                    fontSize = 8.2.sp,
                                    lineHeight = 10.sp,
                                    fontWeight = if (hasExam && !isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    softWrap = false,
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                                        isNow -> MaterialTheme.colorScheme.primary
                                        hasExam -> examTintText
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                                    }
                                )
                            }
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

    // Course Detail Bottom Sheet (Color-Matched Pastel Header, live-backed by snapshot!)
    val currentDetail = detailCourse?.let { d -> snapshot.courses.firstOrNull { it.id == d.id } ?: d }
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
                        icon = Icons.Rounded.School,
                        label = "课程学分",
                        value = "${currentDetail.credit} 学分"
                    )
                }
                val officialTags = remember(currentDetail) {
                    buildList {
                        if (currentDetail.courseType.isNotBlank()) add(currentDetail.courseType)
                        if (currentDetail.examMethod.isNotBlank()) add("考核：${currentDetail.examMethod}")
                        if (currentDetail.totalHours.isNotBlank()) add("${currentDetail.totalHours}学时")
                        if (currentDetail.classGroup.isNotBlank()) add("教学班：${currentDetail.classGroup}")
                    }
                }
                if (officialTags.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 2.dp, bottom = 4.dp)
                    ) {
                        officialTags.forEach { tag ->
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = tag,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp)
                                )
                            }
                        }
                    }
                }
                val linkedTasks = remember(snapshot.tasks, currentDetail.name) {
                    snapshot.tasks.filter {
                        !it.isDone && it.courseName.trim().equals(currentDetail.name.trim(), ignoreCase = true)
                    }
                }
                if (linkedTasks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text(
                                text = "本课待办 DDL（${linkedTasks.size} 项）",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            linkedTasks.forEach { t ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onToggleTaskDone(t.id) }
                                        .padding(vertical = 2.dp)
                                ) {
                                    Checkbox(
                                        checked = t.isDone,
                                        onCheckedChange = { onToggleTaskDone(t.id) },
                                        modifier = Modifier.size(26.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = t.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (t.dueDate.isNotBlank()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = t.dueDate,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                // Inline Quick Course Note (for attendance rules, grading weights, open-book tips!)
                var editingInlineNote by remember(currentDetail.id) { mutableStateOf(false) }
                var noteDraft by remember(currentDetail.id, currentDetail.note) { mutableStateOf(currentDetail.note) }
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "课堂笔记 / 考核备忘",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            TextButton(
                                onClick = { editingInlineNote = !editingInlineNote },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text(
                                    text = if (editingInlineNote) "取消" else if (currentDetail.note.isBlank()) "+ 记一条" else "编辑",
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                        if (editingInlineNote) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                listOf("闭卷", "开卷", "论文考核", "平时40%期末60%", "考勤严格", "学习通签到").forEach { tag ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                noteDraft = if (noteDraft.isBlank()) tag
                                                else if (noteDraft.contains(tag)) noteDraft
                                                else "$noteDraft · $tag"
                                            }
                                    ) {
                                        Text(
                                            text = "+ $tag",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = noteDraft,
                                onValueChange = { noteDraft = it },
                                placeholder = { Text("如：平时分40%、学习通签到、期末开卷带A4纸...", fontSize = 12.sp) },
                                minLines = 2,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = {
                                        onUpdateCourseNote(currentDetail.id, noteDraft)
                                        detailCourse = currentDetail.copy(note = noteDraft.trim())
                                        editingInlineNote = false
                                    },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("保存笔记", fontSize = 12.sp)
                                }
                            }
                        } else if (currentDetail.note.isNotBlank()) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = currentDetail.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "点右上角随手记下老师考勤要求、平时分占比或考试范围",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                            )
                        }

                        // Subtle 1-row Attendance & Bonus Counter (super handy for tracking absences & participation!)
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AttendanceCounterPill(
                                label = "请假/缺勤",
                                count = currentDetail.absenceCount,
                                isWarn = currentDetail.absenceCount >= 2,
                                onDecrement = { onUpdateCourseAttendance(currentDetail.id, -1, 0) },
                                onIncrement = { onUpdateCourseAttendance(currentDetail.id, 1, 0) },
                                modifier = Modifier.weight(1f)
                            )
                            AttendanceCounterPill(
                                label = "发言/加分",
                                count = currentDetail.bonusCount,
                                isWarn = false,
                                onDecrement = { onUpdateCourseAttendance(currentDetail.id, 0, -1) },
                                onIncrement = { onUpdateCourseAttendance(currentDetail.id, 0, 1) },
                                modifier = Modifier.weight(1f)
                            )
                        }
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
                    val resolved = resolvedTimeMap[c.id] ?: GdouTimeRules.resolveCourseTime(c, settings.campus)
                    val palette = coursePaletteFor(c.colorIndex, isDark)
                    val isPinned = GdouTimeRules.courseConflictKey(c) in pinnedKeys
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
                                    text = if (isPinned) "★ ${c.name}" else c.name,
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
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        onTogglePinConflictCourse(c, overlappedCourses)
                                        conflictSheetData = null
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Rounded.PushPin, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(if (isPinned) "取消置顶" else "置顶显示", fontSize = 11.5.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        conflictSheetData = null
                                        detailCourse = c
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) { Text("查看详情", fontSize = 11.5.sp) }
                                FilledTonalButton(
                                    onClick = {
                                        conflictSheetData = null
                                        singleWeekShiftCourse = c
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) { Text("仅调第${selectedWeek}周", fontSize = 11.5.sp) }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    // All Conflicts Summary BottomSheet for selectedWeek (individually clickable course rows!)
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
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "点击任意课程可直接查看详情、编辑或「仅调本周」错开：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                weekConflictClusters.forEach { (day, cluster) ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${GdouTimeRules.dayOfWeekLabel(day)} 第${cluster.minStartSection}-${cluster.maxEndSection}节（${cluster.courses.size} 门课重叠）",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color(0xFFFFB085) else Color(0xFFC84B00)
                                )
                                TextButton(
                                    onClick = {
                                        showAllWeekConflictsSheet = false
                                        conflictSheetData = day to cluster.courses
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text("对比处理", fontSize = 11.5.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            cluster.courses.forEach { c ->
                                val r = GdouTimeRules.resolveCourseTime(c, settings.campus)
                                val pal = coursePaletteFor(c.colorIndex, isDark)
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            showAllWeekConflictsSheet = false
                                            detailCourse = c
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(9.dp)
                                                .clip(CircleShape)
                                                .background(pal.accent)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = c.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${r.startTime}-${r.endTime} · ${c.location.ifBlank { "地点待定" }}" +
                                                    if (r.isSpecialStaggered) " · 错峰" else "",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }

    if (showToolsMenu) {
        ScheduleToolboxBottomSheet(
            selectedWeek = selectedWeek,
            campusLabel = settings.campus.label,
            practiceCount = snapshot.practiceCourses.size,
            onOpenEmptyClassroom = onOpenEmptyClassroom,
            onOpenCourseSearch = { showCourseSearchSheet = true },
            onOpenFreeSlotMatcher = { showFreeSlotMatcher = true },
            onOpenBellSchedule = { showBellRefDialog = true },
            onOpenAddCourse = {
                addPrefillDay = selectedDayOfWeek
                addPrefillSection = 1
                editingCourse = null
                showAddCourseSheet = true
            },
            onExportWeekImage = { onExportWeekImage(selectedWeek) },
            onOpenHolidayShift = { showHolidayShiftDialog = true },
            onOpenShareCode = { showShareCodeDialog = true },
            onOpenCampusServices = onOpenCampusServices,
            onOpenPracticeCourses = { showPracticeCoursesDialog = true },
            onCopyWeekText = { onCopyWeekScheduleText(selectedWeek) },
            onDismiss = { showToolsMenu = false }
        )
    }

    // Full Semester Course Search & Credit Summary BottomSheet (with grouped/expanded toggle & 1-tap jump to week!)
    if (showCourseSearchSheet) {
        CourseSearchBottomSheet(
            courses = snapshot.courses,
            resolvedTimeMap = resolvedTimeMap,
            campus = settings.campus,
            isDark = isDark,
            onDismiss = { showCourseSearchSheet = false },
            onSelectCourse = { c ->
                showCourseSearchSheet = false
                detailCourse = c
            },
            onJumpToWeek = { targetWeek ->
                showCourseSearchSheet = false
                scope.launch {
                    pagerState.animateScrollToPage((targetWeek - 1).coerceIn(0, totalWeeks - 1))
                }
            }
        )
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

    // Offline Partner / Roommate Joint Free-Slot Matcher
    if (showFreeSlotMatcher) {
        FreeSlotMatcherBottomSheet(
            myCourses = snapshot.courses,
            initialWeek = selectedWeek,
            totalWeeks = totalWeeks,
            savedPartnerName = settings.savedPartnerName,
            savedPartnerCode = settings.savedPartnerCode,
            isDark = isDark,
            onParseShareCode = onParsePartnerShareCode,
            onSavePartnerProfile = onSavePartnerProfile,
            onShowMessage = onShowMessage,
            onDismiss = { showFreeSlotMatcher = false }
        )
    }

    if (showPracticeCoursesDialog && snapshot.practiceCourses.isNotEmpty()) {
        io.github.rex25415.haiyu.schedule.ui.components.PracticeCoursesBottomSheet(
            practiceCourses = snapshot.practiceCourses,
            onDismiss = { showPracticeCoursesDialog = false }
        )
    }

    // All Exams Sheet for the selected week when tapping the "本周 N 场考试" pill
    if (showAllWeekExamsSheet && selectedWeekExams.isNotEmpty()) {
        val sortedWeekExams = remember(selectedWeekExams) {
            selectedWeekExams.sortedWith(compareBy<ResolvedExamGridBlock> { it.date }.thenBy { it.startTime })
        }
        ModalBottomSheet(
            onDismissRequest = { showAllWeekExamsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "第 $selectedWeek 周全部考试（共 ${sortedWeekExams.size} 场）",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sortedWeekExams.forEach { exBlock ->
                        val ex = exBlock.exam
                        val daysLeft = ChronoUnit.DAYS.between(today, exBlock.date)
                        val badgeText = when {
                            daysLeft < 0L -> "已考完"
                            daysLeft == 0L -> "今天"
                            daysLeft == 1L -> "明天"
                            else -> "${daysLeft}天后"
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isDark) Color(0xFF472C10) else Color(0xFFFFF5E6),
                            border = BorderStroke(1.dp, (if (isDark) Color(0xFFF59E0B) else Color(0xFFD97706)).copy(alpha = 0.55f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAllWeekExamsSheet = false
                                    selectedExamDetail = exBlock
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = ex.courseName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDark) Color(0xFFFFE4BE) else Color(0xFF78350F),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = if (isDark) Color(0xFFF59E0B) else Color(0xFFD97706),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = badgeText,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${GdouTimeRules.dayOfWeekLabel(exBlock.dayOfWeek)} ${exBlock.date.monthValue}月${exBlock.date.dayOfMonth}日 · ${exBlock.startTime}-${exBlock.endTime}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = (if (isDark) Color(0xFFFFE4BE) else Color(0xFF78350F)).copy(alpha = 0.85f)
                                    )
                                    Text(
                                        text = buildString {
                                            append(ex.location.ifBlank { "考场待定" })
                                            if (ex.seatNumber.isNotBlank()) append(" · ${ex.seatNumber}号座")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isDark) Color(0xFFFFCA80) else Color(0xFF92400E)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = if (isDark) Color(0xFFFFCA80) else Color(0xFFB45309)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }

    // Exam Detail Dialog when tapping a projected exam card on the week grid
    selectedExamDetail?.let { exBlock ->
        val ex = exBlock.exam
        val daysLeft = ChronoUnit.DAYS.between(today, exBlock.date)
        val countdownLabel = when {
            daysLeft < 0L -> "已考完"
            daysLeft == 0L -> "就在今天 · 加油！"
            daysLeft == 1L -> "明天开考 · 记得带证"
            else -> "距开考还有 $daysLeft 天"
        }
        AlertDialog(
            onDismissRequest = { selectedExamDetail = null },
            title = { Text("${ex.courseName}（${ex.examType.ifBlank { "考试" }}）") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = if (isDark) Color(0xFF472C10) else Color(0xFFFFF0DB),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = countdownLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFFFFCA80) else Color(0xFFB45309),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                    DetailInfoRow(
                        icon = Icons.Rounded.AccessTime,
                        label = "考试时间",
                        value = ex.examTimeRaw.ifBlank { "${exBlock.date} ${exBlock.startTime}-${exBlock.endTime}" }
                    )
                    DetailInfoRow(
                        icon = Icons.Rounded.LocationOn,
                        label = "考场与座号",
                        value = buildString {
                            append(ex.location.ifBlank { "考场待定" })
                            if (ex.seatNumber.isNotBlank()) append(" · ${ex.seatNumber}号座")
                        }
                    )
                    if (ex.campus.isNotBlank()) {
                        DetailInfoRow(
                            icon = Icons.Rounded.School,
                            label = "校区",
                            value = ex.campus
                        )
                    }
                }
            },
                    confirmButton = {
                Button(onClick = { selectedExamDetail = null }) { Text("知道了") }
            }
        )
    }
}

