package io.github.rex25415.haiyu.schedule.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class GdouCampus(val id: String, val label: String, val shortDesc: String) {
    HUGUANG("huguang", "湖光校区", "广学楼/明德楼 3-4节 10:05-11:35，7-8节 16:25-17:50；其余教学楼按常规作息"),
    HAIBIN("haibin", "海滨校区", "实验楼/实+房号/球场与方位场地 3-4节 10:05-11:35，7-8节 16:25-17:50"),
    YANGJIANG("yangjiang", "阳江校区", "慎思楼 3-4节 10:25-12:00，非慎思楼 3-4节 10:10-11:40；全校区 7-8节 16:20-17:55");

    companion object {
        fun fromIdOrNull(id: String?): GdouCampus? = entries.firstOrNull {
            it.id.equals(id?.trim(), ignoreCase = true)
        }
    }
}

@Serializable
enum class AppThemePalette(val id: String, val label: String, val desc: String) {
    HAIYU_MINT("mint", "海屿薄荷青", "清爽护眼海洋薄荷绿"),
    DEEP_OCEAN("ocean", "深海蔚蓝", "广海经典海浪科技蓝"),
    SUNSET_AMBER("amber", "霞海日落", "温暖活力湛阳晚霞橙")
}

@Serializable
enum class DarkModeOption(val id: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色模式"),
    DARK("dark", "深色模式")
}

@Serializable
enum class GridRowDensity(val id: String, val label: String, val baseRowDp: Float, val breakGapDp: Float) {
    COMPACT("compact", "紧凑一屏", 48f, 10f),
    STANDARD("standard", "标准舒适", 60f, 13f),
    COMFORTABLE("comfortable", "宽松大字", 72f, 16f)
}

@Serializable
data class TimeSlot(
    val number: Int,
    val startTime: String,
    val endTime: String
)

@Serializable
data class CourseItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val teacher: String = "",
    val location: String = "",
    val dayOfWeek: Int, // 1 = Monday .. 7 = Sunday
    val startSection: Int,
    val endSection: Int,
    val weeks: List<Int>,
    val colorIndex: Int = -1,
    val credit: String = "",
    val note: String = "",
    val customStartTime: String? = null,
    val customEndTime: String? = null,
    val isManual: Boolean = false
)

@Serializable
data class GradeItem(
    val id: String = UUID.randomUUID().toString(),
    val academicYear: String, // e.g. "2025-2026"
    val semester: String,     // e.g. "第一学期" or "第二学期"
    val courseName: String,
    val score: String,        // e.g. "92" or "优秀"
    val gradePoint: Double,   // e.g. 4.2
    val credit: Double,       // e.g. 3.0
    val courseType: String = "", // 必修 / 选修 / 公选
    val examNature: String = "正常考试",
    val teacher: String = "",
    val isSimulated: Boolean = false,
    val isDemo: Boolean = false
)

@Serializable
data class ExamItem(
    val id: String = UUID.randomUUID().toString(),
    val academicYear: String = "",
    val semester: String = "",
    val courseName: String,
    val examTimeRaw: String,  // e.g. "2026-12-28(09:00-11:00)"
    val location: String = "",
    val seatNumber: String = "",
    val examType: String = "期末考试",
    val campus: String = "",
    val isManual: Boolean = false,
    val isDemo: Boolean = false
)

@Serializable
data class TodoTaskItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val courseName: String = "",
    val dueDate: String = "", // yyyy-MM-dd
    val isDone: Boolean = false,
    val note: String = "",
    val isDemo: Boolean = false
)

@Serializable
data class AppSettings(
    val campus: GdouCampus = GdouCampus.HUGUANG,
    val themePalette: AppThemePalette = AppThemePalette.HAIYU_MINT,
    val darkMode: DarkModeOption = DarkModeOption.SYSTEM,
    val semesterStartDate: String = "2026-08-31", // yyyy-MM-dd (Week 1 Monday)
    val totalWeeks: Int = 20,
    val academicYearLabel: String = "2026-2027 第一学期",
    val lastSyncYearCode: String = "",
    val lastSyncSemesterCode: String = "",
    val showNonCurrentWeekCourses: Boolean = true,
    val showWeekend: Boolean = true,
    val settingsVersion: Int = 0,
    val reminderEnabled: Boolean = false,
    val reminderMinutesBefore: Int = 15,
    val lastSyncTime: String = "",
    val preferOffCampusChannel: Boolean = true,
    val preferDesktopUa: Boolean = false,
    val autoCheckUpdates: Boolean = true,
    val lastUpdateCheckEpochMs: Long = 0L,
    val ignoredUpdateVersion: String = "",
    val pinnedConflictKeys: List<String> = emptyList(),
    val scheduleBgFileName: String = "",
    val courseCardAlpha: Float = 0.92f,
    val gridRowDensity: GridRowDensity = GridRowDensity.STANDARD,
    val showExamsOnTimetable: Boolean = true,
    val legacyCleanupDismissed: Boolean = false,
    val savedPartnerName: String = "",
    val savedPartnerCode: String = ""
)

@Serializable
data class AppDataSnapshot(
    val courses: List<CourseItem> = emptyList(),
    val grades: List<GradeItem> = emptyList(),
    val exams: List<ExamItem> = emptyList(),
    val tasks: List<TodoTaskItem> = emptyList(),
    val settings: AppSettings = AppSettings()
)

@Serializable
data class ScheduleShareBundle(
    val format: String = "HAIYU_GDOU_V1",
    val campus: GdouCampus = GdouCampus.HUGUANG,
    val semesterStartDate: String = "",
    val totalWeeks: Int = 20,
    val academicYearLabel: String = "",
    val courses: List<CourseItem> = emptyList()
)
