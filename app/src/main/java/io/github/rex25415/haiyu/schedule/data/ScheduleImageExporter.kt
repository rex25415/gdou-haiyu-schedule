package io.github.rex25415.haiyu.schedule.data

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import io.github.rex25415.haiyu.schedule.model.AppDataSnapshot
import java.io.File
import java.io.FileOutputStream
import java.time.format.DateTimeFormatter

data class ImageExportResult(
    val ok: Boolean,
    val savedToGallery: Boolean = false,
    val sharedViaSheet: Boolean = false
)

/**
 * 原生高清周课表图片生成器（1080×1920），一键分享给室友/对象或存相册设为锁屏壁纸
 */
object ScheduleImageExporter {

    private val CARD_BG = intArrayOf(
        0xFFD4F5EC.toInt(), 0xFFD9ECFF.toInt(), 0xFFFFE5D4.toInt(), 0xFFE8E2FF.toInt(),
        0xFFDDF4D8.toInt(), 0xFFFFE1EA.toInt(), 0xFFFFF1CE.toInt(), 0xFFD6F1F8.toInt(),
        0xFFF3E2FA.toInt(), 0xFFE4F0D3.toInt(), 0xFFFFE3DF.toInt(), 0xFFE0EBFA.toInt()
    )
    private val CARD_ACCENT = intArrayOf(
        0xFF0E8A7D.toInt(), 0xFF1E78D6.toInt(), 0xFFD86826.toInt(), 0xFF6B52D1.toInt(),
        0xFF378F3E.toInt(), 0xFFD44374.toInt(), 0xFFC2870E.toInt(), 0xFF1485A3.toInt(),
        0xFF9C47BA.toInt(), 0xFF638C28.toInt(), 0xFFC94A3D.toInt(), 0xFF3E6BB5.toInt()
    )
    private val CARD_TEXT = intArrayOf(
        0xFF0A433D.toInt(), 0xFF0E3866.toInt(), 0xFF5E2B0C.toInt(), 0xFF2C1E63.toInt(),
        0xFF16471A.toInt(), 0xFF5E1630.toInt(), 0xFF543903.toInt(), 0xFF083C4A.toInt(),
        0xFF471957.toInt(), 0xFF2B3F0E.toInt(), 0xFF5B1B15.toInt(), 0xFF19325C.toInt()
    )

