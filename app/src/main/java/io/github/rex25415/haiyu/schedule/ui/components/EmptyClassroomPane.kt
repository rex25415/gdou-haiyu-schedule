package io.github.rex25415.haiyu.schedule.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rex25415.haiyu.schedule.data.GdouTimeRules
import io.github.rex25415.haiyu.schedule.model.AppSettings
import io.github.rex25415.haiyu.schedule.model.ClassroomDaySchedule
import io.github.rex25415.haiyu.schedule.model.CourseItem
import io.github.rex25415.haiyu.schedule.model.EmptyClassroomItem
import io.github.rex25415.haiyu.schedule.model.GdouCampus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private data class EmptySlotPreset(
    val startSection: Int,
    val endSection: Int,
    val label: String
)

private enum class RoomSizeFilter(val label: String) {
    ALL("不限座"),
    SMALL("小室 ≤60"),
    LARGE("大室 ≥100")
}

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

private val SPECIALIZED_ROOM_NAME_RE =
    Regex("实验室|实训|机房|语音|画室|琴房|多功能厅|咖啡|活动中心")
private val SPECIALIZED_ROOM_TYPE_RE =
    Regex("语音室|计算机房|多功能计算机房|设计教室|画室|绘图教室|艺术类|声乐|键盘|琴房|舞蹈|实验剧场")
private val SPECIALIZED_BUILDING_RE =
    Regex("工程训练中心|轮机实验楼|水产科技楼|第二实验综合楼|智能制造")
private val DIGIT_CHUNK_RE = Regex("\\d+")

/**
 * GDOU Zhengfang tags standard non-multimedia classrooms under cdlbmc="实验室" even in main teaching buildings
 * (such as 广学楼105, 钟海楼01003, 海滨教学楼103, 海滨实验楼308, 阳江博学楼A101).
 * We distinguish true general self-study classrooms from specialized labs/computer rooms/art studios.
 */
internal fun isPrimeSelfStudyClassroom(room: EmptyClassroomItem): Boolean {
    if (SPECIALIZED_ROOM_NAME_RE.containsMatchIn(room.roomName)) return false
    if (SPECIALIZED_ROOM_TYPE_RE.containsMatchIn(room.roomType)) return false
    if (SPECIALIZED_BUILDING_RE.containsMatchIn(room.building)) return false
    return true
}

internal fun naturalRoomSortKey(roomName: String): String {
    return DIGIT_CHUNK_RE.replace(roomName.trim()) { m ->
        m.value.padStart(6, '0')
    }
}

private fun presetBuildingsForCampus(campus: GdouCampus): List<Pair<String, String>> {
    return when (campus) {
        GdouCampus.HUGUANG -> listOf(
            "" to "全部楼栋",
            "广学" to "广学楼",
            "明德" to "明德楼",
            "钟海" to "钟海楼",
            "博文" to "博文楼",
            "兴教" to "兴教楼",
            "兴农" to "兴农楼",
            "兴海" to "兴海楼",
            "望海" to "望海楼",
            "图海" to "图海楼",
            "艺海" to "艺海楼",
            "海科" to "海科楼"
        )
        GdouCampus.HAIBIN -> listOf(
            "" to "全部楼栋",
            "教学" to "教学楼",
            "实验" to "实验楼",
            "电教" to "电教楼",
            "济海" to "济海楼"
        )
        GdouCampus.YANGJIANG -> listOf(
            "" to "全部楼栋",
            "慎思" to "慎思楼",
            "厚为" to "厚为楼",
            "博学" to "博学楼",
            "明辨" to "明辨楼",
            "海纳" to "海纳楼",
            "海科" to "海科楼一",
            "海行" to "海行楼",
            "济航" to "济航楼"
        )
    }
}

private fun buildingPriorityIndex(campus: GdouCampus, buildingName: String): Int {
    val priorities = when (campus) {
        GdouCampus.HUGUANG -> listOf("广学楼", "明德楼", "钟海楼", "博文楼", "兴教楼", "兴农楼", "兴海楼", "望海楼", "图海楼", "艺海楼", "海科楼")
        GdouCampus.HAIBIN -> listOf("教学楼", "实验楼", "电教楼", "济海楼")
        GdouCampus.YANGJIANG -> listOf("慎思楼", "厚为楼", "博学楼", "明辨楼", "海纳楼", "海科楼一", "海行楼", "济航楼")
    }
    val idx = priorities.indexOfFirst { buildingName.contains(it.removeSuffix("楼")) }
    return if (idx >= 0) idx else 99
}

