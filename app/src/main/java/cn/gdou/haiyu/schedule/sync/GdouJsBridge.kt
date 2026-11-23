package cn.gdou.haiyu.schedule.sync

import android.webkit.JavascriptInterface
import cn.gdou.haiyu.schedule.model.CourseItem
import cn.gdou.haiyu.schedule.model.ExamItem
import cn.gdou.haiyu.schedule.model.GradeItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
    val credit: String = ""
)

@Serializable
private data class SyncedCoursesPayload(
    val courses: List<SyncedCourseDto> = emptyList(),
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
    val teacher: String = ""
)

@Serializable
private data class SyncedGradesPayload(
    val grades: List<SyncedGradeDto> = emptyList()
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
        detectedCampusId: String
    )
    fun onGradesSynced(grades: List<GradeItem>)
    fun onExamsSynced(exams: List<ExamItem>)
    fun onComplete(summary: String)
    fun onError(errorMessage: String)
    fun onQuickRefreshNeedsLogin(reason: String) {}
}

class GdouJsBridge(
    private val callback: GdouSyncCallback
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @JavascriptInterface
    fun onSyncProgress(message: String) {
        callback.onProgress(message)
    }

    @JavascriptInterface
    fun onTermOptionsLoaded(payloadJson: String) {
        try {
            val payload = json.decodeFromString(TermOptionsPayload.serializer(), payloadJson)
            callback.onTermOptionsLoaded(payload)
        } catch (e: Exception) {
            callback.onError("解析学年选项失败：${e.message}")
        }
    }

    @JavascriptInterface
    fun onCoursesSynced(payloadJson: String) {
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
                    isManual = false
                )
            }
            callback.onCoursesSynced(
                courses = mapped,
                startDate = payload.semesterStartDate.ifBlank { null },
                totalWeeks = payload.totalWeeks,
                termLabel = payload.termLabel,
                yearCode = payload.academicYear,
                semesterCode = payload.semesterCode,
                detectedCampusId = payload.detectedCampus
            )
        } catch (e: Exception) {
            callback.onError("解析课表数据失败：${e.message}")
        }
    }

    @JavascriptInterface
    fun onGradesSynced(payloadJson: String) {
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
                    isSimulated = false
                )
            }
            callback.onGradesSynced(mapped)
        } catch (e: Exception) {
            callback.onError("解析成绩数据失败：${e.message}")
        }
    }

    @JavascriptInterface
    fun onExamsSynced(payloadJson: String) {
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
        callback.onComplete(summary)
    }

    @JavascriptInterface
    fun onSyncError(errorMessage: String) {
        callback.onError(errorMessage)
    }

    @JavascriptInterface
    fun onQuickRefreshNeedsLogin(reason: String) {
        callback.onQuickRefreshNeedsLogin(reason)
    }
}
