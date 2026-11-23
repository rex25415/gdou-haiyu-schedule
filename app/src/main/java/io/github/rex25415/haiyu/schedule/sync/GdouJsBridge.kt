package io.github.rex25415.haiyu.schedule.sync

import android.webkit.JavascriptInterface
import io.github.rex25415.haiyu.schedule.model.CourseItem
import io.github.rex25415.haiyu.schedule.model.EmptyClassroomItem
import io.github.rex25415.haiyu.schedule.model.ExamItem
import io.github.rex25415.haiyu.schedule.model.GradeItem
import io.github.rex25415.haiyu.schedule.model.LevelExamItem
import io.github.rex25415.haiyu.schedule.model.PracticeCourseItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.util.Locale

@Serializable
data class TermOption(
    val value: String,
    val text: String
)

@Serializable
data class TermOptionsPayload(
    val yearOptions: List<TermOption> = emptyList(),
    val semesterOptions: List<TermOption> = emptyList(),
    val defaultYearIndex: Int = 0,
    val defaultSemesterIndex: Int = 0,
    val isFallback: Boolean = false
)

@Serializable
private data class SyncedCourseDto(
    val name: String,
    val teacher: String = "",
    val location: String = "",
    val dayOfWeek: Int = 1,
    val startSection: Int = 1,
    val endSection: Int = 2,
    val weeks: List<Int> = emptyList(),
    val credit: String = "",
    val courseType: String = "",
    val examMethod: String = "",
    val classGroup: String = "",
    val totalHours: String = ""
)

@Serializable
private data class SyncedPracticeCourseDto(
    val name: String,
    val teacher: String = "",
    val weeksText: String = "",
    val weeks: List<Int> = emptyList(),
    val credit: String = "",
    val courseType: String = "实践环节",
    val note: String = ""
)

@Serializable
private data class SyncedCoursesPayload(
    val courses: List<SyncedCourseDto> = emptyList(),
    val practiceCourses: List<SyncedPracticeCourseDto> = emptyList(),
    val semesterStartDate: String = "",
    val totalWeeks: Int = 20,
    val termLabel: String = "",
    val academicYear: String = "",
    val semesterCode: String = "",
    val detectedCampus: String = ""
)

@Serializable
private data class SyncedGradeDto(
    val academicYear: String = "",
    val semester: String = "",
    val courseName: String = "",
    val score: String = "",
    val gradePoint: Double = 0.0,
    val credit: Double = 0.0,
    val courseType: String = "",
    val examNature: String = "正常考试",
    val teacher: String = "",
    val breakdown: String = ""
)

@Serializable
private data class SyncedLevelExamDto(
    val examName: String = "",
    val score: String = "",
    val examDate: String = "",
    val detail: String = ""
)

@Serializable
private data class SyncedGradesPayload(
    val grades: List<SyncedGradeDto> = emptyList(),
    val levelExams: List<SyncedLevelExamDto> = emptyList()
)

@Serializable
private data class SyncedEmptyClassroomsPayload(
    val rooms: List<EmptyClassroomItem> = emptyList(),
    val summary: String = ""
)

@Serializable
private data class SyncedExamDto(
    val academicYear: String = "",
    val semester: String = "",
    val courseName: String = "",
    val examTimeRaw: String = "",
    val location: String = "",
    val seatNumber: String = "",
    val examType: String = "期末考试",
    val campus: String = ""
)

@Serializable
private data class SyncedExamsPayload(
    val exams: List<SyncedExamDto> = emptyList()
)

interface GdouSyncCallback {
    fun onProgress(message: String)
    fun onTermOptionsLoaded(options: TermOptionsPayload)
    fun onCoursesSynced(
        courses: List<CourseItem>,
        startDate: String?,
        totalWeeks: Int,
        termLabel: String,
        yearCode: String,
        semesterCode: String,
        detectedCampusId: String,
        practiceCourses: List<PracticeCourseItem> = emptyList()
    )
    fun onGradesSynced(grades: List<GradeItem>, levelExams: List<LevelExamItem> = emptyList())
    fun onExamsSynced(exams: List<ExamItem>)
    fun onEmptyClassroomsLoaded(rooms: List<EmptyClassroomItem>, summary: String) {}
    fun onComplete(summary: String)
    fun onError(errorMessage: String)
    fun onQuickRefreshNeedsLogin(reason: String) {}
}

