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
 * jw.gdou.edu.cn（教务入口）、authserver.gdou.edu.cn（统一认证）与 webvpn.gdou.edu.cn（校外免校园网 WebVPN）
 * 真实可达性，避开部分教务网关对 HEAD 的 403/断连限制，并过滤未认证校园 Wi-Fi 劫持跳转。
 */
object NetworkChannelProbe {

    @Volatile
    private var cachedProbeResult: NetworkProbeResult? = null
    @Volatile
    private var cachedProbeAtMs: Long = 0L

    suspend fun probeCached(
        context: Context,
        maxAgeMs: Long = 45_000L,
        forceRefresh: Boolean = false
    ): NetworkProbeResult {
        val now = System.currentTimeMillis()
        val prev = cachedProbeResult
        if (
            !forceRefresh &&
            prev != null &&
            prev.state != CampusNetworkState.OFFLINE_OR_BLOCKED &&
            now - cachedProbeAtMs < maxAgeMs
        ) {
            return prev
        }
        val fresh = probe(context)
        cachedProbeResult = fresh
        cachedProbeAtMs = System.currentTimeMillis()
        return fresh
    }

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

    /**
     * 当用户原本偏好「校园网直连」且此前仅做过临时降级时，一旦检测到当前已回到校园网且尚未登录，
     * 应自动恢复「校园网直连」。
     */
    fun shouldAutoRestoreOnCampus(
        userPrefersOffCampus: Boolean,
        currentlyOffCampus: Boolean,
        state: CampusNetworkState,
        isLoggedIntoJw: Boolean
    ): Boolean {
        return !userPrefersOffCampus &&
            currentlyOffCampus &&
            !isLoggedIntoJw &&
            state == CampusNetworkState.ON_CAMPUS_DIRECT_READY
    }

    private data class ProbeOutcome(
        val latencyMs: Long?,
        val timedOut: Boolean,
        val tcpConnectTimedOut: Boolean = false
    )

    /**
     * 校外网络 SYN 丢包触发 TCP 建连超时且校外通道（webvpn）快速响应（<600ms）时无需二次等待，直接秒切校外；
     * 若 TCP 建连成功但读超时/连接重置（典型如学校 jw.gdou.edu.cn 负载均衡单后端节点无响应），或整体链路偏慢，
     * 必须允许带 Connection: close 复测避开坏节点，防止把校园网误判为仅校外可用。
     */
    fun shouldRetryJw(
        jwTimedOut: Boolean,
        wfwLatencyMs: Long?,
        jwTcpConnectTimedOut: Boolean = jwTimedOut
    ): Boolean {
        if (!jwTcpConnectTimedOut) return true
        return !jwTimedOut || wfwLatencyMs == null || wfwLatencyMs >= 600L
    }

