package io.github.rex25415.haiyu.schedule.data

import io.github.rex25415.haiyu.schedule.model.CourseItem
import io.github.rex25415.haiyu.schedule.model.ExamItem
import io.github.rex25415.haiyu.schedule.model.GdouCampus
import io.github.rex25415.haiyu.schedule.model.GradeItem
import io.github.rex25415.haiyu.schedule.model.TimeSlot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

data class ResolvedCourseTime(
    val startTime: String,
    val endTime: String,
    val isSpecialStaggered: Boolean,
    val reason: String? = null
)

data class CourseOccurrenceStatus(
    val course: CourseItem,
    val time: ResolvedCourseTime,
    val state: OccurrenceState,
    val minutesDelta: Long
)

enum class OccurrenceState {
    ONGOING,
    UPCOMING,
    FINISHED
}

enum class TermWeekPhase {
    BEFORE_TERM,
    IN_TERM,
    AFTER_TERM
}

data class TermWeekInfo(
    val rawWeek: Int,
    val clampedWeek: Int,
    val phase: TermWeekPhase,
    val daysUntilStart: Long
)

data class ResolvedScheduledCourse(
    val course: CourseItem,
    val time: ResolvedCourseTime
)

data class CourseOverlapCluster(
    val courses: List<CourseItem>,
    val minStartSection: Int,
    val maxEndSection: Int
) {
    val hasConflict: Boolean get() = courses.size > 1
}

data class ResolvedExamGridBlock(
    val exam: ExamItem,
    val date: LocalDate,
    val startTime: String,
    val endTime: String,
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int
)

enum class JointFreeState {
    BOTH_FREE,
    ONLY_ME_BUSY,
    ONLY_PARTNER_BUSY,
    BOTH_BUSY
}

data class JointFreeCell(
    val dayOfWeek: Int,
    val blockIndex: Int, // 0..4 (1-2, 3-4, 5-6, 7-8, 9-10)
    val label: String,
    val state: JointFreeState
)

object GdouTimeRules {

    // 广东海洋大学全校统一标准作息时间（1-10节，2026 现行）
    val STANDARD_SLOTS: List<TimeSlot> = listOf(
        TimeSlot(1, "08:10", "08:55"),
        TimeSlot(2, "09:00", "09:45"),
        TimeSlot(3, "10:15", "11:00"),
        TimeSlot(4, "11:05", "11:50"),
        TimeSlot(5, "14:30", "15:15"),
        TimeSlot(6, "15:20", "16:05"),
        TimeSlot(7, "16:30", "17:15"),
        TimeSlot(8, "17:20", "18:05"),
        TimeSlot(9, "19:30", "20:15"),
        TimeSlot(10, "20:25", "21:10")
    )

    // 阳江校区基础作息（慎思楼）
    val YANGJIANG_SLOTS: List<TimeSlot> = listOf(
        TimeSlot(1, "08:10", "08:55"),
        TimeSlot(2, "09:00", "09:45"),
        TimeSlot(3, "10:25", "11:10"),
        TimeSlot(4, "11:15", "12:00"),
        TimeSlot(5, "14:30", "15:15"),
        TimeSlot(6, "15:20", "16:05"),
        TimeSlot(7, "16:20", "17:05"),
        TimeSlot(8, "17:10", "17:55"),
        TimeSlot(9, "19:30", "20:15"),
        TimeSlot(10, "20:25", "21:10")
    )

    private val EXTENDED_EVENING_SLOTS: List<TimeSlot> = listOf(
        TimeSlot(11, "21:15", "22:00"),
        TimeSlot(12, "22:05", "22:50")
    )

    private val HAIBIN_LAB_SHORT_REGEX = Regex("^实\\d")
    private val HAIBIN_OUTDOOR_KEYWORDS = listOf("场", "东面", "南面", "西面", "北面")
    private val HUGUANG_SPECIAL_VENUES = listOf("广学楼", "明德楼")
    private val TIME_HH_MM_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val PARSED_TIME_CACHE = ConcurrentHashMap<String, LocalTime>()

    fun courseConflictKey(course: CourseItem): String {
        return "${course.name.trim()}|${course.dayOfWeek}|${course.startSection}"
    }

    fun slotsForCampus(campus: GdouCampus, maxSection: Int = 10): List<TimeSlot> {
        val base = if (campus == GdouCampus.YANGJIANG) YANGJIANG_SLOTS else STANDARD_SLOTS
        return if (maxSection <= 10) {
            base
        } else {
            val extraCount = (maxSection - 10).coerceAtMost(EXTENDED_EVENING_SLOTS.size)
            base + EXTENDED_EVENING_SLOTS.take(extraCount)
        }
    }

