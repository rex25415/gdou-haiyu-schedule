package io.github.rex25415.haiyu.schedule.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import io.github.rex25415.haiyu.schedule.model.AppDataSnapshot
import io.github.rex25415.haiyu.schedule.model.AppSettings
import io.github.rex25415.haiyu.schedule.model.CourseItem
import io.github.rex25415.haiyu.schedule.model.ExamItem
import io.github.rex25415.haiyu.schedule.model.GdouCampus
import io.github.rex25415.haiyu.schedule.model.GradeItem
import io.github.rex25415.haiyu.schedule.model.LevelExamItem
import io.github.rex25415.haiyu.schedule.model.PracticeCourseItem
import io.github.rex25415.haiyu.schedule.model.ScheduleShareBundle
import io.github.rex25415.haiyu.schedule.model.TodoTaskItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.Executors

class AppRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dataFile = File(appContext.filesDir, "haiyu_schedule_v1.json")
    private val ioExecutor = Executors.newSingleThreadExecutor()

    private val storageJson = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val prettyJson = Json {
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
        val tmpFile = File(appContext.filesDir, "haiyu_schedule_v1.json.tmp")
        val sourceFile = when {
            dataFile.exists() && dataFile.length() > 0L -> dataFile
            tmpFile.exists() && tmpFile.length() > 0L -> tmpFile
            else -> null
        }
        if (sourceFile != null) {
            try {
                val content = sourceFile.readText(Charsets.UTF_8)
                if (content.isNotBlank()) {
                    val decoded = storageJson.decodeFromString(AppDataSnapshot.serializer(), content)
                    if (sourceFile == tmpFile) {
                        tmpFile.renameTo(dataFile)
                    }
                    // One-time v2.3.0 migration: ensure 7-day Mon-Sun week view stays consistent on every week page
                    if (decoded.settings.settingsVersion < 2) {
                        val migrated = decoded.copy(
                            settings = decoded.settings.copy(
                                showWeekend = true,
                                settingsVersion = 2
                            )
                        )
                        try {
                            dataFile.writeText(storageJson.encodeToString(migrated), Charsets.UTF_8)
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
        // Immediate 0ms state update on main thread; serialize & atomically flush on dedicated ordered IO thread
        _state.value = snapshot
        ioExecutor.execute {
            try {
                val encoded = storageJson.encodeToString(snapshot)
                val tmpFile = File(appContext.filesDir, "haiyu_schedule_v1.json.tmp")
                tmpFile.writeText(encoded, Charsets.UTF_8)
                if (!tmpFile.renameTo(dataFile)) {
                    if (dataFile.exists()) dataFile.delete()
                    tmpFile.renameTo(dataFile)
                }
            } catch (_: Exception) {
            }
        }
    }

    /**
     * 在重课冲突簇中置顶某门课程（优先展示于课表卡片、顶部提醒与课前通知；再次点击取消置顶）
     */
    fun togglePinConflictCourse(target: CourseItem, peers: List<CourseItem>): Boolean {
        val current = _state.value
        val targetKey = GdouTimeRules.courseConflictKey(target)
        val peerKeys = peers.map { GdouTimeRules.courseConflictKey(it) }.toSet()
        val existing = current.settings.pinnedConflictKeys.toMutableList()
        val wasPinned = targetKey in existing
        existing.removeAll { it in peerKeys || it == targetKey }
        if (!wasPinned) {
            existing.add(targetKey)
        }
        persist(current.copy(settings = current.settings.copy(pinnedConflictKeys = existing)))
        return !wasPinned
    }

    /**
     * 导出全量本地换机备份（课表 + 作业 DDL + 成绩 + 考试 + 设置，纯离线无账号密码）
     */
    fun exportFullBackupAndShare(context: Context): Boolean {
        val current = _state.value
        val sanitized = current.copy(
            settings = current.settings.copy(scheduleBgFileName = "")
        )
        val text = prettyJson.encodeToString(sanitized)
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
        val backupFile = File(exportDir, "海屿空间全量备份_$stamp.json")
        backupFile.writeText(text, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            backupFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "海屿空间全量备份 ($stamp)")
            clipData = ClipData.newRawUri("海屿空间全量备份.json", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "保存或发送全量备份文件").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
        return true
    }

    fun importFullBackupJson(rawJson: String): Result<String> {
        return try {
            val cleaned = rawJson.trim()
            if (cleaned.isEmpty()) return Result.failure(IllegalArgumentException("备份文件为空"))
            val decoded = storageJson.decodeFromString(AppDataSnapshot.serializer(), cleaned)
            if (decoded.courses.isEmpty() && decoded.tasks.isEmpty() && decoded.grades.isEmpty() && decoded.exams.isEmpty()) {
                return Result.failure(IllegalArgumentException("备份文件中没有可恢复的数据"))
            }
            val currentBg = _state.value.settings.scheduleBgFileName
            val restored = decoded.copy(
                courses = assignStableColors(decoded.courses),
                settings = decoded.settings.copy(
                    scheduleBgFileName = currentBg,
                    settingsVersion = maxOf(2, decoded.settings.settingsVersion)
                )
            )
            persist(restored)
            Result.success(
                "已恢复：课表 ${restored.courses.size} 条 · 作业 ${restored.tasks.size} 项 · 成绩 ${restored.grades.size} 门 · 考试 ${restored.exams.size} 场"
            )
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("备份文件格式无效：${e.message ?: "解析失败"}"))
        }
    }

    /**
     * 零权限保存自定义课表壁纸（单次流落盘缓冲 + ImageDecoder/BitmapFactory 双引擎适配 Android 8–17 与 HEIC/EXIF 旋转，
     * 自动等比压至 <= 1440px，仅存本机私有目录）
     */
    fun saveScheduleBackgroundFromUri(uri: Uri): Result<String> {
        var tempSrcFile: File? = null
        var decodedBitmap: Bitmap? = null
        var outputBitmap: Bitmap? = null
        return try {
            val stream = try {
                appContext.contentResolver.openInputStream(uri)
                    ?: appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.createInputStream()
            } catch (_: SecurityException) {
                return Result.failure(IllegalArgumentException("没有读取该图片的临时权限，请点「从系统文件选图」重试"))
            } catch (_: Exception) {
                null
            } ?: return Result.failure(IllegalArgumentException("无法打开所选图片，请确认图片未被移除后重试"))

            val maxInputBytes = 40L * 1024L * 1024L
            val tmp = File.createTempFile("wp_src_", ".img", appContext.cacheDir)
            tempSrcFile = tmp
            var totalCopied = 0L
            stream.use { input ->
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        totalCopied += read
                        if (totalCopied > maxInputBytes) {
                            return Result.failure(IllegalArgumentException("所选图片体积过大（超过 40MB），请选择轻量图片"))
                        }
                        out.write(buf, 0, read)
                    }
                }
            }
            if (totalCopied <= 0L) {
                return Result.failure(IllegalArgumentException("所选图片内容为空（若为云相册照片请先下载到本地后再试）"))
            }

            decodedBitmap = decodeScaledWallpaperFile(tmp, maxDim = 1440)
                ?: return Result.failure(IllegalArgumentException("图片格式暂不支持或文件已损坏，请换一张 JPG/PNG/HEIC 图片"))

            val flattenNeeded = decodedBitmap.hasAlpha() || decodedBitmap.config != Bitmap.Config.ARGB_8888
            val bitmapToSave = if (flattenNeeded) {
                val safe = Bitmap.createBitmap(
                    decodedBitmap.width.coerceAtLeast(1),
                    decodedBitmap.height.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(safe)
                canvas.drawColor(Color.WHITE)
                canvas.drawBitmap(decodedBitmap, 0f, 0f, null)
                outputBitmap = safe
                safe
            } else {
                decodedBitmap
            }

            val oldName = _state.value.settings.scheduleBgFileName
            val newName = "schedule_bg_${System.currentTimeMillis()}.jpg"
            val dstFile = File(appContext.filesDir, newName)
            val dstTmp = File(appContext.filesDir, "$newName.tmp")
            val compressed = FileOutputStream(dstTmp).use { out ->
                bitmapToSave.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            if (!compressed || !dstTmp.exists() || dstTmp.length() <= 0L) {
                dstTmp.delete()
                return Result.failure(IllegalArgumentException("壁纸编码保存失败，请检查设备剩余存储空间"))
            }
            if (dstFile.exists()) dstFile.delete()
            if (!dstTmp.renameTo(dstFile)) {
                dstTmp.copyTo(dstFile, overwrite = true)
                dstTmp.delete()
            }

            if (oldName.isNotBlank() && oldName != newName) {
                File(appContext.filesDir, oldName).delete()
            }
            val current = _state.value
            val defaultAlpha = if (current.settings.courseCardAlpha >= 0.99f) 0.86f else current.settings.courseCardAlpha
            persist(
                current.copy(
                    settings = current.settings.copy(
                        scheduleBgFileName = newName,
                        courseCardAlpha = defaultAlpha
                    )
                )
            )
            Result.success(newName)
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("设置壁纸失败：${e.message ?: "未知错误"}"))
        } finally {
            outputBitmap?.let { if (!it.isRecycled) it.recycle() }
            decodedBitmap?.let { if (it !== outputBitmap && !it.isRecycled) it.recycle() }
            tempSrcFile?.delete()
        }
    }

    private fun decodeScaledWallpaperFile(srcFile: File, maxDim: Int = 1440): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(srcFile)
                return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    // Software allocator is mandatory so Bitmap.compress() never fails on HARDWARE bitmaps
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                    val (tw, th) = computeScaledTargetDimensions(info.size.width, info.size.height, maxDim)
                    if (tw > 0 && th > 0) {
                        decoder.setTargetSize(tw, th)
                    }
                }
            } catch (_: Throwable) {
                // Fall back to BitmapFactory if ImageDecoder fails on an uncommon container
            }
        }
        return decodeViaBitmapFactory(srcFile, maxDim)
    }

    private fun decodeViaBitmapFactory(srcFile: File, maxDim: Int = 1440): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // Note: decodeFile with inJustDecodeBounds=true always returns null; inspect outWidth/outHeight instead
        BitmapFactory.decodeFile(srcFile.absolutePath, boundsOpts)
        val srcW = boundsOpts.outWidth
        val srcH = boundsOpts.outHeight
        if (srcW <= 0 || srcH <= 0) return null

        val sample = computeBitmapInSampleSize(srcW, srcH, maxDim)
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeFile(srcFile.absolutePath, decodeOpts) ?: return null
        val (targetW, targetH) = computeScaledTargetDimensions(decoded.width, decoded.height, maxDim)
        if (decoded.width == targetW && decoded.height == targetH) return decoded
        val scaled = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
        if (scaled !== decoded && !decoded.isRecycled) decoded.recycle()
        return scaled
    }

    fun clearScheduleBackground() {
        val current = _state.value
        val oldName = current.settings.scheduleBgFileName
        if (oldName.isNotBlank()) {
            File(appContext.filesDir, oldName).delete()
        }
        persist(current.copy(settings = current.settings.copy(scheduleBgFileName = "")))
    }

    fun getScheduleBackgroundFile(fileName: String): File? {
        if (fileName.isBlank()) return null
        val f = File(appContext.filesDir, fileName)
        return if (f.exists() && f.length() > 0L) f else null
    }

    fun toggleFavoriteBuilding(campus: GdouCampus, building: String): Boolean {
        val normalized = building.trim()
        if (normalized.isEmpty()) return false
        val key = "${campus.id}|$normalized"
        val current = _state.value
        val existing = current.settings.favoriteBuildings.toMutableList()
        val added = if (key in existing) {
            existing.removeAll { it == key }
            false
        } else {
            existing.add(0, key)
            true
        }
        persist(current.copy(settings = current.settings.copy(favoriteBuildings = existing)))
        return added
    }

    fun toggleFavoriteClassroom(
        campus: GdouCampus,
        building: String,
        roomName: String,
        seats: String = "",
        roomType: String = ""
    ): Boolean {
        val cleanRoom = roomName.trim()
        val cleanBuilding = building.trim()
        if (cleanRoom.isEmpty()) return false
        val matchPrefix = "${campus.id}|$cleanBuilding|$cleanRoom|"
        val legacyExact = "${campus.id}|$cleanBuilding|$cleanRoom"
        val encoded = "$matchPrefix${seats.trim()}|${roomType.trim()}"
        val current = _state.value
        val existing = current.settings.favoriteClassrooms.toMutableList()
        val wasPresent = existing.any { it.startsWith(matchPrefix) || it == legacyExact }
        existing.removeAll { it.startsWith(matchPrefix) || it == legacyExact }
        val added = if (!wasPresent) {
            existing.add(0, encoded)
            true
        } else {
            false
        }
        persist(current.copy(settings = current.settings.copy(favoriteClassrooms = existing)))
        return added
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
        detectedCampusId: String? = null,
        practiceCourses: List<PracticeCourseItem> = emptyList()
    ): GdouCampus? {
        val current = _state.value
        val manualCourses = current.courses.filter { it.isManual }
        // Preserve user's local notes & attendance counts across re-syncs!
        val mergedCourses = mergeSyncedCourseMetadata(current.courses, courses)
        val coloredCourses = assignStableColors(mergedCourses)
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
                practiceCourses = practiceCourses,
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
     * 快速更新单门课的课堂笔记 / 考勤备忘（无需进入完整编辑表单）
     */
    fun updateCourseNote(courseId: String, newNote: String) {
        val current = _state.value
        val trimmed = newNote.trim()
        val updated = current.courses.map { c ->
            if (c.id == courseId) c.copy(note = trimmed) else c
        }
        persist(current.copy(courses = updated))
    }

    /**
     * 快速更新单门课的考勤/平时分小记（请假/缺勤 或 发言/加分次数，同步应用至同名课程便于跨节统计）
     */
    fun updateCourseAttendance(courseId: String, absenceDelta: Int, bonusDelta: Int) {
        val current = _state.value
        val targetName = current.courses.firstOrNull { it.id == courseId }?.name?.trim() ?: return
        val updated = current.courses.map { c ->
            if (c.id == courseId || c.name.trim() == targetName) {
                c.copy(
                    absenceCount = (c.absenceCount + absenceDelta).coerceIn(0, 99),
                    bonusCount = (c.bonusCount + bonusDelta).coerceIn(0, 99)
                )
            } else c
        }
        persist(current.copy(courses = updated))
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

    fun replaceSyncedGrades(grades: List<GradeItem>, levelExams: List<LevelExamItem> = emptyList()) {
        val current = _state.value
        val simulated = current.grades.filter { it.isSimulated }
        val prevBreakdowns = current.grades
            .filter { !it.isSimulated && it.breakdown.isNotBlank() }
            .associate { "${it.academicYear}|${it.semester}|${it.courseName}" to it.breakdown }
        val merged = grades.map { g ->
            val key = "${g.academicYear}|${g.semester}|${g.courseName}"
            if (g.breakdown.isBlank() && prevBreakdowns[key] != null) {
                g.copy(breakdown = prevBreakdowns[key].orEmpty())
            } else g
        }
        val nowLabel = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
        persist(
            current.copy(
                grades = merged + simulated,
                levelExams = if (levelExams.isNotEmpty()) levelExams else current.levelExams,
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
        val pinnedKeys = current.exams
            .filter { !it.isManual && it.isPinned }
            .map { "${it.courseName.trim()}|${it.examTimeRaw.trim()}" }
            .toSet()
        val pinnedNames = current.exams
            .filter { !it.isManual && it.isPinned }
            .map { it.courseName.trim() }
            .toSet()
        val merged = exams.map { ex ->
            val wasPinned = "${ex.courseName.trim()}|${ex.examTimeRaw.trim()}" in pinnedKeys ||
                ex.courseName.trim() in pinnedNames
            if (wasPinned) ex.copy(isPinned = true) else ex
        }
        val nowLabel = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
        persist(
            current.copy(
                exams = merged + manual,
                settings = current.settings.copy(lastSyncTime = nowLabel)
            )
        )
    }

    fun toggleExamPin(examId: String): Boolean {
        val current = _state.value
        var nextPinned = false
        val updated = current.exams.map { ex ->
            if (ex.id == examId) {
                nextPinned = !ex.isPinned
                ex.copy(isPinned = nextPinned)
            } else ex
        }
        persist(current.copy(exams = updated))
        return nextPinned
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

    fun parseScheduleShareCode(raw: String): Result<ScheduleShareBundle> {
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
            Result.success(bundle)
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("分享码格式有误：${e.message}"))
        }
    }

    fun importScheduleShareCode(raw: String): Result<Int> {
        val parsed = parseScheduleShareCode(raw)
        val bundle = parsed.getOrElse { return Result.failure(it) }
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
        return Result.success(imported.size)
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
                practiceCourses = emptyList(),
                grades = emptyList(),
                levelExams = emptyList(),
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

internal fun computeScaledTargetDimensions(
    srcWidth: Int,
    srcHeight: Int,
    maxDim: Int = 1440
): Pair<Int, Int> {
    val safeMax = maxDim.coerceAtLeast(1)
    if (srcWidth <= 0 || srcHeight <= 0) return safeMax to safeMax
    if (srcWidth <= safeMax && srcHeight <= safeMax) return srcWidth to srcHeight
    val scale = safeMax.toFloat() / maxOf(srcWidth, srcHeight).toFloat()
    val w = (srcWidth * scale).toInt().coerceIn(1, safeMax)
    val h = (srcHeight * scale).toInt().coerceIn(1, safeMax)
    return w to h
}

internal fun computeBitmapInSampleSize(
    srcWidth: Int,
    srcHeight: Int,
    maxDim: Int = 1440
): Int {
    val safeMax = maxDim.coerceAtLeast(1)
    if (srcWidth <= 0 || srcHeight <= 0) return 1
    var sample = 1
    while (srcWidth / sample > safeMax || srcHeight / sample > safeMax) {
        sample *= 2
    }
    return sample
}

internal fun mergeSyncedCourseMetadata(
    previousCourses: List<CourseItem>,
    syncedCourses: List<CourseItem>
): List<CourseItem> {
    val nonManualPrev = previousCourses.filterNot { it.isManual }
    val prevBySlot = nonManualPrev.associateBy {
        "${it.name.trim()}|${it.dayOfWeek}|${it.startSection}|${it.endSection}"
    }
    val prevByName = nonManualPrev
        .filter { it.note.isNotBlank() || it.absenceCount > 0 || it.bonusCount > 0 }
        .associateBy { it.name.trim() }
    return syncedCourses.map { c ->
        val matched = prevBySlot["${c.name.trim()}|${c.dayOfWeek}|${c.startSection}|${c.endSection}"]
            ?: prevByName[c.name.trim()]
        if (matched != null) {
            c.copy(
                courseType = c.courseType.ifBlank { matched.courseType },
                examMethod = c.examMethod.ifBlank { matched.examMethod },
                classGroup = c.classGroup.ifBlank { matched.classGroup },
                totalHours = c.totalHours.ifBlank { matched.totalHours },
                note = matched.note.ifBlank { c.note },
                absenceCount = if (c.absenceCount > 0) c.absenceCount else matched.absenceCount,
                bonusCount = if (c.bonusCount > 0) c.bonusCount else matched.bonusCount
            )
        } else c
    }
}
