package io.github.rex25415.haiyu.schedule.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rex25415.haiyu.schedule.data.GdouTimeRules
import io.github.rex25415.haiyu.schedule.model.AppSettings
import io.github.rex25415.haiyu.schedule.model.EmptyClassroomItem
import io.github.rex25415.haiyu.schedule.model.GdouCampus
import java.time.LocalDate
import java.time.LocalTime

private fun inferSmartSlotPair(campus: GdouCampus, now: LocalTime): Pair<Int, Int> {
    val slots = GdouTimeRules.slotsForCampus(campus, 10)
    val pairs = listOf(1 to 2, 3 to 4, 5 to 6, 7 to 8, 9 to 10)
    for ((s1, s2) in pairs) {
        val endSlot = slots.firstOrNull { it.number == s2 } ?: continue
        val et = GdouTimeRules.parseLocalTime(endSlot.endTime)
        if (!now.isAfter(et)) return s1 to s2
    }
    return 1 to 2
}

/**
 * Full-height native empty-classroom self-study finder living cleanly inside the Sync portal tab,
 * sharing the active Zhengfang session cookies with zero extra login or memory overhead.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmptyClassroomPane(
    settings: AppSettings,
    isLoggedIntoJw: Boolean,
    isQuerying: Boolean,
    results: List<EmptyClassroomItem>?,
    summary: String,
    onGoToWebLogin: () -> Unit,
    onExecuteQuery: (
        campusKeyword: String,
        weekNumber: Int,
        dayOfWeek: Int,
        startSection: Int,
        endSection: Int,
        buildingKeyword: String
    ) -> Unit,
    onOpenOriginalWeb: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val actualWeek = remember(settings.semesterStartDate, settings.totalWeeks) {
        GdouTimeRules.calculateCurrentWeek(settings.semesterStartDate, settings.totalWeeks)
    }
    val initialSlot = remember(settings.campus) {
        inferSmartSlotPair(settings.campus, LocalTime.now())
    }
    val todayDow = remember { LocalDate.now().dayOfWeek.value }

    var queryCampus by remember { mutableStateOf(settings.campus) }
    var queryWeek by remember(actualWeek) { mutableIntStateOf(actualWeek) }
    var queryDay by remember { mutableIntStateOf(todayDow) }
    var queryStartSec by remember { mutableIntStateOf(initialSlot.first) }
    var queryEndSec by remember { mutableIntStateOf(initialSlot.second) }
    var queryBuilding by remember(queryCampus) { mutableStateOf("") }
    var roomFilterQuery by remember { mutableStateOf("") }

    val buildingChoices = remember(queryCampus) {
        when (queryCampus) {
            GdouCampus.HUGUANG -> listOf("" to "全部楼栋", "广学" to "广学楼", "明德" to "明德楼", "钟海" to "钟海楼", "主楼" to "主楼")
            GdouCampus.HAIBIN -> listOf("" to "全部楼栋", "实验" to "实验楼", "教学" to "教学楼")
            GdouCampus.YANGJIANG -> listOf("" to "全部楼栋", "慎思" to "慎思楼", "海纳" to "海纳楼", "厚德" to "厚德楼")
        }
    }
    val slotPairs = remember { listOf(1 to 2, 3 to 4, 5 to 6, 7 to 8, 9 to 10) }

    val filteredRooms = remember(results, roomFilterQuery) {
        val raw = results ?: return@remember null
        val q = roomFilterQuery.trim()
        if (q.isEmpty()) raw else raw.filter {
            it.roomName.contains(q, ignoreCase = true) ||
                it.building.contains(q, ignoreCase = true) ||
                it.roomType.contains(q, ignoreCase = true)
        }
    }
    val groupedRooms = remember(filteredRooms) {
        filteredRooms
            ?.groupBy { it.building.ifBlank { "综合教学区" } }
            ?.entries
            ?.sortedByDescending { it.value.size }
            .orEmpty()
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (!isLoggedIntoJw) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onGoToWebLogin)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "尚未登录广海教务会话",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = "点此切回「教务网页」完成一次登录后，即可免校园网实时查全校自习室。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.86f)
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = "去登录",
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.MeetingRoom,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "筛选空闲自习教室",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable {
                                    val nowSlot = inferSmartSlotPair(queryCampus, LocalTime.now())
                                    queryWeek = actualWeek
                                    queryDay = LocalDate.now().dayOfWeek.value
                                    queryStartSec = nowSlot.first
                                    queryEndSec = nowSlot.second
                                    onShowMessage("已自动对齐第${actualWeek}周 · ${GdouTimeRules.dayOfWeekLabel(queryDay)} ${queryStartSec}-${queryEndSec}节")
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AccessTime,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "对齐此时此刻",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    // Campus row + Week stepper
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            GdouCampus.entries.forEach { cp ->
                                val sel = queryCampus == cp
                                Surface(
                                    color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
                                    shape = RoundedCornerShape(9.dp),
                                    border = if (sel) null else BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(9.dp))
                                        .clickable { queryCampus = cp }
                                ) {
                                    Text(
                                        text = cp.label.removeSuffix("校区"),
                                        fontSize = 11.5.sp,
                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                        color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 7.dp)
                                    )
                                }
                            }
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(9.dp),
                            border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "−",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { if (queryWeek > 1) queryWeek-- }
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                                Text(
                                    text = "第${queryWeek}周",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "+",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { if (queryWeek < settings.totalWeeks) queryWeek++ }
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    // Weekday Mon..Sun
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        for (d in 1..7) {
                            val sel = queryDay == d
                            Surface(
                                color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(8.dp),
                                border = if (sel) null else BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { queryDay = d }
                            ) {
                                Text(
                                    text = GdouTimeRules.dayOfWeekLabel(d).removePrefix("周"),
                                    fontSize = 12.sp,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 7.dp)
                                )
                            }
                        }
                    }

                    // Section slots
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        slotPairs.forEach { (s1, s2) ->
                            val sel = queryStartSec == s1 && queryEndSec == s2
                            Surface(
                                color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(8.dp),
                                border = if (sel) null else BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        queryStartSec = s1
                                        queryEndSec = s2
                                    }
                            ) {
                                Text(
                                    text = "${s1}-${s2}节",
                                    fontSize = 11.sp,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(vertical = 7.dp)
                                )
                            }
                        }
                    }

                    // Building filter pills
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        buildingChoices.forEach { (kw, label) ->
                            val sel = queryBuilding == kw
                            Surface(
                                color = if (sel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(
                                    0.7.dp,
                                    if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { queryBuilding = kw }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (sel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                if (!isLoggedIntoJw) {
                                    onShowMessage("请先在「教务网页」完成登录再点查询")
                                    onGoToWebLogin()
                                } else {
                                    onExecuteQuery(
                                        queryCampus.label.removeSuffix("校区"),
                                        queryWeek,
                                        queryDay,
                                        queryStartSec,
                                        queryEndSec,
                                        queryBuilding
                                    )
                                }
                            },
                            enabled = !isQuerying,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        ) {
                            if (isQuerying) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("正在直连正方查询...", fontWeight = FontWeight.Bold)
                            } else {
                                Text("立即查询空闲教室", fontWeight = FontWeight.Bold)
                            }
                        }
                        OutlinedButton(
                            onClick = onOpenOriginalWeb,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.height(42.dp)
                        ) {
                            Text("原网页", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        if (filteredRooms != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = summary.ifBlank { "共找到 ${filteredRooms.size} 间空闲教室" },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (!results.isNullOrEmpty()) {
                        OutlinedTextField(
                            value = roomFilterQuery,
                            onValueChange = { roomFilterQuery = it },
                            placeholder = { Text("秒搜教室号/楼层（如 505、阶梯）", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (filteredRooms.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "当前筛选时段暂无匹配空教室",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "可切换相邻节次、放宽楼栋过滤，或点击右侧「原网页」手动对比正方场地列表。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                groupedRooms.forEach { (buildingName, rooms) ->
                    item(key = "building_$buildingName") {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = buildingName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${rooms.size} 间",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    rooms.take(80).forEach { room ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = room.roomName,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                                if (room.seats.isNotBlank()) {
                                                    Spacer(modifier = Modifier.width(5.dp))
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "${room.seats}座",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
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
                }
            }
        }
    }
}