    /**
     * 精确计算某门课的实际上下课时间，自动适配海滨 / 湖光 / 阳江错峰作息
     */
    fun resolveCourseTime(course: CourseItem, campus: GdouCampus): ResolvedCourseTime {
        if (!course.customStartTime.isNullOrBlank() && !course.customEndTime.isNullOrBlank()) {
            return ResolvedCourseTime(
                startTime = course.customStartTime,
                endTime = course.customEndTime,
                isSpecialStaggered = true,
                reason = "自定义/错峰时间"
            )
        }

        val loc = course.location.trim()
        val s = course.startSection
        val e = course.endSection

        when (campus) {
            GdouCampus.HAIBIN -> {
                val hitReason = when {
                    loc.contains("实验楼") -> "海滨实验楼错峰"
                    HAIBIN_LAB_SHORT_REGEX.containsMatchIn(loc) -> "海滨实验楼简写错峰"
                    HAIBIN_OUTDOOR_KEYWORDS.any { loc.contains(it) } -> "海滨场地/方位错峰"
                    else -> null
                }
                if (hitReason != null) {
                    if (s == 3 && e == 4) {
                        return ResolvedCourseTime("10:05", "11:35", true, hitReason)
                    }
                    if (s == 7 && e == 8) {
                        return ResolvedCourseTime("16:25", "17:50", true, hitReason)
                    }
                }
            }

            GdouCampus.HUGUANG -> {
                val hit = HUGUANG_SPECIAL_VENUES.firstOrNull { loc.contains(it) }
                if (hit != null) {
                    if (s == 3 && e == 4) {
                        return ResolvedCourseTime("10:05", "11:35", true, "湖光${hit}错峰")
                    }
                    if (s == 7 && e == 8) {
                        return ResolvedCourseTime("16:25", "17:50", true, "湖光${hit}错峰")
                    }
                }
            }

            GdouCampus.YANGJIANG -> {
                if (loc.isNotBlank() && !loc.contains("慎思楼") && s == 3 && e == 4) {
                    return ResolvedCourseTime("10:10", "11:40", true, "阳江非慎思楼错峰")
                }
            }
        }

        val slots = slotsForCampus(campus, maxOf(10, e))
        val start = slots.firstOrNull { it.number == s }?.startTime ?: "08:10"
        val end = slots.firstOrNull { it.number == e }?.endTime
            ?: slots.lastOrNull()?.endTime
            ?: "21:10"

        return ResolvedCourseTime(start, end, false, null)
    }

    /**
     * 将同一天在节次上有交集的课程合并为冲突簇（支持完全重叠、部分跨节重叠及多门连续重课），
     * 若用户对冲突簇内某门课设置了「优先显示（置顶）」，则自动将其排在簇首位。
     */
    fun groupOverlappingCourses(
        dayCourses: List<CourseItem>,
        pinnedKeys: Set<String> = emptySet()
    ): List<CourseOverlapCluster> {
        if (dayCourses.isEmpty()) return emptyList()
        val sorted = dayCourses.sortedWith(
            compareBy<CourseItem> { it.startSection }
                .thenBy { it.endSection }
                .thenBy { it.name }
        )
        val clusters = mutableListOf<CourseOverlapCluster>()
        var currentList = mutableListOf(sorted.first())
        var minStart = sorted.first().startSection
        var maxEnd = sorted.first().endSection

        fun prioritize(list: List<CourseItem>): List<CourseItem> {
            if (pinnedKeys.isEmpty() || list.size <= 1) return list
            return list.sortedWith(
                compareByDescending<CourseItem> { courseConflictKey(it) in pinnedKeys }
                    .thenBy { it.startSection }
                    .thenBy { it.endSection }
                    .thenBy { it.name }
            )
        }

        for (i in 1 until sorted.size) {
            val next = sorted[i]
            if (next.startSection <= maxEnd) {
                currentList.add(next)
                if (next.endSection > maxEnd) maxEnd = next.endSection
            } else {
                clusters.add(CourseOverlapCluster(prioritize(currentList.toList()), minStart, maxEnd))
                currentList = mutableListOf(next)
                minStart = next.startSection
                maxEnd = next.endSection
            }
        }
        clusters.add(CourseOverlapCluster(prioritize(currentList.toList()), minStart, maxEnd))
        return clusters
    }

    /**
     * 返回指定教学周中所有存在时间冲突（重课）的课程 ID 集合
     */
    fun findConflictingCourseIds(courses: List<CourseItem>, weekNumber: Int): Set<String> {
        val result = mutableSetOf<String>()
        for (day in 1..7) {
            val active = courses.filter { it.dayOfWeek == day && weekNumber in it.weeks }
            groupOverlappingCourses(active)
                .filter { it.hasConflict }
                .forEach { cluster -> cluster.courses.forEach { result.add(it.id) } }
        }
        return result
    }

    /**
     * 返回指定教学周的全部重课簇（Pair<星期几, 冲突簇>）
     */
    fun findConflictClustersForWeek(
        courses: List<CourseItem>,
        weekNumber: Int,
        pinnedKeys: Set<String> = emptySet()
    ): List<Pair<Int, CourseOverlapCluster>> {
        val list = mutableListOf<Pair<Int, CourseOverlapCluster>>()
        for (day in 1..7) {
            val active = courses.filter { it.dayOfWeek == day && weekNumber in it.weeks }
            groupOverlappingCourses(active, pinnedKeys)
                .filter { it.hasConflict }
                .forEach { list.add(day to it) }
        }
        return list
    }

    fun parseLocalDate(dateStr: String): LocalDate {
        return try {
            LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: Exception) {
            // Fallback to nearest past Monday
            LocalDate.now().with(DayOfWeek.MONDAY)
        }
    }

