package io.github.rex25415.haiyu.schedule.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rex25415.haiyu.schedule.data.GdouTimeRules
import io.github.rex25415.haiyu.schedule.model.ClassroomDaySchedule
import io.github.rex25415.haiyu.schedule.model.ClassroomSlotStatus
import io.github.rex25415.haiyu.schedule.model.CourseItem

internal fun findLongestContinuousFreeStreak(slots: List<ClassroomSlotStatus>): Triple<Int, Int, Int> {
    var bestStart = -1
    var bestEnd = -1
    var bestLen = 0
    var curStart = -1
    var curEnd = -1
    var curLen = 0
    for (sl in slots) {
        if (sl.isFree) {
            if (curLen == 0) curStart = sl.startSection
            curEnd = sl.endSection
            curLen += (sl.endSection - sl.startSection + 1).coerceAtLeast(1)
            if (curLen > bestLen) {
                bestLen = curLen
                bestStart = curStart
                bestEnd = curEnd
            }
        } else {
            curLen = 0
        }
    }
    return Triple(bestStart, bestEnd, bestLen)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClassroomDayTimelineSheet(
    schedule: ClassroomDaySchedule,
    isLoading: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = schedule.roomName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    val meta = listOfNotNull(
                        schedule.campus.label,
                        schedule.building.takeIf { it.isNotBlank() },
                        schedule.roomType.takeIf { it.isNotBlank() },
                        schedule.seats.takeIf { it.isNotBlank() && it != "0" }?.let { "${it}座" }
                    ).joinToString(" · ")
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(
                    onClick = onToggleFavorite,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isFavorite) "已收藏" else "收藏教室", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Text(
                text = "第 ${schedule.weekNumber} 周 · ${GdouTimeRules.dayOfWeekLabel(schedule.dayOfWeek)} 全天占用/空闲时间轴",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            if (isLoading && schedule.slots.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("正在直连正方逐节检查全天 5 大节空课...", style = MaterialTheme.typography.bodySmall)
                }
            } else if (schedule.slots.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.48f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "暂未拉取到该教室今日 1-10 节完整时间轴（可能与教务接口限流或会话波动有关）",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        FilledTonalButton(
                            onClick = onRetry,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("重新检测全天 5 大节", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                val locForBell = schedule.building.ifBlank { schedule.roomName }
                val resolvedSlots = remember(schedule.slots, schedule.weekNumber, schedule.dayOfWeek, schedule.campus, locForBell) {
                    schedule.slots.map { slot ->
                        val resolved = GdouTimeRules.resolveCourseTime(
                            CourseItem(
                                name = "",
                                location = locForBell,
                                dayOfWeek = schedule.dayOfWeek,
                                startSection = slot.startSection,
                                endSection = slot.endSection,
                                weeks = listOf(schedule.weekNumber)
                            ),
                            schedule.campus
                        )
                        slot to resolved
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    resolvedSlots.forEach { (slot, resolved) ->
                        Surface(
                            color = if (slot.isFree) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "第 ${slot.startSection}-${slot.endSection} 节 · ${resolved.startTime}-${resolved.endTime}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (resolved.isSpecialStaggered && resolved.reason != null) {
                                        Text(
                                            text = resolved.reason,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Surface(
                                    color = if (slot.isFree) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = if (slot.isFree) "空闲可自习 ✓" else "已排课/占用",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (slot.isFree) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                val longestStreak = remember(schedule.slots) {
                    findLongestContinuousFreeStreak(schedule.slots)
                }

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (longestStreak.third > 0) {
                            "💡 连续自习建议：今日最长可连续自习第 ${longestStreak.first}-${longestStreak.second} 节（连空 ${longestStreak.third} 小节不赶人）"
                        } else {
                            "今天 1-10 节该教室均已排课或未开放，可在列表切换同楼层其他教室。"
                        },
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                    )
                }
            }
        }
    }
}
