package io.github.rex25415.haiyu.schedule.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rex25415.haiyu.schedule.update.AppUpdateManager
import io.github.rex25415.haiyu.schedule.update.QrCodeEncoder

/**
 * 分享「海屿空间」给同学的轻量分享弹窗（面对面扫码、直接传安装包、系统分享与网址复制）：
 */
@Composable
fun ShareAppDialog(
    targetVersionName: String,
    directMirrorApkUrl: String,
    onShareApkFile: () -> Unit = {},
    onSystemShare: () -> Unit,
    onCopyShareText: () -> Unit,
    onCopyDownloadPageUrl: () -> Unit,
    onCopyDirectApkUrl: () -> Unit,
    onDismiss: () -> Unit
) {
    val qrMatrix = remember {
        QrCodeEncoder.encodeByteModeM(AppUpdateManager.DOWNLOAD_PAGE_URL)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分享海屿空间给同学") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (qrMatrix != null) {
                            Box(
                                modifier = Modifier
                                    .size(104.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White)
                                    .border(1.dp, Color(0xFFD7EAE6), RoundedCornerShape(10.dp))
                                    .padding(8.dp)
                            ) {
                                Canvas(modifier = Modifier.matchParentSize()) {
                                    val cellW = size.width / qrMatrix.size
                                    val cellH = size.height / qrMatrix.size
                                    val dark = Color(0xFF0A2926)
                                    for (y in 0 until qrMatrix.size) {
                                        for (x in 0 until qrMatrix.size) {
                                            if (qrMatrix.get(x, y)) {
                                                drawRect(
                                                    color = dark,
                                                    topLeft = Offset(x * cellW, y * cellH),
                                                    size = Size(cellW + 0.35f, cellH + 0.35f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "面对面扫码下载",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "请用「手机相机 / 浏览器」扫（微信会拦截 github 网页，勿用微信扫码）。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "相机直扫 · 约 2 MB",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = onShareApkFile,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Android, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("直接传安装包（.apk）给同学", fontSize = 13.sp, maxLines = 1, softWrap = false)
                }

                FilledTonalButton(
                    onClick = onSystemShare,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("发推荐文案与网址（QQ / 微信）", fontSize = 12.5.sp, maxLines = 1, softWrap = false)
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onCopyShareText,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("复制推荐文案", fontSize = 11.5.sp, maxLines = 1, softWrap = false)
                    }
                    OutlinedButton(
                        onClick = onCopyDownloadPageUrl,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("复制下载网址", fontSize = 11.5.sp, maxLines = 1, softWrap = false)
                    }
                }

                OutlinedButton(
                    onClick = onCopyDirectApkUrl,
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "复制 v$targetVersionName 国内加速 APK 直链",
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = "最省事：直接传安装包（QQ/互传秒收）或相机扫码；微信会拦截 github 网页且常无右上角菜单，请提醒对方复制网址到手机浏览器打开（直链默认走 ${directMirrorApkUrl.substringBefore("https://github.com/")}）。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

/**
 * 克制的开源作者说明与问题反馈指引弹窗（严格仅展示公开 GitHub 账号 @rex25415，绝不暴露个人真实信息）
 */
@Composable
fun FeedbackGuideDialog(
    diagnosticInfo: String,
    onOpenGitHubIssues: () -> Unit,
    onCopyDiagnosticInfo: () -> Unit,
    onCopyIssuesUrl: () -> Unit,
    onOpenGitHubRepo: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("反馈问题与开源指引") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Code,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "开源维护：GitHub @${AppUpdateManager.REPO_OWNER}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "海屿空间是个人业余维护的免费非商业开源项目。遇到显示异常、某校区作息偏差或有好点子，欢迎通过 GitHub Issues 留言，我会抽空认真逐条查看并修复。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = onOpenGitHubIssues,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("前往 GitHub Issues 提交反馈", fontSize = 13.sp)
                }

                FilledTonalButton(
                    onClick = onCopyDiagnosticInfo,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("复制纯脱敏环境信息（便于排查）", fontSize = 12.5.sp)
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = diagnosticInfo,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "以上仅含版本号与显示偏好，严格不含学号、姓名、课表或成绩。",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onCopyIssuesUrl,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("复制反馈网址", fontSize = 12.sp, maxLines = 1, softWrap = false)
                    }
                    OutlinedButton(
                        onClick = onOpenGitHubRepo,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("查看项目主页", fontSize = 12.sp, maxLines = 1, softWrap = false)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