    fun parseLocalTime(timeStr: String): LocalTime {
        val key = timeStr.trim()
        PARSED_TIME_CACHE[key]?.let { return it }
        val parsed = try {
            LocalTime.parse(key, TIME_HH_MM_FORMATTER)
        } catch (_: Exception) {
            LocalTime.of(8, 10)
        }
        if (PARSED_TIME_CACHE.size < 128) {
            PARSED_TIME_CACHE[key] = parsed
        }
        return parsed
    }

    /**
     * 精确计算指定日期处于学期的真实阶段（开学前 / 学期中 / 学期已结束）及夹逼后的预览周次，
     * 避免寒暑假或开学前把假期误判为第 1 周或最后一周导致提醒与小组件误报。
     */
    fun calculateTermWeekInfo(
        startDateStr: String,
        totalWeeks: Int,
        today: LocalDate = LocalDate.now()
    ): TermWeekInfo {
        val safeTotal = totalWeeks.coerceAtLeast(1)
        val startMonday = parseLocalDate(startDateStr).with(DayOfWeek.MONDAY)
        val daysBetween = ChronoUnit.DAYS.between(startMonday, today)
        if (daysBetween < 0) {
            return TermWeekInfo(
                rawWeek = 0,
                clampedWeek = 1,
                phase = TermWeekPhase.BEFORE_TERM,
                daysUntilStart = -daysBetween
            )
        }
        val rawWeek = (daysBetween / 7).toInt() + 1
        val phase = if (rawWeek > safeTotal) TermWeekPhase.AFTER_TERM else TermWeekPhase.IN_TERM
        return TermWeekInfo(
            rawWeek = rawWeek,
            clampedWeek = rawWeek.coerceIn(1, safeTotal),
            phase = phase,
            daysUntilStart = 0L
        )
    }

    /**
     * 计算当前处于第几教学周（1-based 夹逼预览值，用于课表页定位）
     */
    fun calculateCurrentWeek(startDateStr: String, totalWeeks: Int, today: LocalDate = LocalDate.now()): Int {
        return calculateTermWeekInfo(startDateStr, totalWeeks, today).clampedWeek
    }

    fun getWeekDates(startDateStr: String, weekNumber: Int): List<LocalDate> {
        val startMonday = parseLocalDate(startDateStr).with(DayOfWeek.MONDAY)
        val weekMonday = startMonday.plusWeeks((weekNumber - 1).toLong())
        return (0L..6L).map { weekMonday.plusDays(it) }
    }

