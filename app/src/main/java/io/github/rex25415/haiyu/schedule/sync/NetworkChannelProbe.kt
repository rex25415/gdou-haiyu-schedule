package io.github.rex25415.haiyu.schedule.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
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
    val wfwLatencyMs: Long? = null
)

/**
 * 轻量级只读网络通道探测：
 * 仅发送单次 HEAD 请求检测 jw.gdou.edu.cn（校园网直连）与 wfw.gdou.edu.cn（校外免校园网）可达性，
 * 零登录信息、零高频重试，帮同学在校园网与 4G/5G 之间顺滑无缝切换。
 */
object NetworkChannelProbe {

    fun classifyState(jwReachable: Boolean, wfwReachable: Boolean): CampusNetworkState = when {
        jwReachable -> CampusNetworkState.ON_CAMPUS_DIRECT_READY
        wfwReachable -> CampusNetworkState.OFF_CAMPUS_ONLY
        else -> CampusNetworkState.OFFLINE_OR_BLOCKED
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

    suspend fun probe(context: Context): NetworkProbeResult = withContext(Dispatchers.IO) {
        val isWifi = isWifiConnected(context)
        val jwDeferred = async { probeHead(GdouSyncScript.URL_ON_CAMPUS) }
        val wfwDeferred = async { probeHead(GdouSyncScript.URL_OFF_CAMPUS) }
        val jwMs = jwDeferred.await()
        val wfwMs = wfwDeferred.await()
        NetworkProbeResult(
            state = classifyState(jwMs != null, wfwMs != null),
            isOnWifi = isWifi,
            jwLatencyMs = jwMs,
            wfwLatencyMs = wfwMs
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

    private fun probeHead(urlStr: String): Long? {
        val start = System.currentTimeMillis()
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                instanceFollowRedirects = false
                connectTimeout = 2500
                readTimeout = 2500
                setRequestProperty("User-Agent", "HaiyuSchedule-ChannelCheck")
            }
            val code = conn.responseCode
            if (code in 200..499) {
                (System.currentTimeMillis() - start).coerceAtLeast(1L)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
