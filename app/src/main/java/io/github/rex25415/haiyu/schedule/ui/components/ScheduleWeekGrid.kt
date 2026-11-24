package io.github.rex25415.haiyu.schedule.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rex25415.haiyu.schedule.data.CourseOverlapCluster
import io.github.rex25415.haiyu.schedule.data.GdouTimeRules
import io.github.rex25415.haiyu.schedule.data.ResolvedCourseTime
import io.github.rex25415.haiyu.schedule.data.ResolvedExamGridBlock
import io.github.rex25415.haiyu.schedule.model.CourseItem
import io.github.rex25415.haiyu.schedule.model.GdouCampus
import io.github.rex25415.haiyu.schedule.model.GridRowDensity
import io.github.rex25415.haiyu.schedule.ui.theme.CourseCardPalette
import io.github.rex25415.haiyu.schedule.ui.theme.coursePaletteFor
import java.time.LocalDate

private data class WeekDayCellData(
    val outWeekCourses: List<CourseItem>,
    val activeClusters: List<CourseOverlapCluster>
)

@Composable
internal fun WeekGridScheduleView(
    coursesByDay: Map<Int, List<CourseItem>>,
    hasAnyWeekendCourse: Boolean,
    maxGridSection: Int,
    weekExams: List<ResolvedExamGridBlock>,
    resolvedTimeMap: Map<String, ResolvedCourseTime>,
    pinnedKeys: Set<String>,
    selectedWeek: Int,
    today: LocalDate,
    activeSectionNumber: Int?,
    startDateStr: String,
    campus: GdouCampus,
    rowDensity: GridRowDensity,
    showWeekend: Boolean,
    showNonCurrentWeek: Boolean,
    isDark: Boolean,
    backgroundBitmap: ImageBitmap?,
    cardAlpha: Float,
    onCourseClick: (CourseItem) -> Unit,
    onExamClick: (ResolvedExamGridBlock) -> Unit,
    onConflictClusterClick: (day: Int, overlappedCourses: List<CourseItem>) -> Unit,
    onEmptyCellClick: (day: Int, section: Int) -> Unit
) {
    val dayCount = if (showWeekend || hasAnyWeekendCourse) 7 else 5
    val slots = remember(campus, maxGridSection) { GdouTimeRules.slotsForCampus(campus, maxGridSection) }
    val weekDates = remember(startDateStr, selectedWeek) {
        GdouTimeRules.getWeekDates(startDateStr, selectedWeek)
    }
    val weekContainsToday = remember(weekDates, today) { weekDates.any { it == today } }
    val resolvedActiveSection = if (weekContainsToday) activeSectionNumber else null

    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.45f)
    val baseTimeW = if (dayCount == 7) 36f else 42f
    val timeColumnWidth = (baseTimeW * (1f + (fontScale - 1f) * 0.65f)).dp
    val breakGap = (rowDensity.breakGapDp * (1f + (fontScale - 1f) * 0.35f)).dp
    val totalBreaks = (if (slots.size > 4) 1 else 0) + (if (slots.size > 8) 1 else 0)

    val baseTextStyle = LocalTextStyle.current
    val tightTextStyle = remember(baseTextStyle) {
        baseTextStyle.copy(platformStyle = PlatformTextStyle(includeFontPadding = false))
    }

    // Precompute week placement across all 7 days once per week/toggle instead of 7 separate scans
    val weekColumnsData = remember(coursesByDay, dayCount, selectedWeek, showNonCurrentWeek, pinnedKeys) {
        (1..dayCount).map { day ->
            val allDay = coursesByDay[day].orEmpty()
            val inWeek = allDay.filter { selectedWeek in it.weeks }
            val activeClusters = GdouTimeRules.groupOverlappingCourses(inWeek, pinnedKeys)
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
            } else {
                emptyList()
            }
            WeekDayCellData(outWeekCourses = outWeek, activeClusters = activeClusters)
        }
    }
    val examsByDay = remember(weekExams) { weekExams.groupBy { it.dayOfWeek } }

    Column(modifier = Modifier.fillMaxSize()) {
        // Day-of-Week Header Row
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
                        text = GdouTimeRules.dayOfWeekLabel(day),
                        fontSize = if (dayCount == 7) 10.5.sp else 11.5.sp,
                        maxLines = 1,
                        softWrap = false,
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
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val viewportHeightDp = maxHeight.value
            val rowHeight = if (rowDensity == GridRowDensity.COMPACT && viewportHeightDp > 200f) {
                val availableForSlots = viewportHeightDp - breakGap.value * totalBreaks - 4f
                (availableForSlots / slots.size.coerceAtLeast(1)).coerceIn(33f, 48f).dp
            } else {
                (rowDensity.baseRowDp * (1f + (fontScale - 1f) * 0.45f)).dp
            }
            val totalGridHeight = rowHeight * slots.size + breakGap * totalBreaks
            val isTightRow = rowHeight < 46.dp

            fun yOffsetForSection(section: Int): Dp {
                val breaksBefore = (if (section > 4) 1 else 0) + (if (section > 8) 1 else 0)
                return rowHeight * (section - 1) + breakGap * breaksBefore
            }

            fun heightForSpan(startSection: Int, endSection: Int): Dp {
                val top = yOffsetForSection(startSection)
                val bottom = yOffsetForSection(endSection) + rowHeight
                return bottom - top
            }

            if (backgroundBitmap != null) {
                Image(
                    bitmap = backgroundBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isDark) Color.Black.copy(alpha = 0.42f)
                            else Color.White.copy(alpha = 0.28f)
                        )
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(end = 3.dp, bottom = if (rowDensity == GridRowDensity.COMPACT) 4.dp else 20.dp)
            ) {
                Column(modifier = Modifier.width(timeColumnWidth)) {
                    slots.forEach { slot ->
                        val isCurrentSlot = resolvedActiveSection == slot.number
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
                            Spacer(modifier = Modifier.height(if (isTightRow) 0.5.dp else 1.5.dp))
                            Text(
                                text = slot.startTime,
                                fontSize = if (isTightRow) 8.sp else 8.5.sp,
                                lineHeight = if (isTightRow) 9.sp else 10.sp,
                                style = tightTextStyle,
                                maxLines = 1,
                                softWrap = false,
                                fontWeight = if (isCurrentSlot) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isCurrentSlot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (rowHeight >= 37.dp) {
                                Text(
                                    text = slot.endTime,
                                    fontSize = if (isTightRow) 8.sp else 8.5.sp,
                                    lineHeight = if (isTightRow) 9.sp else 10.sp,
                                    style = tightTextStyle,
                                    maxLines = 1,
                                    softWrap = false,
                                    color = if (isCurrentSlot) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                )
                            }
                        }
                        if (slot.number == 4 || slot.number == 8) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(breakGap)
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (slot.number == 4) "午" else "晚",
                                    fontSize = 8.5.sp,
                                    lineHeight = 9.5.sp,
                                    style = tightTextStyle,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                for (dayIdx in 0 until dayCount) {
                    val day = dayIdx + 1
                    val dayData = weekColumnsData[dayIdx]
                    val outWeekCourses = dayData.outWeekCourses
                    val activeClusters = dayData.activeClusters

                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .height(totalGridHeight)
                    ) {
                        val colWidth = maxWidth
                        val canSideBySide = colWidth >= 96.dp

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

                        val cardHorizMargin = if (dayCount == 5) 1.5.dp else 0.8.dp
                        outWeekCourses.forEach { course ->
                            val clampedStart = course.startSection.coerceIn(1, slots.size)
                            val clampedEnd = course.endSection.coerceIn(clampedStart, slots.size)
                            val span = (clampedEnd - clampedStart + 1)
                            val topOffset = yOffsetForSection(clampedStart)
                            val blockHeight = heightForSpan(clampedStart, clampedEnd)
                            val resolved = resolvedTimeMap[course.id] ?: GdouTimeRules.resolveCourseTime(course, campus)
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
                                rowDensity = rowDensity,
                                fontScale = fontScale,
                                cardAlpha = cardAlpha,
                                tightTextStyle = tightTextStyle,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(blockHeight)
                                    .offset(y = topOffset)
                                    .padding(horizontal = cardHorizMargin, vertical = 1.5.dp),
                                onClick = { onCourseClick(course) }
                            )
                        }

                        activeClusters.forEach { cluster ->
                            if (!cluster.hasConflict) {
                                val course = cluster.courses.first()
                                val clampedStart = course.startSection.coerceIn(1, slots.size)
                                val clampedEnd = course.endSection.coerceIn(clampedStart, slots.size)
                                val span = (clampedEnd - clampedStart + 1)
                                val topOffset = yOffsetForSection(clampedStart)
                                val blockHeight = heightForSpan(clampedStart, clampedEnd)
                                val resolved = resolvedTimeMap[course.id] ?: GdouTimeRules.resolveCourseTime(course, campus)
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
                                    rowDensity = rowDensity,
                                    fontScale = fontScale,
                                    cardAlpha = cardAlpha,
                                    tightTextStyle = tightTextStyle,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(blockHeight)
                                        .offset(y = topOffset)
                                        .padding(horizontal = cardHorizMargin, vertical = 1.5.dp),
                                    onClick = { onCourseClick(course) }
                                )
                            } else if (canSideBySide) {
                                val count = cluster.courses.size.coerceAtMost(3)
                                val subW = colWidth / count
                                cluster.courses.take(count).forEachIndexed { idx, course ->
                                    val clampedStart = course.startSection.coerceIn(1, slots.size)
                                    val clampedEnd = course.endSection.coerceIn(clampedStart, slots.size)
                                    val span = (clampedEnd - clampedStart + 1)
                                    val topOffset = yOffsetForSection(clampedStart)
                                    val blockHeight = heightForSpan(clampedStart, clampedEnd)
                                    val resolved = resolvedTimeMap[course.id] ?: GdouTimeRules.resolveCourseTime(course, campus)
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
                                        rowDensity = rowDensity,
                                        fontScale = fontScale,
                                        cardAlpha = cardAlpha,
                                        tightTextStyle = tightTextStyle,
                                        modifier = Modifier
                                            .width(subW)
                                            .height(blockHeight)
                                            .offset(x = subW * idx, y = topOffset)
                                            .padding(1.dp),
                                        onClick = { onConflictClusterClick(day, cluster.courses) }
                                    )
                                }
                            } else {
                                val primary = cluster.courses.first()
                                val secondary = cluster.courses[1]
                                val clampedStart = cluster.minStartSection.coerceIn(1, slots.size)
                                val clampedEnd = cluster.maxEndSection.coerceIn(clampedStart, slots.size)
                                val span = (clampedEnd - clampedStart + 1)
                                val topOffset = yOffsetForSection(clampedStart)
                                val blockHeight = heightForSpan(clampedStart, clampedEnd)
                                val primaryResolved = resolvedTimeMap[primary.id] ?: GdouTimeRules.resolveCourseTime(primary, campus)
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
                                        color = secondaryPalette.container.copy(alpha = cardAlpha),
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
                                        rowDensity = rowDensity,
                                        fontScale = fontScale,
                                        cardAlpha = cardAlpha,
                                        tightTextStyle = tightTextStyle,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(end = 3.dp, bottom = 3.dp),
                                        onClick = { onConflictClusterClick(day, cluster.courses) }
                                    )
                                }
                            }
                        }

                        examsByDay[day].orEmpty().forEach { exBlock ->
                            val clampedStart = exBlock.startSection.coerceIn(1, slots.size)
                            val clampedEnd = exBlock.endSection.coerceIn(clampedStart, slots.size)
                            val topOffset = yOffsetForSection(clampedStart)
                            val blockHeight = heightForSpan(clampedStart, clampedEnd)
                            val examBg = (if (isDark) Color(0xFF472C10) else Color(0xFFFFF0DB)).copy(alpha = cardAlpha)
                            val examFg = if (isDark) Color(0xFFFFE4BE) else Color(0xFF78350F)
                            val examAccent = if (isDark) Color(0xFFF59E0B) else Color(0xFFD97706)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = examBg,
                                border = BorderStroke(1.3.dp, examAccent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(blockHeight)
                                    .offset(y = topOffset)
                                    .padding(horizontal = 1.5.dp, vertical = 1.5.dp)
                                    .clickable { onExamClick(exBlock) }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 3.5.dp, vertical = 4.5.dp),
                                    verticalArrangement = Arrangement.Top
                                ) {
                                    Surface(
                                        color = examAccent,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "考试 ${exBlock.startTime}",
                                            fontSize = 7.2.sp,
                                            lineHeight = 8.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = exBlock.exam.courseName,
                                        fontSize = when (rowDensity) {
                                            GridRowDensity.COMPACT -> if (dayCount == 5) 10.5.sp else 9.5.sp
                                            GridRowDensity.STANDARD -> if (dayCount == 5) 11.sp else 10.sp
                                            GridRowDensity.COMFORTABLE -> if (dayCount == 5) 12.5.sp else 11.5.sp
                                        },
                                        lineHeight = when (rowDensity) {
                                            GridRowDensity.COMPACT -> 11.sp
                                            GridRowDensity.STANDARD -> 12.sp
                                            GridRowDensity.COMFORTABLE -> 14.sp
                                        },
                                        style = tightTextStyle,
                                        fontWeight = FontWeight.Bold,
                                        color = examFg,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val roomSeat = buildString {
                                        append(exBlock.exam.location.ifBlank { "考场待定" })
                                        if (exBlock.exam.seatNumber.isNotBlank()) append("·${exBlock.exam.seatNumber}座")
                                    }
                                    Spacer(modifier = Modifier.height(if (rowDensity == GridRowDensity.COMPACT) 1.dp else 2.dp))
                                    Text(
                                        text = roomSeat,
                                        fontSize = when (rowDensity) {
                                            GridRowDensity.COMPACT -> 7.8.sp
                                            GridRowDensity.STANDARD -> 8.2.sp
                                            GridRowDensity.COMFORTABLE -> 9.2.sp
                                        },
                                        lineHeight = when (rowDensity) {
                                            GridRowDensity.COMPACT -> 9.sp
                                            GridRowDensity.STANDARD -> 9.5.sp
                                            GridRowDensity.COMFORTABLE -> 11.sp
                                        },
                                        style = tightTextStyle,
                                        fontWeight = FontWeight.Medium,
                                        color = examFg.copy(alpha = 0.82f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
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

@Composable
private fun WeekGridCourseCard(
    course: CourseItem,
    resolved: ResolvedCourseTime,
    palette: CourseCardPalette,
    isDark: Boolean,
    isInCurrentWeek: Boolean,
    conflictCount: Int,
    showInlineConflictTag: Boolean,
    dayCount: Int,
    span: Int,
    rowDensity: GridRowDensity = GridRowDensity.STANDARD,
    fontScale: Float,
    cardAlpha: Float,
    tightTextStyle: TextStyle,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val isConflict = conflictCount > 1 || showInlineConflictTag
    val verticalCardPad = when (rowDensity) {
        GridRowDensity.COMPACT -> 2.5.dp
        GridRowDensity.STANDARD -> 4.dp
        GridRowDensity.COMFORTABLE -> 5.dp
    }
    val titleFontSize = when (rowDensity) {
        GridRowDensity.COMPACT -> if (dayCount == 5) 10.8.sp else 9.8.sp
        GridRowDensity.STANDARD -> if (dayCount == 5) 11.5.sp else 10.5.sp
        GridRowDensity.COMFORTABLE -> if (dayCount == 5) 13.sp else 12.sp
    }
    val titleLineHeight = when (rowDensity) {
        GridRowDensity.COMPACT -> if (dayCount == 5) 12.5.sp else 11.3.sp
        GridRowDensity.STANDARD -> if (dayCount == 5) 14.sp else 12.5.sp
        GridRowDensity.COMFORTABLE -> if (dayCount == 5) 16.sp else 14.5.sp
    }
    val locFontSize = when (rowDensity) {
        GridRowDensity.COMPACT -> if (dayCount == 5) 8.8.sp else 8.2.sp
        GridRowDensity.STANDARD -> if (dayCount == 5) 9.5.sp else 8.8.sp
        GridRowDensity.COMFORTABLE -> if (dayCount == 5) 10.5.sp else 9.8.sp
    }
    val locLineHeight = when (rowDensity) {
        GridRowDensity.COMPACT -> 9.6.sp
        GridRowDensity.STANDARD -> 10.5.sp
        GridRowDensity.COMFORTABLE -> 12.sp
    }
    val bgColor = if (isInCurrentWeek) {
        palette.container.copy(alpha = cardAlpha)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = (if (isDark) 0.45f else 0.52f) * cardAlpha)
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

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        border = borderStroke,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (dayCount == 5) 5.dp else 2.2.dp, vertical = verticalCardPad),
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
                    Spacer(modifier = Modifier.height(if (rowDensity == GridRowDensity.COMPACT) 1.dp else 2.dp))
                    Surface(
                        color = palette.accent,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = resolved.startTime,
                            fontSize = 7.2.sp,
                            lineHeight = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFF0A1614) else Color.White,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.8.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(if (rowDensity == GridRowDensity.COMPACT) 1.dp else 2.dp))
            } else if (showInlineConflictTag) {
                Surface(
                    color = Color(0xFFD94A1E),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (resolved.isSpecialStaggered) "冲突 · ${resolved.startTime}" else "冲突",
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
                fontSize = titleFontSize,
                lineHeight = titleLineHeight,
                letterSpacing = if (dayCount == 7) (-0.25).sp else 0.sp,
                style = tightTextStyle,
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
                Spacer(modifier = Modifier.height(if (rowDensity == GridRowDensity.COMPACT) 1.5.dp else 2.5.dp))
                Text(
                    text = course.location,
                    fontSize = locFontSize,
                    lineHeight = locLineHeight,
                    letterSpacing = if (dayCount == 7) (-0.2).sp else 0.sp,
                    style = tightTextStyle,
                    fontWeight = FontWeight.Medium,
                    color = textColor.copy(alpha = 0.76f),
                    maxLines = when {
                        span <= 1 -> 1
                        rowDensity == GridRowDensity.COMPACT && conflictCount > 1 && span <= 2 -> 1
                        else -> 2
                    },
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
