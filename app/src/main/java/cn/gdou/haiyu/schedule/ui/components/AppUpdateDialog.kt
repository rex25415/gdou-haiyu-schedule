package cn.gdou.haiyu.schedule.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.gdou.haiyu.schedule.BuildConfig
import cn.gdou.haiyu.schedule.update.ApkDownloadProgress
import cn.gdou.haiyu.schedule.update.ApkDownloadResult
import cn.gdou.haiyu.schedule.update.AppReleaseInfo
import cn.gdou.haiyu.schedule.update.AppUpdateManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun AppUpdateDialog(
    release: AppReleaseInfo,
    onDismiss: () -> Unit,
    onIgnoreVersion: (String) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isDownloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<ApkDownloadProgress?>(null) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    var shaVerified by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var needsInstallPermHint by remember { mutableStateOf(false) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    fun triggerInstall(file: File) {
        if (!AppUpdateManager.canInstallPackages(context)) {
            needsInstallPermHint = true
            val opened = AppUpdateManager.openInstallPermissionSettings(context)
            onShowMessage(
                if (opened) "请开启「允许来自此来源的应用」后返回，点「立即安装」即可升级"
                else "请在系统设置中允许海屿课表安装未知应用后重试"
            )
            return
        }
        needsInstallPermHint = false
        val launched = try {
            AppUpdateManager.launchApkInstaller(context, file)
        } catch (e: Exception) {
            errorMsg = "调起系统安装器失败：${e.localizedMessage ?: "未知错误"}"
            false
        }
        if (!launched && errorMsg == null) {
            errorMsg = "无法调起系统安装器，请点击下方「浏览器下载」手动安装"
        }
    }

    fun startDownload() {
        if (isDownloading) return
        errorMsg = null
        needsInstallPermHint = false
        isDownloading = true
        progress = ApkDownloadProgress(0L, release.apkSizeBytes)
        downloadJob = scope.launch {
            try {
                when (val result = AppUpdateManager.downloadApk(context, release) { p -> progress = p }) {
                    is ApkDownloadResult.Success -> {
                        isDownloading = false
                        downloadedFile = result.file
                        shaVerified = result.sha256Verified
                        triggerInstall(result.file)
                    }
                    is ApkDownloadResult.Error -> {
                        isDownloading = false
                        errorMsg = result.message
                    }
                }
            } catch (_: CancellationException) {
                isDownloading = false
                progress = null
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!isDownloading) onDismiss()
        },
        icon = {
            Icon(
                imageVector = Icons.Rounded.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("发现新版本 v${release.versionName}", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    val sizeText = AppUpdateManager.formatBytes(release.apkSizeBytes)
                    val meta = buildString {
                        append("v${BuildConfig.VERSION_NAME} → v${release.versionName}")
                        if (sizeText.isNotBlank()) append(" · $sizeText")
                    }
                    Text(
                        text = meta,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 210.dp)
                ) {
                    Text(
                        text = release.releaseNotes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    )
                }

                Text(
                    text = "覆盖安装会自动保留你的全部课表、作业 DDL、成绩与个性化设置。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isDownloading) {
                    val p = progress
                    val pct = if (p != null && p.fraction >= 0f) (p.fraction * 100).toInt() else -1
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (p != null && p.fraction >= 0f) {
                            LinearProgressIndicator(
                                progress = { p.fraction },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        val routePrefix = p?.routeLabel?.takeIf { it.isNotBlank() }?.let { "[$it] " } ?: ""
                        val detail = if (p != null && p.totalBytes > 0L) {
                            "${routePrefix}正在下载：${AppUpdateManager.formatBytes(p.downloadedBytes)} / ${AppUpdateManager.formatBytes(p.totalBytes)}（$pct%）"
                        } else {
                            "${routePrefix}正在连接下载线路..."
                        }
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (downloadedFile != null && !isDownloading) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when {
                                    needsInstallPermHint -> "安装包已就绪！开启「安装未知应用」权限后点下方「立即安装」"
                                    shaVerified -> "安装包下载完成并已通过 SHA-256 安全校验"
                                    else -> "安装包已下载完成，点击「立即安装」完成升级"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // Always provide a convenient one-tap browser fallback for campus/mobile networks
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (!AppUpdateManager.openUrlInBrowser(context, AppUpdateManager.DOWNLOAD_PAGE_URL)) {
                                onShowMessage("未检测到可用浏览器，下载主页：${AppUpdateManager.DOWNLOAD_PAGE_URL}")
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("下载主页", fontSize = 12.sp)
                    }
                    if (!isDownloading && downloadedFile == null) {
                        FilledTonalButton(
                            onClick = {
                                onIgnoreVersion(release.versionName)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("忽略此版本", fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                isDownloading -> {
                    TextButton(
                        onClick = {
                            downloadJob?.cancel()
                            isDownloading = false
                            progress = null
                        }
                    ) { Text("取消下载") }
                }
                downloadedFile != null -> {
                    Button(onClick = { triggerInstall(downloadedFile!!) }) {
                        Text("立即安装")
                    }
                }
                else -> {
                    Button(onClick = { startDownload() }) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (errorMsg != null) "重试下载" else "立即下载并安装")
                    }
                }
            }
        },
        dismissButton = {
            if (!isDownloading) {
                TextButton(onClick = onDismiss) { Text("稍后再说") }
            }
        }
    )
}
