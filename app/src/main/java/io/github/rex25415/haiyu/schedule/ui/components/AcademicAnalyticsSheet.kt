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
import androidx.compose.material.icons.rounded.CheckCircle
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
        "及格", "合格", "通过", "免修" -> 65.0
        "不及格", "不合格", "缺考", "舞弊", "取消资格" -> 0.0
        else -> null
    }
}

internal fun isGradePassed(grade: GradeItem): Boolean {
    if (grade.gradePoint > 0.0) return true
    val num = parseScoreNumeric(grade.score) ?: return false
    return num >= 60.0
}

private val PRACTICE_COURSE_NAME_RE =
    Regex("实验|实训|实习|训练|军事技能|军训|课程设计|毕业设计|毕业论文|社会实践|金工")

/**
 * GDOU Zhengfang's kcxzmc field only labels courses as "必修"/"任选"/"限选", while practical training
 * and laboratory courses (e.g. 大学物理实验II, 海上求生训练, 基本安全训练, 工程训练) are identified by course name.
 */
internal fun isPracticeOrExperimentGrade(grade: GradeItem): Boolean {
    val t = grade.courseType.trim()
    if (t.contains("实践") || t.contains("实验")) return true
    return PRACTICE_COURSE_NAME_RE.containsMatchIn(grade.courseName)
}

internal enum class GpaCalcMode(val label: String, val shortHint: String) {
    EFFECTIVE_BEST("有效去重", "同名课初修与补考/重修自动取最佳成绩，不重复双倍计入挂科学分"),
    PASSED_ONLY("仅已通过", "仅统计已及格通过（拿到学分）的课程均绩与已获学分"),
    RAW_ALL("原始全计", "教务原始成绩单全部记录不合并直接算术加权")
}

internal data class SupersededGradeInfo(
    val bestAttempt: GradeItem,
    val isBestPassed: Boolean
)

internal data class DeduplicatedGradesResult(
    val effectiveGrades: List<GradeItem>,
    val passedGrades: List<GradeItem>,
    val supersededByMap: Map<String, SupersededGradeInfo>,
    val earnedCredits: Double,
    val attemptedDistinctCredits: Double,
    val duplicateCreditsRemoved: Double,
    val duplicateCourseCount: Int,
    val makeupRecoveredCourses: List<GradeItem>
)

/**
 * Deduplicates multi-attempt courses (initial exam + 补考/重修) returned by Zhengfang's raw grade list.
 * Without deduplication, a 4.5-credit course that failed in initial exam and makeup exam would be
 * counted as 9.0 credits of 0.0 GPA, and a course passed in makeup exam (e.g. 37 -> 96) would still
 * carry its old 1.0 credit of 0.0 GPA in the denominator.
 */
internal fun deduplicateEffectiveGrades(grades: List<GradeItem>): DeduplicatedGradesResult {
    val grouped = grades.groupBy { g ->
        if (g.isSimulated) "__sim_${g.id}" else g.courseName.trim()
    }
    val effective = ArrayList<GradeItem>(grouped.size)
    val supersededMap = LinkedHashMap<String, SupersededGradeInfo>()
    var duplicateCrRemoved = 0.0
    var duplicateCourses = 0
    val recovered = ArrayList<GradeItem>()

    val attemptComparator = Comparator<GradeItem> { a, b ->
        val passCmp = isGradePassed(a).compareTo(isGradePassed(b))
        if (passCmp != 0) return@Comparator passCmp
        val gpCmp = a.gradePoint.compareTo(b.gradePoint)
        if (gpCmp != 0) return@Comparator gpCmp
        val scA = parseScoreNumeric(a.score) ?: -1.0
        val scB = parseScoreNumeric(b.score) ?: -1.0
        val scCmp = scA.compareTo(scB)
        if (scCmp != 0) return@Comparator scCmp
        "${a.academicYear} ${a.semester}".compareTo("${b.academicYear} ${b.semester}")
    }

    for ((_, group) in grouped) {
        if (group.size <= 1) {
            effective.addAll(group)
        } else {
            duplicateCourses++
            val best = group.maxWith(attemptComparator)
            val bestPassed = isGradePassed(best)
            effective.add(best)
            var hadFailedAttempt = false
            for (item in group) {
                if (!isGradePassed(item)) hadFailedAttempt = true
                if (item.id != best.id) {
                    supersededMap[item.id] = SupersededGradeInfo(bestAttempt = best, isBestPassed = bestPassed)
                    if (item.credit > 0.0) {
                        duplicateCrRemoved += item.credit
                    }
                }
            }
            if (bestPassed && hadFailedAttempt) {
                recovered.add(best)
            }
        }
    }

    val passed = effective.filter { isGradePassed(it) }
    val earnedCr = passed.filter { it.credit > 0.0 }.sumOf { it.credit }
    val attemptedCr = effective.filter { it.credit > 0.0 }.sumOf { it.credit }

    return DeduplicatedGradesResult(
        effectiveGrades = effective,
        passedGrades = passed,
        supersededByMap = supersededMap,
        earnedCredits = earnedCr,
        attemptedDistinctCredits = attemptedCr,
        duplicateCreditsRemoved = duplicateCrRemoved,
        duplicateCourseCount = duplicateCourses,
        makeupRecoveredCourses = recovered
    )
}

