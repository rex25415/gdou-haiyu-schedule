package io.github.rex25415.haiyu.schedule.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

enum class CampusNetworkState {
    PROBING,
    ON_CAMPUS_DIRECT_READY,   // jw.gdou.edu.cn 直接可达（校园网环境）
    OFF_CAMPUS_ONLY,          // jw.gdou.edu.cn 不可直连，wfw.gdou.edu.cn 可达（校外流量/Wi-Fi）
    OFFLINE_OR_BLOCKED        // 两者均暂不可达（断网或校园网未认证）
}

data class NetworkProbeResult(
    val state: CampusNetworkState,
    val isOnWifi: Boolean,
    val jwLatencyMs: Long? = null,
    val wfwLatencyMs: Long? = null,
    val authLatencyMs: Long? = null
)

/**
 * 轻量级只读网络通道探测：
 * 并发发送轻量级 GET（仅读状态码与 Location 头、不拉正文、不自动重定向）检测
 * jw.gdou.edu.cn（教务入口）、authserver.gdou.edu.cn（统一认证）与 wfw.gdou.edu.cn（校外免校园网）
 * 真实可达性，避开部分教务网关对 HEAD 的 403/断连限制，并过滤未认证校园 Wi-Fi 劫持跳转。
 */
object NetworkChannelProbe {

    fun classifyState(
        jwReachable: Boolean,
        wfwReachable: Boolean,
        authReachable: Boolean = true
    ): CampusNetworkState = when {
        jwReachable && authReachable -> CampusNetworkState.ON_CAMPUS_DIRECT_READY
        wfwReachable -> CampusNetworkState.OFF_CAMPUS_ONLY
        else -> CampusNetworkState.OFFLINE_OR_BLOCKED
    }

    /**
     * 过滤宿舍/公共 Wi-Fi 未认证劫持页（如 302 跳转到非 gdou.edu.cn 域名或内网认证 IP）及 404/407/5xx 异常。
     */
    fun isValidProbeResponse(statusCode: Int, locationHeader: String?): Boolean {
        if (statusCode !in 200..399 && statusCode != 401 && statusCode != 403) return false
        if (statusCode in 300..399 && !locationHeader.isNullOrBlank()) {
            val loc = locationHeader.trim()
            if (loc.startsWith("http://", ignoreCase = true) || loc.startsWith("https://", ignoreCase = true)) {
                val host = GdouSyncScript.extractHostLower(loc)
                if (host.isNotEmpty() && host != "gdou.edu.cn" && !host.endsWith(".gdou.edu.cn")) {
                    return false
                }
            }
        }
        return true
    }

    /**
     * 判断是否建议自动切换通道（纯函数，便于单元测试）
     * 返回 true 表示当前「校园网直连」不可达，应自动切到「校外通用(免校园网)」
     */
    fun shouldAutoFallbackToOffCampus(
        currentlyOffCampus: Boolean,
        state: CampusNetworkState
    ): Boolean {
        return !currentlyOffCampus && state == CampusNetworkState.OFF_CAMPUS_ONLY
    }

    private data class ProbeOutcome(
        val latencyMs: Long?,
        val timedOut: Boolean
    )

    /**
     * 校外网络 SYN 丢包触发首次超时且校外通道（wfw）快速响应（<600ms）时无需二次等待，直接秒切校外；
     * 若属于冷握手/连接重置报错，或整体链路偏慢，则允许快速复测 1 次以防校园网抖动误切。
     */
    fun shouldRetryJw(jwTimedOut: Boolean, wfwLatencyMs: Long?): Boolean {
        return !jwTimedOut || wfwLatencyMs == null || wfwLatencyMs >= 600L
    }

    suspend fun probe(context: Context): NetworkProbeResult = withContext(Dispatchers.IO) {
        val isWifi = isWifiConnected(context)
        val jwFirstDeferred = async { probeReachability(GdouSyncScript.URL_ON_CAMPUS, 1800, 2000) }
        val authFirstDeferred = async { probeReachability(GdouSyncScript.URL_AUTH_SERVER, 1800, 2000) }
        val wfwFirstDeferred = async { probeReachability(GdouSyncScript.URL_OFF_CAMPUS, 1800, 2000) }
        val jwFirst = jwFirstDeferred.await()
        val authFirst = authFirstDeferred.await()
        val wfwFirst = wfwFirstDeferred.await()

        val wfwRetryDeferred = if (wfwFirst.latencyMs == null) {
            async { probeReachability(GdouSyncScript.URL_OFF_CAMPUS, 2400, 2600).latencyMs }
        } else {
            null
        }
        val jwRetryDeferred = if (jwFirst.latencyMs == null && shouldRetryJw(jwFirst.timedOut, wfwFirst.latencyMs)) {
            async { probeReachability(GdouSyncScript.URL_ON_CAMPUS, 2200, 2400).latencyMs }
        } else {
            null
        }
        val authRetryDeferred = if (
            authFirst.latencyMs == null &&
            (jwFirst.latencyMs != null || wfwFirst.latencyMs != null || !jwFirst.timedOut || !wfwFirst.timedOut)
        ) {
            async { probeReachability(GdouSyncScript.URL_AUTH_SERVER, 2200, 2400).latencyMs }
        } else {
            null
        }

        val wfwMs = wfwFirst.latencyMs ?: wfwRetryDeferred?.await()
        val jwMs = jwFirst.latencyMs ?: jwRetryDeferred?.await()
        val authMs = authFirst.latencyMs ?: authRetryDeferred?.await()

        NetworkProbeResult(
            state = classifyState(
                jwReachable = jwMs != null,
                wfwReachable = wfwMs != null,
                authReachable = authMs != null
            ),
            isOnWifi = isWifi,
            jwLatencyMs = jwMs,
            wfwLatencyMs = wfwMs,
            authLatencyMs = authMs
        )
    }

    private fun isWifiConnected(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (_: Exception) {
            false
        }
    }

    private fun probeReachability(
        urlStr: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): ProbeOutcome {
        val start = System.currentTimeMillis()
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/128.0.0.0 Mobile Safari/537.36"
                )
                setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*")
                setRequestProperty("Accept-Encoding", "identity")
            }
            val code = conn.responseCode
            val loc = conn.getHeaderField("Location")
            val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
            if (isValidProbeResponse(code, loc)) {
                safeLog(false, "probe ok $urlStr code=$code loc=$loc (${elapsed}ms)")
                ProbeOutcome(latencyMs = elapsed, timedOut = false)
            } else {
                safeLog(true, "probe rejected $urlStr code=$code loc=$loc (${elapsed}ms)")
                ProbeOutcome(latencyMs = null, timedOut = false)
            }
        } catch (e: SocketTimeoutException) {
            safeLog(true, "probe timeout $urlStr (${connectTimeoutMs}ms): ${e.message}")
            ProbeOutcome(latencyMs = null, timedOut = true)
        } catch (e: Exception) {
            safeLog(true, "probe fail $urlStr: ${e.javaClass.simpleName}: ${e.message}")
            ProbeOutcome(latencyMs = null, timedOut = false)
        } finally {
            conn?.disconnect()
        }
    }

    private fun safeLog(warn: Boolean, msg: String) {
        try {
            if (warn) {
                android.util.Log.w("NetworkChannelProbe", msg)
            } else {
                android.util.Log.d("NetworkChannelProbe", msg)
            }
        } catch (_: Throwable) {
        }
    }
}
