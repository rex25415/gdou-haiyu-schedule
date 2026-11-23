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
    val totalBytes: Long,
    val routeLabel: String = ""
) {
    val fraction: Float
        get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else -1f
}

data class ApkDownloadRoute(
    val label: String,
    val url: String,
    val requiresSha256: Boolean
)

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
    const val DOWNLOAD_PAGE_URL = "https://$REPO_OWNER.github.io/$REPO_NAME/"
    const val LATEST_RELEASE_WEB_URL = "$REPO_WEB_URL/releases/latest"
    private const val LATEST_RELEASE_API_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
    private const val RAW_LATEST_MANIFEST_URL = "https://raw.githubusercontent.com/$REPO_OWNER/$REPO_NAME/main/docs/latest.json"
    private const val PAGES_LATEST_MANIFEST_URL = "${DOWNLOAD_PAGE_URL}latest.json"

    // 经实测可用的 HTTPS 国内加速前缀（仅在具备 SHA-256 官方摘要时启用，校验不通过立即丢弃并回落官方源）
    private val VERIFIED_MIRROR_PREFIXES = listOf(
        "国内加速线路 1" to "https://gh-proxy.com/",
        "国内加速线路 2" to "https://ghfast.top/",
        "国内加速线路 3" to "https://ghproxy.net/"
    )

    private val MANIFEST_FALLBACK_URLS = listOf(
        PAGES_LATEST_MANIFEST_URL,
        "https://gh-proxy.com/$RAW_LATEST_MANIFEST_URL",
        "https://ghfast.top/$RAW_LATEST_MANIFEST_URL"
    )

    const val AUTO_CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L // 12 hours

    /**
     * 生成安全的多线路下载候选列表（纯函数，便于单元测试）：
     * - 当存在官方 SHA-256 摘要时，优先走国内加速线路并严格校验 SHA-256，失败自动回落 GitHub 官方源；
     * - 当不存在 SHA-256 摘要时，为确保零篡改风险，仅直连 GitHub 官方源，不经任何镜像。
     */
    fun buildDownloadRoutes(release: AppReleaseInfo): List<ApkDownloadRoute> {
        val officialUrl = release.apkDownloadUrl.trim()
        if (officialUrl.isEmpty()) return emptyList()
        val hasSha = !release.expectedSha256.isNullOrBlank()
        val routes = mutableListOf<ApkDownloadRoute>()
        if (hasSha && officialUrl.startsWith("https://github.com/", ignoreCase = true)) {
            VERIFIED_MIRROR_PREFIXES.forEach { (label, prefix) ->
                routes.add(ApkDownloadRoute(label = label, url = "$prefix$officialUrl", requiresSha256 = true))
            }
        }
        routes.add(ApkDownloadRoute(label = "GitHub 官方源", url = officialUrl, requiresSha256 = hasSha))
        return routes
    }

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

    /**
     * 解析托管在 docs/latest.json 的静态版本清单（用于国内 5G / 校园网免 VPN 容灾检查）
     */
    fun parseStaticManifestJson(rawJson: String): AppReleaseInfo? {
        return try {
            val root: JsonObject = json.parseToJsonElement(rawJson).jsonObject
            val tagName = root["tagName"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val versionName = root["versionName"]?.jsonPrimitive?.contentOrNull?.trim()
                ?.ifBlank { normalizeVersion(tagName) }
                ?: normalizeVersion(tagName)
            if (versionName.isEmpty()) return null
            val finalTag = tagName.ifBlank { "v$versionName" }
            val title = root["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { "海屿课表 v$versionName" }
            val notes = root["releaseNotes"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val htmlUrl = root["htmlUrl"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                .ifBlank { "$REPO_WEB_URL/releases/tag/$finalTag" }
            val apkName = root["apkFileName"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                .ifBlank { "HaiyuSchedule-GDOU-v$versionName.apk" }
            val apkUrl = root["apkDownloadUrl"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                .ifBlank { "$REPO_WEB_URL/releases/download/$finalTag/$apkName" }
            val apkSize = root["apkSizeBytes"]?.jsonPrimitive?.longOrNull ?: 0L
            val sha = root["expectedSha256"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase(Locale.US)
                ?.takeIf { it.length == 64 && it.all { ch -> ch in '0'..'9' || ch in 'a'..'f' } }

            AppReleaseInfo(
                tagName = finalTag,
                versionName = versionName,
                title = title,
                releaseNotes = cleanReleaseNotes(notes),
                htmlUrl = htmlUrl,
                apkDownloadUrl = apkUrl,
                apkFileName = apkName,
                apkSizeBytes = apkSize,
                expectedSha256 = sha
            )
        } catch (_: Exception) {
            null
        }
    }

    fun friendlyNetworkError(e: Throwable?): String {
        val msg = e?.message.orEmpty()
        return when {
            msg.contains("Unable to resolve host", ignoreCase = true) ||
                e is java.net.UnknownHostException -> "当前网络 DNS 无法解析该域名"
            msg.contains("timed out", ignoreCase = true) ||
                e is java.net.SocketTimeoutException -> "连接超时"
            msg.contains("Connection reset", ignoreCase = true) ||
                msg.contains("unexpected end of stream", ignoreCase = true) -> "连接被网络中断"
            msg.contains("SSL", ignoreCase = true) -> "HTTPS 握手异常"
            else -> "网络连接不稳定"
        }
    }

    private fun cleanReleaseNotes(raw: String): String {
        if (raw.isBlank()) return "优化体验并修复已知问题，建议更新到最新版本。"
        val cleaned = raw
            .replace("\r\n", "\n")
            .lines()
            .filterNot { line ->
                val t = line.trim()
                t.startsWith("SHA-256", ignoreCase = true) ||
                    t.startsWith("- SHA-256", ignoreCase = true) ||
                    t.startsWith("### 文件校验") ||
                    t.startsWith("- 文件名") ||
                    t.startsWith("- 包名") ||
                    t.contains("中文极速下载主页") ||
                    t.contains("点击下方") ||
                    t.contains("若你已安装") ||
                    t.startsWith("## 🌊 海屿课表")
            }
            .joinToString("\n") { line ->
                line
                    .replace(Regex("^#{1,4}\\s*"), "")
                    .replace(Regex("\\[([^\\]]+)]\\(https?://[^)]+\\)"), "$1")
                    .replace("**", "")
                    .replace("`", "")
                    .trimEnd()
            }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
            .take(1000)
        return cleaned.ifBlank { "优化体验并修复已知问题，建议更新到最新版本。" }
    }

    /**
     * 检查最新 Release：
     * 1. 优先查 GitHub Releases API；
     * 2. 若国内 5G/校园网遇到 DNS 或 API 限流，自动按序走 GitHub Pages / 国内镜像拉取 docs/latest.json（含完整 SHA-256 摘要）；
     * 3. 兜底走 /releases/latest 重定向探测。
     */
    suspend fun checkLatestRelease(currentVersion: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        var lastError: String? = null
        // 1. Official GitHub Releases API
        try {
            val conn = (URL(LATEST_RELEASE_API_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5500
                readTimeout = 7000
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
                    lastError = "版本信息解析失败"
                } else {
                    lastError = "官方接口返回 HTTP $code"
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastError = friendlyNetworkError(e)
        }

        // 2. Zero-VPN China 5G failover: GitHub Pages + domestic accelerated raw latest.json (carries exact SHA-256!)
        for (manifestUrl in MANIFEST_FALLBACK_URLS) {
            try {
                val conn = openFollowingRedirects(manifestUrl, maxHops = 4)
                try {
                    if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                        val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        val parsed = parseStaticManifestJson(text)
                        if (parsed != null) {
                            return@withContext if (isNewerVersion(parsed.versionName, currentVersion)) {
                                UpdateCheckResult.UpdateAvailable(parsed)
                            } else {
                                UpdateCheckResult.UpToDate(parsed.versionName)
                            }
                        }
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = friendlyNetworkError(e)
            }
        }

        // 3. Final fallback: probe /releases/latest redirect
        try {
            val redirectConn = (URL(LATEST_RELEASE_WEB_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                instanceFollowRedirects = false
                connectTimeout = 6000
                readTimeout = 6000
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
            "检查更新失败（${lastError ?: "网络异常"}），可稍后重试或点下方下载主页获取新版"
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

        val routes = buildDownloadRoutes(release)
        if (routes.isEmpty()) {
            return@withContext ApkDownloadResult.Error("未找到可用的安装包下载地址，请打开下载主页获取")
        }

        var lastError = "网络连接不稳定"
        for (route in routes) {
            if (!isActive) throw CancellationException("下载已取消")
            partFile.delete()
            onProgress(ApkDownloadProgress(0L, release.apkSizeBytes, route.label))
            try {
                val conn = openFollowingRedirects(route.url)
                try {
                    val code = conn.responseCode
                    if (code != HttpURLConnection.HTTP_OK) {
                        lastError = "${route.label} 返回 HTTP $code"
                        continue
                    }
                    val contentType = conn.contentType.orEmpty().lowercase(Locale.US)
                    if (contentType.contains("text/html")) {
                        lastError = "${route.label} 返回异常网页内容"
                        continue
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
                                    onProgress(ApkDownloadProgress(downloaded, contentLength, route.label))
                                }
                            }
                            output.flush()
                        }
                    }

                    if (downloaded < 100_000L) {
                        partFile.delete()
                        lastError = "${route.label} 文件大小异常"
                        continue
                    }

                    val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
                    val expected = release.expectedSha256
                    if (route.requiresSha256 && (expected == null || !actualSha256.equals(expected, ignoreCase = true))) {
                        partFile.delete()
                        lastError = "${route.label} SHA-256 校验未通过，已自动切换下一线路"
                        continue
                    }

                    if (targetFile.exists()) targetFile.delete()
                    if (!partFile.renameTo(targetFile)) {
                        partFile.copyTo(targetFile, overwrite = true)
                        partFile.delete()
                    }
                    onProgress(ApkDownloadProgress(downloaded, maxOf(downloaded, contentLength), route.label))
                    return@withContext ApkDownloadResult.Success(targetFile, sha256Verified = expected != null)
                } finally {
                    conn.disconnect()
                }
            } catch (e: CancellationException) {
                partFile.delete()
                throw e
            } catch (e: Exception) {
                partFile.delete()
                lastError = "${route.label}失败（${friendlyNetworkError(e)}）"
            }
        }

        ApkDownloadResult.Error("全部下载线路均未成功（$lastError），建议点击下方「下载主页」在浏览器中下载")
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