internal data class GradeStatMetrics(
    val gpa: Double,
    val weightedAvgScore: Double,
    val totalCredits: Double,
    val earnedCredits: Double,
    val courseCount: Int
)

internal fun computeGradeMetrics(
    grades: List<GradeItem>,
    mode: GpaCalcMode = GpaCalcMode.EFFECTIVE_BEST
): GradeStatMetrics {
    val dedup = deduplicateEffectiveGrades(grades)
    val targetList = when (mode) {
        GpaCalcMode.EFFECTIVE_BEST -> dedup.effectiveGrades
        GpaCalcMode.PASSED_ONLY -> dedup.passedGrades
        GpaCalcMode.RAW_ALL -> grades
    }
    var totalCr = 0.0
    var weightedGp = 0.0
    var weightedScore = 0.0
    var numericCr = 0.0
    for (g in targetList) {
        if (g.credit > 0.0) {
            totalCr += g.credit
            weightedGp += g.gradePoint * g.credit
            val num = parseScoreNumeric(g.score)
            if (num != null) {
                weightedScore += num * g.credit
                numericCr += g.credit
            }
        }
    }
    val gpa = if (totalCr > 0.0) weightedGp / totalCr else 0.0
    val avg = if (numericCr > 0.0) weightedScore / numericCr else 0.0
    return GradeStatMetrics(
        gpa = gpa,
        weightedAvgScore = avg,
        totalCredits = totalCr,
        earnedCredits = dedup.earnedCredits,
        courseCount = targetList.size
    )
}