class GdouJsBridge(
    private val callback: GdouSyncCallback,
    private val currentUrlProvider: () -> String? = { null }
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun isAllowedOrigin(): Boolean {
        val url = currentUrlProvider()
        return isTrustedGdouOrigin(url)
    }

    @JavascriptInterface
    fun onSyncProgress(message: String) {
        if (!isAllowedOrigin()) return
        callback.onProgress(message)
    }

    @JavascriptInterface
    fun onTermOptionsLoaded(payloadJson: String) {
        if (!isAllowedOrigin()) return
        try {
            val payload = json.decodeFromString(TermOptionsPayload.serializer(), payloadJson)
            callback.onTermOptionsLoaded(payload)
        } catch (e: Exception) {
            callback.onError("解析学年选项失败：${e.message}")
        }
    }

    @JavascriptInterface
    fun onCoursesSynced(payloadJson: String) {
        if (!isAllowedOrigin()) return
        try {
            val payload = json.decodeFromString(SyncedCoursesPayload.serializer(), payloadJson)
            val mapped = payload.courses.map { dto ->
                CourseItem(
                    name = dto.name,
                    teacher = dto.teacher,
                    location = dto.location,
                    dayOfWeek = dto.dayOfWeek,
                    startSection = dto.startSection,
                    endSection = dto.endSection,
                    weeks = dto.weeks,
                    credit = dto.credit,
                    courseType = dto.courseType,
                    examMethod = dto.examMethod,
                    classGroup = dto.classGroup,
                    totalHours = dto.totalHours,
                    isManual = false
                )
            }
            val mappedPractice = payload.practiceCourses.map { dto ->
                PracticeCourseItem(
                    name = dto.name,
                    teacher = dto.teacher,
                    weeksText = dto.weeksText,
                    weeks = dto.weeks,
                    credit = dto.credit,
                    courseType = dto.courseType.ifBlank { "实践环节" },
                    note = dto.note
                )
            }
            callback.onCoursesSynced(
                courses = mapped,
                startDate = payload.semesterStartDate.ifBlank { null },
                totalWeeks = payload.totalWeeks,
                termLabel = payload.termLabel,
                yearCode = payload.academicYear,
                semesterCode = payload.semesterCode,
                detectedCampusId = payload.detectedCampus,
                practiceCourses = mappedPractice
            )
        } catch (e: Exception) {
            callback.onError("解析课表数据失败：${e.message}")
        }
    }

    @JavascriptInterface
    fun onGradesSynced(payloadJson: String) {
        if (!isAllowedOrigin()) return
        try {
            val payload = json.decodeFromString(SyncedGradesPayload.serializer(), payloadJson)
            val mapped = payload.grades.map { dto ->
                GradeItem(
                    academicYear = dto.academicYear,
                    semester = dto.semester,
                    courseName = dto.courseName,
                    score = dto.score,
                    gradePoint = dto.gradePoint,
                    credit = dto.credit,
                    courseType = dto.courseType,
                    examNature = dto.examNature,
                    teacher = dto.teacher,
                    breakdown = dto.breakdown,
                    isSimulated = false
                )
            }
            val mappedLevels = payload.levelExams.mapNotNull { dto ->
                if (dto.examName.isBlank()) null
                else LevelExamItem(
                    examName = dto.examName,
                    score = dto.score,
                    examDate = dto.examDate,
                    detail = dto.detail
                )
            }
            callback.onGradesSynced(mapped, mappedLevels)
        } catch (e: Exception) {
            callback.onError("解析成绩数据失败：${e.message}")
        }
    }

    @JavascriptInterface
    fun onEmptyClassroomsLoaded(payloadJson: String) {
        if (!isAllowedOrigin()) return
        try {
            val payload = json.decodeFromString(SyncedEmptyClassroomsPayload.serializer(), payloadJson)
            callback.onEmptyClassroomsLoaded(payload.rooms, payload.summary)
        } catch (e: Exception) {
            callback.onError("解析空教室结果失败：${e.message}")
        }
    }

    @JavascriptInterface
    fun onExamsSynced(payloadJson: String) {
        if (!isAllowedOrigin()) return
        try {
            val payload = json.decodeFromString(SyncedExamsPayload.serializer(), payloadJson)
            val mapped = payload.exams.map { dto ->
                ExamItem(
                    academicYear = dto.academicYear,
                    semester = dto.semester,
                    courseName = dto.courseName,
                    examTimeRaw = dto.examTimeRaw,
                    location = dto.location,
                    seatNumber = dto.seatNumber,
                    examType = dto.examType,
                    campus = dto.campus,
                    isManual = false
                )
            }
            callback.onExamsSynced(mapped)
        } catch (e: Exception) {
            callback.onError("解析考试数据失败：${e.message}")
        }
    }

    @JavascriptInterface
    fun onSyncComplete(summary: String) {
        if (!isAllowedOrigin()) return
        callback.onComplete(summary)
    }

    @JavascriptInterface
    fun onSyncError(errorMessage: String) {
        if (!isAllowedOrigin()) return
        callback.onError(errorMessage)
    }

    @JavascriptInterface
    fun onQuickRefreshNeedsLogin(reason: String) {
        if (!isAllowedOrigin()) return
        callback.onQuickRefreshNeedsLogin(reason)
    }

    companion object {
        /**
         * 严格域名白名单校验：仅允许通过 HTTPS 访问的 gdou.edu.cn 官方域名调用原生桥接，
         * 杜绝伪造域名（如 gdou.edu.cn.evil.com）或非 HTTPS 页面触发任何回调。
         */
        fun isTrustedGdouOrigin(url: String?): Boolean {
            if (url.isNullOrBlank()) return false
            return try {
                val uri = URI(url.trim())
                val scheme = uri.scheme?.lowercase(Locale.US) ?: return false
                val host = uri.host?.lowercase(Locale.US)?.trimEnd('.') ?: return false
                scheme == "https" && (host == "gdou.edu.cn" || host.endsWith(".gdou.edu.cn"))
            } catch (_: Exception) {
                false
            }
        }

        /**
         * 广海校内直连（jw.gdou.edu.cn/sso/jziotlogin）会 302 跳转到 http://authserver.gdou.edu.cn/...，
         * 自动将其无感升级为 https://，既保证校内直连畅通，又杜绝校园网明文传密码风险！
         */
        fun upgradeGdouHttpToHttps(url: String?): String? {
            if (url.isNullOrBlank()) return null
            val trimmed = url.trim()
            if (!trimmed.startsWith("http://", ignoreCase = true)) return null
            val upgraded = "https://" + trimmed.substring(7)
            return if (isTrustedGdouOrigin(upgraded)) upgraded else null
        }
    }
}
