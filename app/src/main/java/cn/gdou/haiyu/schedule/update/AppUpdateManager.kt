package cn.gdou.haiyu.schedule.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long,
    val expectedSha256: String? = null
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val release: AppReleaseInfo) : UpdateCheckResult
    data class UpToDate(val latestVersion: String) : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

data class ApkDownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long
) {
    val fraction: Float
        get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else -1f
}

sealed interface ApkDownloadResult {
    data class Success(val file: File, val sha256Verified: Boolean) : ApkDownloadResult
    data class Error(val message: String) : ApkDownloadResult
}

/**
 * 基于公开 GitHub Releases 的纯净应用内自更新管理器：
 * 1. 零统计、零设备上报，仅请求开源仓库公开接口；
 * 2. 支持校园网出口同 IP 防限流自动降级（API 限流时自动改走 Releases 重定向探测）；
 * 3. 下载完成后自动校验 SHA-256 完整性，并通过系统 FileProvider 触发保留数据的覆盖升级。
 */
object AppUpdateManager {

    const val REPO_OWNER = "rex25415"
    const val REPO_NAME = "gdou-haiyu-schedule"
    const val REPO_WEB_URL = "https://github.com/$REPO_OWNER/$REPO_NAME"
    const val LATEST_RELEASE_WEB_URL = "$REPO_WEB_URL/releases/latest"
    private const val LATEST_RELEASE_API_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"

    const val AUTO_CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L // 12 hours

    private val json = Json { ignoreUnknownKeys = true }
    private val sha256Regex = Regex("(?i)\\b([a-f0-9]{64})\\b")

    /**
     * 规范化版本号（去除前缀 v/V 及构建后缀），按语义化数字段逐位比较
     */
    fun normalizeVersion(raw: String): String {
        return raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
            .trim()
    }

    fun parseVersionParts(raw: String): List<Int> {
        val cleaned = normalizeVersion(raw)
        if (cleaned.isEmpty()) return emptyList()
        return cleaned.split('.').mapNotNull { part ->
            val digits = part.takeWhile { it.isDigit() }
            digits.toIntOrNull()
        }
    }

    fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
        val remote = parseVersionParts(remoteVersion)
        val current = parseVersionParts(currentVersion)
        if (remote.isEmpty() || current.isEmpty()) return false
        val maxLen = maxOf(remote.size, current.size)
        for (i in 0 until maxLen) {
            val r = remote.getOrElse(i) { 0 }
            val c = current.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        return false
    }