private fun parseFavoriteBuildingSet(favorites: List<String>, campus: GdouCampus): Set<String> {
    val prefix = "${campus.id}|"
    return favorites.mapNotNull { entry ->
        if (entry.startsWith(prefix)) entry.removePrefix(prefix).trim().takeIf { it.isNotEmpty() } else null
    }.toSet()
}

private fun parseFavoriteClassrooms(favorites: List<String>, campus: GdouCampus): List<EmptyClassroomItem> {
    val prefix = "${campus.id}|"
    return favorites.mapNotNull { entry ->
        if (!entry.startsWith(prefix)) return@mapNotNull null
        val parts = entry.removePrefix(prefix).split("|")
        val b = parts.getOrNull(0).orEmpty().trim()
        val r = parts.getOrNull(1).orEmpty().trim()
        if (r.isEmpty()) return@mapNotNull null
        EmptyClassroomItem(
            roomName = r,
            building = b,
            seats = parts.getOrNull(2).orEmpty().trim(),
            roomType = parts.getOrNull(3).orEmpty().trim(),
            campus = campus.label
        )
    }
}

private fun isRoomFavorited(favorites: List<String>, campus: GdouCampus, building: String, roomName: String): Boolean {
    val prefix = "${campus.id}|${building.trim()}|${roomName.trim()}|"
    val legacy = "${campus.id}|${building.trim()}|${roomName.trim()}"
    return favorites.any { it.startsWith(prefix) || it == legacy }
}

private fun floorBucket(floor: Int?): Int? {
    if (floor == null || floor <= 0) return null
    return if (floor >= 5) 5 else floor
}

private fun floorLabel(bucket: Int): String = if (bucket >= 5) "5楼+" else "${bucket}楼"

