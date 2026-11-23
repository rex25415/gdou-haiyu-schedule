package cn.gdou.haiyu.schedule.data

import android.content.Context
import cn.gdou.haiyu.schedule.model.AppDataSnapshot
import cn.gdou.haiyu.schedule.model.AppSettings
import cn.gdou.haiyu.schedule.model.CourseItem
import cn.gdou.haiyu.schedule.model.ExamItem
import cn.gdou.haiyu.schedule.model.GdouCampus
import cn.gdou.haiyu.schedule.model.GradeItem
import cn.gdou.haiyu.schedule.model.ScheduleShareBundle
import cn.gdou.haiyu.schedule.model.TodoTaskItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class AppRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dataFile = File(appContext.filesDir, "haiyu_schedule_v1.json")

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val compactJson = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private val _state = MutableStateFlow(loadInitialSnapshot())
    val state: StateFlow<AppDataSnapshot> = _state.asStateFlow()

    private fun loadInitialSnapshot(): AppDataSnapshot {
        if (dataFile.exists()) {
            try {
                val content = dataFile.readText(Charsets.UTF_8)
                if (content.isNotBlank()) {
                    val decoded = json.decodeFromString(AppDataSnapshot.serializer(), content)
                    // One-time v2.3.0 migration: ensure 7-day Mon-Sun week view stays consistent on every week page
                    if (decoded.settings.settingsVersion < 2) {
                        val migrated = decoded.copy(
                            settings = decoded.settings.copy(
                                showWeekend = true,
                                settingsVersion = 2
                            )
                        )
                        try {
                            dataFile.writeText(json.encodeToString(migrated), Charsets.UTF_8)
                        } catch (_: Exception) {
                        }
                        return migrated
                    }
                    return decoded
                }
            } catch (_: Exception) {
            }
        }
        val today = LocalDate.now()
        val termStart = inferDefaultSemesterMonday(today)
        val yearLabel = inferDefaultTermLabel(today)
        return AppDataSnapshot(
            courses = emptyList(),
            grades = emptyList(),
            exams = emptyList(),
            tasks = emptyList(),
            settings = AppSettings(
                semesterStartDate = termStart.format(DateTimeFormatter.ISO_LOCAL_DATE),
                academicYearLabel = yearLabel,
                showWeekend = true,
                settingsVersion = 2
            )
        )
    }

    private fun inferDefaultSemesterMonday(today: LocalDate): LocalDate {
        val anchor = if (today.monthValue >= 8) {
            LocalDate.of(today.year, 9, 1)
        } else if (today.monthValue == 1) {
            LocalDate.of(today.year - 1, 9, 1)
        } else {
            LocalDate.of(today.year, 2, 24)
        }
        return anchor.with(DayOfWeek.MONDAY)
    }

    private fun inferDefaultTermLabel(today: LocalDate): String {
        return when {
            today.monthValue >= 8 -> "${today.year}-${today.year + 1} 第一学期"
            today.monthValue == 1 -> "${today.year - 1}-${today.year} 第一学期"
            else -> "${today.year - 1}-${today.year} 第二学期"
        }
    }

    @Synchronized
    private fun persist(snapshot: AppDataSnapshot) {
        _state.value = snapshot
        try {
            val tmpFile = File(appContext.filesDir, "haiyu_schedule_v1.json.tmp")
            tmpFile.writeText(json.encodeToString(snapshot), Charsets.UTF_8)
            if (dataFile.exists()) dataFile.delete()
            tmpFile.renameTo(dataFile)
        } catch (_: Exception) {
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val current = _state.value
        persist(current.copy(settings = transform(current.settings)))
    }

    fun replaceSyncedCourses(
        courses: List<CourseItem>,
        semesterStartDate: String?,
        totalWeeks: Int?,
        academicYearLabel: String?,
        yearCode: String? = null,
        semesterCode: String? = null,
        detectedCampusId: String? = null
    ): GdouCampus? {
        val current = _state.value
        val manualCourses = current.courses.filter { it.isManual }
        val coloredCourses = assignStableColors(courses)
        val nowLabel = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
        val detectedCampus = GdouCampus.fromIdOrNull(detectedCampusId)
        val nextSettings = current.settings.copy(
            campus = detectedCampus ?: current.settings.campus,
            semesterStartDate = semesterStartDate?.takeIf { it.isNotBlank() } ?: current.settings.semesterStartDate,
            totalWeeks = (totalWeeks ?: current.settings.totalWeeks).coerceIn(1, 30),
            academicYearLabel = academicYearLabel?.takeIf { it.isNotBlank() } ?: current.settings.academicYearLabel,
            lastSyncYearCode = yearCode?.takeIf { it.isNotBlank() } ?: current.settings.lastSyncYearCode,
            lastSyncSemesterCode = semesterCode?.takeIf { it.isNotBlank() } ?: current.settings.lastSyncSemesterCode,
            lastSyncTime = nowLabel
        )
        // Automatically clean up leftover demo tasks / demo grades / demo exams once real courses are synced!
        val cleanedTasks = current.tasks.filterNot { isLegacyOrFlaggedDemoTask(it) }
        val cleanedGrades = current.grades.filterNot { isLegacyOrFlaggedDemoGrade(it) }
        val cleanedExams = current.exams.filterNot { isLegacyOrFlaggedDemoExam(it) }
        persist(
            current.copy(
                courses = coloredCourses + manualCourses,
                tasks = cleanedTasks,
                grades = cleanedGrades,
                exams = cleanedExams,
                settings = nextSettings
            )
        )
        return detectedCampus
    }

    private fun isLegacyOrFlaggedDemoTask(t: TodoTaskItem): Boolean {
        if (t.isDemo) return true
        return t.note.startsWith("示例：") ||
            (t.title == "高数第 5 章课后习题" && t.courseName == "高等数学A(上)") ||
            (t.title == "数据结构实验报告（链表与栈）" && t.courseName == "数据结构与算法实验")
    }

    private fun isLegacyOrFlaggedDemoGrade(g: GradeItem): Boolean {
        if (g.isDemo) return true
        if (g.isSimulated) return false
        return (g.courseName == "线性代数" && g.teacher == "王启明" && g.score == "91") ||
            (g.courseName == "C语言程序设计" && g.teacher == "林海涛" && g.score == "95") ||
            (g.courseName == "大学物理B" && g.teacher == "赵志远" && g.score == "84")
    }

    private fun isLegacyOrFlaggedDemoExam(e: ExamItem): Boolean {
        if (e.isDemo) return true
        return e.courseName.contains("[示例]")
    }

    fun upsertCourse(course: CourseItem) {
        val current = _state.value
        val existingIdx = current.courses.indexOfFirst { it.id == course.id }
        val nextList = current.courses.toMutableList()
        if (existingIdx >= 0) {
            nextList[existingIdx] = course
        } else {
            val assignedColor = if (course.colorIndex >= 0) course.colorIndex else nextColorForName(course.name, current.courses)
            nextList.add(course.copy(colorIndex = assignedColor))
        }
        persist(current.copy(courses = nextList))
    }

    fun deleteCourse(courseId: String) {
        val current = _state.value
        persist(current.copy(courses = current.courses.filterNot { it.id == courseId }))
    }

    /**
     * 仅调本周（单次临时调课）：将该课程的指定周次独立拆分到新时间/教室，完全不影响其他周次！
     */
    fun rescheduleSingleWeek(
        courseId: String,
        weekNumber: Int,
        newDay: Int,
        newStartSection: Int,
        newEndSection: Int,
        newLocation: String
    ): Boolean {
        val current = _state.value
        val src = current.courses.firstOrNull { it.id == courseId } ?: return false
        if (weekNumber !in src.weeks) return false

        val updated = current.courses.toMutableList()
        val remainingWeeks = src.weeks.filter { it != weekNumber }
        if (remainingWeeks.isEmpty()) {
            updated.removeAll { it.id == courseId }
        } else {
            val idx = updated.indexOfFirst { it.id == courseId }
            if (idx >= 0) updated[idx] = src.copy(weeks = remainingWeeks)
        }

        val shiftNote = "第${weekNumber}周临时调课（原${GdouTimeRules.dayOfWeekLabel(src.dayOfWeek)} ${src.startSection}-${src.endSection}节）"
        updated.add(
            src.copy(
                id = UUID.randomUUID().toString(),
                dayOfWeek = newDay,
                startSection = newStartSection,
                endSection = newEndSection,
                location = newLocation.trim(),
                weeks = listOf(weekNumber),
                note = if (src.note.isBlank()) shiftNote else "${src.note} · $shiftNote",
                customStartTime = null,
                customEndTime = null,
                isManual = true
            )
        )
        persist(current.copy(courses = updated))
        return true
    }

    fun applyHolidayShift(
        sourceWeek: Int,
        sourceDay: Int,
        targetWeek: Int,
        targetDay: Int,
        moveInsteadOfCopy: Boolean
    ): Int {
        val current = _state.value
        val matched = current.courses.filter { it.dayOfWeek == sourceDay && sourceWeek in it.weeks }
        if (matched.isEmpty()) return 0

        val updated = current.courses.toMutableList()
        if (moveInsteadOfCopy) {
            for (i in updated.indices) {
                val c = updated[i]
                if (c.dayOfWeek == sourceDay && sourceWeek in itWeeks(c)) {
                    updated[i] = c.copy(weeks = c.weeks.filter { it != sourceWeek })
                }
            }
            updated.removeAll { it.weeks.isEmpty() }
        }

        matched.forEach { src ->
            updated.add(
                src.copy(
                    id = UUID.randomUUID().toString(),
                    dayOfWeek = targetDay,
                    weeks = listOf(targetWeek),
                    note = (if (src.note.isBlank()) "" else "${src.note} · ") + "调休自第${sourceWeek}周${GdouTimeRules.dayOfWeekLabel(sourceDay)}",
                    isManual = true
                )
            )
        }
        persist(current.copy(courses = updated))
        return matched.size
    }

    private fun itWeeks(c: CourseItem): List<Int> = c.weeks

    // ==================== 作业 / 实验 DDL 管理 ====================

    fun upsertTask(task: TodoTaskItem) {
        val current = _state.value
        val list = current.tasks.toMutableList()
        val idx = list.indexOfFirst { it.id == task.id }
        if (idx >= 0) list[idx] = task else list.add(0, task)
        persist(current.copy(tasks = list))
    }

    fun toggleTaskDone(taskId: String) {
        val current = _state.value
        val list = current.tasks.map { if (it.id == taskId) it.copy(isDone = !it.isDone) else it }
        persist(current.copy(tasks = list))
    }

    fun deleteTask(taskId: String) {
        val current = _state.value
        persist(current.copy(tasks = current.tasks.filterNot { it.id == taskId }))
    }

    fun clearCompletedTasks(): Int {
        val current = _state.value
        val removed = current.tasks.count { it.isDone }
        if (removed > 0) {
            persist(current.copy(tasks = current.tasks.filterNot { it.isDone }))
        }
        return removed
    }

    // ==================== 成绩与考试 ====================

    fun replaceSyncedGrades(grades: List<GradeItem>) {
        val current = _state.value
        val simulated = current.grades.filter { it.isSimulated }
        val nowLabel = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
        persist(
            current.copy(
                grades = grades + simulated,
                settings = current.settings.copy(lastSyncTime = nowLabel)
            )
        )
    }

    fun upsertGrade(grade: GradeItem) {
        val current = _state.value
        val list = current.grades.toMutableList()
        val idx = list.indexOfFirst { it.id == grade.id }
        if (idx >= 0) list[idx] = grade else list.add(0, grade)
        persist(current.copy(grades = list))
    }

    fun deleteGrade(gradeId: String) {
        val current = _state.value
        persist(current.copy(grades = current.grades.filterNot { it.id == gradeId }))
    }

    fun replaceSyncedExams(exams: List<ExamItem>) {
        val current = _state.value
        val manual = current.exams.filter { it.isManual }
        val nowLabel = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
        persist(
            current.copy(
                exams = exams + manual,
                settings = current.settings.copy(lastSyncTime = nowLabel)
            )
        )
    }

    fun upsertExam(exam: ExamItem) {
        val current = _state.value
        val list = current.exams.toMutableList()
        val idx = list.indexOfFirst { it.id == exam.id }
        if (idx >= 0) list[idx] = exam else list.add(0, exam)
        persist(current.copy(exams = list))
    }

    fun deleteExam(examId: String) {
        val current = _state.value
        persist(current.copy(exams = current.exams.filterNot { it.id == examId }))
    }

    // ==================== 课表分享码导入/导出（同班一键互传） ====================

    fun exportScheduleShareCode(): String? {
        val current = _state.value
        if (current.courses.isEmpty()) return null
        val bundle = ScheduleShareBundle(
            campus = current.settings.campus,
            semesterStartDate = current.settings.semesterStartDate,
            totalWeeks = current.settings.totalWeeks,
            academicYearLabel = current.settings.academicYearLabel,
            courses = current.courses.map { it.copy(id = "") }
        )
        return compactJson.encodeToString(bundle)
    }

    fun importScheduleShareCode(raw: String): Result<Int> {
        return try {
            val cleaned = raw.trim()
            val startIdx = cleaned.indexOf('{')
            val endIdx = cleaned.lastIndexOf('}')
            if (startIdx < 0 || endIdx <= startIdx) {
                return Result.failure(IllegalArgumentException("未识别到有效的海屿课表分享码"))
            }
            val bundle = compactJson.decodeFromString(
                ScheduleShareBundle.serializer(),
                cleaned.substring(startIdx, endIdx + 1)
            )
            if (bundle.courses.isEmpty()) {
                return Result.failure(IllegalArgumentException("分享码中不包含课程数据"))
            }
            val imported = assignStableColors(
                bundle.courses.map { it.copy(id = UUID.randomUUID().toString()) }
            )
            val current = _state.value
            persist(
                current.copy(
                    courses = imported,
                    settings = current.settings.copy(
                        campus = bundle.campus,
                        semesterStartDate = bundle.semesterStartDate.ifBlank { current.settings.semesterStartDate },
                        totalWeeks = bundle.totalWeeks.coerceIn(1, 30),
                        academicYearLabel = bundle.academicYearLabel.ifBlank { current.settings.academicYearLabel }
                    )
                )
            )
            Result.success(imported.size)
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("分享码格式有误：${e.message}"))
        }
    }

    fun loadDemoData() {
        val current = _state.value
        val campus = current.settings.campus
        val mondayThisWeek = LocalDate.now().with(DayOfWeek.MONDAY).minusWeeks(4)
        val demoTasks = listOf(
            TodoTaskItem(
                title = "数据结构实验报告（链表与栈）",
                courseName = "数据结构与算法实验",
                dueDate = LocalDate.now().plusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE),
                note = "示例：在课表点任意课程即可快捷添加作业 DDL",
                isDemo = true
            ),
            TodoTaskItem(
                title = "高数第 5 章课后习题",
                courseName = "高等数学A(上)",
                dueDate = LocalDate.now().plusDays(5).format(DateTimeFormatter.ISO_LOCAL_DATE),
                isDemo = true
            )
        )
        persist(
            current.copy(
                courses = GdouTimeRules.createDemoCourses(campus),
                grades = GdouTimeRules.createDemoGrades(),
                exams = GdouTimeRules.createDemoExams(campus),
                tasks = demoTasks,
                settings = current.settings.copy(
                    semesterStartDate = mondayThisWeek.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    totalWeeks = 20,
                    academicYearLabel = "${campus.label}示例课表"
                )
            )
        )
    }

    fun clearAllAcademicData() {
        val current = _state.value
        persist(
            current.copy(
                courses = emptyList(),
                grades = emptyList(),
                exams = emptyList(),
                tasks = emptyList()
            )
        )
    }

    private fun assignStableColors(courses: List<CourseItem>): List<CourseItem> {
        val colorMap = LinkedHashMap<String, Int>()
        var nextIdx = 0
        return courses.map { c ->
            val key = c.name.trim()
            val idx = colorMap.getOrPut(key) {
                val assigned = nextIdx % 12
                nextIdx++
                assigned
            }
            c.copy(colorIndex = idx)
        }
    }

    private fun nextColorForName(name: String, existing: List<CourseItem>): Int {
        existing.firstOrNull { it.name.trim() == name.trim() && it.colorIndex >= 0 }?.let {
            return it.colorIndex
        }
        return kotlin.math.abs(name.hashCode()) % 12
    }

    companion object {
        @Volatile
        private var instance: AppRepository? = null

        fun getInstance(context: Context): AppRepository {
            return instance ?: synchronized(this) {
                instance ?: AppRepository(context).also { instance = it }
            }
        }
    }
}
