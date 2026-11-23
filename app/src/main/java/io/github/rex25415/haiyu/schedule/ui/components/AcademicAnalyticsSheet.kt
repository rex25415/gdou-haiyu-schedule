package io.github.rex25415.haiyu.schedule.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rex25415.haiyu.schedule.data.GdouTimeRules
import io.github.rex25415.haiyu.schedule.model.GradeItem
import io.github.rex25415.haiyu.schedule.model.GraduationCreditTargets
import io.github.rex25415.haiyu.schedule.model.LevelExamItem
import java.util.Locale

internal fun parseScoreNumeric(score: String): Double? {
    score.trim().toDoubleOrNull()?.let { return it }
    return when (score.trim()) {
        "优秀", "优" -> 95.0
        "良好", "良" -> 85.0
        "中等", "中" -> 75.0
        "及格", "合格", "通过" -> 65.0
        "不及格", "不合格", "缺考" -> 0.0
        else -> null
    }
}

/**
 * Full-depth academic analytics bottom sheet keeping the main grades ledger uncluttered while
 * offering semester GPA sparklines, graduation credit audit bars, CET/NCRE archives, and GPA planning.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AcademicAnalyticsBottomSheet(
    grades: List<GradeItem>,
    levelExams: List<LevelExamItem>,
    creditTargets: GraduationCreditTargets,
    onOpenEditTargets: () -> Unit,
    onDismiss: () -> Unit
) {
    val termGpaSeries = remember(grades) {
        grades
            .filter { it.credit > 0.0 }
            .groupBy { "${it.academicYear} ${it.semester}" }
            .entries
            .sortedBy { (k, _) -> k }
            .mapNotNull { (termKey, list) ->
                val cr = list.sumOf { it.credit }
                if (cr <= 0.0) null else {
                    val gp = list.sumOf { it.gradePoint * it.credit } / cr
                    val shortLabel = termKey
                        .replace("20", "")
                        .replace(" 第一学期", "①")
                        .replace(" 第二学期", "②")
                        .replace(" 第三学期", "③")
                    shortLabel to gp
                }
            }
    }

    val creditByCategory = remember(grades) {
        grades
            .filter { it.credit > 0.0 }
            .groupBy { g ->
                val raw = g.courseType.trim()
                when {
                    raw.isBlank() -> "其他课程"
                    raw.contains("必修") -> "必修课"
                    raw.contains("选修") || raw.contains("任选") || raw.contains("限选") -> "选修课"
                    raw.contains("实践") || raw.contains("实验") -> "实践环节"
                    raw.contains("通识") || raw.contains("公选") -> "通识/公选"
                    else -> raw.take(6)
                }
            }
            .map { (cat, list) -> Triple(cat, list.sumOf { it.credit }, list.size) }
            .sortedByDescending { it.second }
    }

    val allTermStats = remember(grades) {
        var totalCr = 0.0
        var weightedGp = 0.0
        grades.forEach { g ->
            if (g.credit > 0.0) {
                totalCr += g.credit
                weightedGp += g.gradePoint * g.credit
            }
        }
        val gpa = if (totalCr > 0.0) weightedGp / totalCr else 0.0
        gpa to totalCr
    }

    val lowScoreCourses = remember(grades) {
        grades.filter { g ->
            g.credit > 0.0 && !g.isSimulated && (
                g.gradePoint <= 0.0 || (parseScoreNumeric(g.score)?.let { it < 60.0 } == true)
            )
        }
    }

    val earnedMap = remember(grades, creditTargets) {
        val valid = grades.filter {
            it.credit > 0.0 && (it.gradePoint > 0.0 || (parseScoreNumeric(it.score)?.let { s -> s >= 60.0 } != false))
        }
        val total = valid.sumOf { it.credit }
        val comp = valid.filter { it.courseType.contains("必修") }.sumOf { it.credit }
        val prac = valid.filter { it.courseType.contains("实践") || it.courseType.contains("实验") }.sumOf { it.credit }
        val gen = valid.filter { it.courseType.contains("通识") || it.courseType.contains("公选") }.sumOf { it.credit }
        val elec = (total - comp - prac - gen).coerceAtLeast(0.0)
        listOf(
            Triple("毕业总学分", total, creditTargets.totalRequired),
            Triple("必修课学分", comp, creditTargets.compulsoryRequired),
            Triple("专业选修学分", elec, creditTargets.electiveRequired),
            Triple("通识/公选学分", gen, creditTargets.generalRequired),
            Triple("实践教学环节", prac, creditTargets.practiceRequired)
        )
    }

    var targetGpaInput by remember { mutableStateOf("3.5") }
    var futureCreditsInput by remember { mutableStateOf("20") }
    var showLowScoreList by remember { mutableStateOf(false) }

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Insights,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "学业全景分析报告",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = "总 GPA ${String.format(Locale.US, "%.2f", allTermStats.first)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // 1. GPA Trend Sparkline
            if (termGpaSeries.size >= 2) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "各学期 GPA 走势（共 ${termGpaSeries.size} 学期）",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val lineColor = MaterialTheme.colorScheme.primary
                        val dotFill = MaterialTheme.colorScheme.surface
                        val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        val minGpa = (termGpaSeries.minOf { it.second } - 0.35).coerceAtLeast(0.0)
                        val maxGpa = (termGpaSeries.maxOf { it.second } + 0.35).coerceAtMost(5.0)
                        val span = (maxGpa - minGpa).coerceAtLeast(0.5)

                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(86.dp)
                                .padding(vertical = 8.dp)
                        ) {
                            val w = size.width
                            val h = size.height
                            drawLine(gridColor, Offset(0f, h), Offset(w, h), strokeWidth = 1.5f)
                            drawLine(gridColor, Offset(0f, h * 0.5f), Offset(w, h * 0.5f), strokeWidth = 1f)

                            val colW = w / termGpaSeries.size.coerceAtLeast(1)
                            val pts = termGpaSeries.mapIndexed { idx, (_, gp) ->
                                val x = colW * (idx + 0.5f)
                                val ratio = ((gp - minGpa) / span).toFloat().coerceIn(0f, 1f)
                                val y = h * (1f - ratio)
                                Offset(x, y)
                            }
                            val path = Path().apply {
                                pts.forEachIndexed { idx, pt ->
                                    if (idx == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = lineColor,
                                style = Stroke(width = 5f, cap = StrokeCap.Round)
                            )
                            pts.forEach { pt ->
                                drawCircle(color = lineColor, radius = 9f, center = pt)
                                drawCircle(color = dotFill, radius = 4.5f, center = pt)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            termGpaSeries.forEach { (label, gp) ->
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "%.2f", gp),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Text(
                                        text = label,
                                        fontSize = 9.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Graduation Credit Progress Audit + Category Badges
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
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
                            text = "毕业学分修读进度审计",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "设置专业目标学分 →",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onOpenEditTargets)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    earnedMap.filter { it.third > 0.0 }.forEach { (label, earned, req) ->
                        val frac = (earned / req).toFloat().coerceIn(0f, 1f)
                        val remain = (req - earned).coerceAtLeast(0.0)
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (remain <= 0.05) {
                                        "${String.format(Locale.US, "%.1f", earned)} / ${String.format(Locale.US, "%.1f", req)}分 (已达标 ✓)"
                                    } else {
                                        "${String.format(Locale.US, "%.1f", earned)} / ${String.format(Locale.US, "%.1f", req)}分 (差${String.format(Locale.US, "%.1f", remain)}分)"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = if (remain <= 0.05) FontWeight.Bold else FontWeight.Normal,
                                    color = if (remain <= 0.05) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(frac)
                                        .height(7.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }

                    if (creditByCategory.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Text(
                            text = "已修学分课程性质分布（共 ${creditByCategory.sumOf { it.third }} 门）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            creditByCategory.forEach { (cat, cr, count) ->
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.78f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "$cat ${String.format(Locale.US, "%.1f", cr)}分 · ${count}门",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Level Exams Archive (CET-4/6 / NCRE)
            if (levelExams.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.School,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "证书与等级考试档案（共 ${levelExams.size} 项）",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        levelExams.forEach { lv ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(lv.examName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    val sub = listOfNotNull(
                                        lv.examDate.takeIf { it.isNotBlank() },
                                        lv.detail.takeIf { it.isNotBlank() }
                                    ).joinToString(" · ")
                                    if (sub.isNotBlank()) {
                                        Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Text(
                                    text = lv.score,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // 4. Target GPA Reverse Calculator
            if (allTermStats.second > 0.0) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "目标 GPA 反推测算（基于已修 ${String.format(Locale.US, "%.1f", allTermStats.second)} 学分）",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            listOf("2.5", "3.0", "3.5", "3.8", "4.0").forEach { preset ->
                                val sel = targetGpaInput.trim() == preset
                                Surface(
                                    color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { targetGpaInput = preset }
                                ) {
                                    Text(
                                        text = "目标 $preset",
                                        fontSize = 11.sp,
                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                        color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = targetGpaInput,
                                onValueChange = { targetGpaInput = it.filter { ch -> ch.isDigit() || ch == '.' }.take(4) },
                                label = { Text("目标总绩点", fontSize = 11.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = futureCreditsInput,
                                onValueChange = { futureCreditsInput = it.filter { ch -> ch.isDigit() || ch == '.' }.take(5) },
                                label = { Text("后续预计学分", fontSize = 11.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        val targetVal = targetGpaInput.toDoubleOrNull()
                        val futureCrVal = futureCreditsInput.toDoubleOrNull()
                        if (targetVal != null && futureCrVal != null && futureCrVal > 0.0) {
                            val plan = remember(allTermStats, targetVal, futureCrVal) {
                                GdouTimeRules.calculateTargetGpaPlan(
                                    currentGpa = allTermStats.first,
                                    currentCredits = allTermStats.second,
                                    targetGpa = targetVal,
                                    plannedFutureCredits = futureCrVal
                                )
                            }
                            Surface(
                                color = if (plan.achievable) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = plan.summaryText,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (plan.achievable) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 5. Low-Score / Retake Reminder
            if (lowScoreCourses.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showLowScoreList = !showLowScoreList }
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "学业体检：${lowScoreCourses.size} 门课程待留意（补考/重修参考）",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (showLowScoreList) "收起" else "展开",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (showLowScoreList) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = lowScoreCourses.joinToString(" · ") {
                                    "${it.courseName}(${it.score.ifBlank { "0" }}/${it.credit}分)"
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.88f)
                            )
                        }
                    }
                }
            }
        }
    }
}
