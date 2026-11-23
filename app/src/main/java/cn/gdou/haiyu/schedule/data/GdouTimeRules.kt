package cn.gdou.haiyu.schedule.data

import cn.gdou.haiyu.schedule.model.CourseItem
import cn.gdou.haiyu.schedule.model.ExamItem
import cn.gdou.haiyu.schedule.model.GdouCampus
import cn.gdou.haiyu.schedule.model.GradeItem
import cn.gdou.haiyu.schedule.model.TimeSlot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

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

data class CourseOverlapCluster(
    val courses: List<CourseItem>,
    val minStartSection: Int,
    val maxEndSection: Int
) {
    val hasConflict: Boolean get() = courses.size > 1
}

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
     * 将同一天在节次上有交集的课程合并为冲突簇（支持完全重叠、部分跨节重叠及多门连续重课）
     */
    fun groupOverlappingCourses(dayCourses: List<CourseItem>): List<CourseOverlapCluster> {
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

        for (i in 1 until sorted.size) {
            val next = sorted[i]
            if (next.startSection <= maxEnd) {
                currentList.add(next)
                if (next.endSection > maxEnd) maxEnd = next.endSection
            } else {
                clusters.add(CourseOverlapCluster(currentList.toList(), minStart, maxEnd))
                currentList = mutableListOf(next)
                minStart = next.startSection
                maxEnd = next.endSection
            }
        }
        clusters.add(CourseOverlapCluster(currentList.toList(), minStart, maxEnd))
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
        weekNumber: Int
    ): List<Pair<Int, CourseOverlapCluster>> {
        val list = mutableListOf<Pair<Int, CourseOverlapCluster>>()
        for (day in 1..7) {
            val active = courses.filter { it.dayOfWeek == day && weekNumber in it.weeks }
            groupOverlappingCourses(active)
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
        return try {
            LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"))
        } catch (_: Exception) {
            LocalTime.of(8, 10)
        }
    }

    /**
     * 计算当前处于第几教学周（1-based；开学前返回 1，但 isBeforeTerm = true）
     */
    fun calculateCurrentWeek(startDateStr: String, totalWeeks: Int, today: LocalDate = LocalDate.now()): Int {
        val startMonday = parseLocalDate(startDateStr).with(DayOfWeek.MONDAY)
        val daysBetween = ChronoUnit.DAYS.between(startMonday, today)
        if (daysBetween < 0) return 1
        val week = (daysBetween / 7).toInt() + 1
        return week.coerceIn(1, totalWeeks.coerceAtLeast(1))
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

    fun getTodayCoursesWithStatus(
        courses: List<CourseItem>,
        startDateStr: String,
        totalWeeks: Int,
        campus: GdouCampus,
        now: LocalDateTime = LocalDateTime.now()
    ): List<CourseOccurrenceStatus> {
        val today = now.toLocalDate()
        val currentWeek = calculateCurrentWeek(startDateStr, totalWeeks, today)
        val todayDow = today.dayOfWeek.value // 1..7
        val currentTime = now.toLocalTime()

        return courses
            .filter { it.dayOfWeek == todayDow && currentWeek in it.weeks }
            .sortedWith(
                compareBy<CourseItem> { it.startSection }
                    .thenBy { it.endSection }
                    .thenBy { it.name }
            )
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
        val dateMatch = Regex("(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})").find(raw) ?: return null
        val year = dateMatch.groupValues[1].toIntOrNull() ?: return null
        val month = dateMatch.groupValues[2].toIntOrNull() ?: return null
        val day = dateMatch.groupValues[3].toIntOrNull() ?: return null

        val timeMatch = Regex("(\\d{1,2}):(\\d{2})").find(raw)
        val hour = timeMatch?.groupValues?.get(1)?.toIntOrNull() ?: 9
        val minute = timeMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0

        return try {
            LocalDateTime.of(year, month, day, hour, minute)
        } catch (_: Exception) {
            null
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
