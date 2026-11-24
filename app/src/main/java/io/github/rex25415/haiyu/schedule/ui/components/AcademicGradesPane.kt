package io.github.rex25415.haiyu.schedule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rex25415.haiyu.schedule.model.GradeItem
import io.github.rex25415.haiyu.schedule.model.GraduationCreditTargets
import io.github.rex25415.haiyu.schedule.model.LevelExamItem
import io.github.rex25415.haiyu.schedule.model.PracticeCourseItem
import java.util.Locale

internal enum class GradeNatureFilter(val label: String) {
    ALL("全部"),
    COMPULSORY("必修"),
    ELECTIVE("选/公选"),
    HIGH_SCORE("≥90满绩"),
    RETAKE_OR_FAILED("挂科/补重")
}

internal fun isRetakeOrFailedGrade(grade: GradeItem): Boolean {
    if (!isGradePassed(grade)) return true
    val nature = grade.examNature.trim()
    return nature.isNotBlank() && nature != "正常考试"
}

internal fun filterAcademicGrades(
    grades: List<GradeItem>,
    selectedTerm: String,
    natureFilter: GradeNatureFilter,
    searchQuery: String
): List<GradeItem> {
    val q = searchQuery.trim()
    return grades.filter { g ->
        val termMatch = selectedTerm == "全部学期" || "${g.academicYear} ${g.semester}" == selectedTerm
        val natureMatch = when (natureFilter) {
            GradeNatureFilter.ALL -> true
            GradeNatureFilter.COMPULSORY -> g.courseType.contains("必修")
            GradeNatureFilter.ELECTIVE -> g.courseType.contains("选") || g.courseType.contains("通识")
            GradeNatureFilter.HIGH_SCORE -> g.gradePoint >= 4.0 || (parseScoreNumeric(g.score)?.let { it >= 90.0 } == true)
            GradeNatureFilter.RETAKE_OR_FAILED -> isRetakeOrFailedGrade(g)
        }
        val queryMatch = q.isEmpty() ||
            g.courseName.contains(q, ignoreCase = true) ||
            g.courseType.contains(q, ignoreCase = true) ||
            g.teacher.contains(q, ignoreCase = true) ||
            g.examNature.contains(q, ignoreCase = true)
        termMatch && natureMatch && queryMatch
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AcademicGradesPane(
    grades: List<GradeItem>,
    isDark: Boolean = false,
    levelExams: List<LevelExamItem> = emptyList(),
    practiceCourses: List<PracticeCourseItem> = emptyList(),
    creditTargets: GraduationCreditTargets = GraduationCreditTargets(),
    lastGradeSkipReason: String?,
    onJumpToSync: () -> Unit,
    onUpdateGrade: (GradeItem) -> Unit = {},
    onUpdateCreditTargets: (GraduationCreditTargets) -> Unit = {},
    onDeleteGrade: (String) -> Unit
) {
    if (grades.isEmpty()) {
        EmptyAcademicHint(
            title = "暂无离线成绩记录",
            desc = "前往「教务」页登录广海正方教务后点击「一键全同步」，即可离线查看全部成绩并自动计算加权 GPA；也可以点右上角「+ 模拟算分」手动测算。",
            extraNotice = lastGradeSkipReason?.takeIf { it.isNotBlank() }?.let { reason ->
                "上次同步成绩提示：$reason。若教务首页有未读系统通知弹窗，先点击「查看详情 / 已阅读」后再点同步成绩即可。"
            },
            onJumpToSync = onJumpToSync
        )
        return
    }

    val termTags = remember(grades) {
        listOf("全部学期") + grades.map { "${it.academicYear} ${it.semester}" }.distinct().sortedDescending()
    }
    var selectedTerm by remember(termTags) { mutableStateOf("全部学期") }
    var selectedNatureFilter by remember { mutableStateOf(GradeNatureFilter.ALL) }
    var calcMode by remember { mutableStateOf(GpaCalcMode.EFFECTIVE_BEST) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearchField by remember { mutableStateOf(false) }
    var showTermDropdown by remember { mutableStateOf(false) }
    var showCalcModeDropdown by remember { mutableStateOf(false) }
    var showAnalyticsSheet by remember { mutableStateOf(false) }
    var showPracticeSheet by remember { mutableStateOf(false) }
    var collapsedTerms by remember { mutableStateOf(setOf<String>()) }
    var selectedGradeDetail by remember { mutableStateOf<GradeItem?>(null) }
    var showEditTargetsDialog by remember { mutableStateOf(false) }

    val globalDedup = remember(grades) { deduplicateEffectiveGrades(grades) }
    val retakeOrFailedCount = remember(grades) { grades.count { isRetakeOrFailedGrade(it) } }

    val filtered = remember(grades, selectedTerm, selectedNatureFilter, searchQuery) {
        filterAcademicGrades(grades, selectedTerm, selectedNatureFilter, searchQuery)
    }

    val groupedByTerm = remember(filtered) {
        filtered
            .groupBy { "${it.academicYear} ${it.semester}" }
            .entries
            .sortedByDescending { it.key }
    }

    val metrics = remember(filtered, calcMode) {
        computeGradeMetrics(filtered, calcMode)
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 84.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // GPA Gradient Summary Card + Analytics Report entry
        item {
            val heroStart = if (isDark) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary
            val heroEnd = if (isDark) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.tertiary
            val heroFg = if (isDark) MaterialTheme.colorScheme.onPrimaryContainer else Color.White
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(heroStart, heroEnd)))
                    .padding(18.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val scopeTitle = buildString {
                            append(if (selectedTerm == "全部学期") "累计加权平均绩点 (GPA)" else "$selectedTerm · GPA")
                            append(" · ${calcMode.label}")
                            if (selectedNatureFilter != GradeNatureFilter.ALL) append(" · ${selectedNatureFilter.label}")
                        }
                        Text(
                            text = scopeTitle,
                            color = heroFg.copy(alpha = 0.88f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        Surface(
                            color = heroFg.copy(alpha = if (isDark) 0.14f else 0.22f),
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable { showAnalyticsSheet = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Insights,
                                    contentDescription = null,
                                    tint = heroFg,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "学业分析报告 ↗",
                                    color = heroFg,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.2f", metrics.gpa),
                            color = heroFg,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            StatSmallColumn("加权均分", String.format(Locale.US, "%.1f", metrics.weightedAvgScore), heroFg)
                            val creditLabel = when (calcMode) {
                                GpaCalcMode.EFFECTIVE_BEST -> "已获/修读学分"
                                GpaCalcMode.PASSED_ONLY -> "已获学分"
                                GpaCalcMode.RAW_ALL -> "原始累算学分"
                            }
                            val creditVal = when (calcMode) {
                                GpaCalcMode.EFFECTIVE_BEST -> "${String.format(Locale.US, "%.1f", metrics.earnedCredits)}/${String.format(Locale.US, "%.1f", metrics.totalCredits)}"
                                GpaCalcMode.PASSED_ONLY -> String.format(Locale.US, "%.1f", metrics.earnedCredits)
                                GpaCalcMode.RAW_ALL -> String.format(Locale.US, "%.1f", metrics.totalCredits)
                            }
                            StatSmallColumn(creditLabel, creditVal, heroFg)
                            StatSmallColumn("门数", "${metrics.courseCount}", heroFg)
                        }
                    }
                    if (globalDedup.duplicateCourseCount > 0 && selectedTerm == "全部学期") {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = when (calcMode) {
                                GpaCalcMode.EFFECTIVE_BEST -> "✓ 已自动合并 ${globalDedup.duplicateCourseCount} 门补考/重修重复记录（剔除重复分母 ${String.format(Locale.US, "%.2f", globalDedup.duplicateCreditsRemoved)} 学分）"
                                GpaCalcMode.PASSED_ONLY -> "✓ 仅统计已及格通过课程（共通过 ${globalDedup.passedGrades.size} 门 · 获 ${String.format(Locale.US, "%.1f", globalDedup.earnedCredits)} 学分）"
                                GpaCalcMode.RAW_ALL -> "提示：原始全计包含 ${globalDedup.duplicateCourseCount} 门初修与补考重复记录（多计 ${String.format(Locale.US, "%.2f", globalDedup.duplicateCreditsRemoved)} 学分）"
                            },
                            color = heroFg.copy(alpha = 0.86f),
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Responsive FlowRow Toolbar + Scrollable Filter Pills (never clips on large font scaling!)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showTermDropdown = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = selectedTerm,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "切换统计学期",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showTermDropdown,
                            onDismissRequest = { showTermDropdown = false }
                        ) {
                            termTags.forEach { tag ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = tag,
                                            fontWeight = if (tag == selectedTerm) FontWeight.Bold else FontWeight.Normal,
                                            color = if (tag == selectedTerm) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        selectedTerm = tag
                                        showTermDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    Box {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showCalcModeDropdown = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = calcMode.label,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "切换绩点口径",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showCalcModeDropdown,
                            onDismissRequest = { showCalcModeDropdown = false }
                        ) {
                            GpaCalcMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Column(modifier = Modifier.widthIn(max = 260.dp)) {
                                            Text(
                                                text = if (mode == GpaCalcMode.EFFECTIVE_BEST) "${mode.label}（推荐）" else mode.label,
                                                fontWeight = if (calcMode == mode) FontWeight.Bold else FontWeight.Medium,
                                                color = if (calcMode == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = mode.shortHint,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 14.sp
                                            )
                                        }
                                    },
                                    onClick = {
                                        calcMode = mode
                                        showCalcModeDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    if (levelExams.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.78f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showAnalyticsSheet = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.School,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "等级考(${levelExams.size})",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    if (practiceCourses.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showPracticeSheet = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.School,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "实践(${practiceCourses.size})",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    Surface(
                        color = if (showSearchField || searchQuery.isNotBlank()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                if (showSearchField) {
                                    searchQuery = ""
                                    showSearchField = false
                                } else {
                                    showSearchField = true
                                }
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (showSearchField) Icons.Rounded.Close else Icons.Rounded.Search,
                                contentDescription = "搜索课程成绩",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (showSearchField) "收起" else "搜课",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // Horizontally scrollable filter pills so all labels breathe comfortably even on narrow screens / large fonts
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    GradeNatureFilter.entries.forEach { nat ->
                        val sel = selectedNatureFilter == nat
                        val chipLabel = if (nat == GradeNatureFilter.RETAKE_OR_FAILED && retakeOrFailedCount > 0) {
                            "${nat.label}($retakeOrFailedCount)"
                        } else {
                            nat.label
                        }
                        Surface(
                            color = if (sel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedNatureFilter = nat }
                        ) {
                            Text(
                                text = chipLabel,
                                fontSize = 11.sp,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                color = if (sel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        if (showSearchField || searchQuery.isNotBlank()) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索课程名、教师、性质或补考/重修...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        groupedByTerm.forEach { (termKey, termGrades) ->
            val isCollapsed = termKey in collapsedTerms
            item(key = "header_$termKey") {
                val termStat = remember(termGrades, calcMode, globalDedup) {
                    val activeGrades = when (calcMode) {
                        GpaCalcMode.EFFECTIVE_BEST -> termGrades.filter { it.id !in globalDedup.supersededByMap }
                        GpaCalcMode.PASSED_ONLY -> termGrades.filter { it.id !in globalDedup.supersededByMap && isGradePassed(it) }
                        GpaCalcMode.RAW_ALL -> termGrades
                    }
                    val skippedDuplicates = termGrades.size - activeGrades.size
                    var cr = 0.0
                    var gpSum = 0.0
                    var scSum = 0.0
                    var scCr = 0.0
                    activeGrades.forEach { g ->
                        if (g.credit > 0.0) {
                            cr += g.credit
                            gpSum += g.gradePoint * g.credit
                            val num = parseScoreNumeric(g.score)
                            if (num != null) {
                                scSum += num * g.credit
                                scCr += g.credit
                            }
                        }
                    }
                    val gpa = if (cr > 0.0) gpSum / cr else 0.0
                    val avg = if (scCr > 0.0) scSum / scCr else 0.0
                    listOf(gpa, avg, cr, skippedDuplicates.toDouble())
                }
                val skippedCount = termStat[3].toInt()
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            collapsedTerms = if (isCollapsed) collapsedTerms - termKey else collapsedTerms + termKey
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "$termKey（${termGrades.size} 门）",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = buildString {
                                    append("学期 GPA ${String.format(Locale.US, "%.2f", termStat[0])} · 加权均分 ${String.format(Locale.US, "%.1f", termStat[1])} · 计入 ${String.format(Locale.US, "%.2f", termStat[2]).removeSuffix("0").removeSuffix(".")} 学分")
                                    if (skippedCount > 0 && calcMode == GpaCalcMode.EFFECTIVE_BEST) {
                                        append("（已去重 $skippedCount 条同课重复记录）")
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            imageVector = if (isCollapsed) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (!isCollapsed) {
                items(termGrades, key = { it.id }) { grade ->
                    val numeric = parseScoreNumeric(grade.score)
                    val supersededInfo = globalDedup.supersededByMap[grade.id]
                    val scoreColor = when {
                        numeric == null -> MaterialTheme.colorScheme.primary
                        numeric >= 90 -> if (isDark) Color(0xFF4ADE80) else Color(0xFF0E8A5F)
                        numeric >= 80 -> if (isDark) Color(0xFF60A5FA) else Color(0xFF1A6FD4)
                        numeric >= 60 -> MaterialTheme.colorScheme.onSurface
                        supersededInfo?.isBestPassed == true -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.error
                    }
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedGradeDetail = grade }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = grade.courseName,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (grade.examNature.isNotBlank() && grade.examNature != "正常考试") {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        val isPassMakeup = isGradePassed(grade)
                                        Surface(
                                            color = if (isPassMakeup) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.tertiaryContainer,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = grade.examNature,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isPassMakeup) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    if (grade.isSimulated) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "模拟",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = listOfNotNull(
                                        grade.courseType.takeIf { it.isNotBlank() && it != "无" },
                                        grade.credit.takeIf { it > 0 }?.let { "${it}学分" },
                                        grade.teacher.takeIf { it.isNotBlank() && it != "无" }
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (supersededInfo != null && calcMode != GpaCalcMode.RAW_ALL) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val best = supersededInfo.bestAttempt
                                    Text(
                                        text = if (supersededInfo.isBestPassed) {
                                            "✓ 已由${best.examNature}(${best.score}分)通过替代 · 不重复计入总学分"
                                        } else {
                                            "同课多次考试记录 · 已按最高分(${best.score}分)单次计入总学分"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (supersededInfo.isBestPassed) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (grade.breakdown.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "分项：${grade.breakdown}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = grade.score.ifBlank { "--" },
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = scoreColor
                                )
                                Text(
                                    text = "绩点 ${String.format(Locale.US, "%.2f", grade.gradePoint)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (grade.isSimulated) {
                                IconButton(
                                    onClick = { onDeleteGrade(grade.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = "删除模拟成绩",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAnalyticsSheet) {
        AcademicAnalyticsBottomSheet(
            grades = grades,
            levelExams = levelExams,
            creditTargets = creditTargets,
            onOpenEditTargets = {
                showAnalyticsSheet = false
                showEditTargetsDialog = true
            },
            onDismiss = { showAnalyticsSheet = false }
        )
    }

    if (showPracticeSheet && practiceCourses.isNotEmpty()) {
        PracticeCoursesBottomSheet(
            practiceCourses = practiceCourses,
            title = "整周实践 / 实训学分",
            onDismiss = { showPracticeSheet = false }
        )
    }

    selectedGradeDetail?.let { g ->
        var breakdownDraft by remember(g) { mutableStateOf(g.breakdown) }
        AlertDialog(
            onDismissRequest = { selectedGradeDetail = null },
            title = { Text(g.courseName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (g.courseType.isNotBlank() && g.courseType != "无") {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = g.courseType,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Surface(
                            color = if (g.examNature != "正常考试") MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = g.examNature.ifBlank { "正常考试" },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (g.examNature != "正常考试") MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "${g.academicYear} ${g.semester}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "总评成绩：${g.score.ifBlank { "--" }}   |   绩点：${String.format(Locale.US, "%.2f", g.gradePoint)}   |   学分：${g.credit}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (g.teacher.isNotBlank()) {
                        Text("任课教师：${g.teacher}", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(
                        value = breakdownDraft,
                        onValueChange = { breakdownDraft = it },
                        label = { Text("平时/实验/期末分项成绩或备忘") },
                        placeholder = { Text("如：平时95(30%) · 实验90(10%) · 期末85(60%)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateGrade(g.copy(breakdown = breakdownDraft.trim()))
                        selectedGradeDetail = null
                    }
                ) { Text("保存分项备忘") }
            },
            dismissButton = {
                TextButton(onClick = { selectedGradeDetail = null }) { Text("关闭") }
            }
        )
    }

    if (showEditTargetsDialog) {
        var totalStr by remember(creditTargets) { mutableStateOf(creditTargets.totalRequired.toString()) }
        var compStr by remember(creditTargets) { mutableStateOf(creditTargets.compulsoryRequired.toString()) }
        var elecStr by remember(creditTargets) { mutableStateOf(creditTargets.electiveRequired.toString()) }
        var genStr by remember(creditTargets) { mutableStateOf(creditTargets.generalRequired.toString()) }
        var pracStr by remember(creditTargets) { mutableStateOf(creditTargets.practiceRequired.toString()) }
        AlertDialog(
            onDismissRequest = { showEditTargetsDialog = false },
            title = { Text("自定义专业毕业学分要求") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "可对照教务系统「学生学业情况查询」或本专业培养方案填入要求学分：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = totalStr,
                        onValueChange = { totalStr = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("毕业总学分要求") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = compStr,
                            onValueChange = { compStr = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("必修要求") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = elecStr,
                            onValueChange = { elecStr = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("专业选修要求") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = genStr,
                            onValueChange = { genStr = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("通识/公选要求") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = pracStr,
                            onValueChange = { pracStr = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("实践环节要求") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateCreditTargets(
                            GraduationCreditTargets(
                                totalRequired = totalStr.toDoubleOrNull() ?: 160.0,
                                compulsoryRequired = compStr.toDoubleOrNull() ?: 105.0,
                                electiveRequired = elecStr.toDoubleOrNull() ?: 30.0,
                                generalRequired = genStr.toDoubleOrNull() ?: 10.0,
                                practiceRequired = pracStr.toDoubleOrNull() ?: 15.0
                            )
                        )
                        showEditTargetsDialog = false
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showEditTargetsDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
internal fun EmptyAcademicHint(
    title: String,
    desc: String,
    extraNotice: String? = null,
    onJumpToSync: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.School,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!extraNotice.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.78f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(16.dp).padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = extraNotice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onJumpToSync, shape = RoundedCornerShape(12.dp)) {
            Text("前往广海教务同步")
        }
    }
}

@Composable
private fun StatSmallColumn(label: String, value: String, fgColor: Color = Color.White) {
    Column(horizontalAlignment = Alignment.End) {
        Text(value, color = fgColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, color = fgColor.copy(alpha = 0.82f), fontSize = 11.sp)
    }
}

@Composable
internal fun AddSimulatedGradeDialog(
    onDismiss: () -> Unit,
    onSave: (GradeItem) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var score by remember { mutableStateOf("90") }
    var credit by remember { mutableStateOf("3.0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("模拟算分（测算 GPA）") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("课程名称 *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = score,
                        onValueChange = { score = it },
                        label = { Text("预计分数") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = credit,
                        onValueChange = { credit = it },
                        label = { Text("学分") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = "自动按广海 5 分制折算绩点（如 90分 = 4.0，85分 = 3.5，低于 60 = 0）。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isNotEmpty()) {
                        val s = score.toDoubleOrNull() ?: 80.0
                        val c = credit.toDoubleOrNull() ?: 2.0
                        val gp = if (s < 60.0) 0.0 else ((s - 50.0) / 10.0).coerceAtLeast(1.0)
                        onSave(
                            GradeItem(
                                academicYear = "模拟测算",
                                semester = "自定义",
                                courseName = trimmed,
                                score = score.trim(),
                                gradePoint = gp,
                                credit = c,
                                courseType = "模拟课程",
                                isSimulated = true
                            )
                        )
                    }
                }
            ) { Text("加入计算") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
