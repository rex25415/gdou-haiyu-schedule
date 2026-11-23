package io.github.rex25415.haiyu.schedule.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rex25415.haiyu.schedule.data.GdouTimeRules
import io.github.rex25415.haiyu.schedule.data.JointFreeState
import io.github.rex25415.haiyu.schedule.model.CourseItem
import io.github.rex25415.haiyu.schedule.model.ScheduleShareBundle

/**
 * 纯离线双人 / 室友「空课交集」对比面板
 * 粘贴对方的海屿分享码，零联网即刻算出两人都没课的时段，方便约饭、自习与小组讨论。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeSlotMatcherBottomSheet(
    myCourses: List<CourseItem>,
    initialWeek: Int,
    totalWeeks: Int,
    savedPartnerName: String,
    savedPartnerCode: String,
    isDark: Boolean,
    onParseShareCode: (String) -> Result<ScheduleShareBundle>,
    onSavePartnerProfile: (name: String, code: String) -> Unit,
    onShowMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var partnerName by remember(savedPartnerName) { mutableStateOf(savedPartnerName.ifBlank { "室友/搭子" }) }
    var codeInput by remember(savedPartnerCode) { mutableStateOf(savedPartnerCode) }
    var parsedBundle by remember(savedPartnerCode) {
        mutableStateOf(
            if (savedPartnerCode.isNotBlank()) onParseShareCode(savedPartnerCode).getOrNull() else null
        )
    }
    var parseError by remember { mutableStateOf<String?>(null) }
    var activeWeek by remember(initialWeek) { mutableIntStateOf(initialWeek.coerceIn(1, totalWeeks)) }
    var showEditInput by remember(parsedBundle) { mutableStateOf(parsedBundle == null) }

    val matrix = remember(myCourses, parsedBundle, activeWeek) {
        val partner = parsedBundle ?: return@remember emptyList()
        GdouTimeRules.computeJointFreeMatrix(myCourses, partner.courses, activeWeek)
    }
    val jointFreeSlots = remember(matrix) {
        matrix.filter { it.state == JointFreeState.BOTH_FREE }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Group,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "双人空课交集对比",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (parsedBundle != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { showEditInput = !showEditInput },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(if (showEditInput) "收起输入" else "更换分享码", fontSize = 12.sp)
                        }
                        IconButton(
                            onClick = {
                                parsedBundle = null
                                codeInput = ""
                                showEditInput = true
                                onSavePartnerProfile("", "")
                                onShowMessage("已清除已保存的对端课表")
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "清除对比",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (showEditInput || parsedBundle == null) {
                Text(
                    text = "粘贴室友、对象或小组成员的「海屿课表分享码」，纯离线算出本周两人都没课的空闲时段（不上传任何数据）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = partnerName,
                    onValueChange = { partnerName = it },
                    label = { Text("对方备注（如：室友 / 小组搭子）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = {
                        codeInput = it
                        parseError = null
                    },
                    placeholder = { Text("粘贴对方的海屿课表分享码...") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                if (parseError != null) {
                    Text(parseError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            val clip = clipboard.getText()?.text.orEmpty()
                            if (clip.isNotBlank()) {
                                codeInput = clip
                                parseError = null
                            } else {
                                parseError = "剪贴板为空，请先让对方复制分享码发你"
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("粘贴剪贴板", fontSize = 12.5.sp)
                    }
                    Button(
                        onClick = {
                            onParseShareCode(codeInput).onSuccess { bundle ->
                                parsedBundle = bundle
                                parseError = null
                                showEditInput = false
                                onSavePartnerProfile(partnerName.trim().ifBlank { "室友/搭子" }, codeInput.trim())
                            }.onFailure { err ->
                                parseError = err.message ?: "分享码格式有误"
                            }
                        },
                        enabled = codeInput.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("开始对比空课", fontSize = 12.5.sp)
                    }
                }
            }

            if (parsedBundle != null && matrix.isNotEmpty()) {
                // Week stepper + legend
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { if (activeWeek > 1) activeWeek-- },
                            enabled = activeWeek > 1,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "上一周")
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "第 $activeWeek 周 · 与「${partnerName.ifBlank { "对方" }}」对比",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "共同空闲 ${jointFreeSlots.size} / 35 个大节",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = { if (activeWeek < totalWeeks) activeWeek++ },
                            enabled = activeWeek < totalWeeks,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "下一周")
                        }
                    }
                }

                // Compact Legend Row
                val freeBg = if (isDark) Color(0xFF14453B) else Color(0xFFD5F5EC)
                val freeFg = if (isDark) Color(0xFF8EF5DC) else Color(0xFF0A5C51)
                val busyBg = MaterialTheme.colorScheme.surfaceContainerHigh
                val busyFg = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LegendDot(color = freeBg, borderColor = freeFg, label = "都有空 ✓")
                    LegendDot(color = busyBg, borderColor = MaterialTheme.colorScheme.outlineVariant, label = "我忙 / 对方 / 都忙")
                }

                // Clean 7 x 5 Matrix
                val blockLabels = listOf("1-2节", "3-4节", "5-6节", "7-8节", "9-10节")
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Header row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.width(44.dp))
                        for (day in 1..7) {
                            Text(
                                text = GdouTimeRules.dayOfWeekLabel(day),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    blockLabels.forEachIndexed { bIdx, bLabel ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = bLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(41.dp)
                            )
                            for (day in 1..7) {
                                val cell = matrix.firstOrNull { it.dayOfWeek == day && it.blockIndex == bIdx }
                                val isFree = cell?.state == JointFreeState.BOTH_FREE
                                val cellText = when (cell?.state) {
                                    JointFreeState.BOTH_FREE -> ""
                                    JointFreeState.ONLY_ME_BUSY -> "我"
                                    JointFreeState.ONLY_PARTNER_BUSY -> "TA"
                                    JointFreeState.BOTH_BUSY -> "满"
                                    null -> ""
                                }
                                Surface(
                                    color = if (isFree) freeBg else busyBg,
                                    shape = RoundedCornerShape(7.dp),
                                    border = if (isFree) BorderStroke(0.8.dp, freeFg.copy(alpha = 0.45f)) else null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(30.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (isFree) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = "都有空",
                                                tint = freeFg,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        } else {
                                            Text(
                                                text = cellText,
                                                fontSize = 10.sp,
                                                color = busyFg,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 1-Tap Copy Free Time Text for WeChat
                FilledTonalButton(
                    onClick = {
                        val summary = buildString {
                            appendLine("【海屿空课交集】第${activeWeek}周（我 & ${partnerName.ifBlank { "搭子" }}）")
                            for (day in 1..7) {
                                val freeDay = jointFreeSlots.filter { it.dayOfWeek == day }
                                if (freeDay.isNotEmpty()) {
                                    appendLine("• ${GdouTimeRules.dayOfWeekLabel(day)}：${freeDay.joinToString("、") { it.label }}")
                                }
                            }
                        }.trim()
                        clipboard.setText(AnnotatedString(summary))
                        onShowMessage("已复制第 $activeWeek 周共同空课时间，可直接粘贴发微信")
                    },
                    enabled = jointFreeSlots.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("复制本周共同空课时间（发微信约饭/自习）", fontSize = 12.5.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun LegendDot(color: Color, borderColor: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = color,
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(0.8.dp, borderColor.copy(alpha = 0.5f)),
            modifier = Modifier.size(12.dp)
        ) {}
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