    fun dayOfWeekLabel(day: Int): String = when (day) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        7 -> "周日"
        else -> "周$day"
    }

    fun formatWeeksCompact(weeks: List<Int>): String {
        val sorted = weeks.distinct().sorted()
        if (sorted.isEmpty()) return "未设置周次"
        if (sorted.size == 1) return "第${sorted[0]}周"

        // Detect pure odd or pure even contiguous range
        val isAllOdd = sorted.all { it % 2 == 1 }
        val isAllEven = sorted.all { it % 2 == 0 }
        if ((isAllOdd || isAllEven) && sorted.size >= 3) {
            val step2 = (1 until sorted.size).all { sorted[it] - sorted[it - 1] == 2 }
            if (step2) {
                val tag = if (isAllOdd) "(单)" else "(双)"
                return "${sorted.first()}-${sorted.last()}周$tag"
            }
        }

        val ranges = mutableListOf<String>()
        var rangeStart = sorted[0]
        var prev = sorted[0]
        for (i in 1 until sorted.size) {
            if (sorted[i] == prev + 1) {
                prev = sorted[i]
            } else {
                ranges += if (rangeStart == prev) "$rangeStart" else "$rangeStart-$prev"
                rangeStart = sorted[i]
                prev = sorted[i]
            }
        }
        ranges += if (rangeStart == prev) "$rangeStart" else "$rangeStart-$prev"
        return ranges.joinToString(",") + "周"
    }

    /**
     * 查询某一天（如明天）在学期内的全部真实课程，按实际开课时间（含错峰）正序排列；
     * 若目标日期处于开学前或假期，直接返回空列表。
     */
    fun getCoursesForDate(
        courses: List<CourseItem>,
        startDateStr: String,
        totalWeeks: Int,
        campus: GdouCampus,
        date: LocalDate,
        pinnedKeys: Set<String> = emptySet()
    ): List<ResolvedScheduledCourse> {
        val weekInfo = calculateTermWeekInfo(startDateStr, totalWeeks, date)
        if (weekInfo.phase != TermWeekPhase.IN_TERM) return emptyList()
        val dow = date.dayOfWeek.value
        val active = courses.filter { it.dayOfWeek == dow && weekInfo.rawWeek in it.weeks }
        return groupOverlappingCourses(active, pinnedKeys)
            .flatMap { cluster ->
                cluster.courses
                    .map { c -> ResolvedScheduledCourse(c, resolveCourseTime(c, campus)) }
                    .sortedWith(
                        compareByDescending<ResolvedScheduledCourse> { courseConflictKey(it.course) in pinnedKeys }
                            .thenBy { it.time.startTime }
                            .thenBy { it.course.startSection }
                            .thenBy { it.course.name }
                    )
            }
    }

    fun getTodayCoursesWithStatus(
        courses: List<CourseItem>,
        startDateStr: String,
        totalWeeks: Int,
        campus: GdouCampus,
        now: LocalDateTime = LocalDateTime.now(),
        pinnedKeys: Set<String> = emptySet()
    ): List<CourseOccurrenceStatus> {
        val today = now.toLocalDate()
        val weekInfo = calculateTermWeekInfo(startDateStr, totalWeeks, today)
        if (weekInfo.phase != TermWeekPhase.IN_TERM) return emptyList()
        val currentWeek = weekInfo.rawWeek
        val todayDow = today.dayOfWeek.value // 1..7
        val currentTime = now.toLocalTime()

        val active = courses.filter { it.dayOfWeek == todayDow && currentWeek in it.weeks }
        return groupOverlappingCourses(active, pinnedKeys)
            .flatMap { it.courses }
            .map { course ->
                val resolved = resolveCourseTime(course, campus)
                val start = parseLocalTime(resolved.startTime)
                val end = parseLocalTime(resolved.endTime)
                val (state, delta) = when {
                    currentTime.isBefore(start) -> {
                        OccurrenceState.UPCOMING to ChronoUnit.MINUTES.between(currentTime, start)
                    }
                    !currentTime.isAfter(end) -> {
                        OccurrenceState.ONGOING to ChronoUnit.MINUTES.between(currentTime, end)
                    }
                    else -> {
                        OccurrenceState.FINISHED to ChronoUnit.MINUTES.between(end, currentTime)
                    }
                }
                CourseOccurrenceStatus(course, resolved, state, delta)
            }
    }

    /**
     * 解析正方考试时间字符串中的开始日期时间，例如 "2026-12-28(09:00-11:00)" 或 "2026-12-28 09:00-11:00"
     */
    fun parseExamStartDateTime(raw: String): LocalDateTime? {
        val window = parseExamTimeWindow(raw) ?: return null
        return LocalDateTime.of(window.first, window.second)
    }

    /**
     * 解析考试时间段，返回 (日期, 开始时间, 结束时间)；兼容 "2026-12-28(08:30-10:30)"、"2026-12-28 14:30-16:30" 等格式
     */
    fun parseExamTimeWindow(raw: String): Triple<LocalDate, LocalTime, LocalTime>? {
        val dateMatch = Regex("(\\d{4})[-/.年](\\d{1,2})[-/.月](\\d{1,2})").find(raw) ?: return null
        val year = dateMatch.groupValues[1].toIntOrNull() ?: return null
        val month = dateMatch.groupValues[2].toIntOrNull() ?: return null
        val day = dateMatch.groupValues[3].toIntOrNull() ?: return null
        val date = try {
            LocalDate.of(year, month, day)
        } catch (_: Exception) {
            return null
        }

        val times = Regex("(\\d{1,2}):(\\d{2})").findAll(raw).toList()
        val start = if (times.isNotEmpty()) {
            val h = times[0].groupValues[1].toIntOrNull()?.coerceIn(0, 23) ?: 9
            val m = times[0].groupValues[2].toIntOrNull()?.coerceIn(0, 59) ?: 0
            LocalTime.of(h, m)
        } else {
            LocalTime.of(9, 0)
        }
        val end = if (times.size >= 2) {
            val h = times[1].groupValues[1].toIntOrNull()?.coerceIn(0, 23) ?: start.hour
            val m = times[1].groupValues[2].toIntOrNull()?.coerceIn(0, 59) ?: start.minute
            val parsedEnd = LocalTime.of(h, m)
            if (parsedEnd.isAfter(start)) parsedEnd else start.plusHours(2)
        } else {
            start.plusHours(2)
        }
        return Triple(date, start, end)
    }

    /**
     * 将落入指定教学周内的考试安排投影为周课表节次块，期末周看课表也能一眼看到考试时间与座号
     */
    fun resolveExamsForWeek(
        exams: List<ExamItem>,
        startDateStr: String,
        weekNumber: Int,
        campus: GdouCampus,
        maxSection: Int = 10
    ): List<ResolvedExamGridBlock> {
        if (exams.isEmpty()) return emptyList()
        val weekDates = getWeekDates(startDateStr, weekNumber)
        val firstDay = weekDates.first()
        val lastDay = weekDates.last()
        val slots = slotsForCampus(campus, maxSection.coerceIn(10, 12))

        val result = mutableListOf<ResolvedExamGridBlock>()
        for (exam in exams) {
            val (date, start, end) = parseExamTimeWindow(exam.examTimeRaw) ?: continue
            if (date.isBefore(firstDay) || date.isAfter(lastDay)) continue
            val dow = date.dayOfWeek.value

            // Match strictly overlapping slots (touching boundary e.g. end=16:30 & slot7 start=16:30 does NOT overlap!)
            val overlapped = slots.filter { s ->
                val ss = parseLocalTime(s.startTime)
                val se = parseLocalTime(s.endTime)
                se.isAfter(start) && ss.isBefore(end)
            }
            val (startSec, endSec) = if (overlapped.isNotEmpty()) {
                val s = overlapped.first().number
                val e = maxOf(overlapped.last().number, (s + 1).coerceAtMost(slots.size))
                s to e
            } else {
                val nearest = slots.minByOrNull { s ->
                    kotlin.math.abs(ChronoUnit.MINUTES.between(parseLocalTime(s.startTime), start))
                }?.number ?: 1
                nearest to (nearest + 1).coerceAtMost(slots.size)
            }
            result.add(
                ResolvedExamGridBlock(
                    exam = exam,
                    date = date,
                    startTime = start.format(TIME_HH_MM_FORMATTER),
                    endTime = end.format(TIME_HH_MM_FORMATTER),
                    dayOfWeek = dow,
                    startSection = startSec,
                    endSection = endSec
                )
            )
        }
        return result.sortedWith(compareBy<ResolvedExamGridBlock> { it.dayOfWeek }.thenBy { it.startTime })
    }

    /**
     * 纯离线双人/室友「空课交集」计算（5 个大节 × 7 天）
     */
    fun computeJointFreeMatrix(
        myCourses: List<CourseItem>,
        partnerCourses: List<CourseItem>,
        weekNumber: Int
    ): List<JointFreeCell> {
        val blocks = listOf(
            Triple(0, "1-2节", 1..2),
            Triple(1, "3-4节", 3..4),
            Triple(2, "5-6节", 5..6),
            Triple(3, "7-8节", 7..8),
            Triple(4, "9-10节", 9..10)
        )
        fun isBusy(courses: List<CourseItem>, day: Int, range: IntRange): Boolean {
            return courses.any { c ->
                c.dayOfWeek == day &&
                    weekNumber in c.weeks &&
                    c.startSection <= range.last &&
                    range.first <= c.endSection
            }
        }
        val list = mutableListOf<JointFreeCell>()
        for (day in 1..7) {
            for ((idx, label, range) in blocks) {
                val me = isBusy(myCourses, day, range)
                val other = isBusy(partnerCourses, day, range)
                val state = when {
                    !me && !other -> JointFreeState.BOTH_FREE
                    me && !other -> JointFreeState.ONLY_ME_BUSY
                    !me && other -> JointFreeState.ONLY_PARTNER_BUSY
                    else -> JointFreeState.BOTH_BUSY
                }
                list.add(JointFreeCell(dayOfWeek = day, blockIndex = idx, label = label, state = state))
            }
        }
        return list
    }

    /**
     * 一键生成整洁纯文本周课表（适合发微信/QQ 给室友或家人）
     */
    fun formatWeekScheduleAsText(
        courses: List<CourseItem>,
        weekNumber: Int,
        campus: GdouCampus,
        termLabel: String,
        pinnedKeys: Set<String> = emptySet()
    ): String {
        val sb = StringBuilder()
        sb.appendLine("【海屿空间】第${weekNumber}周（${campus.label} · $termLabel）")
        var anyCourse = false
        for (day in 1..7) {
            val active = courses.filter { it.dayOfWeek == day && weekNumber in it.weeks }
            if (active.isEmpty()) continue
            anyCourse = true
            sb.appendLine("${dayOfWeekLabel(day)}：")
            groupOverlappingCourses(active, pinnedKeys)
                .flatMap { it.courses }
                .forEach { c ->
                    val t = resolveCourseTime(c, campus)
                    val loc = c.location.ifBlank { "地点待定" }
                    val stag = if (t.isSpecialStaggered) " [错峰]" else ""
                    sb.appendLine("  • 第${c.startSection}-${c.endSection}节 ${t.startTime}-${t.endTime}$stag  ${c.name} @ $loc")
                }
        }
        if (!anyCourse) sb.appendLine("本周暂无排课安排")
        return sb.toString().trim()
    }

    /**
     * 一键生成整洁纯文本考试清单
     */
    fun formatExamsAsText(exams: List<ExamItem>, termLabel: String): String {
        val now = LocalDateTime.now()
        val sorted = exams.sortedWith(
            compareBy<ExamItem> {
                val dt = parseExamStartDateTime(it.examTimeRaw)
                if (dt == null) 1 else if (dt.isBefore(now)) 2 else 0
            }.thenBy { parseExamStartDateTime(it.examTimeRaw)?.toString() ?: "9999" }
        )
        val sb = StringBuilder()
        sb.appendLine("【海屿空间 · 考试清单】$termLabel（共 ${sorted.size} 场）")
        sorted.forEachIndexed { i, ex ->
            val seat = if (ex.seatNumber.isNotBlank()) " · ${ex.seatNumber}号座" else ""
            val loc = ex.location.ifBlank { "考场待定" }
            val time = ex.examTimeRaw.ifBlank { "时间待定" }
            sb.appendLine("${i + 1}. ${ex.courseName}｜$time｜$loc$seat")
        }
        return sb.toString().trim()
    }

    /**
     * 纯本地目标 GPA 反推计算（按广海 5 分制：score ≈ gpa * 10 + 50）
     */
    data class TargetGpaPlan(
        val alreadyReached: Boolean,
        val achievable: Boolean,
        val requiredFutureGpa: Double,
        val estimatedScore: Double,
        val summaryText: String
    )

    fun calculateTargetGpaPlan(
        currentGpa: Double,
        currentCredits: Double,
        targetGpa: Double,
        plannedFutureCredits: Double
    ): TargetGpaPlan {
        val safeCurCr = currentCredits.coerceAtLeast(0.0)
        val safeFutureCr = plannedFutureCredits.coerceAtLeast(0.5)
        val safeTarget = targetGpa.coerceIn(0.0, 5.0)
        if (safeCurCr <= 0.0) {
            val est = (safeTarget * 10.0 + 50.0).coerceIn(60.0, 100.0)
            return TargetGpaPlan(
                alreadyReached = false,
                achievable = true,
                requiredFutureGpa = safeTarget,
                estimatedScore = est,
                summaryText = String.format(
                    java.util.Locale.US,
                    "后续 %.1f 学分平均达到 GPA %.2f（约 %.0f 分）即可达成",
                    safeFutureCr, safeTarget, est
                )
            )
        }
        val neededGpa = ((safeTarget * (safeCurCr + safeFutureCr)) - (currentGpa * safeCurCr)) / safeFutureCr
        return when {
            neededGpa <= 0.0 -> TargetGpaPlan(
                alreadyReached = true,
                achievable = true,
                requiredFutureGpa = 0.0,
                estimatedScore = 60.0,
                summaryText = String.format(
                    java.util.Locale.US,
                    "当前总绩点（%.2f）已高于目标，后续 %.1f 学分正常及格即可稳稳保持",
                    currentGpa, safeFutureCr
                )
            )
            neededGpa > 5.0 -> {
                val extraCreditsAt40 = if (safeTarget < 4.0) {
                    ((safeTarget - currentGpa) * safeCurCr / (4.0 - safeTarget)).coerceAtLeast(1.0)
                } else null
                val hint = if (extraCreditsAt40 != null) {
                    String.format(
                        java.util.Locale.US,
                        "仅靠 %.1f 学分需 GPA %.2f（超过满绩 5.0）；若后续按均绩 4.0（90分）计算，约需 %.0f 学分可拉至 %.2f",
                        safeFutureCr, neededGpa, extraCreditsAt40, safeTarget
                    )
                } else {
                    String.format(
                        java.util.Locale.US,
                        "仅靠 %.1f 学分需 GPA %.2f（超过满绩 5.0），可适当调高预计后续学分或分阶段设定目标",
                        safeFutureCr, neededGpa
                    )
                }
                TargetGpaPlan(
                    alreadyReached = false,
                    achievable = false,
                    requiredFutureGpa = neededGpa,
                    estimatedScore = 100.0,
                    summaryText = hint
                )
            }
            else -> {
                val estScore = if (neededGpa < 1.0) 60.0 else (neededGpa * 10.0 + 50.0).coerceIn(60.0, 100.0)
                val prefix = if (currentGpa >= safeTarget) "当前已达标；后续" else "后续"
                TargetGpaPlan(
                    alreadyReached = currentGpa >= safeTarget,
                    achievable = true,
                    requiredFutureGpa = neededGpa,
                    estimatedScore = estScore,
                    summaryText = String.format(
                        java.util.Locale.US,
                        "%s %.1f 学分平均达到 GPA %.2f（广海 5 分制约合 %.1f 分）即可达到 %.2f",
                        prefix, safeFutureCr, neededGpa, estScore, safeTarget
                    )
                )
            }
        }
    }

    /**
     * 广东海洋大学三校区专属演示课表（自动按所选湖光 / 海滨 / 阳江校区展示真实教学楼与错峰规则）
     */
    fun createDemoCourses(campus: GdouCampus): List<CourseItem> {
        val allWeeks = (1..16).toList()
        val oddWeeks = (1..15 step 2).toList()
        val evenWeeks = (2..16 step 2).toList()
        return when (campus) {
            GdouCampus.HUGUANG -> listOf(
                CourseItem(
                    name = "高等数学A(上)",
                    teacher = "陈明远",
                    location = "钟海楼02016",
                    dayOfWeek = 1,
                    startSection = 1,
                    endSection = 2,
                    weeks = allWeeks,
                    colorIndex = 0,
                    credit = "4.5"
                ),
                CourseItem(
                    name = "大学物理(一)",
                    teacher = "林海涛",
                    location = "广学楼302",
                    dayOfWeek = 1,
                    startSection = 3,
                    endSection = 4,
                    weeks = allWeeks,
                    colorIndex = 1,
                    credit = "3.5",
                    note = "示例：自动识别湖光广学楼错峰 10:05-11:35"
                ),
                CourseItem(
                    name = "大学英语(三)",
                    teacher = "周雅婷",
                    location = "钟海楼03012",
                    dayOfWeek = 2,
                    startSection = 5,
                    endSection = 6,
                    weeks = allWeeks,
                    colorIndex = 2,
                    credit = "3.0"
                ),
                CourseItem(
                    name = "线性代数",
                    teacher = "黄志强",
                    location = "明德楼205",
                    dayOfWeek = 2,
                    startSection = 7,
                    endSection = 8,
                    weeks = allWeeks,
                    colorIndex = 3,
                    credit = "3.0",
                    note = "示例：自动识别湖光明德楼错峰 16:25-17:50"
                ),
                CourseItem(
                    name = "海洋科学导论",
                    teacher = "梁国华",
                    location = "兴海楼208",
                    dayOfWeek = 3,
                    startSection = 1,
                    endSection = 2,
                    weeks = allWeeks,
                    colorIndex = 4,
                    credit = "2.0"
                ),
                CourseItem(
                    name = "数据结构与算法实验",
                    teacher = "吴建峰",
                    location = "科技楼405机房",
                    dayOfWeek = 3,
                    startSection = 3,
                    endSection = 4,
                    weeks = oddWeeks,
                    colorIndex = 5,
                    credit = "1.5"
                ),
                CourseItem(
                    name = "计算机网络",
                    teacher = "李思远",
                    location = "主楼310",
                    dayOfWeek = 4,
                    startSection = 3,
                    endSection = 4,
                    weeks = allWeeks,
                    colorIndex = 6,
                    credit = "3.5"
                ),
                CourseItem(
                    name = "概率论与数理统计",
                    teacher = "张文静",
                    location = "钟海楼04018",
                    dayOfWeek = 4,
                    startSection = 7,
                    endSection = 8,
                    weeks = evenWeeks,
                    colorIndex = 7,
                    credit = "3.0"
                ),
                CourseItem(
                    name = "Python数据分析",
                    teacher = "郑海洋",
                    location = "科技楼302机房",
                    dayOfWeek = 5,
                    startSection = 5,
                    endSection = 6,
                    weeks = allWeeks,
                    colorIndex = 8,
                    credit = "2.5"
                ),
                CourseItem(
                    name = "海洋文化与岭南历史(公选)",
                    teacher = "苏婉清",
                    location = "广学楼多功能厅",
                    dayOfWeek = 5,
                    startSection = 9,
                    endSection = 10,
                    weeks = (2..12).toList(),
                    colorIndex = 9,
                    credit = "1.5"
                ),
                CourseItem(
                    name = "创新创业实践课",
                    teacher = "蔡文博",
                    location = "钟海楼01008",
                    dayOfWeek = 6,
                    startSection = 3,
                    endSection = 4,
                    weeks = evenWeeks,
                    colorIndex = 10,
                    credit = "1.0"
                )
            )

            GdouCampus.YANGJIANG -> listOf(
                CourseItem(
                    name = "高等数学A(上)",
                    teacher = "陈明远",
                    location = "慎思楼A201",
                    dayOfWeek = 1,
                    startSection = 1,
                    endSection = 2,
                    weeks = allWeeks,
                    colorIndex = 0,
                    credit = "4.5"
                ),
                CourseItem(
                    name = "机械设计基础实验",
                    teacher = "林海涛",
                    location = "厚德楼305",
                    dayOfWeek = 1,
                    startSection = 3,
                    endSection = 4,
                    weeks = allWeeks,
                    colorIndex = 1,
                    credit = "2.0",
                    note = "示例：自动识别阳江非慎思楼错峰 10:10-11:40"
                ),
                CourseItem(
                    name = "大学英语(三)",
                    teacher = "周雅婷",
                    location = "慎思楼B304",
                    dayOfWeek = 2,
                    startSection = 3,
                    endSection = 4,
                    weeks = allWeeks,
                    colorIndex = 2,
                    credit = "3.0"
                ),
                CourseItem(
                    name = "大学体育(篮球)",
                    teacher = "黄志强",
                    location = "阳江综合体育场",
                    dayOfWeek = 2,
                    startSection = 7,
                    endSection = 8,
                    weeks = allWeeks,
                    colorIndex = 3,
                    credit = "1.0"
                ),
                CourseItem(
                    name = "工程材料与热处理",
                    teacher = "梁国华",
                    location = "慎思楼A402",
                    dayOfWeek = 3,
                    startSection = 1,
                    endSection = 2,
                    weeks = allWeeks,
                    colorIndex = 4,
                    credit = "2.5"
                ),
                CourseItem(
                    name = "电路分析实验",
                    teacher = "吴建峰",
                    location = "铭德楼208",
                    dayOfWeek = 3,
                    startSection = 3,
                    endSection = 4,
                    weeks = oddWeeks,
                    colorIndex = 5,
                    credit = "1.5"
                ),
                CourseItem(
                    name = "计算机网络",
                    teacher = "李思远",
                    location = "慎思楼B206",
                    dayOfWeek = 4,
                    startSection = 5,
                    endSection = 6,
                    weeks = allWeeks,
                    colorIndex = 6,
                    credit = "3.5"
                ),
                CourseItem(
                    name = "学术前沿与海丝文化(公选)",
                    teacher = "苏婉清",
                    location = "海纳楼113报告厅",
                    dayOfWeek = 5,
                    startSection = 9,
                    endSection = 10,
                    weeks = (2..12).toList(),
                    colorIndex = 9,
                    credit = "1.5"
                )
            )

            GdouCampus.HAIBIN -> listOf(
                CourseItem(
                    name = "高等数学A(上)",
                    teacher = "陈明远",
                    location = "教学楼204",
                    dayOfWeek = 1,
                    startSection = 1,
                    endSection = 2,
                    weeks = allWeeks,
                    colorIndex = 0,
                    credit = "4.5"
                ),
                CourseItem(
                    name = "数据结构与算法实验",
                    teacher = "林海涛",
                    location = "实506-1计算机(2)室",
                    dayOfWeek = 1,
                    startSection = 3,
                    endSection = 4,
                    weeks = allWeeks,
                    colorIndex = 1,
                    credit = "1.5",
                    note = "示例：自动识别海滨实验楼简写错峰 10:05-11:35"
                ),
                CourseItem(
                    name = "大学英语(三)",
                    teacher = "周雅婷",
                    location = "教学楼305",
                    dayOfWeek = 2,
                    startSection = 5,
                    endSection = 6,
                    weeks = allWeeks,
                    colorIndex = 2,
                    credit = "3.0"
                ),
                CourseItem(
                    name = "大学体育(网球)",
                    teacher = "黄志强",
                    location = "网球场（实验楼东面）",
                    dayOfWeek = 2,
                    startSection = 7,
                    endSection = 8,
                    weeks = allWeeks,
                    colorIndex = 3,
                    credit = "1.0",
                    note = "示例：自动识别室外场地错峰 16:25-17:50"
                ),
                CourseItem(
                    name = "海洋科学导论",
                    teacher = "梁国华",
                    location = "教学楼108",
                    dayOfWeek = 3,
                    startSection = 1,
                    endSection = 2,
                    weeks = allWeeks,
                    colorIndex = 4,
                    credit = "2.0"
                ),
                CourseItem(
                    name = "电路与电子技术实验",
                    teacher = "吴建峰",
                    location = "实验楼301",
                    dayOfWeek = 3,
                    startSection = 3,
                    endSection = 4,
                    weeks = oddWeeks,
                    colorIndex = 5,
                    credit = "1.5"
                ),
                CourseItem(
                    name = "计算机网络",
                    teacher = "李思远",
                    location = "教学楼402",
                    dayOfWeek = 4,
                    startSection = 3,
                    endSection = 4,
                    weeks = allWeeks,
                    colorIndex = 6,
                    credit = "3.5"
                ),
                CourseItem(
                    name = "概率论与数理统计",
                    teacher = "张文静",
                    location = "教学楼206",
                    dayOfWeek = 4,
                    startSection = 7,
                    endSection = 8,
                    weeks = evenWeeks,
                    colorIndex = 7,
                    credit = "3.0"
                ),
                CourseItem(
                    name = "Python数据分析与可视化",
                    teacher = "郑海洋",
                    location = "实402机房",
                    dayOfWeek = 5,
                    startSection = 5,
                    endSection = 6,
                    weeks = allWeeks,
                    colorIndex = 8,
                    credit = "2.5"
                ),
                CourseItem(
                    name = "海洋文化与岭南历史(公选)",
                    teacher = "苏婉清",
                    location = "学术报告厅",
                    dayOfWeek = 5,
                    startSection = 9,
                    endSection = 10,
                    weeks = (2..12).toList(),
                    colorIndex = 9,
                    credit = "1.5"
                )
            )
        }
    }

    fun createHaibinDemoCourses(): List<CourseItem> = createDemoCourses(GdouCampus.HAIBIN)

    fun createDemoGrades(): List<GradeItem> = listOf(
        GradeItem(
            academicYear = "2025-2026",
            semester = "第二学期",
            courseName = "线性代数",
            score = "91",
            gradePoint = 4.1,
            credit = 3.0,
            courseType = "学科基础必修",
            teacher = "王启明",
            isDemo = true
        ),
        GradeItem(
            academicYear = "2025-2026",
            semester = "第二学期",
            courseName = "C语言程序设计",
            score = "95",
            gradePoint = 4.5,
            credit = 4.0,
            courseType = "专业必修",
            teacher = "林海涛",
            isDemo = true
        ),
        GradeItem(
            academicYear = "2025-2026",
            semester = "第二学期",
            courseName = "大学物理B",
            score = "84",
            gradePoint = 3.4,
            credit = 3.5,
            courseType = "学科基础必修",
            teacher = "赵志远",
            isDemo = true
        )
    )

    fun createDemoExams(campus: GdouCampus = GdouCampus.HUGUANG): List<ExamItem> {
        val nextWeek = LocalDate.now().plusDays(12)
        val nextWeek2 = LocalDate.now().plusDays(16)
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        val (loc1, loc2) = when (campus) {
            GdouCampus.HUGUANG -> "湖光校区广学楼302" to "湖光校区钟海楼02016"
            GdouCampus.HAIBIN -> "海滨校区教学楼204" to "海滨校区教学楼402"
            GdouCampus.YANGJIANG -> "阳江校区慎思楼A201" to "阳江校区慎思楼B206"
        }
        return listOf(
            ExamItem(
                academicYear = "2026-2027",
                semester = "第一学期",
                courseName = "高等数学A(上) [示例]",
                examTimeRaw = "${nextWeek.format(fmt)}(09:00-11:00)",
                location = loc1,
                seatNumber = "18",
                examType = "期末考试",
                campus = campus.label,
                isDemo = true
            ),
            ExamItem(
                academicYear = "2026-2027",
                semester = "第一学期",
                courseName = "计算机网络 [示例]",
                examTimeRaw = "${nextWeek2.format(fmt)}(14:30-16:30)",
                location = loc2,
                seatNumber = "32",
                examType = "期末考试",
                campus = campus.label,
                isDemo = true
            )
        )
    }
}