    suspend fun probe(context: Context): NetworkProbeResult = withContext(Dispatchers.IO) {
        val isWifi = isWifiConnected(context)
        val cellularOnly = isCellularOnly(context)
        // On pure 4G/5G cellular (no Wi-Fi/Ethernet/VPN), jw.gdou.edu.cn cannot be reached directly:
        // skip the 1s+ TCP SYN blackhole wait entirely and probe only off-campus WebVPN + authserver!
        val jwFirstDeferred = if (cellularOnly) null else async { probeReachability(GdouSyncScript.URL_ON_CAMPUS, 1000, 1300) }
        val authFirstDeferred = async { probeReachability(GdouSyncScript.URL_AUTH_SERVER, 1100, 1400) }
        val wfwFirstDeferred = async { probeReachability(GdouSyncScript.URL_OFF_CAMPUS, 1200, 1500) }
        val jwFirst = jwFirstDeferred?.await() ?: ProbeOutcome(latencyMs = null, timedOut = true, tcpConnectTimedOut = true)
        val authFirst = authFirstDeferred.await()
        val wfwFirst = wfwFirstDeferred.await()

        val wfwRetryDeferred = if (wfwFirst.latencyMs == null) {
            async {
                probeReachability(GdouSyncScript.URL_OFF_CAMPUS, 1800, 2000).latencyMs
                    ?: probeReachability(GdouSyncScript.URL_WFW_OFF_CAMPUS, 1800, 2000).latencyMs
            }
        } else {
            null
        }
        val jwRetryDeferred = if (
            !cellularOnly &&
            jwFirst.latencyMs == null &&
            shouldRetryJw(jwFirst.timedOut, wfwFirst.latencyMs, jwFirst.tcpConnectTimedOut)
        ) {
            async {
                val retry1 = probeReachability(GdouSyncScript.URL_ON_CAMPUS, 1100, 1300)
                if (retry1.latencyMs != null) {
                    retry1.latencyMs
                } else if (!retry1.tcpConnectTimedOut) {
                    // TCP handshake succeeded (on campus), retry once more with a fresh socket to dodge bad ADC node
                    probeReachability(GdouSyncScript.URL_ON_CAMPUS, 1200, 1400).latencyMs
                } else {
                    null
                }
            }
        } else {
            null
        }
        val authRetryDeferred = if (
            authFirst.latencyMs == null &&
            (jwFirst.latencyMs != null || wfwFirst.latencyMs != null || !jwFirst.timedOut || !wfwFirst.timedOut)
        ) {
            async { probeReachability(GdouSyncScript.URL_AUTH_SERVER, 1500, 1800).latencyMs }
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

    /**
     * 纯蜂窝移动网络判定（仅蜂窝、无 Wi-Fi / 以太网 / VPN）：
     * 物理上绝不可能直连校内 jw.gdou.edu.cn，可直接 0 延迟切校外通道，省去 1–10s 无效超时。
     */
    fun isCellularOnly(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            classifyCellularOnly(
                hasWifiOrEthernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
                hasCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
                hasVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            )
        } catch (_: Exception) {
            false
        }
    }

    fun classifyCellularOnly(
        hasWifiOrEthernet: Boolean,
        hasCellular: Boolean,
        hasVpn: Boolean
    ): Boolean = hasCellular && !hasWifiOrEthernet && !hasVpn

    fun shouldPreferOffCampusImmediately(context: Context): Boolean {
        if (isCellularOnly(context)) return true
        val prev = cachedProbeResult
        return prev != null &&
            prev.state == CampusNetworkState.OFF_CAMPUS_ONLY &&
            System.currentTimeMillis() - cachedProbeAtMs < 45_000L
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
        var connectCompleted = false
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
                setRequestProperty("Connection", "close")
            }
            conn.connect()
            connectCompleted = true
            val code = conn.responseCode
            val loc = conn.getHeaderField("Location")
            val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
            if (isValidProbeResponse(code, loc)) {
                safeLog(false, "probe ok $urlStr code=$code loc=$loc (${elapsed}ms)")
                ProbeOutcome(latencyMs = elapsed, timedOut = false, tcpConnectTimedOut = false)
            } else {
                safeLog(true, "probe rejected $urlStr code=$code loc=$loc (${elapsed}ms)")
                ProbeOutcome(latencyMs = null, timedOut = false, tcpConnectTimedOut = false)
            }
        } catch (e: SocketTimeoutException) {
            val phase = if (connectCompleted) "read" else "connect"
            safeLog(true, "probe $phase timeout $urlStr (${connectTimeoutMs}/${readTimeoutMs}ms): ${e.message}")
            ProbeOutcome(latencyMs = null, timedOut = true, tcpConnectTimedOut = !connectCompleted)
        } catch (e: Exception) {
            safeLog(true, "probe fail $urlStr (connected=$connectCompleted): ${e.javaClass.simpleName}: ${e.message}")
            ProbeOutcome(latencyMs = null, timedOut = false, tcpConnectTimedOut = false)
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