    /**
     * 解析 GitHub Releases API 返回 JSON（纯函数，便于单元测试）
     */
    fun parseReleaseJson(rawJson: String): AppReleaseInfo? {
        return try {
            val root: JsonObject = json.parseToJsonElement(rawJson).jsonObject
            val tagName = root["tag_name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (tagName.isEmpty()) return null
            val versionName = normalizeVersion(tagName)
            val title = root["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { "海屿课表 v$versionName" }
            val body = root["body"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val htmlUrl = root["html_url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                .ifBlank { "$REPO_WEB_URL/releases/tag/$tagName" }

            val assets: JsonArray? = root["assets"]?.jsonArray
            var apkUrl = ""
            var apkName = "HaiyuSchedule-GDOU-v$versionName.apk"
            var apkSize = 0L
            var apiDigestSha256: String? = null

            if (assets != null) {
                for (el in assets) {
                    val obj = el.jsonObject
                    val name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val url = obj["browser_download_url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                        apkName = name
                        apkUrl = url
                        apkSize = obj["size"]?.jsonPrimitive?.longOrNull ?: 0L
                        val digest = obj["digest"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (digest.startsWith("sha256:", ignoreCase = true)) {
                            val candidate = digest.substringAfter(':').trim().lowercase(Locale.US)
                            if (candidate.length == 64 && candidate.all { it in '0'..'9' || it in 'a'..'f' }) {
                                apiDigestSha256 = candidate
                            }
                        }
                        break
                    }
                }
            }

            if (apkUrl.isBlank()) {
                apkUrl = "$REPO_WEB_URL/releases/download/$tagName/$apkName"
            }

            val bodySha256 = if (apiDigestSha256 == null) {
                sha256Regex.find(body)?.groupValues?.get(1)?.lowercase(Locale.US)
            } else {
                null
            }

            AppReleaseInfo(
                tagName = tagName,
                versionName = versionName,
                title = title,
                releaseNotes = cleanReleaseNotes(body),
                htmlUrl = htmlUrl,
                apkDownloadUrl = apkUrl,
                apkFileName = apkName,
                apkSizeBytes = apkSize,
                expectedSha256 = apiDigestSha256 ?: bodySha256
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun cleanReleaseNotes(raw: String): String {
        if (raw.isBlank()) return "优化体验并修复已知问题，建议更新到最新版本。"
        return raw
            .replace("\r\n", "\n")
            .lines()
            .filterNot { line ->
                val t = line.trim()
                t.startsWith("SHA-256", ignoreCase = true) ||
                    t.startsWith("- SHA-256", ignoreCase = true) ||
                    t.startsWith("### 文件校验")
            }
            .joinToString("\n") { line ->
                line
                    .replace(Regex("^#{1,4}\\s*"), "")
                    .replace("**", "")
                    .replace("`", "")
                    .trimEnd()
            }
            .trim()
            .take(1200)
    }

    /**
     * 检查 GitHub 最新 Release（自动支持 API 限流时的网页重定向降级，适配校园网大出口 NAT）
     */
    suspend fun checkLatestRelease(currentVersion: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        var apiError: String? = null
        try {
            val conn = (URL(LATEST_RELEASE_API_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 10000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "HaiyuSchedule-Android/$currentVersion")
            }
            try {
                val code = conn.responseCode
                if (code == HttpURLConnection.HTTP_OK) {
                    val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val parsed = parseReleaseJson(text)
                    if (parsed != null) {
                        return@withContext if (isNewerVersion(parsed.versionName, currentVersion)) {
                            UpdateCheckResult.UpdateAvailable(parsed)
                        } else {
                            UpdateCheckResult.UpToDate(parsed.versionName)
                        }
                    }
                    apiError = "版本信息解析失败"
                } else {
                    apiError = "GitHub 接口返回 HTTP $code"
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            apiError = e.localizedMessage ?: "连接更新服务器超时"
        }

        // Fallback: probe /releases/latest redirect (immune to GitHub REST API 60/hr IP rate limit!)
        try {
            val redirectConn = (URL(LATEST_RELEASE_WEB_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                instanceFollowRedirects = false
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "HaiyuSchedule-Android/$currentVersion")
            }
            try {
                val loc = redirectConn.getHeaderField("Location").orEmpty()
                val tag = loc.substringAfter("/releases/tag/", "").substringBefore('?').substringBefore('#').trim()
                if (tag.isNotBlank()) {
                    val ver = normalizeVersion(tag)
                    val fileName = "HaiyuSchedule-GDOU-v$ver.apk"
                    val fallbackRelease = AppReleaseInfo(
                        tagName = tag,
                        versionName = ver,
                        title = "海屿课表 v$ver",
                        releaseNotes = "检测到新版本 v$ver，建议立即更新以获取最新功能与体验优化。",
                        htmlUrl = "$REPO_WEB_URL/releases/tag/$tag",
                        apkDownloadUrl = "$REPO_WEB_URL/releases/download/$tag/$fileName",
                        apkFileName = fileName,
                        apkSizeBytes = 0L,
                        expectedSha256 = null
                    )
                    return@withContext if (isNewerVersion(ver, currentVersion)) {
                        UpdateCheckResult.UpdateAvailable(fallbackRelease)
                    } else {
                        UpdateCheckResult.UpToDate(ver)
                    }
                }
            } finally {
                redirectConn.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }

        UpdateCheckResult.Error(
            "检查更新失败（${apiError ?: "网络异常"}），可检查网络或直接点浏览器查看 GitHub Releases"
        )
    }

    /**
     * 在应用专属缓存目录下载更新包，带实时进度回调与 SHA-256 校验
     */
    suspend fun downloadApk(
        context: Context,
        release: AppReleaseInfo,
        onProgress: (ApkDownloadProgress) -> Unit
    ): ApkDownloadResult = withContext(Dispatchers.IO) {
        val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val safeName = release.apkFileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "HaiyuSchedule-v${release.versionName}.apk" }
        val targetFile = File(updateDir, safeName)
        val partFile = File(updateDir, "$safeName.part")

        // Reuse already downloaded verified APK if present
        if (targetFile.exists() && targetFile.length() > 100_000L) {
            val expected = release.expectedSha256
            if (expected == null || sha256OfFile(targetFile).equals(expected, ignoreCase = true)) {
                onProgress(ApkDownloadProgress(targetFile.length(), targetFile.length()))
                return@withContext ApkDownloadResult.Success(targetFile, sha256Verified = expected != null)
            } else {
                targetFile.delete()
            }
        }

        try {
            val conn = openFollowingRedirects(release.apkDownloadUrl)
            try {
                val code = conn.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    return@withContext ApkDownloadResult.Error("下载失败（HTTP $code），建议改用系统浏览器下载")
                }
                val contentLength = conn.contentLengthLong.takeIf { it > 0L } ?: release.apkSizeBytes
                var downloaded = 0L
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(16 * 1024)
                var lastEmit = 0L

                conn.inputStream.use { input ->
                    FileOutputStream(partFile).use { output ->
                        while (true) {
                            if (!isActive) {
                                partFile.delete()
                                throw CancellationException("下载已取消")
                            }
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastEmit >= 120L || downloaded == contentLength) {
                                lastEmit = now
                                onProgress(ApkDownloadProgress(downloaded, contentLength))
                            }
                        }
                        output.flush()
                    }
                }

                if (downloaded < 100_000L) {
                    partFile.delete()
                    return@withContext ApkDownloadResult.Error("下载的文件异常不完整，请重试或改用浏览器下载")
                }

                val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
                val expected = release.expectedSha256
                if (expected != null && !actualSha256.equals(expected, ignoreCase = true)) {
                    partFile.delete()
                    return@withContext ApkDownloadResult.Error("安装包安全校验（SHA-256）未通过，已自动丢弃，请重新下载")
                }

                if (targetFile.exists()) targetFile.delete()
                if (!partFile.renameTo(targetFile)) {
                    partFile.copyTo(targetFile, overwrite = true)
                    partFile.delete()
                }
                onProgress(ApkDownloadProgress(downloaded, maxOf(downloaded, contentLength)))
                ApkDownloadResult.Success(targetFile, sha256Verified = expected != null)
            } finally {
                conn.disconnect()
            }
        } catch (e: CancellationException) {
            partFile.delete()
            throw e
        } catch (e: Exception) {
            partFile.delete()
            ApkDownloadResult.Error("下载中断（${e.localizedMessage ?: "网络连接不稳定"}），可点击重试或改用浏览器下载")
        }
    }

    private fun openFollowingRedirects(initialUrl: String, maxHops: Int = 6): HttpURLConnection {
        var currentUrl = initialUrl
        repeat(maxHops) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = 12000
                readTimeout = 25000
                setRequestProperty("User-Agent", "HaiyuSchedule-Android")
            }
            val code = conn.responseCode
            if (code in listOf(301, 302, 303, 307, 308)) {
                val next = conn.getHeaderField("Location")
                conn.disconnect()
                if (next.isNullOrBlank()) throw IllegalStateException("无效的重定向地址")
                currentUrl = URL(URL(currentUrl), next).toString()
            } else {
                return conn
            }
        }
        throw IllegalStateException("下载跳转次数过多")
    }

    private fun sha256OfFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buf)
                if (read == -1) break
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 调起系统安装包界面执行无缝覆盖升级（返回 false 表示需要用户先授权「允许安装未知应用」）
     */
    fun launchApkInstaller(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists()) return false
        if (!canInstallPackages(context)) return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
        return true
    }

    fun openUrlInBrowser(context: Context, url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 升级完成后自动清理旧缓存安装包，不占手机空间
     */
    fun cleanupCachedApks(context: Context, currentVersion: String) {
        try {
            val dir = File(context.cacheDir, "updates")
            if (!dir.exists()) return
            dir.listFiles()?.forEach { f ->
                val verMatch = Regex("v?(\\d+\\.\\d+(?:\\.\\d+)?)").find(f.name)
                val fileVer = verMatch?.groupValues?.get(1)
                if (f.name.endsWith(".part") || fileVer == null || !isNewerVersion(fileVer, currentVersion)) {
                    f.delete()
                }
            }
        } catch (_: Exception) {
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return ""
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.2f MB", mb)
        } else {
            String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        }
    }
}
