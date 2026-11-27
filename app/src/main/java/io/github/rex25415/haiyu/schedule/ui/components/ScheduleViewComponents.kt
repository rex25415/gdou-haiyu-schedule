package io.github.rex25415.haiyu.schedule.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rex25415.haiyu.schedule.sync.CampusNetworkState
import io.github.rex25415.haiyu.schedule.sync.NetworkChannelProbe
import io.github.rex25415.haiyu.schedule.data.CourseOccurrenceStatus
import io.github.rex25415.haiyu.schedule.data.GdouTimeRules
import io.github.rex25415.haiyu.schedule.data.OccurrenceState
import io.github.rex25415.haiyu.schedule.data.ResolvedCourseTime
import io.github.rex25415.haiyu.schedule.data.ResolvedExamGridBlock
import io.github.rex25415.haiyu.schedule.data.ResolvedScheduledCourse
import io.github.rex25415.haiyu.schedule.data.TermWeekInfo
import io.github.rex25415.haiyu.schedule.data.TermWeekPhase
import io.github.rex25415.haiyu.schedule.model.CourseItem
import io.github.rex25415.haiyu.schedule.model.GdouCampus
import io.github.rex25415.haiyu.schedule.ui.theme.coursePaletteFor
import java.time.LocalDate

@Composable
fun DetailInfoRow(
    icon: ImageVector,
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
fun EmptyScheduleOnboarding(
    selectedCampus: GdouCampus,
    onSelectCampus: (GdouCampus) -> Unit,
    onJumpToSync: () -> Unit,
    onLoadDemoData: () -> Unit,
    onManualAdd: () -> Unit
) {
    val context = LocalContext.current
    var detectedProbe by remember { mutableStateOf<CampusNetworkState?>(null) }
    val initialCellularOnly = remember { NetworkChannelProbe.isCellularOnly(context) }
    LaunchedEffect(Unit) {
        detectedProbe = NetworkChannelProbe.probeCached(context).state
    }
    val onboardingNetHint = when (detectedProbe) {
        CampusNetworkState.ON_CAMPUS_DIRECT_READY -> "已接入校园网 · 将直接连接校内教务极速同步"
        CampusNetworkState.OFF_CAMPUS_ONLY -> "已识别校外网络 · 将经广海官方 WebVPN 免代理登录"
        CampusNetworkState.OFFLINE_OR_BLOCKED -> "暂未连通学校网络 · 请检查流量或校园网连接"
        CampusNetworkState.PROBING, null -> if (initialCellularOnly) {
            "当前为移动网络 · 将经广海官方 WebVPN 免代理同步"
        } else {
            "自动识别校园网与校外 WebVPN，无需额外配置代理"
        }
    }

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
                    text = "欢迎使用海屿空间",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "广东海洋大学全校区纯净开源学业与课表空间：无需校园网，支持校外流量/Wi-Fi 免 VPN 一键同步正方课表、成绩与考场，精准识别分楼错峰作息，零广告离线秒开。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(14.dp))

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
                        val selected = selectedCampus == campus
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectCampus(campus) },
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
                                    campus.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
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
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = onboardingNetHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
                    textAlign = TextAlign.Center
                )
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
fun AttendanceCounterPill(
    label: String,
    count: Int,
    isWarn: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isWarn) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 10.sp,
                    color = if (isWarn) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "${count} 次",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isWarn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(enabled = count > 0, onClick = onDecrement)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "−",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (count > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        )
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onIncrement)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "+",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NextClassHeroBanner(
    termWeekInfo: TermWeekInfo,
    todayStatuses: List<CourseOccurrenceStatus>,
    tomorrowCourses: List<ResolvedScheduledCourse>,
    todayDayOfWeek: Int,
    tomorrowDayOfWeek: Int,
    isCompactHeight: Boolean,
    isBannerCollapsed: Boolean = false,
    onToggleBannerCollapse: (() -> Unit)? = null,
    isDark: Boolean,
    onCourseClick: (CourseItem) -> Unit,
    onConflictClick: (Int, List<CourseItem>) -> Unit
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

    val tomorrowFirstGroup = if (active == null && termWeekInfo.phase == TermWeekPhase.IN_TERM && tomorrowCourses.isNotEmpty()) {
        val first = tomorrowCourses.first()
        tomorrowCourses.filter {
            it.course.startSection <= first.course.endSection &&
                first.course.startSection <= it.course.endSection
        }
    } else {
        emptyList()
    }
    val tomorrowFirst = tomorrowFirstGroup.firstOrNull()
    val hasTomorrowConflict = tomorrowFirstGroup.size > 1

    val startBg = if (isDark) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary
    val endBg = if (isDark) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.tertiary
    val fgColor = if (isDark) MaterialTheme.colorScheme.onPrimaryContainer else Color.White

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = if (isCompactHeight) 2.dp else 3.dp)
            .clip(RoundedCornerShape(if (isCompactHeight) 10.dp else 13.dp))
            .background(Brush.horizontalGradient(listOf(startBg, endBg)))
            .clickable(enabled = active != null || tomorrowFirst != null) {
                when {
                    active != null && hasActiveConflict -> onConflictClick(todayDayOfWeek, activeGroup.map { it.course })
                    active != null -> onCourseClick(active.course)
                    tomorrowFirst != null && hasTomorrowConflict -> onConflictClick(tomorrowDayOfWeek, tomorrowFirstGroup.map { it.course })
                    tomorrowFirst != null -> onCourseClick(tomorrowFirst.course)
                }
            }
            .padding(horizontal = 12.dp, vertical = if (isCompactHeight) 5.dp else 7.dp)
    ) {
        if (isCompactHeight && (active != null || tomorrowFirst != null)) {
            val badgeText = when {
                active != null && active.state == OccurrenceState.ONGOING -> "上课中·剩${active.minutesDelta}m"
                active != null -> {
                    val h = active.minutesDelta / 60
                    val m = active.minutesDelta % 60
                    if (h > 0) "下一节·${h}h${m}m后" else "下一节·${m}m后"
                }
                else -> "明日首节"
            }
            val titleText = active?.course?.name ?: tomorrowFirst!!.course.name
            val timeLoc = if (active != null) {
                "${active.time.startTime}-${active.time.endTime} · ${active.course.location.ifBlank { "地点待定" }}"
            } else {
                "${GdouTimeRules.dayOfWeekLabel(tomorrowDayOfWeek)} ${tomorrowFirst!!.time.startTime} · ${tomorrowFirst.course.location.ifBlank { "地点待定" }}"
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(color = fgColor.copy(alpha = 0.2f), shape = RoundedCornerShape(999.dp)) {
                    Text(
                        text = badgeText,
                        color = fgColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = titleText,
                    color = fgColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = timeLoc,
                    color = fgColor.copy(alpha = 0.9f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false
                )
                if (onToggleBannerCollapse != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(fgColor.copy(alpha = 0.18f))
                            .clickable(onClick = onToggleBannerCollapse)
                            .padding(2.dp)
                    ) {
                        Icon(
                            imageVector = if (isBannerCollapsed) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                            contentDescription = if (isBannerCollapsed) "展开预告卡片" else "收起预告卡片",
                            tint = fgColor,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
            return@Box
        }
        if (active == null && tomorrowFirst == null) {
            val idleText = when (termWeekInfo.phase) {
                TermWeekPhase.BEFORE_TERM -> "距离开学还有 ${termWeekInfo.daysUntilStart} 天，提前熟悉新学期课表吧"
                TermWeekPhase.AFTER_TERM -> "本学期已结束 · 假期愉快，好好休息充电~"
                TermWeekPhase.IN_TERM -> if (todayStatuses.isEmpty()) {
                    "今天与明天均无排课，好好享受校园时光"
                } else {
                    "今日 ${todayStatuses.size} 节课已全部上完，明天也无排课"
                }
            }
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
                    text = idleText,
                    color = fgColor,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else if (active == null && tomorrowFirst != null) {
            val tmrBadge = if (todayStatuses.isEmpty()) {
                "今日无课 · 明日首节"
            } else {
                "今日已结课 · 明日首节"
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
                                text = tmrBadge,
                                color = fgColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                            )
                        }
                        if (hasTomorrowConflict) {
                            Spacer(modifier = Modifier.width(5.dp))
                            Surface(
                                color = Color(0xFFD94A1E),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = "重×${tomorrowFirstGroup.size}",
                                    color = Color.White,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                        if (tomorrowFirst.time.isSpecialStaggered) {
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
                        text = if (hasTomorrowConflict) {
                            "${tomorrowFirst.course.name} 等 ${tomorrowFirstGroup.size} 门重课"
                        } else {
                            tomorrowFirst.course.name
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
                        text = "${GdouTimeRules.dayOfWeekLabel(tomorrowDayOfWeek)} ${tomorrowFirst.time.startTime}",
                        color = fgColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    )
                    Text(
                        text = if (hasTomorrowConflict) "点此切换查看" else tomorrowFirst.course.location.ifBlank { "地点待定" },
                        color = fgColor.copy(alpha = 0.88f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (onToggleBannerCollapse != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(fgColor.copy(alpha = 0.16f))
                            .clickable(onClick = onToggleBannerCollapse)
                            .padding(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ExpandLess,
                            contentDescription = "收起为极简预告条",
                            tint = fgColor,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        } else {
            val activeNow = active!!
            val statusBadge = when (activeNow.state) {
                OccurrenceState.ONGOING -> "上课中 · 剩${activeNow.minutesDelta}m"
                OccurrenceState.UPCOMING -> {
                    val h = activeNow.minutesDelta / 60
                    val m = activeNow.minutesDelta % 60
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
                        if (activeNow.time.isSpecialStaggered) {
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
                            "${activeNow.course.name} 等 ${activeGroup.size} 门重课"
                        } else {
                            activeNow.course.name
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
                        text = "${activeNow.time.startTime}-${activeNow.time.endTime}",
                        color = fgColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    )
                    Text(
                        text = if (hasActiveConflict) "点此切换查看" else activeNow.course.location.ifBlank { "地点待定" },
                        color = fgColor.copy(alpha = 0.88f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (onToggleBannerCollapse != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(fgColor.copy(alpha = 0.16f))
                            .clickable(onClick = onToggleBannerCollapse)
                            .padding(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ExpandLess,
                            contentDescription = "收起为极简预告条",
                            tint = fgColor,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DayTimelineScheduleView(
    courses: List<CourseItem>,
    weekExams: List<ResolvedExamGridBlock>,
    today: LocalDate,
    selectedWeek: Int,
    selectedDay: Int,
    onSelectDay: (Int) -> Unit,
    startDateStr: String,
    campus: GdouCampus,
    isDark: Boolean,
    onCourseClick: (CourseItem) -> Unit,
    onExamClick: (ResolvedExamGridBlock) -> Unit
) {
    val weekDates = remember(startDateStr, selectedWeek) {
        GdouTimeRules.getWeekDates(startDateStr, selectedWeek)
    }
    val dayExams = remember(weekExams, selectedDay) {
        weekExams.filter { it.dayOfWeek == selectedDay }.sortedBy { it.startTime }
    }
    val dayCourses = remember(courses, selectedWeek, selectedDay, campus) {
        courses
            .filter { it.dayOfWeek == selectedDay && selectedWeek in it.weeks }
            .map { it to GdouTimeRules.resolveCourseTime(it, campus) }
            .sortedWith(
                compareBy<Pair<CourseItem, ResolvedCourseTime>> { it.second.startTime }
                    .thenBy { it.first.startSection }
                    .thenBy { it.first.endSection }
                    .thenBy { it.first.name }
            )
    }
    val conflictingIds = remember(courses, selectedWeek) {
        GdouTimeRules.findConflictingCourseIds(courses, selectedWeek)
    }

    Column(modifier = Modifier.fillMaxSize()) {
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
                val hasExam = remember(weekExams, d) {
                    weekExams.any { it.dayOfWeek == d }
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
                                        hasExam && !isSelected -> Color(0xFFD97706)
                                        hasExam && isSelected -> Color(0xFFFFD580)
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

        if (dayCourses.isEmpty() && dayExams.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "${GdouTimeRules.dayOfWeekLabel(selectedDay)}没有排课与考试，好好充电或休息吧 ~",
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
                items(dayExams, key = { "exam_${it.exam.id}_${it.startTime}" }) { exBlock ->
                    val ex = exBlock.exam
                    val examBg = if (isDark) Color(0xFF472C10) else Color(0xFFFFF0DB)
                    val examFg = if (isDark) Color(0xFFFFE4BE) else Color(0xFF78350F)
                    val examAccent = if (isDark) Color(0xFFF59E0B) else Color(0xFFD97706)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = examBg),
                        border = BorderStroke(1.dp, examAccent.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onExamClick(exBlock) }
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
                                    text = exBlock.startTime,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = examAccent
                                )
                                Text(
                                    text = exBlock.endTime,
                                    fontSize = 12.sp,
                                    color = examFg.copy(alpha = 0.8f)
                                )
                                Surface(
                                    color = examAccent,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "考试",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(examAccent)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ex.courseName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = examFg,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = buildString {
                                        append(ex.location.ifBlank { "考场待定" })
                                        if (ex.seatNumber.isNotBlank()) append(" · ${ex.seatNumber}号座")
                                    },
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = examFg.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
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