/**
 * Full-depth academic analytics bottom sheet keeping the main grades ledger uncluttered while
 * offering authentic deduplicated semester GPA sparklines, graduation credit audit bars, CET/NCRE archives, and GPA planning.
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
    val dedup = remember(grades) { deduplicateEffectiveGrades(grades) }
    val effectiveGrades = dedup.effectiveGrades

    val termGpaSeries = remember(effectiveGrades) {
        effectiveGrades
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

    val creditByCategory = remember(dedup.passedGrades) {
        dedup.passedGrades
            .filter { it.credit > 0.0 }
            .groupBy { g ->
                val raw = g.courseType.trim()
                when {
                    isPracticeOrExperimentGrade(g) -> "实践/实验"
                    raw.isBlank() -> "其他课程"
                    raw.contains("必修") -> "必修理论"
                    raw.contains("通识") || raw.contains("公选") || raw.contains("任选") -> "通识/公选"
                    raw.contains("选修") || raw.contains("限选") -> "专业选修"
                    else -> raw.take(6)
                }
            }
            .map { (cat, list) -> Triple(cat, list.sumOf { it.credit }, list.size) }
            .sortedByDescending { it.second }
    }

    val effectiveMetrics = remember(grades) {
        computeGradeMetrics(grades, GpaCalcMode.EFFECTIVE_BEST)
    }
    val passedMetrics = remember(grades) {
        computeGradeMetrics(grades, GpaCalcMode.PASSED_ONLY)
    }

    val lowScoreCourses = remember(effectiveGrades) {
        effectiveGrades.filter { g ->
            g.credit > 0.0 && !g.isSimulated && !isGradePassed(g)
        }
    }

    val earnedMap = remember(dedup.passedGrades, creditTargets) {
        val valid = dedup.passedGrades.filter { it.credit > 0.0 }
        val total = valid.sumOf { it.credit }
        val prac = valid.filter { isPracticeOrExperimentGrade(it) }.sumOf { it.credit }
        val nonPrac = valid.filterNot { isPracticeOrExperimentGrade(it) }
        val comp = nonPrac.filter { it.courseType.contains("必修") }.sumOf { it.credit }
        val gen = nonPrac.filter {
            it.courseType.contains("通识") || it.courseType.contains("公选") || it.courseType.contains("任选")
        }.sumOf { it.credit }
        val elec = (total - prac - comp - gen).coerceAtLeast(0.0)
        listOf(
            Triple("毕业总学分 (已获)", total, creditTargets.totalRequired),
            Triple("必修理论课学分", comp, creditTargets.compulsoryRequired),
            Triple("专业选修学分", elec, creditTargets.electiveRequired),
            Triple("通识/公选学分", gen, creditTargets.generalRequired),
            Triple("实践/实验教学环节", prac, creditTargets.practiceRequired)
        )
    }

    var targetGpaInput by remember { mutableStateOf("2.5") }
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
                        text = "有效 GPA ${String.format(Locale.US, "%.2f", effectiveMetrics.gpa)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Authentic Deduplication & Passed Summary Banner
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "已获学分 ${String.format(Locale.US, "%.1f", dedup.earnedCredits)} 分 · 修读总学分 ${String.format(Locale.US, "%.1f", dedup.attemptedDistinctCredits)} 分 · 已通过课程 GPA ${String.format(Locale.US, "%.2f", passedMetrics.gpa)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    if (dedup.duplicateCourseCount > 0) {
                        Text(
                            text = buildString {
                                append("智能校准：已自动合并 ${dedup.duplicateCourseCount} 门课程的初修与补考/重修重复记录（剔除重复累加学分 ${String.format(Locale.US, "%.2f", dedup.duplicateCreditsRemoved)} 分）")
                                if (dedup.makeupRecoveredCourses.isNotEmpty()) {
                                    append("，其中 ${dedup.makeupRecoveredCourses.size} 门已通过补考/重修消警（${dedup.makeupRecoveredCourses.joinToString("、") { it.courseName }}）。")
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f)
                        )
                    }
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
                            text = "各学期有效 GPA 走势（共 ${termGpaSeries.size} 学期）",
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
                        val zeroCreditPassedCount = dedup.passedGrades.count { it.credit <= 0.0 }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Text(
                            text = buildString {
                                append("含学分已获分布（去重通过 ${creditByCategory.sumOf { it.third }} 门")
                                if (zeroCreditPassedCount > 0) append(" · 另 $zeroCreditPassedCount 门 0 学分已通过")
                                append("）")
                            },
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
            if (effectiveMetrics.totalCredits > 0.0) {
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
                            text = "目标 GPA 反推测算（基于有效修读 ${String.format(Locale.US, "%.1f", effectiveMetrics.totalCredits)} 学分）",
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
                            listOf("2.0", "2.5", "3.0", "3.5", "3.8").forEach { preset ->
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
                            val plan = remember(effectiveMetrics, targetVal, futureCrVal) {
                                GdouTimeRules.calculateTargetGpaPlan(
                                    currentGpa = effectiveMetrics.gpa,
                                    currentCredits = effectiveMetrics.totalCredits,
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

            // 5. Low-Score / Retake Reminder (Deduplicated: excludes courses already passed in makeup/retake!)
            if (dedup.makeupRecoveredCourses.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "已通过补考/重修消警 ${dedup.makeupRecoveredCourses.size} 门：${dedup.makeupRecoveredCourses.joinToString("、") { "${it.courseName}(${it.score}分)" }}",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            if (lowScoreCourses.isNotEmpty()) {
                val lowCrSum = lowScoreCourses.sumOf { it.credit }
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
                                text = "学业体检：当前待通过/重修 ${lowScoreCourses.size} 门（去重后共 ${String.format(Locale.US, "%.1f", lowCrSum)} 学分）",
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
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                lowScoreCourses.forEach { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "• ${item.courseName}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                                        )
                                        Text(
                                            text = "最高 ${item.score.ifBlank { "0" }} 分 · ${item.credit}学分",
                                            fontSize = 10.5.sp,
                                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
