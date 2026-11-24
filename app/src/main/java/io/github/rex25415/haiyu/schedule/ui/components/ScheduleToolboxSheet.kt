package io.github.rex25415.haiyu.schedule.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class ToolboxTileSpec(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val containerColor: Color,
    val iconTint: Color,
    val onClick: () -> Unit
)

/**
 * Visual 2x4 grid toolbox bottom sheet replacing the crowded text overflow menu
 * on the schedule screen, keeping daily campus utilities 1 tap away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleToolboxBottomSheet(
    selectedWeek: Int,
    campusLabel: String,
    practiceCount: Int,
    onOpenEmptyClassroom: () -> Unit,
    onOpenCourseSearch: () -> Unit,
    onOpenFreeSlotMatcher: () -> Unit,
    onOpenBellSchedule: () -> Unit,
    onOpenAddCourse: () -> Unit,
    onExportWeekImage: () -> Unit,
    onOpenHolidayShift: () -> Unit,
    onOpenShareCode: () -> Unit,
    onOpenCampusServices: () -> Unit = {},
    onOpenPracticeCourses: () -> Unit,
    onCopyWeekText: () -> Unit,
    onDismiss: () -> Unit
) {
    val primaryBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
    val primaryTint = MaterialTheme.colorScheme.primary
    val secondaryBg = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
    val secondaryTint = MaterialTheme.colorScheme.secondary
    val tertiaryBg = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f)
    val tertiaryTint = MaterialTheme.colorScheme.tertiary

    val row1 = listOf(
        ToolboxTileSpec(
            title = "查空教室",
            subtitle = "实时找自习室",
            icon = Icons.Rounded.MeetingRoom,
            containerColor = primaryBg,
            iconTint = primaryTint,
            onClick = { onDismiss(); onOpenEmptyClassroom() }
        ),
        ToolboxTileSpec(
            title = "课程检索",
            subtitle = "搜课跳周学分",
            icon = Icons.Rounded.Search,
            containerColor = tertiaryBg,
            iconTint = tertiaryTint,
            onClick = { onDismiss(); onOpenCourseSearch() }
        ),
        ToolboxTileSpec(
            title = "空课对比",
            subtitle = "约自习/搭子",
            icon = Icons.Rounded.Group,
            containerColor = secondaryBg,
            iconTint = secondaryTint,
            onClick = { onDismiss(); onOpenFreeSlotMatcher() }
        ),
        ToolboxTileSpec(
            title = "校区作息",
            subtitle = "三校区错峰表",
            icon = Icons.Rounded.Schedule,
            containerColor = primaryBg,
            iconTint = primaryTint,
            onClick = { onDismiss(); onOpenBellSchedule() }
        )
    )

    val row2 = listOf(
        ToolboxTileSpec(
            title = "手动加课",
            subtitle = "补录/辅修排课",
            icon = Icons.Rounded.Add,
            containerColor = secondaryBg,
            iconTint = secondaryTint,
            onClick = { onDismiss(); onOpenAddCourse() }
        ),
        ToolboxTileSpec(
            title = "导出长图",
            subtitle = "含期末考场图",
            icon = Icons.Rounded.Image,
            containerColor = tertiaryBg,
            iconTint = tertiaryTint,
            onClick = { onDismiss(); onExportWeekImage() }
        ),
        ToolboxTileSpec(
            title = "假期调休",
            subtitle = "节假日搬整天",
            icon = Icons.Rounded.SwapHoriz,
            containerColor = primaryBg,
            iconTint = primaryTint,
            onClick = { onDismiss(); onOpenHolidayShift() }
        ),
        ToolboxTileSpec(
            title = "同班分享",
            subtitle = "分享码互导入",
            icon = Icons.Rounded.Share,
            containerColor = secondaryBg,
            iconTint = secondaryTint,
            onClick = { onDismiss(); onOpenShareCode() }
        )
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "课表百宝箱",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = "第 $selectedWeek 周 · $campusLabel",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "校园常用查询",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ToolboxRow(row1)

                Text(
                    text = "课表打理与分享",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
                ToolboxRow(row2)
            }

            Surface(
                onClick = { onDismiss(); onOpenCampusServices() },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.AccountBalance,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "广海官方服务与教务公告",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "教务考核/选课公告 · 官方校历 · 一网通办 · 后勤",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (practiceCount > 0) {
                    Surface(
                        onClick = { onDismiss(); onOpenPracticeCourses() },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.School,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "整周实践课 (${practiceCount})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Surface(
                    onClick = { onDismiss(); onCopyWeekText() },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "复制本周课表文本",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
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

@Composable
private fun ToolboxRow(items: List<ToolboxTileSpec>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = item.onClick)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        color = item.containerColor,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                tint = item.iconTint,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = item.title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.subtitle,
                        fontSize = 9.5.sp,
                        lineHeight = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