/**
 * Full-height, intent-driven native empty-classroom self-study finder living cleanly inside the Sync portal tab,
 * supporting continuous block intersection ("上午连空/下午连空"), local schedule-context proximity sorting,
 * floor filtering, favorite buildings/rooms, and full-day 5-block classroom timeline inspection.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EmptyClassroomPane(
    settings: AppSettings,
    courses: List<CourseItem> = emptyList(),
    isLoggedIntoJw: Boolean,
    isQuerying: Boolean,
    results: List<EmptyClassroomItem>?,
    summary: String,
    inspectedSchedule: ClassroomDaySchedule? = null,
    isInspectingRoom: Boolean = false,
    onGoToWebLogin: () -> Unit,
    onExecuteQuery: (
        campusKeyword: String,
        weekNumber: Int,
        dayOfWeek: Int,
        startSection: Int,
        endSection: Int,
        buildingKeyword: String
    ) -> Unit,
    onInspectRoomDay: (
        campus: GdouCampus,
        weekNumber: Int,
        dayOfWeek: Int,
        buildingName: String,
        roomName: String,
        seats: String,
        roomType: String
    ) -> Unit = { _, _, _, _, _, _, _ -> },
    onDismissInspectedRoom: () -> Unit = {},
    onToggleFavoriteBuilding: (GdouCampus, String) -> Unit = { _, _ -> },
    onToggleFavoriteClassroom: (GdouCampus, String, String, String, String) -> Unit = { _, _, _, _, _ -> },
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
    var queryBuilding by remember { mutableStateOf("") }
    var onlySelfStudy by remember { mutableStateOf(true) }
    var sizeFilter by remember { mutableStateOf(RoomSizeFilter.ALL) }
    var floorFilter by remember { mutableStateOf<Int?>(null) }
    var roomFilterQuery by remember { mutableStateOf("") }
    var expandedExtraBuildings by remember(results) { mutableStateOf(setOf<String>()) }
    var collapsedPinnedBuildings by remember(results) { mutableStateOf(setOf<String>()) }
    var showAllBuildings by remember(results, queryCampus) { mutableStateOf(false) }
    var hasAutoQueriedOnEntry by remember { mutableStateOf(false) }

    val slotPresets = remember {
        listOf(
            EmptySlotPreset(1, 2, "1-2节"),
            EmptySlotPreset(3, 4, "3-4节"),
            EmptySlotPreset(5, 6, "5-6节"),
            EmptySlotPreset(7, 8, "7-8节"),
            EmptySlotPreset(9, 10, "9-10节"),
            EmptySlotPreset(1, 4, "上午连空(1-4)"),
            EmptySlotPreset(5, 8, "下午连空(5-8)")
        )
    }

    val favBuildings = remember(settings.favoriteBuildings, queryCampus) {
        parseFavoriteBuildingSet(settings.favoriteBuildings, queryCampus)
    }
    val favRooms = remember(settings.favoriteClassrooms, queryCampus) {
        parseFavoriteClassrooms(settings.favoriteClassrooms, queryCampus)
    }

    val pinnedKeys = remember(settings.pinnedConflictKeys) { settings.pinnedConflictKeys.toSet() }
    val courseContext = remember(
        courses,
        settings.semesterStartDate,
        settings.totalWeeks,
        queryCampus,
        queryWeek,
        queryDay,
        queryStartSec,
        pinnedKeys
    ) {
        GdouTimeRules.inferCourseLocationContext(
            courses = courses,
            startDateStr = settings.semesterStartDate,
            totalWeeks = settings.totalWeeks,
            targetCampus = queryCampus,
            targetWeek = queryWeek,
            targetDay = queryDay,
            queryStartSection = queryStartSec,
            now = LocalDateTime.now(),
            pinnedKeys = pinnedKeys
        )
    }

    fun triggerFullCampusQuery(
        targetCampus: GdouCampus = queryCampus,
        weekNum: Int = queryWeek,
        dayNum: Int = queryDay,
        s1: Int = queryStartSec,
        s2: Int = queryEndSec
    ) {
        if (!isLoggedIntoJw) {
            onShowMessage("请先在「教务登录与同步」完成一次登录，即可免校园网实时查全校自习室")
            onGoToWebLogin()
        } else {
            onExecuteQuery(
                targetCampus.label.removeSuffix("校区"),
                weekNum,
                dayNum,
                s1,
                s2,
                ""
            )
        }
    }

    // Automatically run initial query on entry when already logged in
    LaunchedEffect(isLoggedIntoJw) {
        if (isLoggedIntoJw && results == null && !isQuerying && !hasAutoQueriedOnceOrSet(hasAutoQueriedOnEntry)) {
            hasAutoQueriedOnEntry = true
            triggerFullCampusQuery()
        }
    }

    val buildingChoices = remember(queryCampus, favBuildings, courseContext) {
        val raw = presetBuildingsForCampus(queryCampus)
        val allChip = raw.first()
        val rest = raw.drop(1).sortedWith(
            compareByDescending<Pair<String, String>> { (_, label) -> label in favBuildings }
                .thenByDescending { (_, label) -> courseContext?.building == label }
        )
        listOf(allChip) + rest
    }

    val primeStudyCount = remember(results) {
        results?.count { isPrimeSelfStudyClassroom(it) } ?: 0
    }
    val totalVenueCount = results?.size ?: 0

    val categoryFilteredRooms = remember(results, onlySelfStudy) {
        val raw = results ?: return@remember null
        if (!onlySelfStudy) raw else raw.filter { isPrimeSelfStudyClassroom(it) }
    }

    val sizeFilteredRooms = remember(categoryFilteredRooms, sizeFilter) {
        val base = categoryFilteredRooms ?: return@remember null
        when (sizeFilter) {
            RoomSizeFilter.ALL -> base
            RoomSizeFilter.SMALL -> base.filter { r ->
                val seats = r.seats.toIntOrNull()
                seats != null && seats in 1..60
            }
            RoomSizeFilter.LARGE -> base.filter { r ->
                val seats = r.seats.toIntOrNull()
                seats != null && seats >= 100
            }
        }
    }

    val buildingCounts = remember(sizeFilteredRooms, buildingChoices) {
        val list = sizeFilteredRooms ?: return@remember emptyMap<String, Int>()
        buildingChoices.associate { (kw, _) ->
            val cnt = if (kw.isEmpty()) list.size else list.count {
                it.building.contains(kw, ignoreCase = true) || it.roomName.contains(kw, ignoreCase = true)
            }
            kw to cnt
        }
    }

    val buildingOnlyRooms = remember(sizeFilteredRooms, queryBuilding) {
        val base = sizeFilteredRooms ?: return@remember null
        val bKw = queryBuilding.trim()
        if (bKw.isEmpty()) base else base.filter {
            it.building.contains(bKw, ignoreCase = true) || it.roomName.contains(bKw, ignoreCase = true)
        }
    }

    val availableFloorBuckets = remember(buildingOnlyRooms, queryBuilding) {
        if (queryBuilding.isBlank() || buildingOnlyRooms.isNullOrEmpty()) {
            emptyList()
        } else {
            buildingOnlyRooms.mapNotNull { r ->
                floorBucket(GdouTimeRules.extractRoomFloor(r.roomName, r.building))
            }.distinct().sorted()
        }
    }

    val filteredRooms = remember(buildingOnlyRooms, floorFilter, roomFilterQuery) {
        val base = buildingOnlyRooms ?: return@remember null
        val byFloor = if (floorFilter == null) base else base.filter { r ->
            floorBucket(GdouTimeRules.extractRoomFloor(r.roomName, r.building)) == floorFilter
        }
        val q = roomFilterQuery.trim()
        if (q.isEmpty()) byFloor else byFloor.filter {
            it.roomName.contains(q, ignoreCase = true) ||
                it.building.contains(q, ignoreCase = true) ||
                it.roomType.contains(q, ignoreCase = true)
        }
    }

    val groupedRooms = remember(filteredRooms, queryCampus, favBuildings, courseContext) {
        val contextBuilding = courseContext?.building
        filteredRooms
            ?.groupBy { it.building.ifBlank { "综合教学区" } }
            ?.entries
            ?.sortedWith(
                compareByDescending<Map.Entry<String, List<EmptyClassroomItem>>> { it.key in favBuildings }
                    .thenByDescending { contextBuilding != null && it.key == contextBuilding }
                    .thenBy { buildingPriorityIndex(queryCampus, it.key) }
                    .thenByDescending { it.value.size }
            )
            ?.map { (bName, list) ->
                bName to list.sortedWith(
                    compareByDescending<EmptyClassroomItem> {
                        isRoomFavorited(settings.favoriteClassrooms, queryCampus, bName, it.roomName)
                    }.thenBy { naturalRoomSortKey(it.roomName) }
                )
            }
            .orEmpty()
    }

    // Same-floor siblings when a searched room is currently occupied
    val occupiedSearchSiblings = remember(roomFilterQuery, filteredRooms, sizeFilteredRooms, queryBuilding) {
        val q = roomFilterQuery.trim()
        if (q.isEmpty() || !filteredRooms.isNullOrEmpty()) return@remember null
        val inferredFloor = GdouTimeRules.extractRoomFloor(q, queryBuilding)
        val pool = buildingOnlyRooms ?: sizeFilteredRooms ?: return@remember null
        val sameFloor = if (inferredFloor != null) {
            pool.filter { GdouTimeRules.extractRoomFloor(it.roomName, it.building) == inferredFloor }
        } else {
            emptyList()
        }
        inferredFloor to sameFloor.sortedBy { naturalRoomSortKey(it.roomName) }.take(6)
    }

    // Current query time span badge label
    val currentSlotClockLabel = remember(queryCampus, queryStartSec, queryEndSec, queryBuilding) {
        val sampleLoc = if (queryBuilding.isNotBlank()) "${queryBuilding}楼" else ""
        val resolved = GdouTimeRules.resolveCourseTime(
            CourseItem(
                name = "",
                location = sampleLoc,
                dayOfWeek = queryDay,
                startSection = queryStartSec,
                endSection = queryEndSec,
                weeks = listOf(queryWeek)
            ),
            queryCampus
        )
        "${resolved.startTime}-${resolved.endTime}"
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 24.dp),
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
                                    text = "点此切回「教务登录与同步」完成一次登录后，即可跨校区实时查询湖光、海滨、阳江全楼栋空教室。",
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

        // Compact Query Control Card (~195dp vertical budget)
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Rounded.MeetingRoom,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "全校区空教室",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable {
                                        val nowSlot = inferSmartSlotPair(queryCampus, LocalTime.now())
                                        val nowDay = LocalDate.now().dayOfWeek.value
                                        queryWeek = actualWeek
                                        queryDay = nowDay
                                        queryStartSec = nowSlot.first
                                        queryEndSec = nowSlot.second
                                        onShowMessage("已对齐第${actualWeek}周 · ${GdouTimeRules.dayOfWeekLabel(nowDay)} ${nowSlot.first}-${nowSlot.second}节")
                                        if (isLoggedIntoJw) {
                                            triggerFullCampusQuery(queryCampus, actualWeek, nowDay, nowSlot.first, nowSlot.second)
                                        }
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AccessTime,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "此刻",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
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
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clickable {
                                                if (queryWeek > 1) {
                                                    val nextW = queryWeek - 1
                                                    queryWeek = nextW
                                                    if (isLoggedIntoJw && results != null) {
                                                        triggerFullCampusQuery(weekNum = nextW)
                                                    }
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                    Text(
                                        text = "第${queryWeek}周",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "+",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clickable {
                                                if (queryWeek < settings.totalWeeks) {
                                                    val nextW = queryWeek + 1
                                                    queryWeek = nextW
                                                    if (isLoggedIntoJw && results != null) {
                                                        triggerFullCampusQuery(weekNum = nextW)
                                                    }
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Campus 3-segment switcher
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.fillMaxWidth()
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
                                    .clickable {
                                        val changed = queryCampus != cp
                                        queryCampus = cp
                                        queryBuilding = ""
                                        floorFilter = null
                                        if (changed && isLoggedIntoJw) {
                                            triggerFullCampusQuery(targetCampus = cp)
                                        }
                                    }
                            ) {
                                Text(
                                    text = cp.label.removeSuffix("校区"),
                                    fontSize = 11.5.sp,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
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
                                    .clickable {
                                        val changed = queryDay != d
                                        queryDay = d
                                        if (changed && isLoggedIntoJw && results != null) {
                                            triggerFullCampusQuery(dayNum = d)
                                        }
                                    }
                            ) {
                                Text(
                                    text = GdouTimeRules.dayOfWeekLabel(d).removePrefix("周"),
                                    fontSize = 11.5.sp,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Single Horizontally Scrollable Slot Strip (2-section slots + continuous no-kick-out presets!)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        slotPresets.forEach { preset ->
                            val sel = queryStartSec == preset.startSection && queryEndSec == preset.endSection
                            val isContinuous = preset.endSection - preset.startSection >= 2
                            Surface(
                                color = when {
                                    sel -> MaterialTheme.colorScheme.primary
                                    isContinuous -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                    else -> MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = if (sel) null else BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val changed = queryStartSec != preset.startSection || queryEndSec != preset.endSection
                                        queryStartSec = preset.startSection
                                        queryEndSec = preset.endSection
                                        if (changed && isLoggedIntoJw) {
                                            triggerFullCampusQuery(s1 = preset.startSection, s2 = preset.endSection)
                                        }
                                    }
                            ) {
                                Text(
                                    text = preset.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (sel || isContinuous) FontWeight.Bold else FontWeight.Medium,
                                    color = when {
                                        sel -> MaterialTheme.colorScheme.onPrimary
                                        isContinuous -> MaterialTheme.colorScheme.onSecondaryContainer
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Single Horizontally Scrollable Building Strip (zero multi-row bloat!)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        buildingChoices.forEach { (kw, label) ->
                            val sel = queryBuilding == kw
                            val count = buildingCounts[kw]
                            val isFav = label in favBuildings
                            val isCourseMatch = courseContext?.building == label
                            val hasZero = results != null && kw.isNotEmpty() && count == 0
                            val displayLabel = buildString {
                                if (isFav) append("★ ") else if (isCourseMatch) append("📍")
                                append(label)
                                if (results != null && count != null) {
                                    append(if (hasZero) "(专/0)" else "($count)")
                                }
                            }
                            Surface(
                                color = when {
                                    sel -> MaterialTheme.colorScheme.secondaryContainer
                                    isFav || isCourseMatch -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    else -> MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(
                                    0.7.dp,
                                    if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .alpha(if (hasZero && !sel) 0.55f else 1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (hasZero && onlySelfStudy) {
                                            onlySelfStudy = false
                                            onShowMessage("该楼栋多为专业室/实验画室，已自动为您切至「含专业室」视图")
                                        }
                                        queryBuilding = kw
                                        floorFilter = null
                                    }
                            ) {
                                Text(
                                    text = displayLabel,
                                    fontSize = 11.sp,
                                    fontWeight = if (sel || isFav || isCourseMatch) FontWeight.Bold else FontWeight.Normal,
                                    color = if (sel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
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
                            onClick = { triggerFullCampusQuery() },
                            enabled = !isQuerying,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            if (isQuerying) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(15.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("正在拉取「${queryCampus.label}」...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Text(if (results == null) "立即查询空闲教室" else "刷新当前时段空教室", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        OutlinedButton(
                            onClick = onOpenOriginalWeb,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text("原网页", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Favorite Classrooms Quick Pick Row (shows whenever campus has bookmarked rooms)
        if (favRooms.isNotEmpty() && isLoggedIntoJw) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "★ 常去：",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    favRooms.forEach { fav ->
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .clickable {
                                    onInspectRoomDay(
                                        queryCampus,
                                        queryWeek,
                                        queryDay,
                                        fav.building,
                                        fav.roomName,
                                        fav.seats,
                                        fav.roomType
                                    )
                                }
                        ) {
                            Text(
                                text = fav.roomName + if (fav.seats.isNotBlank()) "(${fav.seats}座)" else "",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        if (filteredRooms != null) {
            // Course-Flow Nearby Strip (zero location permission, purely from user's own timetable!)
            if (courseContext != null) {
                val contextBuildingKey = courseContext.building.removeSuffix("楼")
                val sameBuildingCount = sizeFilteredRooms?.count {
                    it.building.contains(contextBuildingKey, ignoreCase = true) ||
                        it.roomName.contains(contextBuildingKey, ignoreCase = true)
                } ?: 0
                if (sameBuildingCount > 0) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f).padding(end = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.NearMe,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = courseContext.hintText,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${courseContext.building}本时段 $sameBuildingCount 间空闲 · 点击即直达同楼自习",
                                            fontSize = 10.5.sp,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                queryBuilding = contextBuildingKey
                                                floorFilter = null
                                            }
                                    ) {
                                        Text(
                                            text = "看${courseContext.building}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        )
                                    }
                                    val cFloor = floorBucket(courseContext.floor)
                                    if (cFloor != null) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    queryBuilding = contextBuildingKey
                                                    floorFilter = cFloor
                                                }
                                        ) {
                                            Text(
                                                text = floorLabel(cFloor),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
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

            item {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val headlineText = buildString {
                            append(queryCampus.label)
                            append(" · ")
                            if (queryBuilding.isNotBlank() || floorFilter != null) {
                                append("当前筛选共 ${filteredRooms.size} 间")
                            } else if (onlySelfStudy) {
                                append("共 ${filteredRooms.size} 间自习室（共 ${groupedRooms.size} 栋）")
                            } else {
                                append("共 ${filteredRooms.size} 间全场地（共 ${groupedRooms.size} 栋）")
                            }
                        }
                        Text(
                            text = headlineText,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = currentSlotClockLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (totalVenueCount > 0) {
                        // Single Scrollable Secondary Filter Strip (Category + Seat Size + Dynamic Floor)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(
                                true to "自习教室($primeStudyCount)",
                                false to "含专业室($totalVenueCount)"
                            ).forEach { (isPrimeOnly, chipText) ->
                                val selected = onlySelfStudy == isPrimeOnly
                                Surface(
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(
                                        0.7.dp,
                                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onlySelfStudy = isPrimeOnly }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        if (selected) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                        }
                                        Text(
                                            text = chipText,
                                            fontSize = 11.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }

                            RoomSizeFilter.entries.forEach { sf ->
                                val sel = sizeFilter == sf
                                Surface(
                                    color = if (sel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(
                                        0.7.dp,
                                        if (sel) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { sizeFilter = sf }
                                ) {
                                    Text(
                                        text = sf.label,
                                        fontSize = 11.sp,
                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (sel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // Dynamic Floor Strip (appears cleanly only when a single building has >=2 floors)
                        if (availableFloorBuckets.size >= 2) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "楼层：",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                (listOf<Int?>(null) + availableFloorBuckets).forEach { fl ->
                                    val sel = floorFilter == fl
                                    Surface(
                                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest,
                                        shape = RoundedCornerShape(7.dp),
                                        border = if (sel) null else BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(7.dp))
                                            .clickable { floorFilter = fl }
                                    ) {
                                        Text(
                                            text = if (fl == null) "全部楼层" else floorLabel(fl),
                                            fontSize = 11.sp,
                                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                            color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp)
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = roomFilterQuery,
                            onValueChange = { roomFilterQuery = it },
                            placeholder = { Text("搜教室号看今日全天空课表（如 505、208、阶梯）", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = if (roomFilterQuery.isNotEmpty()) {
                                {
                                    IconButton(onClick = { roomFilterQuery = "" }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Rounded.Close, contentDescription = "清空", modifier = Modifier.size(16.dp))
                                    }
                                }
                            } else null,
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
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val hasRoomSearch = roomFilterQuery.isNotBlank()
                            Text(
                                text = if (hasRoomSearch) {
                                    "「${roomFilterQuery.trim()}」当前筛选时段暂无空闲"
                                } else {
                                    "当前筛选条件下暂无匹配空教室"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (hasRoomSearch) {
                                    "可能该教室本时段刚好有课，可一键反查它今日 1-10 节哪几段空闲，或切换自习室/专业室类别。"
                                } else {
                                    "可点击上方「全部楼栋」/「不限座」/「含专业室」，或切换相邻节次对比。"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            if (hasRoomSearch && isLoggedIntoJw) {
                                Button(
                                    onClick = {
                                        onInspectRoomDay(
                                            queryCampus,
                                            queryWeek,
                                            queryDay,
                                            queryBuilding.takeIf { it.isNotBlank() }?.let { kw ->
                                                buildingChoices.firstOrNull { it.first == kw }?.second ?: kw
                                            }.orEmpty(),
                                            roomFilterQuery.trim(),
                                            "",
                                            ""
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("查「${roomFilterQuery.trim()}」今日全天哪几节空", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            val siblingFloor = occupiedSearchSiblings?.first
                            val siblings = occupiedSearchSiblings?.second.orEmpty()
                            if (siblingFloor != null && siblings.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                                Text(
                                    text = "同 ${floorLabel(siblingFloor)} 空房平替推荐：",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    siblings.forEach { sib ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    onInspectRoomDay(
                                                        queryCampus,
                                                        queryWeek,
                                                        queryDay,
                                                        sib.building,
                                                        sib.roomName,
                                                        sib.seats,
                                                        sib.roomType
                                                    )
                                                }
                                        ) {
                                            Text(
                                                text = sib.roomName + if (sib.seats.isNotBlank()) "(${sib.seats}座)" else "",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val useSmartCollapse = queryBuilding.isBlank() &&
                    roomFilterQuery.isBlank() &&
                    !showAllBuildings &&
                    groupedRooms.size > 6

                groupedRooms.forEachIndexed { idx, (buildingName, rooms) ->
                    val isFavBuilding = buildingName in favBuildings
                    val isCourseBuilding = courseContext?.building == buildingName
                    val defaultExpanded = !useSmartCollapse || isFavBuilding || isCourseBuilding || idx < 4
                    val isExpanded = if (defaultExpanded) {
                        buildingName !in collapsedPinnedBuildings
                    } else {
                        buildingName in expandedExtraBuildings
                    }
                    val targetFloor = if (isCourseBuilding) floorBucket(courseContext?.floor) else null

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
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            if (defaultExpanded) {
                                                collapsedPinnedBuildings = if (buildingName in collapsedPinnedBuildings) {
                                                    collapsedPinnedBuildings - buildingName
                                                } else {
                                                    collapsedPinnedBuildings + buildingName
                                                }
                                            } else {
                                                expandedExtraBuildings = if (buildingName in expandedExtraBuildings) {
                                                    expandedExtraBuildings - buildingName
                                                } else {
                                                    expandedExtraBuildings + buildingName
                                                }
                                            }
                                        }
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        IconButton(
                                            onClick = { onToggleFavoriteBuilding(queryCampus, buildingName) },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isFavBuilding) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                                contentDescription = "置顶常去楼栋",
                                                tint = if (isFavBuilding) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = buildingName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isCourseBuilding) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "课表同楼",
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${rooms.size} 间",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                if (isExpanded) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        rooms.forEach { room ->
                                            val shortRoomName = room.roomName
                                                .removePrefix(buildingName)
                                                .removePrefix(buildingName.removeSuffix("楼"))
                                                .trimStart('-', '—', ' ')
                                                .ifBlank { room.roomName }
                                            val isFavRoom = isRoomFavorited(settings.favoriteClassrooms, queryCampus, buildingName, room.roomName)
                                            val isSameFloorAsClass = targetFloor != null &&
                                                floorBucket(GdouTimeRules.extractRoomFloor(room.roomName, buildingName)) == targetFloor
                                            Surface(
                                                color = when {
                                                    isFavRoom -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                                                    isSameFloorAsClass -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                                                    else -> MaterialTheme.colorScheme.surfaceContainerLow
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(
                                                    if (isFavRoom || isSameFloorAsClass) 1.dp else 0.6.dp,
                                                    if (isFavRoom || isSameFloorAsClass) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                                ),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable {
                                                        onInspectRoomDay(
                                                            queryCampus,
                                                            queryWeek,
                                                            queryDay,
                                                            buildingName,
                                                            room.roomName,
                                                            room.seats,
                                                            room.roomType
                                                        )
                                                    }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    if (isFavRoom) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Star,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                    }
                                                    Text(
                                                        text = shortRoomName,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                    if (room.seats.isNotBlank() && room.seats != "0") {
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
                                    Text(
                                        text = "点击任意教室芯片可查看它今天 1-10 节全天空闲时间轴或收藏",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                                    )
                                }
                            }
                        }
                    }
                }

                if (useSmartCollapse) {
                    item {
                        OutlinedButton(
                            onClick = { showAllBuildings = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("一键展开全部 ${groupedRooms.size} 栋楼教室列表", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    if (inspectedSchedule != null) {
        ClassroomDayTimelineSheet(
            schedule = inspectedSchedule,
            isLoading = isInspectingRoom,
            isFavorite = isRoomFavorited(
                settings.favoriteClassrooms,
                inspectedSchedule.campus,
                inspectedSchedule.building,
                inspectedSchedule.roomName
            ),
            onToggleFavorite = {
                onToggleFavoriteClassroom(
                    inspectedSchedule.campus,
                    inspectedSchedule.building,
                    inspectedSchedule.roomName,
                    inspectedSchedule.seats,
                    inspectedSchedule.roomType
                )
            },
            onRetry = {
                onInspectRoomDay(
                    inspectedSchedule.campus,
                    inspectedSchedule.weekNumber,
                    inspectedSchedule.dayOfWeek,
                    inspectedSchedule.building,
                    inspectedSchedule.roomName,
                    inspectedSchedule.seats,
                    inspectedSchedule.roomType
                )
            },
            onDismiss = onDismissInspectedRoom
        )
    }
}

private fun hasAutoQueriedOnceOrSet(flag: Boolean): Boolean = flag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClassroomDayTimelineSheet(
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    schedule.slots.forEach { slot ->
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

                // Compute longest continuous free streak recommendation
                val longestStreak = remember(schedule.slots) {
                    var bestStart = -1
                    var bestEnd = -1
                    var bestLen = 0
                    var curStart = -1
                    var curEnd = -1
                    var curLen = 0
                    schedule.slots.forEach { sl ->
                        if (sl.isFree) {
                            if (curLen == 0) curStart = sl.startSection
                            curEnd = sl.endSection
                            curLen += 2
                            if (curLen > bestLen) {
                                bestLen = curLen
                                bestStart = curStart
                                bestEnd = curEnd
                            }
                        } else {
                            curLen = 0
                        }
                    }
                    Triple(bestStart, bestEnd, bestLen)
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