    fun exportWeekScheduleImageAndShare(
        context: Context,
        snapshot: AppDataSnapshot,
        weekNumber: Int
    ): ImageExportResult {
        if (snapshot.courses.isEmpty() && snapshot.exams.isEmpty()) return ImageExportResult(ok = false)
        val width = 1080
        val height = 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val settings = snapshot.settings
        val maxSec = (snapshot.courses.maxOfOrNull { it.endSection } ?: 10).coerceIn(10, 12)
        val slots = GdouTimeRules.slotsForCampus(settings.campus, maxSec)
        val weekDates = GdouTimeRules.getWeekDates(settings.semesterStartDate, weekNumber)
        val weekExams = if (settings.showExamsOnTimetable) {
            GdouTimeRules.resolveExamsForWeek(
                exams = snapshot.exams,
                startDateStr = settings.semesterStartDate,
                weekNumber = weekNumber,
                campus = settings.campus,
                maxSection = maxSec
            )
        } else emptyList()
        val hasAnyWeekend = settings.showWeekend ||
            snapshot.courses.any { it.dayOfWeek >= 6 } ||
            weekExams.any { it.dayOfWeek >= 6 }
        val dayCount = if (hasAnyWeekend) 7 else 5

        // Soft ocean-mint background
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(0xFFF3FBF8.toInt(), 0xFFEAF6F3.toInt(), 0xFFF7FBFA.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Header banner
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                40f, 40f, width - 40f, 210f,
                0xFF0C7D72.toInt(), 0xFF286684.toInt(),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(RectF(36f, 40f, width - 36f, 206f), 32f, 32f, headerPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 52f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xDDFFFFFF.toInt()
            textSize = 27f
        }
        val fmt = DateTimeFormatter.ofPattern("M月d日")
        canvas.drawText("第 $weekNumber 周课程表", 74f, 114f, titlePaint)
        canvas.drawText(
            "${settings.academicYearLabel} · ${settings.campus.label} · ${weekDates.first().format(fmt)}-${weekDates.last().format(fmt)}",
            74f, 166f, subPaint
        )

        val gridTop = 236f
        val headerRowH = 74f
        val timeColW = 104f
        val marginX = 28f
        val gridBottom = height - 88f
        val colW = (width - marginX * 2 - timeColW) / dayCount
        val rowH = (gridBottom - gridTop - headerRowH) / slots.size

        // Day headers
        val dayTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF15322F.toInt()
            textSize = 28f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val dateSmallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF5A7470.toInt()
            textSize = 22f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("${weekDates.first().monthValue}月", marginX + timeColW / 2f, gridTop + 48f, dayTextPaint)
        for (d in 1..dayCount) {
            val cx = marginX + timeColW + (d - 1) * colW + colW / 2f
            canvas.drawText(GdouTimeRules.dayOfWeekLabel(d), cx, gridTop + 34f, dayTextPaint)
            canvas.drawText("${weekDates[d - 1].dayOfMonth}", cx, gridTop + 64f, dateSmallPaint)
        }

        // Grid lines + left time labels
        val linePaint = Paint().apply {
            color = 0x220E5E56
            strokeWidth = 1.5f
        }
        val secNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF15322F.toInt()
            textSize = 26f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val slotTimePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF607774.toInt()
            textSize = 19f
            textAlign = Paint.Align.CENTER
        }

        val bodyTop = gridTop + headerRowH
        for (i in 0..slots.size) {
            val y = bodyTop + i * rowH
            canvas.drawLine(marginX, y, width - marginX, y, linePaint)
            if (i < slots.size) {
                val s = slots[i]
                val cx = marginX + timeColW / 2f
                canvas.drawText("${s.number}", cx, y + rowH * 0.40f, secNumPaint)
                canvas.drawText(s.startTime, cx, y + rowH * 0.66f, slotTimePaint)
                canvas.drawText(s.endTime, cx, y + rowH * 0.87f, slotTimePaint)
            }
        }
        for (d in 0..dayCount) {
            val x = marginX + timeColW + d * colW
            canvas.drawLine(x, bodyTop, x, gridBottom, linePaint)
        }

        // Draw active courses for the selected week (with full Overlap / Conflict awareness!)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        val pinnedSet = settings.pinnedConflictKeys.toSet()
        for (day in 1..dayCount) {
            val activeDayCourses = snapshot.courses.filter { it.dayOfWeek == day && weekNumber in it.weeks }
            val clusters = GdouTimeRules.groupOverlappingCourses(activeDayCourses, pinnedSet)
            for (cluster in clusters) {
                val course = cluster.courses.first()
                val cIdx = ((course.colorIndex % CARD_BG.size) + CARD_BG.size) % CARD_BG.size
                val s = cluster.minStartSection.coerceIn(1, slots.size)
                val e = cluster.maxEndSection.coerceIn(s, slots.size)
                val left = marginX + timeColW + (day - 1) * colW + 4f
                val right = left + colW - 8f
                val top = bodyTop + (s - 1) * rowH + 4f
                val bottom = bodyTop + e * rowH - 4f

                if (cluster.hasConflict) {
                    // Subtle offset back layer for stacked conflict look
                    fillPaint.color = 0xFFFFEFE2.toInt()
                    canvas.drawRoundRect(RectF(left + 6f, top + 6f, right, bottom), 16f, 16f, fillPaint)
                    strokePaint.color = 0xFFD94A1E.toInt()
                    canvas.drawRoundRect(RectF(left + 6f, top + 6f, right, bottom), 16f, 16f, strokePaint)
                }

                val cardRight = if (cluster.hasConflict) right - 5f else right
                val cardBottom = if (cluster.hasConflict) bottom - 5f else bottom
                val rect = RectF(left, top, cardRight, cardBottom)
                fillPaint.color = CARD_BG[cIdx]
                canvas.drawRoundRect(rect, 18f, 18f, fillPaint)
                strokePaint.color = if (cluster.hasConflict) 0xFFD94A1E.toInt() else CARD_ACCENT[cIdx]
                strokePaint.alpha = if (cluster.hasConflict) 220 else 55
                strokePaint.strokeWidth = if (cluster.hasConflict) 2.5f else 1.5f
                canvas.drawRoundRect(rect, 18f, 18f, strokePaint)

                val resolved = GdouTimeRules.resolveCourseTime(course, settings.campus)
                val innerW = (cardRight - left - 18f).toInt().coerceAtLeast(36)
                var cursorY = top + 11f

                if (cluster.hasConflict) {
                    val badgeText = "重×${cluster.courses.size}"
                    val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE
                        textSize = 17f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    val bw = badgePaint.measureText(badgeText) + 12f
                    fillPaint.color = 0xFFD94A1E.toInt()
                    canvas.drawRoundRect(RectF(left + 9f, cursorY, left + 9f + bw, cursorY + 24f), 7f, 7f, fillPaint)
                    canvas.drawText(badgeText, left + 15f, cursorY + 18f, badgePaint)
                    cursorY += 29f
                } else if (resolved.isSpecialStaggered) {
                    val badgeText = if (dayCount == 5) "错峰${resolved.startTime}" else resolved.startTime
                    val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE
                        textSize = if (dayCount == 5) 19f else 17f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    val bw = badgePaint.measureText(badgeText) + 14f
                    fillPaint.color = CARD_ACCENT[cIdx]
                    canvas.drawRoundRect(RectF(left + 9f, cursorY, left + 9f + bw, cursorY + 25f), 8f, 8f, fillPaint)
                    canvas.drawText(badgeText, left + 16f, cursorY + 19f, badgePaint)
                    cursorY += 30f
                }

                val displayTitle = if (cluster.hasConflict) {
                    cluster.courses.joinToString("\n/ ") { it.name }
                } else {
                    course.name
                }
                val nameTp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = CARD_TEXT[cIdx]
                    textSize = if (dayCount == 5) 26f else 21f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val nameLayout = StaticLayout.Builder.obtain(displayTitle, 0, displayTitle.length, nameTp, innerW)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setMaxLines(if (cluster.hasConflict) 5 else 4)
                    .build()
                canvas.save()
                canvas.translate(left + 9f, cursorY)
                nameLayout.draw(canvas)
                canvas.restore()

                if (!cluster.hasConflict && course.location.isNotBlank()) {
                    val locTp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = CARD_TEXT[cIdx]
                        alpha = 195
                        textSize = if (dayCount == 5) 21f else 17f
                    }
                    val locStr = course.location
                    val locLayout = StaticLayout.Builder.obtain(locStr, 0, locStr.length, locTp, innerW)
                        .setMaxLines(2)
                        .build()
                    val locY = (cursorY + nameLayout.height + 8f).coerceAtMost(cardBottom - locLayout.height - 8f)
                    canvas.save()
                    canvas.translate(left + 9f, locY)
                    locLayout.draw(canvas)
                    canvas.restore()
                }
            }

            // Also draw projected finals exams on the week poster in high-contrast amber!
            val dayExams = weekExams.filter { it.dayOfWeek == day }
            for (exBlock in dayExams) {
                val s = exBlock.startSection.coerceIn(1, slots.size)
                val e = exBlock.endSection.coerceIn(s, slots.size)
                val left = marginX + timeColW + (day - 1) * colW + 4f
                val right = left + colW - 8f
                val top = bodyTop + (s - 1) * rowH + 4f
                val bottom = bodyTop + e * rowH - 4f
                val rect = RectF(left, top, right, bottom)

                fillPaint.color = 0xFFFFF0DB.toInt()
                canvas.drawRoundRect(rect, 18f, 18f, fillPaint)
                strokePaint.color = 0xFFD97706.toInt()
                strokePaint.alpha = 235
                strokePaint.strokeWidth = 2.8f
                canvas.drawRoundRect(rect, 18f, 18f, strokePaint)

                val innerW = (right - left - 18f).toInt().coerceAtLeast(36)
                var cursorY = top + 11f
                val badgeText = "考试 ${exBlock.startTime}"
                val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = 17f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val bw = (badgePaint.measureText(badgeText) + 12f).coerceAtMost(right - left - 14f)
                fillPaint.color = 0xFFD97706.toInt()
                canvas.drawRoundRect(RectF(left + 9f, cursorY, left + 9f + bw, cursorY + 24f), 7f, 7f, fillPaint)
                canvas.drawText(badgeText, left + 15f, cursorY + 18f, badgePaint)
                cursorY += 29f

                val nameTp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF78350F.toInt()
                    textSize = if (dayCount == 5) 25f else 20f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val nameLayout = StaticLayout.Builder.obtain(
                    exBlock.exam.courseName,
                    0,
                    exBlock.exam.courseName.length,
                    nameTp,
                    innerW
                )
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setMaxLines(3)
                    .build()
                canvas.save()
                canvas.translate(left + 9f, cursorY)
                nameLayout.draw(canvas)
                canvas.restore()

                val roomSeat = buildString {
                    append(exBlock.exam.location.ifBlank { "考场待定" })
                    if (exBlock.exam.seatNumber.isNotBlank()) append("·${exBlock.exam.seatNumber}座")
                }
                val locTp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF78350F.toInt()
                    alpha = 215
                    textSize = if (dayCount == 5) 20f else 16.5f
                }
                val locLayout = StaticLayout.Builder.obtain(roomSeat, 0, roomSeat.length, locTp, innerW)
                    .setMaxLines(2)
                    .build()
                val locY = (cursorY + nameLayout.height + 6f).coerceAtMost(bottom - locLayout.height - 8f)
                canvas.save()
                canvas.translate(left + 9f, locY)
                locLayout.draw(canvas)
                canvas.restore()
            }
        }

        // Clean footer watermark
        val footPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF5A7873.toInt()
            textSize = 24f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("由「海屿空间」生成 · 免费纯开源 · rex25415.github.io/gdou-haiyu-schedule", width / 2f, height - 34f, footPaint)

        val fileName = "海屿空间_第${weekNumber}周_${System.currentTimeMillis()}.png"
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val imgFile = File(exportDir, fileName)
        FileOutputStream(imgFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 96, out)
        }

        // Save directly to System Gallery (Pictures/海屿空间) on Android 10+ with zero extra permissions
        // Copy the already-encoded PNG bytes instead of encoding a second 1080x1920 PNG (2x faster!)
        var savedToGallery = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/海屿空间")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val mediaUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (mediaUri != null) {
                    resolver.openOutputStream(mediaUri)?.use { out ->
                        imgFile.inputStream().use { inp -> inp.copyTo(out) }
                    }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(mediaUri, values, null, null)
                    savedToGallery = true
                }
            } catch (_: Exception) {
            }
        }
        bitmap.recycle()

        // Launch Android ShareSheet with ClipData + URI grant so Android 15 renders a live thumbnail preview
        var shared = false
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imgFile)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "第${weekNumber}周课程表")
                clipData = ClipData.newRawUri("海屿空间", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val receivers = context.packageManager.queryIntentActivities(sendIntent, 0)
            if (receivers.isNotEmpty()) {
                val chooser = Intent.createChooser(sendIntent, "分享高清课表图（已同时保存至相册）").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(chooser)
                shared = true
            }
        } catch (_: Exception) {
        }
        return ImageExportResult(ok = true, savedToGallery = savedToGallery, sharedViaSheet = shared)
    }
}
