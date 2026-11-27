package io.github.rex25415.haiyu.schedule.sync

import android.content.Context
import io.github.rex25415.haiyu.schedule.model.ClassroomSlotStatus
import io.github.rex25415.haiyu.schedule.model.CourseItem
import io.github.rex25415.haiyu.schedule.model.EmptyClassroomItem
import io.github.rex25415.haiyu.schedule.model.ExamItem
import io.github.rex25415.haiyu.schedule.model.GradeItem
import io.github.rex25415.haiyu.schedule.model.LevelExamItem
import io.github.rex25415.haiyu.schedule.model.PracticeCourseItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream

enum class SyncChannel(val id: String, val label: String) {
    WEBVPN("webvpn", "校外免校园网 (WebVPN)"),
    ON_CAMPUS("campus", "校园网直连"),
    WFW_LEGACY("wfw", "校外兼容通道 (wfw)");

    val isOffCampus: Boolean get() = this != ON_CAMPUS

    fun baseJwUrl(customOverride: String? = null): String {
        if (!customOverride.isNullOrBlank()) return customOverride.trimEnd('/')
        return when (this) {
            WEBVPN -> "https://webvpn.gdou.edu.cn/https/${GdouSyncScript.WEBVPN_JW_HEX}"
            ON_CAMPUS -> "https://jw.gdou.edu.cn"
            WFW_LEGACY -> "https://wfw.gdou.edu.cn"
        }
    }

    fun authStartUrl(): String = when (this) {
        WEBVPN -> GdouSyncScript.URL_DIRECT_CAS_WEBVPN
        ON_CAMPUS -> GdouSyncScript.URL_ON_CAMPUS
        WFW_LEGACY -> GdouSyncScript.URL_DIRECT_CAS_WFW
    }

    fun jwEntryUrl(): String = when (this) {
        WEBVPN -> GdouSyncScript.URL_WEBVPN_JW_ENTRY
        ON_CAMPUS -> GdouSyncScript.URL_ON_CAMPUS
        WFW_LEGACY -> GdouSyncScript.URL_WFW_JW_ENTRY
    }

    companion object {
        fun preferredOffCampus(preferLegacyWfw: Boolean): SyncChannel =
            if (preferLegacyWfw) WFW_LEGACY else WEBVPN
    }
}

data class DiscoveredMenuEndpoints(
    val gradeEps: List<String> = emptyList(),
    val examEps: List<String> = emptyList(),
    val levelExamEps: List<String> = emptyList(),
    val scheduleIndexEps: List<String> = emptyList(),
    val scheduleDataEps: List<String> = emptyList(),
    val dynamicLinks: List<DynamicMenuEntry> = emptyList()
)

data class DynamicMenuEntry(
    val code: String,
    val path: String,
    val title: String
)

sealed class SessionCheckResult {
    data class Ready(
        val channel: SyncChannel,
        val menuEndpoints: DiscoveredMenuEndpoints,
        val latencyMs: Long
    ) : SessionCheckResult()

    data class NeedsAuth(
        val channel: SyncChannel,
        val resumeUrl: String,
        val reason: String,
        val isMfa: Boolean = false,
        val isWebVpnDenied: Boolean = false
    ) : SessionCheckResult()

    data class NetworkError(
        val channel: SyncChannel,
        val message: String
    ) : SessionCheckResult()
}

data class SyncedCoursesNativeResult(
    val courses: List<CourseItem>,
    val practiceCourses: List<PracticeCourseItem>,
    val startDate: String?,
    val totalWeeks: Int,
    val detectedCampusId: String
)

data class SyncedGradesNativeResult(
    val grades: List<GradeItem>,
    val levelExams: List<LevelExamItem>
)

data class EmptyClassroomQueryNativeResult(
    val rooms: List<EmptyClassroomItem>,
    val summary: String
)

data class RoomDayScheduleNativeResult(
    val campusLabel: String,
    val building: String,
    val roomName: String,
    val weekNumber: Int,
    val dayOfWeek: Int,
    val seats: String,
    val roomType: String,
    val slots: List<ClassroomSlotStatus>
)

sealed class NativeCallResult<out T> {
    data class Success<T>(val data: T) : NativeCallResult<T>()
    data class AuthExpired(
        val resumeUrl: String,
        val message: String,
        val isMfa: Boolean = false,
        val isWebVpnDenied: Boolean = false
    ) : NativeCallResult<Nothing>()
    data class EvalIntercept(val message: String) : NativeCallResult<Nothing>()
    data class Failure(val message: String, val isNetworkError: Boolean = false) : NativeCallResult<Nothing>()
}

data class FullSyncNativeOutcome(
    val coursesResult: NativeCallResult<SyncedCoursesNativeResult>? = null,
    val gradesResult: NativeCallResult<SyncedGradesNativeResult>? = null,
    val examsResult: NativeCallResult<List<ExamItem>>? = null,
    val summaryText: String = "",
    val errorText: String? = null,
    val authExpired: NativeCallResult.AuthExpired? = null,
    val evalInterceptMessage: String? = null
)

data class CourseRefreshDiffSummary(
    val hasChanges: Boolean,
    val addedCount: Int = 0,
    val adjustedCount: Int = 0,
    val removedCount: Int = 0,
    val changedCourseNames: List<String> = emptyList(),
    val message: String
)

sealed class QuickRefreshNativeOutcome {
    data class Success(
        val channel: SyncChannel,
        val courses: List<CourseItem>,
        val practiceCourses: List<PracticeCourseItem>,
        val startDate: String?,
        val totalWeeks: Int,
        val termLabel: String,
        val yearCode: String,
        val semesterCode: String,
        val detectedCampusId: String,
        val summary: String
    ) : QuickRefreshNativeOutcome()

    data class NeedsLogin(
        val channel: SyncChannel,
        val resumeUrl: String,
        val reason: String,
        val isMfa: Boolean = false,
        val isWebVpnDenied: Boolean = false
    ) : QuickRefreshNativeOutcome()

    data class Error(val message: String, val isEvalIntercept: Boolean = false) : QuickRefreshNativeOutcome()
}

/**
 * 广东海洋大学纯 Kotlin 原生 HTTPS 同步引擎：
 * - 零常驻 WebView、零 374KB WebVPN main.js、零 65KB JS 字符串注入、零 Prototype.js 兼容坑。
 * - 直接共享 Android CookieManager（含 WebVPN 的 HttpOnly wengine_vpn_ticket、正方 JSESSIONID 与 CAS CASTGC），
 *   通过 Android 内置 OkHttp-backed HttpsURLConnection + Coroutines 并发直取正方教务 JSON。
 * - 支持自动无头 CAS 302 续期（仅在真遇密码表单/验证码/MFA/denied 时返回 NeedsAuth 唤醒一次性认证沙盒）。
 */
class GdouNativeSyncEngine(
    private val cookieStore: GdouCookieStore = AndroidWebViewCookieStore(),
    private val customBaseUrlOverride: String? = null,
    private val allowLoopbackForTest: Boolean = false,
    private val casLoginBaseOverride: String? = null,
    private val webVpnHostOverride: String? = null,
    private val wfwHostOverride: String? = null
) {

    private sealed class CasTicketMintResult {
        data class Ticket(val ticket: String, val redirectLocation: String) : CasTicketMintResult()
        data class Mfa(val mfaUrl: String) : CasTicketMintResult()
        object NeedsLogin : CasTicketMintResult()
        data class NetworkError(val message: String) : CasTicketMintResult()
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val menuCache = ConcurrentHashMap<SyncChannel, DiscoveredMenuEndpoints>()
    private val termOptionsCache = ConcurrentHashMap<SyncChannel, Pair<Long, TermOptionsPayload>>()
    private val emptyRoomCtxCache = ConcurrentHashMap<String, Pair<Long, EmptyRoomCtx>>()
    private val lastVerifiedAt = ConcurrentHashMap<SyncChannel, Long>()
    @Volatile
    var lastReadyChannel: SyncChannel? = null
        private set

    private data class HttpOutcome(
        val statusCode: Int,
        val finalUrl: String,
        val body: String,
        val contentType: String,
        val authExpired: Boolean = false,
        val isMfa: Boolean = false,
        val isWebVpnDenied: Boolean = false,
        val networkError: Boolean = false,
        val errorDetail: String = "",
        val tcpConnectTimedOut: Boolean = false,
        val nonRetriableNetwork: Boolean = false
    )

    private data class PaginatedOutcome(
        val ok: Boolean,
        val status: Int,
        val items: List<JsonObject>,
        val totalResult: Int,
        val hasTotalResult: Boolean,
        val authExpired: Boolean = false,
        val isMfa: Boolean = false,
        val isWebVpnDenied: Boolean = false,
        val resumeUrl: String = "",
        val htmlHint: String = "",
        val isEvalIntercept: Boolean = false,
        val networkError: Boolean = false
    )

    fun invalidateSessionCache(channel: SyncChannel? = null) {
        if (channel == null) {
            menuCache.clear()
            termOptionsCache.clear()
            emptyRoomCtxCache.clear()
            lastVerifiedAt.clear()
            lastReadyChannel = null
        } else {
            menuCache.remove(channel)
            termOptionsCache.remove(channel)
            val prefix = "${channel.name}|"
            emptyRoomCtxCache.keys.removeIf { it.startsWith(prefix) }
            lastVerifiedAt.remove(channel)
            if (lastReadyChannel == channel) lastReadyChannel = null
        }
    }

    fun getCachedMenuEndpoints(channel: SyncChannel): DiscoveredMenuEndpoints? = menuCache[channel]

    fun clearAllCookiesAndCache() {
        invalidateSessionCache()
        cookieStore.clearAll()
    }

    private fun casLoginEndpoint(): String = casLoginBaseOverride?.trimEnd('/') ?: GdouSyncScript.URL_AUTH_SERVER
    private fun webVpnRootUrl(): String = webVpnHostOverride?.trimEnd('/') ?: "https://webvpn.gdou.edu.cn"
    private fun wfwRootUrl(): String = wfwHostOverride?.trimEnd('/') ?: "https://wfw.gdou.edu.cn"
    private fun casBridgeEnabled(): Boolean = customBaseUrlOverride.isNullOrBlank() || !casLoginBaseOverride.isNullOrBlank()

    fun hasDirectCasTgc(): Boolean =
        cookieStore.getCookieHeader(casLoginEndpoint()).orEmpty().contains("CASTGC=", ignoreCase = true)

    fun hasWebVpnTicketCookie(): Boolean =
        cookieStore.getCookieHeader("${webVpnRootUrl()}/").orEmpty().contains("wengine_vpn_ticket=", ignoreCase = true)

    fun hasWfwSessionCookie(): Boolean =
        cookieStore.getCookieHeader("${wfwRootUrl()}/").orEmpty().contains("WFWSESSID=", ignoreCase = true)

    fun clearStaleWebVpnTicket() {
        cookieStore.clearCookieByPrefix("${webVpnRootUrl()}/", "wengine_vpn_ticket")
    }

    private fun engineLog(warn: Boolean = false, msg: String) {
        try {
            if (warn) {
                android.util.Log.w("GdouNativeSyncEngine", msg)
            } else {
                android.util.Log.d("GdouNativeSyncEngine", msg)
            }
        } catch (_: Throwable) {
        }
    }

    private fun sanitizeUrlForLog(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return ""
        return try {
            val u = URI(trimmed)
            val host = u.host.orEmpty()
            val path = u.rawPath.orEmpty().ifEmpty { "/" }
            val queryKeys = u.rawQuery.orEmpty()
                .split("&")
                .mapNotNull { it.substringBefore("=").takeIf { k -> k.isNotBlank() } }
                .distinct()
                .joinToString(",")
            if (queryKeys.isEmpty()) "$host$path" else "$host$path?$queryKeys"
        } catch (_: Exception) {
            trimmed.substringBefore("?").take(96)
        }
    }

    private fun cookieFlagsSummary(): String {
        val cas = hasDirectCasTgc()
        val vpn = hasWebVpnTicketCookie()
        val wfwCookies = cookieStore.getCookieHeader("${wfwRootUrl()}/").orEmpty()
        val wfwSess = wfwCookies.contains("WFWSESSID=", ignoreCase = true)
        val wfwJw = wfwCookies.contains("JSESSIONID=", ignoreCase = true)
        val jwSess = cookieStore.getCookieHeader("https://jw.gdou.edu.cn/").orEmpty()
            .contains("JSESSIONID=", ignoreCase = true)
        return "cas=$cas,vpn=$vpn,wfw=$wfwSess,wfwJw=$wfwJw,jw=$jwSess"
    }

    private fun ensureWfwJwProxyCookies() {
        cookieStore.saveResponseCookies(
            "${wfwRootUrl()}/",
            listOf(
                "urlss=\"https://jw.gdou.edu.cn/\"; Path=/; Secure",
                "urlss_jw=\"https://jw.gdou.edu.cn/\"; Path=/; Secure"
            )
        )
    }

    /**
     * 直接利用本地第一方 authserver.gdou.edu.cn 的 CASTGC 静默换取单次 CAS Service Ticket（ST-...）：
     * - 校外 4G/5G 可直连 authserver.gdou.edu.cn，无需经过 WebVPN 或 wfw 代理；
     * - 彻底绕开 wfw 代理吞掉 302 跳转并改写 service 参数的历史死循环，同时打通校园网 <-> 5G 零密码漫游！
     */
    private fun mintCasServiceTicket(serviceUrl: String, timeoutMs: Int = 8_000): CasTicketMintResult {
        val reqUrl = "${casLoginEndpoint()}?service=${urlEncode(serviceUrl)}"
        if (!isAllowedRequestUrl(reqUrl)) {
            return CasTicketMintResult.NetworkError("拦截了非官方统一认证地址")
        }
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(reqUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                useCaches = false
                setRequestProperty("User-Agent", MOBILE_CHROME_UA)
                setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
                setRequestProperty("Accept-Encoding", "gzip")
                val cookieHeader = cookieStore.getCookieHeader(reqUrl)
                if (!cookieHeader.isNullOrBlank()) {
                    setRequestProperty("Cookie", cookieHeader)
                }
            }
            val code = conn.responseCode
            val setCookies = extractSetCookieHeaders(conn)
            if (setCookies.isNotEmpty()) {
                cookieStore.saveResponseCookies(reqUrl, setCookies)
            }
            if (code in 300..399) {
                val locRaw = conn.getHeaderField("Location")?.trim().orEmpty()
                drainStreamQuietly(conn, code)
                if (locRaw.isEmpty()) {
                    engineLog(warn = true, "casMint emptyLoc svc=${sanitizeUrlForLog(serviceUrl)} code=$code")
                    return CasTicketMintResult.NeedsLogin
                }
                val resolved = normalizeGdouRedirectUrl(resolveRedirectUrl(reqUrl, locRaw), SyncChannel.ON_CAMPUS)
                if (GdouSyncScript.isMfaUrl(resolved)) {
                    engineLog(msg = "casMint MFA svc=${sanitizeUrlForLog(serviceUrl)}")
                    return CasTicketMintResult.Mfa(resolved)
                }
                val ticket = CAS_TICKET_RE.find(resolved)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                if (ticket.isNotEmpty()) {
                    engineLog(msg = "casMint OK svc=${sanitizeUrlForLog(serviceUrl)} -> ${sanitizeUrlForLog(resolved)}")
                    CasTicketMintResult.Ticket(ticket, resolved)
                } else {
                    engineLog(warn = true, "casMint noTicket svc=${sanitizeUrlForLog(serviceUrl)} loc=${sanitizeUrlForLog(resolved)}")
                    CasTicketMintResult.NeedsLogin
                }
            } else {
                drainStreamQuietly(conn, code)
                engineLog(warn = true, "casMint NeedsLogin svc=${sanitizeUrlForLog(serviceUrl)} code=$code")
                CasTicketMintResult.NeedsLogin
            }
        } catch (e: SocketTimeoutException) {
            try { conn?.disconnect() } catch (_: Throwable) {}
            engineLog(warn = true, "casMint timeout svc=${sanitizeUrlForLog(serviceUrl)}: ${e.message}")
            CasTicketMintResult.NetworkError("统一认证连接超时")
        } catch (e: IOException) {
            try { conn?.disconnect() } catch (_: Throwable) {}
            engineLog(warn = true, "casMint ioErr svc=${sanitizeUrlForLog(serviceUrl)}: ${e.javaClass.simpleName}: ${e.message}")
            CasTicketMintResult.NetworkError("统一认证网络中断：${e.message ?: e.javaClass.simpleName}")
        } catch (e: Exception) {
            try { conn?.disconnect() } catch (_: Throwable) {}
            engineLog(warn = true, "casMint err svc=${sanitizeUrlForLog(serviceUrl)}: ${e.javaClass.simpleName}: ${e.message}")
            CasTicketMintResult.NetworkError(e.message ?: "统一认证异常")
        }
    }

    private suspend fun ensureWebVpnTicketFromCas(): HttpOutcome? {
        val webVpnService = "${webVpnRootUrl()}/login?cas_login=true"
        return when (val minted = mintCasServiceTicket(webVpnService)) {
            is CasTicketMintResult.Ticket -> {
                val redeemUrl = "${webVpnRootUrl()}/login?cas_login=true&ticket=${urlEncode(minted.ticket)}"
                executeHttp(urlStr = redeemUrl, method = "GET", channel = SyncChannel.WEBVPN, timeoutMs = 8_000)
            }
            is CasTicketMintResult.Mfa -> HttpOutcome(302, minted.mfaUrl, "", "", authExpired = true, isMfa = true)
            else -> null
        }
    }

    private suspend fun redeemJwCasTicket(channel: SyncChannel, ticket: String): HttpOutcome {
        if (channel == SyncChannel.WFW_LEGACY) {
            // Clear wfw portal CAS cookies (WFWSESSID/db_agent_token/_path/urlss_file) & stale JSESSIONIDs:
            // authenticated wfw portal cookies cause wfw nginx to intercept and strip `?ticket=` before reaching jw.gdou.edu.cn!
            for (targetUrl in listOf("${wfwRootUrl()}/sso/jziotlogin", "${wfwRootUrl()}/")) {
                for (prefix in listOf("JSESSIONID", "WFWSESSID", "db_agent_token", "_path", "urlss_file")) {
                    cookieStore.clearCookieByPrefix(targetUrl, prefix)
                }
            }
            ensureWfwJwProxyCookies()
        }
        val redeemUrl = when (channel) {
            SyncChannel.WFW_LEGACY -> "${wfwRootUrl()}/sso/jziotlogin?ticket=${urlEncode(ticket)}"
            else -> "${buildJwUrl(channel, "/sso/jziotlogin")}?ticket=${urlEncode(ticket)}"
        }
        val resp = executeHttp(urlStr = redeemUrl, method = "GET", channel = channel, timeoutMs = 10_000)
        if (channel == SyncChannel.WFW_LEGACY) {
            ensureWfwJwProxyCookies()
        }
        val bodyTitle = TITLE_RE.find(resp.body)?.groupValues?.getOrNull(1)?.replace(WS_RE, " ")?.trim().orEmpty().take(40)
        engineLog(
            warn = resp.networkError || resp.authExpired || resp.statusCode !in 200..299 || channel == SyncChannel.WFW_LEGACY,
            msg = "redeemJw ch=${channel.name} code=${resp.statusCode} netErr=${resp.networkError} authExp=${resp.authExpired} final=${sanitizeUrlForLog(resp.finalUrl)} title=[$bodyTitle] len=${resp.body.length}"
        )
        return resp
    }

    private suspend fun establishWfwSessionViaCas(): SessionCheckResult.NeedsAuth? {
        ensureWfwJwProxyCookies()
        return when (val jwTicket = mintCasServiceTicket(GdouSyncScript.URL_JW_SSO_SERVICE)) {
            is CasTicketMintResult.Ticket -> {
                val redeemResp = redeemJwCasTicket(SyncChannel.WFW_LEGACY, jwTicket.ticket)
                if (redeemResp.isMfa) {
                    SessionCheckResult.NeedsAuth(
                        channel = SyncChannel.WFW_LEGACY,
                        resumeUrl = redeemResp.finalUrl,
                        reason = "学校统一认证需要完成二次安全验证（企业微信/短信）",
                        isMfa = true
                    )
                } else {
                    null
                }
            }
            is CasTicketMintResult.Mfa -> SessionCheckResult.NeedsAuth(
                channel = SyncChannel.WFW_LEGACY,
                resumeUrl = jwTicket.mfaUrl,
                reason = "学校统一认证需要完成二次安全验证（企业微信/短信）",
                isMfa = true
            )
            else -> null
        }
    }

    /**
     * 核心会话建立与无头 CAS 静默换票：
     * - WebView 完成第一方 authserver.gdou.edu.cn 统一认证拿到 CASTGC 后即可立刻销毁；
     * - 后台 ~150ms 通过纯 HTTP 直连 authserver 签发单次 ST 票据并核销到正方 / WebVPN / wfw；
     * - 若学号未同步至新版 WebVPN 本地用户库（/wengine-vpn/denied），自动利用已有 CASTGC 零密码无缝切至兼容通道 (wfw)！
     */
    suspend fun establishOrVerifySession(
        channel: SyncChannel,
        forceReverify: Boolean = false,
        allowAutoWfwFailover: Boolean = true
    ): SessionCheckResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        engineLog(msg = "verifyStart ch=${channel.name} force=$forceReverify failover=$allowAutoWfwFailover ${cookieFlagsSummary()}")
        if (!forceReverify) {
            val cachedMenu = menuCache[channel]
            val verifiedTs = lastVerifiedAt[channel] ?: 0L
            if (cachedMenu != null && startMs - verifiedTs < 90_000L) {
                engineLog(msg = "verifyCacheHit ch=${channel.name} menu=${cachedMenu.dynamicLinks.size}")
                return@withContext SessionCheckResult.Ready(channel, cachedMenu, 0L)
            }
        }

        // 0-RTT fast-path & silent cross-channel CASTGC -> WebVPN / WFW ticket minting
        if (casBridgeEnabled()) {
            val hasCasTgc = hasDirectCasTgc()
            when (channel) {
                SyncChannel.WEBVPN -> {
                    val hasVpnTicket = hasWebVpnTicketCookie()
                    if (!hasVpnTicket && !hasCasTgc) {
                        invalidateSessionCache(channel)
                        return@withContext SessionCheckResult.NeedsAuth(
                            channel = channel,
                            resumeUrl = channel.authStartUrl(),
                            reason = "请完成广东海洋大学统一身份认证"
                        )
                    }
                    if (!hasVpnTicket && hasCasTgc) {
                        val ssoResp = ensureWebVpnTicketFromCas()
                        if (ssoResp?.isWebVpnDenied == true) {
                            clearStaleWebVpnTicket()
                            invalidateSessionCache(channel)
                            if (allowAutoWfwFailover && hasDirectCasTgc()) {
                                val fallback = establishOrVerifySession(
                                    channel = SyncChannel.WFW_LEGACY,
                                    forceReverify = true,
                                    allowAutoWfwFailover = false
                                )
                                if (fallback is SessionCheckResult.Ready) {
                                    return@withContext fallback
                                }
                            }
                            return@withContext SessionCheckResult.NeedsAuth(
                                channel = channel,
                                resumeUrl = ssoResp.finalUrl.ifBlank { "${webVpnRootUrl()}/wengine-vpn/denied" },
                                reason = "学校 WebVPN 提示「未在本地用户库注册」，已为您准备校外兼容通道(wfw)与企业微信扫码",
                                isWebVpnDenied = true
                            )
                        }
                        if (ssoResp?.isMfa == true) {
                            invalidateSessionCache(channel)
                            return@withContext SessionCheckResult.NeedsAuth(
                                channel = channel,
                                resumeUrl = ssoResp.finalUrl,
                                reason = "学校统一认证需要完成二次安全验证（企业微信/短信）",
                                isMfa = true
                            )
                        }
                    }
                }
                SyncChannel.ON_CAMPUS -> {
                    val jwCookies = cookieStore.getCookieHeader("https://jw.gdou.edu.cn/").orEmpty()
                    val hasJwSession = jwCookies.contains("JSESSIONID=", ignoreCase = true) ||
                        jwCookies.contains("route=", ignoreCase = true)
                    if (!hasJwSession && !hasCasTgc) {
                        invalidateSessionCache(channel)
                        return@withContext SessionCheckResult.NeedsAuth(
                            channel = channel,
                            resumeUrl = channel.authStartUrl(),
                            reason = "请完成广东海洋大学统一身份认证"
                        )
                    }
                }
                SyncChannel.WFW_LEGACY -> {
                    ensureWfwJwProxyCookies()
                    val wfwCookies = cookieStore.getCookieHeader("${wfwRootUrl()}/").orEmpty()
                    val hasJwSession = wfwCookies.contains("JSESSIONID=", ignoreCase = true)
                    if (!hasJwSession && !hasCasTgc) {
                        invalidateSessionCache(channel)
                        return@withContext SessionCheckResult.NeedsAuth(
                            channel = channel,
                            resumeUrl = channel.authStartUrl(),
                            reason = "请完成广东海洋大学统一身份认证"
                        )
                    }
                    if (!hasJwSession && hasCasTgc) {
                        val mfaStop = establishWfwSessionViaCas()
                        if (mfaStop != null) {
                            invalidateSessionCache(channel)
                            return@withContext mfaStop
                        }
                    }
                }
            }
        }

        val url = buildJwUrl(channel, "/xtgl/index_initMenu.html?jsdm=xs")
        var resp = if (channel == SyncChannel.ON_CAMPUS) {
            executeHttp(urlStr = url, method = "GET", channel = channel, timeoutMs = 4_500, maxAttempts = 2)
        } else {
            executeHttp(urlStr = url, method = "GET", channel = channel, timeoutMs = 10_000)
        }

        // Headless CAS ST renewal if JW JSESSIONID (or outer WebVPN ticket) expired while first-party CASTGC is still valid
        val initialExpired = !resp.networkError && !resp.isMfa && !resp.isWebVpnDenied &&
            (resp.authExpired || isAuthExpiredStatus(resp.statusCode) || isAuthExpiredHtml(resp.body))
        if (initialExpired && casBridgeEnabled() && hasDirectCasTgc()) {
            engineLog(msg = "verifyInitialExpired ch=${channel.name} code=${resp.statusCode} final=${sanitizeUrlForLog(resp.finalUrl)} -> casRenew")
            when (channel) {
                SyncChannel.WFW_LEGACY -> {
                    val mfaStop = establishWfwSessionViaCas()
                    if (mfaStop != null) {
                        invalidateSessionCache(channel)
                        return@withContext mfaStop
                    }
                    resp = executeHttp(urlStr = url, method = "GET", channel = channel, timeoutMs = 10_000)
                }
                SyncChannel.WEBVPN -> {
                    val bouncedToVpnLogin = GdouSyncScript.extractHostLower(resp.finalUrl) ==
                        GdouSyncScript.extractHostLower(webVpnRootUrl()) &&
                        try { URI(resp.finalUrl).path.orEmpty().trimEnd('/') == "/login" } catch (_: Exception) { false }
                    if (bouncedToVpnLogin || !hasWebVpnTicketCookie()) {
                        clearStaleWebVpnTicket()
                        val vpnResp = ensureWebVpnTicketFromCas()
                        if (vpnResp?.isWebVpnDenied == true) {
                            clearStaleWebVpnTicket()
                            invalidateSessionCache(channel)
                            if (allowAutoWfwFailover && hasDirectCasTgc()) {
                                val fallback = establishOrVerifySession(
                                    channel = SyncChannel.WFW_LEGACY,
                                    forceReverify = true,
                                    allowAutoWfwFailover = false
                                )
                                if (fallback is SessionCheckResult.Ready) {
                                    return@withContext fallback
                                }
                            }
                            return@withContext SessionCheckResult.NeedsAuth(
                                channel = channel,
                                resumeUrl = vpnResp.finalUrl.ifBlank { "${webVpnRootUrl()}/wengine-vpn/denied" },
                                reason = "学校 WebVPN 提示「未在本地用户库注册」，已为您准备校外兼容通道(wfw)与企业微信扫码",
                                isWebVpnDenied = true
                            )
                        }
                        if (vpnResp?.isMfa == true) {
                            invalidateSessionCache(channel)
                            return@withContext SessionCheckResult.NeedsAuth(
                                channel = channel,
                                resumeUrl = vpnResp.finalUrl,
                                reason = "学校统一认证需要完成二次安全验证（企业微信/短信）",
                                isMfa = true
                            )
                        }
                    }
                    when (val jwTicket = mintCasServiceTicket(GdouSyncScript.URL_JW_SSO_SERVICE)) {
                        is CasTicketMintResult.Ticket -> {
                            val redeemResp = redeemJwCasTicket(SyncChannel.WEBVPN, jwTicket.ticket)
                            if (redeemResp.isMfa) {
                                invalidateSessionCache(channel)
                                return@withContext SessionCheckResult.NeedsAuth(
                                    channel = channel,
                                    resumeUrl = redeemResp.finalUrl,
                                    reason = "学校统一认证需要完成二次安全验证（企业微信/短信）",
                                    isMfa = true
                                )
                            }
                            resp = executeHttp(urlStr = url, method = "GET", channel = channel, timeoutMs = 10_000)
                        }
                        is CasTicketMintResult.Mfa -> {
                            invalidateSessionCache(channel)
                            return@withContext SessionCheckResult.NeedsAuth(
                                channel = channel,
                                resumeUrl = jwTicket.mfaUrl,
                                reason = "学校统一认证需要完成二次安全验证（企业微信/短信）",
                                isMfa = true
                            )
                        }
                        else -> {}
                    }
                }
                SyncChannel.ON_CAMPUS -> {
                    when (val jwTicket = mintCasServiceTicket(GdouSyncScript.URL_JW_SSO_SERVICE)) {
                        is CasTicketMintResult.Ticket -> {
                            redeemJwCasTicket(SyncChannel.ON_CAMPUS, jwTicket.ticket)
                            resp = executeHttp(urlStr = url, method = "GET", channel = channel, timeoutMs = 10_000)
                        }
                        is CasTicketMintResult.Mfa -> {
                            invalidateSessionCache(channel)
                            return@withContext SessionCheckResult.NeedsAuth(
                                channel = channel,
                                resumeUrl = jwTicket.mfaUrl,
                                reason = "学校统一认证需要完成二次安全验证（企业微信/短信）",
                                isMfa = true
                            )
                        }
                        else -> {}
                    }
                }
            }
        }

        val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)

        if (resp.networkError) {
            engineLog(warn = true, msg = "verifyEnd ch=${channel.name} NetworkError(${elapsed}ms): ${resp.errorDetail}")
            return@withContext SessionCheckResult.NetworkError(
                channel = channel,
                message = resp.errorDetail.ifBlank { "暂未连通学校教务服务器，请检查网络或切换通道" }
            )
        }
        if (resp.isWebVpnDenied) {
            clearStaleWebVpnTicket()
            invalidateSessionCache(channel)
            if (allowAutoWfwFailover && casBridgeEnabled() && hasDirectCasTgc()) {
                val fallback = establishOrVerifySession(
                    channel = SyncChannel.WFW_LEGACY,
                    forceReverify = true,
                    allowAutoWfwFailover = false
                )
                if (fallback is SessionCheckResult.Ready) {
                    return@withContext fallback
                }
            }
            engineLog(warn = true, msg = "verifyEnd ch=${channel.name} NeedsAuth(WebVPN denied, ${elapsed}ms)")
            return@withContext SessionCheckResult.NeedsAuth(
                channel = channel,
                resumeUrl = resp.finalUrl.ifBlank { "${webVpnRootUrl()}/wengine-vpn/denied" },
                reason = "学校 WebVPN 提示「未在本地用户库注册」，可一键切校外兼容通道(wfw)或企业微信扫码",
                isWebVpnDenied = true
            )
        }
        if (resp.isMfa) {
            invalidateSessionCache(channel)
            engineLog(msg = "verifyEnd ch=${channel.name} NeedsAuth(MFA, ${elapsed}ms) final=${sanitizeUrlForLog(resp.finalUrl)}")
            return@withContext SessionCheckResult.NeedsAuth(
                channel = channel,
                resumeUrl = resp.finalUrl,
                reason = "学校统一认证需要完成二次安全验证（企业微信/短信）",
                isMfa = true
            )
        }
        if (resp.authExpired || isAuthExpiredStatus(resp.statusCode) || isAuthExpiredHtml(resp.body)) {
            if (channel == SyncChannel.WEBVPN) {
                val vpnHost = GdouSyncScript.extractHostLower(webVpnRootUrl())
                val finalHost = GdouSyncScript.extractHostLower(resp.finalUrl)
                val lowerFinal = resp.finalUrl.lowercase(Locale.US)
                val finalPath = try { URI(resp.finalUrl).path.orEmpty().trimEnd('/') } catch (_: Exception) { "" }
                val proxiedVpnAuthServer = finalHost == vpnHost && lowerFinal.contains("/authserver/")
                val bouncedToVpnGate = finalHost == vpnHost && (
                    finalPath == "/login" ||
                        lowerFinal.contains("/wengine-vpn/denied") ||
                        lowerFinal.contains("/wengine-vpn/failed")
                    )
                // Keep a valid Enterprise WeChat WebVPN ticket when WebVPN proxies jw's /authserver/ gate;
                // only clear wengine_vpn_ticket when bounced back to WebVPN /login / denied / failed or non-proxied auth.
                if (!proxiedVpnAuthServer && (bouncedToVpnGate || !hasDirectCasTgc() || finalHost == vpnHost)) {
                    clearStaleWebVpnTicket()
                }
            }
            invalidateSessionCache(channel)
            val resume = resolveInteractiveResumeUrl(channel, resp.finalUrl)
            engineLog(
                warn = true,
                msg = "verifyEnd ch=${channel.name} NeedsAuth(${elapsed}ms) code=${resp.statusCode} final=${sanitizeUrlForLog(resp.finalUrl)} resume=${sanitizeUrlForLog(resume)} ${cookieFlagsSummary()}"
            )
            return@withContext SessionCheckResult.NeedsAuth(
                channel = channel,
                resumeUrl = resume,
                reason = "请完成广东海洋大学统一身份认证后即可高速同步"
            )
        }
        if (resp.statusCode in 200..299 && resp.body.isNotBlank()) {
            val discovered = parseMenuEndpoints(resp.body)
            if (discovered.dynamicLinks.isEmpty()) {
                val hasClick = resp.body.contains("clickMenu", ignoreCase = true)
                val menuAttrSamples = Regex("""(?:onclick|href|data-[a-z0-9-]+)\s*=\s*["'][^"']*(?:kbcx|cjcx|kwgl|cdjy|gnmkdm|Menu|\.html)[^"']*["']""", RegexOption.IGNORE_CASE)
                    .findAll(resp.body)
                    .map { it.value.replace(Regex("[\\u4e00-\\u9fa5]"), "*").take(90) }
                    .distinct()
                    .take(4)
                    .joinToString(" | ")
                engineLog(
                    warn = true,
                    msg = "initMenuZeroLinks ch=${channel.name} len=${resp.body.length} clickMenu=$hasClick attrs=[$menuAttrSamples]"
                )
            } else {
                engineLog(msg = "initMenuParsed ch=${channel.name} links=${discovered.dynamicLinks.size}")
            }
            menuCache[channel] = discovered
            lastVerifiedAt[channel] = System.currentTimeMillis()
            lastReadyChannel = channel
            cookieStore.flushAsync()
            engineLog(msg = "verifyEnd ch=${channel.name} Ready(${elapsed}ms) links=${discovered.dynamicLinks.size} ${cookieFlagsSummary()}")
            return@withContext SessionCheckResult.Ready(channel, discovered, elapsed)
        }
        engineLog(warn = true, msg = "verifyEnd ch=${channel.name} HTTP_${resp.statusCode}(${elapsed}ms) bodyLen=${resp.body.length}")
        SessionCheckResult.NetworkError(
            channel = channel,
            message = "教务服务响应异常（HTTP ${resp.statusCode}），请稍后重试"
        )
    }

    private suspend fun trySilentCasRenew(channel: SyncChannel): SessionCheckResult? {
        if (!casBridgeEnabled() || !hasDirectCasTgc()) return null
        engineLog(msg = "silentCasRenew start ch=${channel.name}")
        invalidateSessionCache(channel)
        return establishOrVerifySession(channel, forceReverify = true, allowAutoWfwFailover = false)
    }

    private suspend fun <T> withSilentCasRetry(
        channel: SyncChannel,
        isRetryableAuthExpired: (T) -> Boolean,
        block: suspend () -> T
    ): T {
        val first = block()
        if (!isRetryableAuthExpired(first)) return first
        val renewed = trySilentCasRenew(channel) ?: return first
        return if (renewed is SessionCheckResult.Ready) block() else first
    }

    /**
     * 拉取正方可选学年与学期列表（零网页渲染 + 10 分钟内存缓存，二次点击 0ms 瞬弹！）
     */
    suspend fun fetchAcademicOptions(
        channel: SyncChannel,
        forceRefresh: Boolean = false
    ): NativeCallResult<TermOptionsPayload> = withSilentCasRetry(
        channel = channel,
        isRetryableAuthExpired = { res ->
            res is NativeCallResult.AuthExpired && !res.isMfa && !res.isWebVpnDenied
        }
    ) {
        withContext(Dispatchers.IO) {
            if (!forceRefresh) {
                val cached = termOptionsCache[channel]
                if (cached != null && !cached.second.isFallback && System.currentTimeMillis() - cached.first < 10 * 60_000L) {
                    return@withContext NativeCallResult.Success(cached.second)
                }
            }
            val menu = ensureMenuEndpoints(channel)
            val eps = LinkedHashSet<String>().apply {
                add("/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default")
                addAll(menu.scheduleIndexEps)
            }
            var authOutcome: NativeCallResult.AuthExpired? = null
            var evalHint: String? = null
            var sawNetworkError = false

            for (ep in eps) {
                val resp = executeHttp(buildJwUrl(channel, ep), "GET", channel)
                if (resp.networkError) {
                    sawNetworkError = true
                    continue
                }
                if (resp.isWebVpnDenied || resp.isMfa || resp.authExpired || isAuthExpiredStatus(resp.statusCode)) {
                    invalidateSessionCache(channel)
                    authOutcome = NativeCallResult.AuthExpired(
                        resumeUrl = resolveInteractiveResumeUrl(channel, resp.finalUrl),
                        message = if (resp.isWebVpnDenied) "学校 WebVPN 提示未在本地用户库注册"
                        else if (resp.isMfa) "需要完成统一认证二次验证"
                        else "教务登录态已过期，请重新登录后同步",
                        isMfa = resp.isMfa,
                        isWebVpnDenied = resp.isWebVpnDenied
                    )
                    continue
                }
                if (resp.body.isNotBlank() && isEvalInterceptHtml(resp.body)) {
                    evalHint = classifyNonJsonHtml(resp.body, "")
                    continue
                }
                if (resp.body.isNotBlank() && isAuthExpiredHtml(resp.body)) {
                    invalidateSessionCache(channel)
                    authOutcome = NativeCallResult.AuthExpired(
                        resumeUrl = resolveInteractiveResumeUrl(channel, resp.finalUrl),
                        message = "教务登录态已过期，请重新登录后同步"
                    )
                    continue
                }
                if (resp.statusCode in 200..299 && resp.body.isNotBlank()) {
                    val yearSel = parseSelectOptionsFromHtml(resp.body, "xnm")
                    val semSel = parseSelectOptionsFromHtml(resp.body, "xqm")
                    if (yearSel.options.isNotEmpty() && semSel.options.isNotEmpty()) {
                        val idx = yearSel.defaultIndex.coerceIn(0, yearSel.options.lastIndex)
                        val start = (idx - 3).coerceAtLeast(0)
                        val end = (idx + 3).coerceAtMost(yearSel.options.size)
                        val slicedYears = yearSel.options.subList(start, end)
                        val prettySems = semSel.options.map { opt ->
                            TermOption(opt.value, formatSemesterText(opt.value, opt.text))
                        }
                        lastVerifiedAt[channel] = System.currentTimeMillis()
                        val payload = TermOptionsPayload(
                            yearOptions = slicedYears,
                            semesterOptions = prettySems,
                            defaultYearIndex = (idx - start).coerceIn(0, (slicedYears.size - 1).coerceAtLeast(0)),
                            defaultSemesterIndex = semSel.defaultIndex.coerceIn(0, (prettySems.size - 1).coerceAtLeast(0)),
                            isFallback = false
                        )
                        termOptionsCache[channel] = System.currentTimeMillis() to payload
                        return@withContext NativeCallResult.Success(payload)
                    }
                }
            }

            if (evalHint != null) return@withContext NativeCallResult.EvalIntercept(evalHint)
            if (authOutcome != null) return@withContext authOutcome
            if (sawNetworkError) {
                return@withContext NativeCallResult.Failure("网络波动或暂未连通学校教务，请检查网络或切换通道", isNetworkError = true)
            }
            // Fallback heuristic options if server omitted select dropdowns while authenticated
            val defY = getDefaultAcademicYear()
            val isFirst = isDefaultFirstSemester()
            NativeCallResult.Success(
                TermOptionsPayload(
                    yearOptions = listOf(
                        TermOption(defY.toString(), "$defY-${defY + 1}"),
                        TermOption((defY - 1).toString(), "${defY - 1}-$defY"),
                        TermOption((defY - 2).toString(), "${defY - 2}-${defY - 1}")
                    ),
                    semesterOptions = listOf(
                        TermOption("3", "第一学期"),
                        TermOption("12", "第二学期")
                    ),
                    defaultYearIndex = 0,
                    defaultSemesterIndex = if (isFirst) 0 else 1,
                    isFallback = true
                )
            )
        }
    }

    /**
     * 后台静默预热学年学期选项，点击「一键全同步」时 0ms 瞬开学期选择器
     */
    suspend fun prefetchAcademicOptionsQuietly(channel: SyncChannel) {
        val cached = termOptionsCache[channel]
        if (cached != null && !cached.second.isFallback && System.currentTimeMillis() - cached.first < 10 * 60_000L) return
        try {
            fetchAcademicOptions(channel, forceRefresh = false)
        } catch (_: Throwable) {}
    }

    /**
     * 并发全量同步（课表 + 校历 + 全部历史成绩/等级考 + 考试安排）
     */
    suspend fun syncModules(
        channel: SyncChannel,
        mode: String,
        academicYear: String,
        semesterCode: String,
        termLabel: String,
        onProgress: (String) -> Unit = {}
    ): FullSyncNativeOutcome = withSilentCasRetry(
        channel = channel,
        isRetryableAuthExpired = { out ->
            out.authExpired != null &&
                out.summaryText.isBlank() &&
                !out.authExpired.isMfa &&
                !out.authExpired.isWebVpnDenied
        }
    ) {
        withContext(Dispatchers.IO) {
        if (mode == "all") {
            onProgress("正在并发同步课表、成绩与考试...")
            ensureMenuEndpoints(channel)
            val (cRes, gRes, eRes) = coroutineScope {
                val cDef = async { syncCoursesAndCalendar(channel, academicYear, semesterCode) }
                val gDef = async { syncGradesAndLevelExams(channel) }
                val eDef = async { syncExams(channel, academicYear, semesterCode) }
                Triple(cDef.await(), gDef.await(), eDef.await())
            }
            val summary = mutableListOf<String>()
            val failures = mutableListOf<String>()
            var okModules = 0
            var authExpired: NativeCallResult.AuthExpired? = null
            var evalMsg: String? = null

            when (cRes) {
                is NativeCallResult.Success -> {
                    okModules++
                    val pCount = cRes.data.practiceCourses.size
                    summary.add("课表 ${cRes.data.courses.size} 条" + if (pCount > 0) "(含实践 $pCount 门)" else "")
                }
                is NativeCallResult.AuthExpired -> {
                    authExpired = cRes
                    failures.add(cRes.message)
                    summary.add("课表未获取到(${cRes.message})")
                }
                is NativeCallResult.EvalIntercept -> {
                    evalMsg = cRes.message
                    failures.add(cRes.message)
                    summary.add("课表未获取到(${cRes.message})")
                }
                is NativeCallResult.Failure -> {
                    failures.add(cRes.message)
                    summary.add("课表未获取到(${cRes.message})")
                }
            }

            when (gRes) {
                is NativeCallResult.Success -> {
                    okModules++
                    val lvCount = gRes.data.levelExams.size
                    summary.add("成绩 ${gRes.data.grades.size} 门" + if (lvCount > 0) " · 等级考 $lvCount 项" else "")
                }
                is NativeCallResult.AuthExpired -> {
                    if (authExpired == null) authExpired = gRes
                    failures.add(gRes.message)
                    summary.add("成绩跳过(${gRes.message})")
                }
                is NativeCallResult.EvalIntercept -> {
                    if (evalMsg == null) evalMsg = gRes.message
                    failures.add(gRes.message)
                    summary.add("成绩跳过(${gRes.message})")
                }
                is NativeCallResult.Failure -> {
                    failures.add(gRes.message)
                    summary.add("成绩跳过(${gRes.message})")
                }
            }

            when (eRes) {
                is NativeCallResult.Success -> {
                    okModules++
                    summary.add("考试 ${eRes.data.size} 场")
                }
                is NativeCallResult.AuthExpired -> {
                    if (authExpired == null) authExpired = eRes
                    failures.add(eRes.message)
                    summary.add("考试跳过(${eRes.message})")
                }
                is NativeCallResult.EvalIntercept -> {
                    if (evalMsg == null) evalMsg = eRes.message
                    failures.add(eRes.message)
                    summary.add("考试跳过(${eRes.message})")
                }
                is NativeCallResult.Failure -> {
                    failures.add(eRes.message)
                    summary.add("考试跳过(${eRes.message})")
                }
            }

            cookieStore.flushAsync()
            return@withContext if (okModules == 0) {
                FullSyncNativeOutcome(
                    coursesResult = cRes,
                    gradesResult = gRes,
                    examsResult = eRes,
                    errorText = failures.firstOrNull() ?: "未获取到教务数据，请确认已完成登录或教学评价后重试",
                    authExpired = authExpired,
                    evalInterceptMessage = evalMsg
                )
            } else {
                FullSyncNativeOutcome(
                    coursesResult = cRes,
                    gradesResult = gRes,
                    examsResult = eRes,
                    summaryText = "同步完成：" + summary.joinToString(" · "),
                    evalInterceptMessage = evalMsg
                )
            }
        }

        // Single-module sync modes
        when (mode) {
            "courses" -> {
                onProgress("正在同步课表与校历...")
                when (val cRes = syncCoursesAndCalendar(channel, academicYear, semesterCode)) {
                    is NativeCallResult.Success -> {
                        cookieStore.flushAsync()
                        val pCount = cRes.data.practiceCourses.size
                        FullSyncNativeOutcome(
                            coursesResult = cRes,
                            summaryText = "同步完成：课表 ${cRes.data.courses.size} 条" + if (pCount > 0) "(含实践 $pCount 门)" else ""
                        )
                    }
                    is NativeCallResult.AuthExpired -> FullSyncNativeOutcome(coursesResult = cRes, errorText = cRes.message, authExpired = cRes)
                    is NativeCallResult.EvalIntercept -> FullSyncNativeOutcome(coursesResult = cRes, errorText = cRes.message, evalInterceptMessage = cRes.message)
                    is NativeCallResult.Failure -> FullSyncNativeOutcome(coursesResult = cRes, errorText = cRes.message)
                }
            }
            "grades" -> {
                onProgress("正在同步全部历史成绩与绩点...")
                when (val gRes = syncGradesAndLevelExams(channel)) {
                    is NativeCallResult.Success -> {
                        cookieStore.flushAsync()
                        val lvCount = gRes.data.levelExams.size
                        FullSyncNativeOutcome(
                            gradesResult = gRes,
                            summaryText = "同步完成：成绩 ${gRes.data.grades.size} 门" + if (lvCount > 0) " · 等级考 $lvCount 项" else ""
                        )
                    }
                    is NativeCallResult.AuthExpired -> FullSyncNativeOutcome(gradesResult = gRes, errorText = gRes.message, authExpired = gRes)
                    is NativeCallResult.EvalIntercept -> FullSyncNativeOutcome(gradesResult = gRes, errorText = gRes.message, evalInterceptMessage = gRes.message)
                    is NativeCallResult.Failure -> FullSyncNativeOutcome(gradesResult = gRes, errorText = gRes.message)
                }
            }
            else -> {
                onProgress("正在同步考试时间与考场座位...")
                when (val eRes = syncExams(channel, academicYear, semesterCode)) {
                    is NativeCallResult.Success -> {
                        cookieStore.flushAsync()
                        FullSyncNativeOutcome(
                            examsResult = eRes,
                            summaryText = "同步完成：考试 ${eRes.data.size} 场"
                        )
                    }
                    is NativeCallResult.AuthExpired -> FullSyncNativeOutcome(examsResult = eRes, errorText = eRes.message, authExpired = eRes)
                    is NativeCallResult.EvalIntercept -> FullSyncNativeOutcome(examsResult = eRes, errorText = eRes.message, evalInterceptMessage = eRes.message)
                    is NativeCallResult.Failure -> FullSyncNativeOutcome(examsResult = eRes, errorText = eRes.message)
                }
            }
        }
        }
    }

    /**
     * 首页「刷新课表 · 检查调课」纯原生引擎入口：
     * 0 WebView 启动！0 主线程卡顿！纯蜂窝流量 0ms 秒切校外通道，校园网不通自动无缝探切 WebVPN；
     * 会话过期自动纯 HTTP 跟 CAS 302 静默续期。
     */
    suspend fun quickRefreshCourses(
        context: Context?,
        preferredChannel: SyncChannel,
        savedYear: String,
        savedSem: String,
        savedTermLabel: String,
        preferLegacyWfw: Boolean = false,
        onProgress: (String) -> Unit = {}
    ): QuickRefreshNativeOutcome = withContext(Dispatchers.IO) {
        val offCampusTarget = SyncChannel.preferredOffCampus(preferLegacyWfw)
        var activeChannel = when {
            preferredChannel == SyncChannel.WEBVPN && preferLegacyWfw -> SyncChannel.WFW_LEGACY
            preferredChannel == SyncChannel.ON_CAMPUS &&
                context != null &&
                customBaseUrlOverride.isNullOrBlank() &&
                NetworkChannelProbe.shouldPreferOffCampusImmediately(context) -> offCampusTarget
            else -> preferredChannel
        }
        onProgress("正在校验教务连接...")
        var session = establishOrVerifySession(activeChannel, forceReverify = false)

        // If on-campus direct failed due to network error, probe and seamlessly switch off-campus
        if (session is SessionCheckResult.NetworkError && activeChannel == SyncChannel.ON_CAMPUS && context != null) {
            onProgress("直连受限，正在切至校外通道...")
            val probe = NetworkChannelProbe.probe(context)
            if (probe.state == CampusNetworkState.OFF_CAMPUS_ONLY) {
                activeChannel = offCampusTarget
                session = establishOrVerifySession(activeChannel, forceReverify = true)
            } else if (probe.state == CampusNetworkState.OFFLINE_OR_BLOCKED) {
                return@withContext QuickRefreshNativeOutcome.Error("当前网络暂未连通学校服务器，请检查流量或先连接校园网")
            }
        }

        // Prevent popping dead ON_CAMPUS login sheet on external non-campus Wi-Fi / VPN when cookies are empty
        if (
            session is SessionCheckResult.NeedsAuth &&
            session.channel == SyncChannel.ON_CAMPUS &&
            context != null &&
            customBaseUrlOverride.isNullOrBlank()
        ) {
            val probe = NetworkChannelProbe.probeCached(context)
            if (probe.state == CampusNetworkState.OFF_CAMPUS_ONLY) {
                onProgress("已识别校外网络，正在切换至 WebVPN 通道...")
                activeChannel = offCampusTarget
                session = establishOrVerifySession(activeChannel, forceReverify = false)
            } else if (probe.state == CampusNetworkState.OFFLINE_OR_BLOCKED) {
                return@withContext QuickRefreshNativeOutcome.Error("当前网络暂未连通学校服务器，请检查流量或先连接校园网")
            }
        }

        when (session) {
            is SessionCheckResult.NeedsAuth -> {
                return@withContext QuickRefreshNativeOutcome.NeedsLogin(
                    channel = session.channel,
                    resumeUrl = session.resumeUrl,
                    reason = session.reason,
                    isMfa = session.isMfa,
                    isWebVpnDenied = session.isWebVpnDenied
                )
            }
            is SessionCheckResult.NetworkError -> {
                return@withContext QuickRefreshNativeOutcome.Error(session.message)
            }
            is SessionCheckResult.Ready -> {
                activeChannel = session.channel
            }
        }

        var targetYear = savedYear.trim()
        var targetSem = savedSem.trim()
        var targetLabel = savedTermLabel.trim()
        if (targetYear.isEmpty() || targetSem.isEmpty()) {
            onProgress("正在读取当前学年学期...")
            when (val opts = fetchAcademicOptions(activeChannel)) {
                is NativeCallResult.Success -> {
                    val yOpt = opts.data.yearOptions.getOrNull(opts.data.defaultYearIndex) ?: opts.data.yearOptions.firstOrNull()
                    val sOpt = opts.data.semesterOptions.getOrNull(opts.data.defaultSemesterIndex) ?: opts.data.semesterOptions.firstOrNull()
                    if (yOpt != null && sOpt != null) {
                        targetYear = yOpt.value
                        targetSem = sOpt.value
                        targetLabel = "${yOpt.text} ${sOpt.text}"
                    }
                }
                is NativeCallResult.AuthExpired -> {
                    return@withContext QuickRefreshNativeOutcome.NeedsLogin(
                        channel = activeChannel,
                        resumeUrl = opts.resumeUrl,
                        reason = opts.message,
                        isMfa = opts.isMfa,
                        isWebVpnDenied = opts.isWebVpnDenied
                    )
                }
                is NativeCallResult.EvalIntercept -> {
                    return@withContext QuickRefreshNativeOutcome.Error(opts.message, isEvalIntercept = true)
                }
                is NativeCallResult.Failure -> {
                    val defY = getDefaultAcademicYear()
                    val isFirst = isDefaultFirstSemester()
                    targetYear = defY.toString()
                    targetSem = if (isFirst) "3" else "12"
                    targetLabel = "$defY-${defY + 1} " + if (isFirst) "第一学期" else "第二学期"
                }
            }
        }

        onProgress("正在极速拉取最新课表与校历...")
        when (val cRes = syncCoursesAndCalendar(activeChannel, targetYear, targetSem)) {
            is NativeCallResult.Success -> {
                cookieStore.flushAsync()
                QuickRefreshNativeOutcome.Success(
                    channel = activeChannel,
                    courses = cRes.data.courses,
                    practiceCourses = cRes.data.practiceCourses,
                    startDate = cRes.data.startDate,
                    totalWeeks = cRes.data.totalWeeks,
                    termLabel = targetLabel,
                    yearCode = targetYear,
                    semesterCode = targetSem,
                    detectedCampusId = cRes.data.detectedCampusId,
                    summary = "课表已刷新：最新 ${cRes.data.courses.size} 条课程（已自动保留手动调课）"
                )
            }
            is NativeCallResult.AuthExpired -> {
                QuickRefreshNativeOutcome.NeedsLogin(
                    channel = activeChannel,
                    resumeUrl = cRes.resumeUrl,
                    reason = cRes.message,
                    isMfa = cRes.isMfa,
                    isWebVpnDenied = cRes.isWebVpnDenied
                )
            }
            is NativeCallResult.EvalIntercept -> QuickRefreshNativeOutcome.Error(cRes.message, isEvalIntercept = true)
            is NativeCallResult.Failure -> QuickRefreshNativeOutcome.Error(cRes.message)
        }
    }

    suspend fun syncCoursesAndCalendar(
        channel: SyncChannel,
        academicYear: String,
        semesterCode: String
    ): NativeCallResult<SyncedCoursesNativeResult> = withContext(Dispatchers.IO) {
        val formBody = "xnm=${urlEncode(academicYear)}&xqm=${urlEncode(semesterCode)}&kzlx=ck&xsdm=&kclbdm="
        var (kbResp, semInfo) = coroutineScope {
            val kbDef = async {
                executeHttp(
                    urlStr = buildJwUrl(channel, "/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151"),
                    method = "POST",
                    channel = channel,
                    formBody = formBody,
                    expectJson = true
                )
            }
            val semDef = async { fetchSemesterInfo(channel, academicYear, semesterCode) }
            kbDef.await() to semDef.await()
        }

        val kbTrimmed = kbResp.body.trim()
        val kbIsJson = kbResp.statusCode in 200..299 && (kbTrimmed.startsWith("{") || kbTrimmed.startsWith("["))
        if (!kbIsJson && !kbResp.authExpired && !kbResp.isMfa && !kbResp.isWebVpnDenied && !kbResp.networkError) {
            // Pre-warm schedule index once and retry discovered endpoints
            executeHttp(buildJwUrl(channel, "/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default"), "GET", channel)
            val discovered = ensureMenuEndpoints(channel)
            val retryEps = LinkedHashSet<String>().apply {
                add("/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151")
                addAll(discovered.scheduleDataEps)
            }
            for (ep in retryEps) {
                val retried = executeHttp(buildJwUrl(channel, ep), "POST", channel, formBody = formBody, expectJson = true)
                val t = retried.body.trim()
                if ((retried.statusCode in 200..299 && (t.startsWith("{") || t.startsWith("["))) ||
                    retried.authExpired || retried.isMfa || retried.isWebVpnDenied
                ) {
                    kbResp = retried
                    break
                }
            }
        }

        if (kbResp.isWebVpnDenied || kbResp.isMfa || kbResp.authExpired || isAuthExpiredStatus(kbResp.statusCode)) {
            invalidateSessionCache(channel)
            return@withContext NativeCallResult.AuthExpired(
                resumeUrl = resolveInteractiveResumeUrl(channel, kbResp.finalUrl),
                message = if (kbResp.isMfa) "需要完成二次安全验证" else "教务登录态已失效，请重新登录",
                isMfa = kbResp.isMfa,
                isWebVpnDenied = kbResp.isWebVpnDenied
            )
        }
        if (kbResp.networkError) {
            return@withContext NativeCallResult.Failure("网络波动或暂未连通教务，请检查网络后重试", isNetworkError = true)
        }
        val raw = kbResp.body
        val trimmed = raw.trim()
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
            if (isEvalInterceptHtml(raw)) {
                return@withContext NativeCallResult.EvalIntercept(classifyNonJsonHtml(raw, ""))
            }
            if (isAuthExpiredHtml(raw)) {
                invalidateSessionCache(channel)
                return@withContext NativeCallResult.AuthExpired(
                    resumeUrl = resolveInteractiveResumeUrl(channel, kbResp.finalUrl),
                    message = "登录状态已过期，请重新登录"
                )
            }
            return@withContext NativeCallResult.Failure(
                classifyNonJsonHtml(raw, "教务当前未返回课表数据，请确认已完成登录或评教后重试")
            )
        }

        val root = try {
            json.parseToJsonElement(trimmed) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: return@withContext NativeCallResult.Failure("教务课表数据格式异常，请稍后重试")

        val kbList = (root["kbList"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        val sjkList = (root["sjkList"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

        val rawCourses = mutableListOf<CourseItem>()
        for (item in kbList) {
            val kcmc = item.str("kcmc")
            val xqj = item.str("xqj")
            val jcs = item.str("jcs")
            val zcd = item.str("zcd")
            if (kcmc.isEmpty() || xqj.isEmpty() || jcs.isEmpty() || zcd.isEmpty()) continue
            val weeks = parseWeeks(zcd)
            val sec = parseSectionRange(jcs) ?: continue
            val day = parseDayOfWeek(xqj) ?: continue
            if (weeks.isEmpty()) continue
            rawCourses.add(
                CourseItem(
                    name = normalizeCourseName(kcmc),
                    teacher = item.str("xm"),
                    location = item.str("cdmc"),
                    dayOfWeek = day,
                    startSection = sec.first,
                    endSection = sec.second,
                    weeks = weeks,
                    credit = item.str("xf"),
                    courseType = item.str("kcxzmc"),
                    examMethod = item.str("khfsmc"),
                    classGroup = item.str("jxbmc"),
                    totalHours = item.str("zxs"),
                    note = normalizeCourseRemark(item.str("xkbz")),
                    isManual = false
                )
            )
        }

        val practiceCourses = mutableListOf<PracticeCourseItem>()
        for (item in sjkList) {
            val pName = normalizeCourseName(item.str("kcmc").ifEmpty { item.str("sjkcgs") })
            if (pName.isEmpty()) continue
            val wTxt = item.str("qsjsz").ifEmpty { item.str("zcd") }
            val noteParts = mutableListOf<String>()
            if (item.str("sjkcgs").isNotEmpty() && item.str("kcmc").isNotEmpty()) {
                val desc = normalizeCourseRemark(item.str("sjkcgs"))
                if (desc.isNotEmpty()) noteParts.add(desc)
            }
            val pLoc = normalizeCourseRemark(item.str("cdmc"))
            if (pLoc.isNotEmpty()) noteParts.add("地点:$pLoc")
            val pHours = normalizeCourseRemark(item.str("zxs"))
            if (pHours.isNotEmpty()) noteParts.add("${pHours}学时")
            practiceCourses.add(
                PracticeCourseItem(
                    name = pName,
                    teacher = item.str("jsxm").ifEmpty { item.str("xm") },
                    weeksText = wTxt,
                    weeks = parseWeeks(wTxt),
                    credit = item.str("xf"),
                    courseType = item.str("kcxzmc").ifEmpty { "实践环节" },
                    note = noteParts.joinToString(" · ")
                )
            )
        }

        val merged = mergeAndDistinctCourses(rawCourses)
        if (merged.isEmpty() && practiceCourses.isEmpty()) {
            return@withContext NativeCallResult.Failure("该学年学期未查询到课程，请核对学年学期选择")
        }
        val maxWeek = merged.flatMap { it.weeks }.maxOrNull() ?: 16
        val totalWeeks = maxOf(maxWeek, semInfo?.second ?: 20)
        lastVerifiedAt[channel] = System.currentTimeMillis()
        NativeCallResult.Success(
            SyncedCoursesNativeResult(
                courses = merged,
                practiceCourses = practiceCourses,
                startDate = semInfo?.first,
                totalWeeks = totalWeeks,
                detectedCampusId = detectCampusFromKbList(kbList)
            )
        )
    }

    suspend fun syncGradesAndLevelExams(channel: SyncChannel): NativeCallResult<SyncedGradesNativeResult> =
        withContext(Dispatchers.IO) {
            coroutineScope {
                // Start level-exam query in parallel so it NEVER blocks starting the main grade query!
                val lvDef = async { syncLevelExamsGentle(channel) }
                val wDef = async {
                    executeHttp(buildJwUrl(channel, "/cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005&layout=default"), "GET", channel)
                }
                val mDef = async { ensureMenuEndpoints(channel) }
                val warmResp = wDef.await()
                val discovered = mDef.await()

                val pageDiscovered = mutableListOf<String>()
                if (warmResp.statusCode in 200..299 && warmResp.body.isNotBlank()) {
                    val epRe = Regex("""(/cjcx/cjcx_cx[A-Za-z0-9_]+\.html(?:\?[^'"\s)]*)?)""")
                    epRe.findAll(warmResp.body).forEach { m ->
                        val rawEp = stripProxyPrefix(m.groupValues[1])
                        if (!rawEp.contains("djkscx")) {
                            val sep = if (rawEp.contains("?")) "&" else "?"
                            pageDiscovered.add(if (rawEp.contains("doType=query")) rawEp else "$rawEp${sep}doType=query&gnmkdm=N305005")
                        }
                    }
                }

                val endpoints = LinkedHashSet<String>().apply {
                    addAll(discovered.gradeEps)
                    addAll(pageDiscovered)
                    add("/cjcx/cjcx_cxDcListByXs.html?doType=query&gnmkdm=N305005")
                    add("/cjcx/cjcx_cxXsgrcj.html?doType=query&gnmkdm=N305005")
                    add("/cjcx/cjcx_cxDgXscj.html?doType=query&gnmkdm=N305005")
                    add("/cjcx/cjcx_cxDcListByXs.html?doType=query&gnmkdm=N305007")
                    add("/cjcx/cjcx_cxXsgrcj.html?doType=query&gnmkdm=N305007")
                    add("/cjcx/cjcx_cxDgXscj.html?doType=query&gnmkdm=N305007")
                }

                suspend fun queryGradesOnce(ep: String, xnmVal: String, xqmVal: String): PaginatedOutcome {
                    return fetchPaginatedJwItems(
                        channel = channel,
                        ep = ep,
                        buildFormForPage = { pageNo ->
                            "xnm=${urlEncode(xnmVal)}&xqm=${urlEncode(xqmVal)}&kcbj=&_search=false&nd=${System.currentTimeMillis()}" +
                                "&queryModel.showCount=200&queryModel.currentPage=$pageNo&queryModel.sortName=&queryModel.sortOrder=asc&time=0"
                        },
                        maxPages = 10,
                        fallbackHint = "需先在教务页确认通知或完成评教"
                    )
                }

                val workingEps = mutableListOf<String>()
                var confirmedZeroTotal = false
                var lastHttpError = 0
                var htmlHint = ""
                var evalIntercept = false
                var authOutcome: NativeCallResult.AuthExpired? = null

                for (ep in endpoints) {
                    val res = queryGradesOnce(ep, "", "")
                    if (!res.ok) {
                        if (res.authExpired || res.isMfa || res.isWebVpnDenied) {
                            authOutcome = NativeCallResult.AuthExpired(
                                resumeUrl = resolveInteractiveResumeUrl(channel, res.resumeUrl),
                                message = if (res.isMfa) "需要完成二次验证" else "登录状态已过期，请重新登录",
                                isMfa = res.isMfa,
                                isWebVpnDenied = res.isWebVpnDenied
                            )
                            break
                        }
                        if (res.status >= 400) lastHttpError = res.status
                        if (res.isEvalIntercept) evalIntercept = true
                        if (htmlHint.isEmpty() && res.htmlHint.isNotEmpty()) htmlHint = res.htmlHint
                        continue
                    }
                    workingEps.add(ep)
                    if (res.hasTotalResult && res.totalResult == 0) {
                        confirmedZeroTotal = true
                    }
                    val mapped = mapGradeItems(res.items)
                    if (mapped.isNotEmpty()) {
                        return@coroutineScope NativeCallResult.Success(SyncedGradesNativeResult(mapped, lvDef.await()))
                    }
                }

                if (authOutcome != null) {
                    lvDef.cancel()
                    invalidateSessionCache(channel)
                    return@coroutineScope authOutcome
                }

                if (workingEps.isNotEmpty() && !confirmedZeroTotal) {
                    val curY = getDefaultAcademicYear()
                    val ep = workingEps.first()
                    val yearResults = (0 until 6).map { offset ->
                        async { queryGradesOnce(ep, (curY - offset).toString(), "") }
                    }.awaitAll()
                    val seen = HashSet<String>()
                    val collected = mutableListOf<GradeItem>()
                    for (r in yearResults) {
                        if (r.ok && r.items.isNotEmpty()) {
                            for (g in mapGradeItems(r.items)) {
                                val key = "${g.academicYear}|${g.semester}|${g.courseName}|${g.score}|${g.examNature}"
                                if (seen.add(key)) collected.add(g)
                            }
                        }
                    }
                    if (collected.isNotEmpty()) {
                        return@coroutineScope NativeCallResult.Success(SyncedGradesNativeResult(collected, lvDef.await()))
                    }
                }

                lvDef.cancel()
                if (evalIntercept && htmlHint.isNotEmpty()) return@coroutineScope NativeCallResult.EvalIntercept(htmlHint)
                if (htmlHint.isNotEmpty()) return@coroutineScope NativeCallResult.Failure(htmlHint)
                if (lastHttpError >= 400) {
                    return@coroutineScope NativeCallResult.Failure("成绩接口请求失败(HTTP $lastHttpError)，请刷新后重试")
                }
                NativeCallResult.Failure("暂未获取到成绩（可能当前未出分或需先在教务页确认学业通知）")
            }
        }

    suspend fun syncExams(
        channel: SyncChannel,
        academicYear: String,
        semesterCode: String
    ): NativeCallResult<List<ExamItem>> = withContext(Dispatchers.IO) {
        val (_, discovered) = coroutineScope {
            val wDef = async {
                executeHttp(buildJwUrl(channel, "/kwgl/kscx_cxXsksxxIndex.html?gnmkdm=N358105&layout=default"), "GET", channel)
            }
            val mDef = async { ensureMenuEndpoints(channel) }
            wDef.await() to mDef.await()
        }

        val endpoints = LinkedHashSet<String>().apply {
            addAll(discovered.examEps)
            add("/kwgl/kscx_cxXsksxxIndex.html?doType=query&gnmkdm=N358105")
            add("/kwgl/kscx_cxXsksxxIndex.html?doType=query&gnmkdm=N358110")
        }

        suspend fun queryExamsOnce(ep: String, qXnm: String, qXqm: String): Pair<PaginatedOutcome, List<ExamItem>> {
            val res = fetchPaginatedJwItems(
                channel = channel,
                ep = ep,
                buildFormForPage = { pageNo ->
                    "xnm=${urlEncode(qXnm)}&xqm=${urlEncode(qXqm)}&ksmcdmb_id=&kch=&kc=&ksrq=&kkbm_id=&_search=false&nd=${System.currentTimeMillis()}" +
                        "&queryModel.showCount=200&queryModel.currentPage=$pageNo&queryModel.sortName=&queryModel.sortOrder=asc&time=0"
                },
                maxPages = 10,
                fallbackHint = "需先在教务页确认通知或完成评教"
            )
            if (!res.ok) return res to emptyList()
            val mapped = mutableListOf<ExamItem>()
            val seen = HashSet<String>()
            for (it in res.items) {
                val courseName = it.str("kcmc")
                if (courseName.isEmpty()) continue
                val yr = it.str("xnmc").ifEmpty { qXnm.ifEmpty { academicYear } }
                val sem = resolveSemesterLabel(it.str("xqmmc"), it.str("xqm"), qXqm, "")
                val timeRaw = it.str("kssj")
                val seat = it.str("zwh")
                val key = "$yr|$sem|$courseName|$timeRaw|$seat"
                if (!seen.add(key)) continue
                val ksmc = it.str("ksmc")
                val mode = it.str("ksfs").ifEmpty { it.str("khfs") }
                val examType = when {
                    ksmc.isNotEmpty() && mode.isNotEmpty() && !ksmc.contains(mode) -> "$ksmc · $mode"
                    ksmc.isNotEmpty() -> ksmc
                    mode.isNotEmpty() -> mode
                    else -> "期末考试"
                }
                mapped.add(
                    ExamItem(
                        academicYear = yr,
                        semester = sem,
                        courseName = courseName,
                        examTimeRaw = timeRaw,
                        location = it.str("cdmc").ifEmpty { ksmc },
                        seatNumber = seat,
                        examType = examType,
                        campus = it.str("cdxqmc"),
                        isManual = false
                    )
                )
            }
            return res to mapped
        }

        var workingEp: String? = null
        var lastHttpError = 0
        var htmlHint = ""
        var evalIntercept = false
        var authOutcome: NativeCallResult.AuthExpired? = null

        for (ep in endpoints) {
            val (res, exams) = queryExamsOnce(ep, academicYear, semesterCode)
            if (!res.ok) {
                if (res.authExpired || res.isMfa || res.isWebVpnDenied) {
                    authOutcome = NativeCallResult.AuthExpired(
                        resumeUrl = resolveInteractiveResumeUrl(channel, res.resumeUrl),
                        message = if (res.isMfa) "需要完成二次验证" else "登录状态已过期，请重新登录",
                        isMfa = res.isMfa,
                        isWebVpnDenied = res.isWebVpnDenied
                    )
                    break
                }
                if (res.status >= 400) lastHttpError = res.status
                if (res.isEvalIntercept) evalIntercept = true
                if (htmlHint.isEmpty() && res.htmlHint.isNotEmpty()) htmlHint = res.htmlHint
                continue
            }
            if (workingEp == null) workingEp = ep
            if (exams.isNotEmpty()) return@withContext NativeCallResult.Success(exams)
        }

        if (authOutcome != null) {
            invalidateSessionCache(channel)
            return@withContext authOutcome
        }

        if (workingEp != null && semesterCode.isNotEmpty()) {
            val (yearRes, yearExams) = queryExamsOnce(workingEp, academicYear, "")
            if (yearRes.ok && yearExams.isNotEmpty()) return@withContext NativeCallResult.Success(yearExams)
        }
        if (workingEp != null) {
            val (allRes, allExams) = queryExamsOnce(workingEp, "", "")
            if (allRes.ok && allExams.isNotEmpty()) return@withContext NativeCallResult.Success(allExams)
            return@withContext NativeCallResult.Failure("教务已联通，当前学期暂未公布考场安排")
        }
        if (evalIntercept && htmlHint.isNotEmpty()) return@withContext NativeCallResult.EvalIntercept(htmlHint)
        if (htmlHint.isNotEmpty()) return@withContext NativeCallResult.Failure(htmlHint)
        NativeCallResult.Failure(
            if (lastHttpError >= 400) "考试接口请求失败(HTTP $lastHttpError)，请稍后重试"
            else "考试接口暂不可达，请检查网络后重试"
        )
    }

    /**
     * 原生纯 HTTP 自习空教室全量查询（0 WebView！）
     */
    suspend fun queryEmptyClassrooms(
        channel: SyncChannel,
        campusKeyword: String,
        weekNumber: Int,
        dayOfWeek: Int,
        startSection: Int,
        endSection: Int,
        buildingKeyword: String,
        fallbackAcademicYear: String = "",
        fallbackSemesterCode: String = ""
    ): NativeCallResult<EmptyClassroomQueryNativeResult> = withSilentCasRetry(
        channel = channel,
        isRetryableAuthExpired = { res ->
            res is NativeCallResult.AuthExpired && !res.isMfa && !res.isWebVpnDenied
        }
    ) {
        withContext(Dispatchers.IO) {
        val w = weekNumber.coerceIn(1, 25)
        val d = dayOfWeek.coerceIn(1, 7)
        val s1 = startSection.coerceIn(1, 12)
        val s2 = endSection.coerceIn(s1, 12)

        val ctxRes = resolveEmptyRoomQueryContext(channel, campusKeyword, fallbackAcademicYear, fallbackSemesterCode)
        val ctx = when (ctxRes) {
            is NativeCallResult.Success -> ctxRes.data
            is NativeCallResult.AuthExpired -> return@withContext ctxRes
            is NativeCallResult.EvalIntercept -> return@withContext ctxRes
            is NativeCallResult.Failure -> return@withContext ctxRes
        }

        val zcdMask = 1L shl (w - 1)
        val blocks = mutableListOf<Pair<Int, Int>>()
        if (s2 - s1 >= 2) {
            var cur = s1
            while (cur <= s2) {
                blocks.add(cur to minOf(cur + 1, s2))
                cur += 2
            }
        } else {
            blocks.add(s1 to s2)
        }

        // Sequential per-block fetch avoids Struts2 session clash on cdjy_cxKxcdlb when intersecting 1-4 / 5-8
        val blockResults = mutableListOf<List<JsonObject>>()
        for ((bs, be) in blocks) {
            val res = fetchEmptyRoomBlock(channel, ctx, zcdMask, d, bs, be, "")
            if (!res.ok) {
                if (res.authExpired || res.isMfa || res.isWebVpnDenied) {
                    invalidateSessionCache(channel)
                    return@withContext NativeCallResult.AuthExpired(
                        resumeUrl = resolveInteractiveResumeUrl(channel, res.resumeUrl),
                        message = "教务登录态已过期，请重新登录后再查询空教室",
                        isMfa = res.isMfa,
                        isWebVpnDenied = res.isWebVpnDenied
                    )
                }
                return@withContext NativeCallResult.Failure(
                    res.htmlHint.ifBlank { "空教室接口暂未返回数据，请稍后重试" },
                    isNetworkError = res.networkError
                )
            }
            blockResults.add(res.items)
        }

        val firstMap = normalizeEmptyRooms(blockResults.first(), campusKeyword)
        val otherMaps = blockResults.drop(1).map { normalizeEmptyRooms(it, campusKeyword) }
        val bKw = buildingKeyword.trim()
        val rooms = mutableListOf<EmptyClassroomItem>()
        for ((key, item) in firstMap) {
            if (otherMaps.any { !it.containsKey(key) }) continue
            if (bKw.isNotEmpty() && !item.roomName.contains(bKw) && !item.building.contains(bKw)) continue
            rooms.add(item)
        }

        val primeCount = rooms.count { r ->
            !NON_PRIME_ROOM_RE.containsMatchIn(r.roomName) &&
                !NON_PRIME_TYPE_RE.containsMatchIn(r.roomType) &&
                !NON_PRIME_BLD_RE.containsMatchIn(r.building)
        }
        val buildingCount = rooms.map { it.building }.toSet().size
        NativeCallResult.Success(
            EmptyClassroomQueryNativeResult(
                rooms = rooms,
                summary = "${campusKeyword}校区 · 共 $primeCount 间自习教室（全校区共 ${rooms.size} 间 · 覆盖 $buildingCount 栋教学楼）"
            )
        )
        }
    }

    /**
     * 原生纯 HTTP 单间教室全天（1-10节共5大节）空闲时间轴探测（0 WebView！）
     */
    suspend fun inspectRoomDaySchedule(
        channel: SyncChannel,
        campusKeyword: String,
        weekNumber: Int,
        dayOfWeek: Int,
        buildingName: String,
        roomName: String,
        fallbackAcademicYear: String = "",
        fallbackSemesterCode: String = ""
    ): NativeCallResult<RoomDayScheduleNativeResult> = withSilentCasRetry(
        channel = channel,
        isRetryableAuthExpired = { res ->
            res is NativeCallResult.AuthExpired && !res.isMfa && !res.isWebVpnDenied
        }
    ) {
        withContext(Dispatchers.IO) {
        val w = weekNumber.coerceIn(1, 25)
        val d = dayOfWeek.coerceIn(1, 7)
        val targetRaw = roomName.trim()
        val targetCore = targetRaw.replace(Regex("[（(][^）)]*[）)]$"), "").trim()
        val targetBld = buildingName.trim()
        val shortNoBld = if (targetBld.isNotEmpty() && targetCore.startsWith(targetBld)) {
            targetCore.removePrefix(targetBld).trimStart('-', '—', ' ')
        } else targetCore
        val qKw = (if (shortNoBld.length >= 2) shortNoBld else targetCore).ifEmpty { targetRaw }

        val ctxRes = resolveEmptyRoomQueryContext(channel, campusKeyword, fallbackAcademicYear, fallbackSemesterCode)
        val ctx = when (ctxRes) {
            is NativeCallResult.Success -> ctxRes.data
            is NativeCallResult.AuthExpired -> return@withContext ctxRes
            is NativeCallResult.EvalIntercept -> return@withContext ctxRes
            is NativeCallResult.Failure -> return@withContext ctxRes
        }

        val zcdMask = 1L shl (w - 1)
        val dayBlocks = listOf(1 to 2, 3 to 4, 5 to 6, 7 to 8, 9 to 10)
        val results = arrayOfNulls<List<JsonObject>>(dayBlocks.size)
        var activeKw = qKw
        var switchedToFull = false

        for ((idx, pair) in dayBlocks.withIndex()) {
            var res = fetchEmptyRoomBlock(channel, ctx, zcdMask, d, pair.first, pair.second, activeKw)
            if (!res.ok && activeKw.isNotEmpty()) {
                activeKw = ""
                switchedToFull = true
                res = fetchEmptyRoomBlock(channel, ctx, zcdMask, d, pair.first, pair.second, "")
            }
            if (res.authExpired || res.isMfa || res.isWebVpnDenied) {
                invalidateSessionCache(channel)
                return@withContext NativeCallResult.AuthExpired(
                    resumeUrl = resolveInteractiveResumeUrl(channel, res.resumeUrl),
                    message = "教务登录态已过期，请重新登录",
                    isMfa = res.isMfa,
                    isWebVpnDenied = res.isWebVpnDenied
                )
            }
            results[idx] = if (res.ok) res.items else null
        }

        if (activeKw.isNotEmpty() && !switchedToFull && results.all { it != null && it.isEmpty() }) {
            for ((idx, pair) in dayBlocks.withIndex()) {
                val res = fetchEmptyRoomBlock(channel, ctx, zcdMask, d, pair.first, pair.second, "")
                results[idx] = if (res.ok) res.items else null
            }
        }
        if (results.all { it == null }) {
            return@withContext NativeCallResult.Failure("未能查询该教室今日全天排课状况")
        }

        var resolvedName = targetRaw
        var resolvedBuilding = targetBld
        var resolvedSeats = ""
        var resolvedType = ""
        val bCore = targetBld.removeSuffix("楼")

        val slots = dayBlocks.mapIndexed { idx, pair ->
            val items = results[idx].orEmpty()
            var matched: JsonObject? = null
            for (it in items) {
                val mc = it.str("cdmc")
                val b = it.str("jxlmc").ifEmpty { it.str("lh") }
                val bOk = targetBld.isEmpty() || if (b.isNotEmpty()) (b == targetBld || b.contains(bCore)) else (mc.contains(bCore) || mc == targetRaw)
                val rOk = mc == targetRaw || mc == targetCore ||
                    mc.replace(Regex("[（(][^）)]*[）)]$"), "").trim() == targetCore ||
                    (items.size == 1 && mc.contains(qKw))
                if (bOk && rOk) {
                    matched = it
                    break
                }
            }
            if (matched == null && targetBld.isEmpty() && items.size == 1) {
                matched = items.first()
            }
            if (matched != null) {
                resolvedName = matched.str("cdmc").ifEmpty { targetRaw }
                if (resolvedBuilding.isEmpty()) resolvedBuilding = matched.str("jxlmc").ifEmpty { matched.str("lh") }
                if (resolvedSeats.isEmpty()) resolvedSeats = matched.str("kwzws").ifEmpty { matched.str("zws") }
                if (resolvedType.isEmpty()) resolvedType = matched.str("cdlbmc")
            }
            ClassroomSlotStatus(
                startSection = pair.first,
                endSection = pair.second,
                isFree = matched != null,
                matchedRoomName = matched?.str("cdmc").orEmpty(),
                seats = matched?.let { it.str("kwzws").ifEmpty { it.str("zws") } }.orEmpty(),
                roomType = matched?.str("cdlbmc").orEmpty()
            )
        }

        NativeCallResult.Success(
            RoomDayScheduleNativeResult(
                campusLabel = campusKeyword,
                building = resolvedBuilding,
                roomName = resolvedName,
                weekNumber = w,
                dayOfWeek = d,
                seats = resolvedSeats,
                roomType = resolvedType,
                slots = slots
            )
        )
        }
    }

    // ==================== 内部协议实现与解析 ====================

    private data class EmptyRoomCtx(val xnm: String, val xqm: String, val xqhId: String)

    private suspend fun resolveEmptyRoomQueryContext(
        channel: SyncChannel,
        campusKeyword: String,
        fallbackYear: String,
        fallbackSem: String
    ): NativeCallResult<EmptyRoomCtx> {
        val cacheKey = "${channel.name}|${campusKeyword.trim()}"
        val cached = emptyRoomCtxCache[cacheKey]
        if (cached != null && System.currentTimeMillis() - cached.first < 10 * 60_000L) {
            return NativeCallResult.Success(cached.second)
        }
        var xnm = fallbackYear.ifBlank { getDefaultAcademicYear().toString() }
        var xqm = fallbackSem.ifBlank { if (isDefaultFirstSemester()) "3" else "12" }
        var xqhId = when {
            campusKeyword.contains("湖光") -> "1"
            campusKeyword.contains("海滨") -> "2"
            campusKeyword.contains("阳江") -> "BBF8A51ACEA2144EE053768926D2788B"
            campusKeyword.contains("霞山") -> "3"
            else -> ""
        }
        val resp = executeHttp(buildJwUrl(channel, "/cdjy/cdjy_cxKxcdlb.html?gnmkdm=N2155&layout=default"), "GET", channel)
        if (resp.isWebVpnDenied || resp.isMfa || resp.authExpired || isAuthExpiredStatus(resp.statusCode)) {
            invalidateSessionCache(channel)
            return NativeCallResult.AuthExpired(
                resumeUrl = resolveInteractiveResumeUrl(channel, resp.finalUrl),
                message = "请先完成教务登录后再查询空教室",
                isMfa = resp.isMfa,
                isWebVpnDenied = resp.isWebVpnDenied
            )
        }
        if (resp.statusCode in 200..299 && resp.body.isNotBlank() && !isAuthExpiredHtml(resp.body)) {
            val html = resp.body
            val dmCx = parseSelectOptionsFromHtml(html, "dm_cx")
            val dmVal = dmCx.selectedValue
            if (dmVal.contains("-")) {
                val parts = dmVal.split("-")
                if (parts[0].isNotBlank()) xnm = parts[0].trim()
                if (parts.getOrNull(1)?.isNotBlank() == true) xqm = parts[1].trim()
            }
            val xnmSel = parseSelectOptionsFromHtml(html, "xnm").selectedValue
            val xqmSel = parseSelectOptionsFromHtml(html, "xqm").selectedValue
            if (xnmSel.isNotBlank()) xnm = xnmSel
            if (xqmSel.isNotBlank()) xqm = xqmSel

            val xqSel = parseSelectOptionsFromHtml(html, "xqh_id")
            if (xqSel.options.isNotEmpty()) {
                val matched = xqSel.options.firstOrNull { campusKeyword.isNotBlank() && it.text.contains(campusKeyword) }
                if (matched != null) xqhId = matched.value
                else if (xqhId.isEmpty()) xqhId = xqSel.options.first().value
            }
        }
        val resolved = EmptyRoomCtx(xnm, xqm, xqhId)
        emptyRoomCtxCache[cacheKey] = System.currentTimeMillis() to resolved
        return NativeCallResult.Success(resolved)
    }

    private suspend fun fetchEmptyRoomBlock(
        channel: SyncChannel,
        ctx: EmptyRoomCtx,
        zcdMask: Long,
        dow: Int,
        secStart: Int,
        secEnd: Int,
        roomKeyword: String
    ): PaginatedOutcome {
        var jcdMask = 0L
        for (sec in secStart..secEnd) {
            jcdMask += 1L shl (sec - 1)
        }
        return fetchPaginatedJwItems(
            channel = channel,
            ep = "/cdjy/cdjy_cxKxcdlb.html?doType=query&gnmkdm=N2155",
            buildFormForPage = { pageNo ->
                "fwzt=cx&xqh_id=${urlEncode(ctx.xqhId)}&xnm=${urlEncode(ctx.xnm)}&xqm=${urlEncode(ctx.xqm)}" +
                    "&cdlb_id=&cdejlb_id=&qszws=&jszws=&cdmc=${urlEncode(roomKeyword)}" +
                    "&lh=&jyfs=0&cdjylx=&zcd=$zcdMask&xqj=$dow&jcd=$jcdMask" +
                    "&_search=false&nd=${System.currentTimeMillis() + secStart}" +
                    "&queryModel.showCount=1000&queryModel.currentPage=$pageNo&queryModel.sortName=cdbh&queryModel.sortOrder=asc&time=0"
            },
            maxPages = 10,
            fallbackHint = ""
        )
    }

    private suspend fun syncLevelExamsGentle(channel: SyncChannel): List<LevelExamItem> {
        val discovered = ensureMenuEndpoints(channel)
        val eps = LinkedHashSet<String>().apply {
            addAll(discovered.levelExamEps)
            add("/cjcx/djkscx_cxXsDjksxxIndex.html?doType=query&gnmkdm=N305010")
            add("/cjcx/djkscx_cxXsDjksxxIndex.html?doType=query&gnmkdm=N358115")
        }
        for (ep in eps) {
            val res = fetchPaginatedJwItems(
                channel = channel,
                ep = ep,
                buildFormForPage = { pageNo ->
                    "_search=false&nd=${System.currentTimeMillis()}&queryModel.showCount=200&queryModel.currentPage=$pageNo&queryModel.sortName=&queryModel.sortOrder=asc&time=0"
                },
                maxPages = 5,
                fallbackHint = ""
            )
            if (!res.ok) continue
            val mapped = mutableListOf<LevelExamItem>()
            val seen = HashSet<String>()
            for (it in res.items) {
                val examName = it.str("djksmc").ifEmpty { it.str("ksmc") }
                val score = it.str("cj").ifEmpty { it.str("zcj").ifEmpty { it.str("dj") } }
                if (examName.isEmpty() || score.isEmpty()) continue
                val date = it.str("ksrq").ifEmpty { it.str("xnmc") }
                val detail = formatLevelExamDetail(it)
                val key = "$examName|$score|$date"
                if (seen.add(key)) {
                    mapped.add(LevelExamItem(examName = examName, score = score, examDate = date, detail = detail))
                }
            }
            if (mapped.isNotEmpty()) return mapped
        }
        return emptyList()
    }

    private suspend fun fetchSemesterInfo(channel: SyncChannel, xnm: String, xqm: String): Pair<String?, Int>? {
        val resp = executeHttp(
            urlStr = buildJwUrl(channel, "/kbcx/xskbcxZccx_cxZcByXnxq.html?gnmkdm=N2154"),
            method = "POST",
            channel = channel,
            formBody = "xnm=${urlEncode(xnm)}&xqm=${urlEncode(xqm)}",
            expectJson = true
        )
        if (resp.statusCode !in 200..299) return null
        val trimmed = resp.body.trim()
        if (!trimmed.startsWith("[")) return null
        val arr = try { json.parseToJsonElement(trimmed) as? JsonArray } catch (_: Exception) { null } ?: return null
        if (arr.isEmpty()) return null
        var maxWeekNo = 0
        for (el in arr) {
            val n = (el as? JsonObject)?.get("zs")?.let { (it as? JsonPrimitive)?.contentOrNull?.toIntOrNull() }
            if (n != null && n in 1..30 && n > maxWeekNo) maxWeekNo = n
        }
        return findSemesterStartDate(arr) to (if (maxWeekNo > 0) maxWeekNo else arr.size)
    }

    private suspend fun ensureMenuEndpoints(channel: SyncChannel): DiscoveredMenuEndpoints {
        menuCache[channel]?.let { return it }
        val resp = executeHttp(buildJwUrl(channel, "/xtgl/index_initMenu.html?jsdm=xs"), "GET", channel)
        if (resp.statusCode in 200..299 && resp.body.isNotBlank() && !isAuthExpiredHtml(resp.body)) {
            val parsed = parseMenuEndpoints(resp.body)
            menuCache[channel] = parsed
            return parsed
        }
        return DiscoveredMenuEndpoints()
    }

    private suspend fun fetchPaginatedJwItems(
        channel: SyncChannel,
        ep: String,
        buildFormForPage: (Int) -> String,
        maxPages: Int,
        fallbackHint: String
    ): PaginatedOutcome {
        val cap = maxPages.coerceIn(1, 20)
        val resp1 = executeHttp(
            urlStr = buildJwUrl(channel, ep),
            method = "POST",
            channel = channel,
            formBody = buildFormForPage(1),
            expectJson = true
        )
        if (resp1.isWebVpnDenied || resp1.isMfa || resp1.authExpired || isAuthExpiredStatus(resp1.statusCode)) {
            return PaginatedOutcome(
                ok = false,
                status = resp1.statusCode,
                items = emptyList(),
                totalResult = 0,
                hasTotalResult = false,
                authExpired = true,
                isMfa = resp1.isMfa,
                isWebVpnDenied = resp1.isWebVpnDenied,
                resumeUrl = resp1.finalUrl,
                htmlHint = "登录状态已过期，请重新登录"
            )
        }
        if (resp1.networkError) {
            return PaginatedOutcome(
                ok = false,
                status = 0,
                items = emptyList(),
                totalResult = 0,
                hasTotalResult = false,
                networkError = true
            )
        }
        val trimmed1 = resp1.body.trim()
        if (resp1.statusCode !in 200..299 || !(trimmed1.startsWith("{") || trimmed1.startsWith("["))) {
            val eval = trimmed1.isNotEmpty() && isEvalInterceptHtml(resp1.body)
            val exp = !eval && trimmed1.isNotEmpty() && isAuthExpiredHtml(resp1.body)
            return PaginatedOutcome(
                ok = false,
                status = resp1.statusCode,
                items = emptyList(),
                totalResult = 0,
                hasTotalResult = false,
                authExpired = exp,
                resumeUrl = resp1.finalUrl,
                isEvalIntercept = eval,
                htmlHint = if (trimmed1.isNotEmpty()) classifyNonJsonHtml(resp1.body, fallbackHint) else ""
            )
        }

        val parsed1 = try { json.parseToJsonElement(trimmed1) } catch (_: Exception) { null }
            ?: return PaginatedOutcome(false, resp1.statusCode, emptyList(), 0, false, htmlHint = classifyNonJsonHtml(resp1.body, fallbackHint))

        val (firstItems, totalPage, totalResult, hasTotalResult) = extractJsonPageMeta(parsed1)
        val allItems = firstItems.toMutableList()
        if (totalPage > 1 && allItems.size < totalResult) {
            val limit = minOf(totalPage, cap)
            for (pageNo in 2..limit) {
                val respN = executeHttp(
                    urlStr = buildJwUrl(channel, ep),
                    method = "POST",
                    channel = channel,
                    formBody = buildFormForPage(pageNo),
                    expectJson = true
                )
                if (respN.statusCode !in 200..299) break
                val tN = respN.body.trim()
                if (!(tN.startsWith("{") || tN.startsWith("["))) break
                val parsedN = try { json.parseToJsonElement(tN) } catch (_: Exception) { break }
                val pageItems = extractJsonPageMeta(parsedN).first
                if (pageItems.isEmpty()) break
                allItems.addAll(pageItems)
                if (allItems.size >= totalResult) break
            }
        }
        return PaginatedOutcome(
            ok = true,
            status = resp1.statusCode,
            items = allItems,
            totalResult = totalResult,
            hasTotalResult = hasTotalResult
        )
    }

    private data class PageMeta(
        val first: List<JsonObject>,
        val totalPage: Int,
        val totalResult: Int,
        val hasTotalResult: Boolean
    )

    private fun extractJsonPageMeta(element: JsonElement): PageMeta {
        return when (element) {
            is JsonArray -> {
                val list = element.mapNotNull { it as? JsonObject }
                PageMeta(list, 1, list.size, false)
            }
            is JsonObject -> {
                val items = (element["items"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
                val totalPage = (element["totalPage"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 1
                val rawTotal = (element["totalResult"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
                PageMeta(items, totalPage, rawTotal ?: items.size, rawTotal != null)
            }
            else -> PageMeta(emptyList(), 1, 0, false)
        }
    }

    /**
     * 核心原生 HTTP 执行器：
     * - 手动跟随 302 重定向以精确保存/传递每一跳 Cookie
     * - 自动处理 http 广海域名 -> https 升级与 WebVPN /http/<auth_hex>/ -> /https/<auth_hex>/
     * - 自动识别 MFA / WebVPN denied / 认证表单终止重定向
     * - 对 502/503/504 与 jw 负载均衡断连自动清 ADCCookie 退避重试
     */
    private suspend fun executeHttp(
        urlStr: String,
        method: String,
        channel: SyncChannel,
        formBody: String? = null,
        expectJson: Boolean = false,
        timeoutMs: Int = 11_000,
        maxAttempts: Int = 3
    ): HttpOutcome {
        var lastOutcome: HttpOutcome? = null
        val cap = maxAttempts.coerceIn(1, 3)
        for (attempt in 0 until cap) {
            val outcome = executeHttpOnce(urlStr, method, channel, formBody, expectJson, timeoutMs)
            lastOutcome = outcome
            val transient5xx = outcome.statusCode in listOf(502, 503, 504) ||
                (outcome.statusCode == 500 && urlStr.contains("/sso/jziotlogin", ignoreCase = true))
            if (!transient5xx && !outcome.networkError) return outcome
            if (outcome.nonRetriableNetwork) {
                engineLog(warn = true, msg = "httpAbortNonRetriable ch=${channel.name} url=${sanitizeUrlForLog(urlStr)} err=${outcome.errorDetail}")
                return outcome
            }
            // Off-campus / VPN blackhole: TCP SYN never reached campus gateway within 3.5s, fail fast instead of looping 30s
            if (channel == SyncChannel.ON_CAMPUS && outcome.tcpConnectTimedOut) {
                engineLog(warn = true, msg = "httpFailFastConnectTimeout ch=${channel.name} url=${sanitizeUrlForLog(urlStr)}")
                return outcome
            }
            if (attempt < cap - 1) {
                engineLog(warn = true, msg = "httpRetry#${attempt + 1} ch=${channel.name} code=${outcome.statusCode} url=${sanitizeUrlForLog(urlStr)} err=${outcome.errorDetail}")
                if (channel == SyncChannel.ON_CAMPUS) {
                    cookieStore.clearCookieByPrefix("https://jw.gdou.edu.cn/", "ADCCookie")
                } else if (channel == SyncChannel.WFW_LEGACY) {
                    cookieStore.clearCookieByPrefix("${wfwRootUrl()}/", "ADCCookie")
                }
                delay(280L * (attempt + 1))
            }
        }
        return lastOutcome ?: HttpOutcome(0, urlStr, "", "", networkError = true, errorDetail = "网络请求失败")
    }

    private fun executeHttpOnce(
        startUrl: String,
        initialMethod: String,
        channel: SyncChannel,
        formBody: String?,
        expectJson: Boolean,
        timeoutMs: Int
    ): HttpOutcome {
        var currentUrl = startUrl
        var currentMethod = initialMethod
        var currentBody = formBody
        val isWfwRootTicketFlow = channel == SyncChannel.WFW_LEGACY && startUrl.contains("/?ticket=")
        val effectiveConnectTimeoutMs = if (channel == SyncChannel.ON_CAMPUS) minOf(timeoutMs, 3_500) else timeoutMs

        data class HopCookie(val host: String, val path: String, val name: String, val value: String)
        val hopCookies = mutableListOf<HopCookie>()

        fun parseSetCookieToHop(urlStr: String, raw: String): HopCookie? {
            val parts = raw.split(";")
            val nv = parts.firstOrNull()?.trim() ?: return null
            val eq = nv.indexOf('=')
            if (eq <= 0) return null
            val name = nv.substring(0, eq).trim()
            val value = nv.substring(eq + 1).trim()
            val uri = try { URI(urlStr) } catch (_: Exception) { return null }
            val host = uri.host?.lowercase(Locale.US) ?: return null
            val reqPath = uri.rawPath.orEmpty().ifEmpty { "/" }
            var cookiePath = if (reqPath.endsWith("/")) reqPath else reqPath.substringBeforeLast("/", "") + "/"
            for (i in 1 until parts.size) {
                val attr = parts[i].trim()
                if (attr.startsWith("path=", ignoreCase = true)) {
                    val p = attr.substringAfter("=").trim()
                    if (p.isNotEmpty()) cookiePath = p
                }
            }
            return HopCookie(host, cookiePath, name, value)
        }

        fun pathMatchesCookie(reqPath: String, cookiePath: String): Boolean {
            if (cookiePath == "/") return true
            if (!reqPath.startsWith(cookiePath)) return false
            if (reqPath.length == cookiePath.length || cookiePath.endsWith("/")) return true
            return reqPath[cookiePath.length] == '/'
        }

        for (hop in 0 until 12) {
            if (!isAllowedRequestUrl(currentUrl)) {
                return HttpOutcome(0, currentUrl, "", "", networkError = true, errorDetail = "拦截了非广海官方域名的跳转")
            }
            var conn: HttpURLConnection? = null
            var connectCompleted = false
            var sentCookieNames = ""
            try {
                conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = currentMethod
                    instanceFollowRedirects = false
                    connectTimeout = effectiveConnectTimeoutMs
                    readTimeout = timeoutMs
                    useCaches = false
                    setRequestProperty("User-Agent", MOBILE_CHROME_UA)
                    setRequestProperty(
                        "Accept",
                        if (expectJson) "application/json, text/javascript, */*; q=0.01"
                        else "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                    )
                    setRequestProperty("Accept-Encoding", "gzip")
                    if (expectJson) {
                        setRequestProperty("X-Requested-With", "XMLHttpRequest")
                    }
                    val baseCookieHeader = cookieStore.getCookieHeader(currentUrl).orEmpty()
                    val curUri = try { URI(currentUrl) } catch (_: Exception) { null }
                    val curHost = curUri?.host?.lowercase(Locale.US).orEmpty()
                    val curPath = curUri?.rawPath.orEmpty().ifEmpty { "/" }
                    val matchingHops = hopCookies
                        .filter { it.host == curHost && pathMatchesCookie(curPath, it.path) }
                        .sortedByDescending { it.path.length }
                    val effectivePairs = mutableListOf<String>()
                    val seenHopKeys = HashSet<String>()
                    for (hc in matchingHops) {
                        val k = hc.name.lowercase(Locale.US)
                        if (seenHopKeys.add(k)) {
                            effectivePairs.add("${hc.name}=${hc.value}")
                        }
                    }
                    val isWfwPreSeed = channel == SyncChannel.WFW_LEGACY && startUrl.contains("/xtgl/login_getPublicKey.html")
                    val isWfwCasRedeem = channel == SyncChannel.WFW_LEGACY && startUrl.contains("/sso/jziotlogin")
                    if (baseCookieHeader.isNotBlank()) {
                        baseCookieHeader.split(";").forEach { part ->
                            val trimmed = part.trim()
                            val k = trimmed.substringBefore("=").trim().lowercase(Locale.US)
                            val skipWfwStale = channel == SyncChannel.WFW_LEGACY && (
                                k == "urlss_file" ||
                                    (k == "urlss" && !trimmed.contains("jw.gdou.edu.cn", ignoreCase = true)) ||
                                    (isWfwPreSeed && k in setOf("wfwsessid", "db_agent_token", "_path", "jsessionid")) ||
                                    (isWfwCasRedeem && k in setOf("db_agent_token", "_path"))
                                )
                            if (trimmed.isNotEmpty() && !seenHopKeys.contains(k) && !skipWfwStale) {
                                effectivePairs.add(trimmed)
                            }
                        }
                    }
                    val mergedCookieHeader = effectivePairs.joinToString("; ")
                    sentCookieNames = effectivePairs.joinToString(",") { it.substringBefore("=").trim() }
                    if (mergedCookieHeader.isNotBlank()) {
                        setRequestProperty("Cookie", mergedCookieHeader)
                    }
                    if (currentMethod == "POST" && currentBody != null) {
                        doOutput = true
                        setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                        val bytes = currentBody.toByteArray(Charsets.UTF_8)
                        setFixedLengthStreamingMode(bytes.size)
                        outputStream.use { it.write(bytes) }
                    }
                }
                conn.connect()
                connectCompleted = true

                val code = conn.responseCode
                val setCookies = extractSetCookieHeaders(conn)
                if (setCookies.isNotEmpty()) {
                    cookieStore.saveResponseCookies(currentUrl, setCookies)
                    for (raw in setCookies) {
                        parseSetCookieToHop(currentUrl, raw)?.let { hc ->
                            hopCookies.removeAll { it.host == hc.host && it.path == hc.path && it.name.equals(hc.name, ignoreCase = true) }
                            hopCookies.add(hc)
                        }
                    }
                    // Keep wfw's urlss pinned to jw.gdou.edu.cn so redirects don't hijack proxy target to file.gdou.edu.cn
                    if (channel == SyncChannel.WFW_LEGACY &&
                        !isWfwRootTicketFlow &&
                        setCookies.any { it.startsWith("urlss=", ignoreCase = true) && !it.contains("jw.gdou.edu.cn", ignoreCase = true) }
                    ) {
                        ensureWfwJwProxyCookies()
                    }
                }
                val recvCookieSummary = setCookies.joinToString(",") { raw ->
                    val parsed = parseSetCookieToHop(currentUrl, raw)
                    if (parsed != null) "${parsed.name}(${parsed.path})" else raw.substringBefore("=").trim()
                }

                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location")?.trim().orEmpty()
                    // Drain tiny redirect stream without calling disconnect() so OkHttp returns the TLS socket to Keep-Alive pool!
                    drainStreamQuietly(conn, code)
                    if (loc.isEmpty()) {
                        return HttpOutcome(code, currentUrl, "", "", authExpired = true)
                    }
                    var nextUrl = resolveRedirectUrl(currentUrl, loc)
                    nextUrl = normalizeGdouRedirectUrl(nextUrl, channel)
                    engineLog(
                        warn = channel == SyncChannel.WFW_LEGACY,
                        msg = "httpHop#$hop ch=${channel.name} $code ${sanitizeUrlForLog(currentUrl)} -> ${sanitizeUrlForLog(nextUrl)} sent=[$sentCookieNames] recv=[$recvCookieSummary]"
                    )

                    val lowerNext = nextUrl.lowercase(Locale.US)
                    if (lowerNext.contains("/wengine-vpn/denied") || lowerNext.contains("/wengine-vpn/failed")) {
                        return HttpOutcome(code, nextUrl, "", "", authExpired = true, isWebVpnDenied = true)
                    }
                    if (GdouSyncScript.isMfaUrl(nextUrl)) {
                        return HttpOutcome(code, nextUrl, "", "", authExpired = true, isMfa = true)
                    }
                    // On POST -> 301/302/303, follow as GET per RFC / browser convention
                    if (code != 307 && code != 308 && currentMethod == "POST") {
                        currentMethod = "GET"
                        currentBody = null
                    }
                    currentUrl = nextUrl
                    continue
                }

                val body = readResponseBody(conn, code)
                val ct = conn.contentType.orEmpty()
                if (channel == SyncChannel.WFW_LEGACY) {
                    val finalTitle = TITLE_RE.find(body)?.groupValues?.getOrNull(1)?.replace(WS_RE, " ")?.trim().orEmpty().take(40)
                    engineLog(
                        warn = true,
                        msg = "httpFinal#$hop ch=WFW_LEGACY $code ${sanitizeUrlForLog(currentUrl)} sent=[$sentCookieNames] recv=[$recvCookieSummary] title=[$finalTitle] len=${body.length}"
                    )
                }

                val lowerFinal = currentUrl.lowercase(Locale.US)
                if (lowerFinal.contains("/wengine-vpn/denied") || lowerFinal.contains("/wengine-vpn/failed") ||
                    body.contains("未在本地用户库注册") || body.contains("没有权限访问该资源")
                ) {
                    return HttpOutcome(code, currentUrl, body, ct, authExpired = true, isWebVpnDenied = true)
                }
                if (GdouSyncScript.isMfaUrl(currentUrl)) {
                    return HttpOutcome(code, currentUrl, body, ct, authExpired = true, isMfa = true)
                }
                val landedOnAuthGate = (lowerFinal.contains("/authserver/") ||
                    lowerFinal.contains("/cas/login") ||
                    (GdouSyncScript.extractHostLower(currentUrl) == GdouSyncScript.extractHostLower(webVpnRootUrl()) &&
                        URI(currentUrl).path.orEmpty().trimEnd('/') == "/login"))
                if (landedOnAuthGate && (hasAuthFormDom(body) || isStrongAuthExpiredHtml(body))) {
                    return HttpOutcome(code, currentUrl, body, ct, authExpired = true)
                }
                return HttpOutcome(code, currentUrl, body, ct)
            } catch (e: UnknownHostException) {
                try { conn?.disconnect() } catch (_: Throwable) {}
                return HttpOutcome(
                    0, currentUrl, "", "",
                    networkError = true,
                    errorDetail = "域名解析失败：${e.message ?: "UnknownHost"}",
                    nonRetriableNetwork = true
                )
            } catch (e: SocketTimeoutException) {
                try { conn?.disconnect() } catch (_: Throwable) {}
                return HttpOutcome(
                    0, currentUrl, "", "",
                    networkError = true,
                    errorDetail = if (connectCompleted) "服务器响应超时，请检查网络或切换通道" else "连接超时，请检查网络或切换通道",
                    tcpConnectTimedOut = !connectCompleted
                )
            } catch (e: IOException) {
                try { conn?.disconnect() } catch (_: Throwable) {}
                return HttpOutcome(0, currentUrl, "", "", networkError = true, errorDetail = "网络中断：${e.message ?: e.javaClass.simpleName}")
            } catch (e: Exception) {
                try { conn?.disconnect() } catch (_: Throwable) {}
                return HttpOutcome(0, currentUrl, "", "", networkError = true, errorDetail = e.message ?: "请求异常")
            }
        }
        return HttpOutcome(0, currentUrl, "", "", authExpired = true, errorDetail = "重定向次数过多，请重新登录")
    }

    private fun drainStreamQuietly(conn: HttpURLConnection, statusCode: Int) {
        try {
            val s = if (statusCode in 200..399) conn.inputStream else conn.errorStream
            s?.use { input ->
                val buf = ByteArray(1024)
                var readTotal = 0
                while (readTotal < 16_384) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    readTotal += n
                }
            }
        } catch (_: Throwable) {}
    }

    private fun extractSetCookieHeaders(conn: HttpURLConnection): List<String> {
        val out = mutableListOf<String>()
        conn.headerFields?.forEach { (k, vals) ->
            if (k != null && k.equals("Set-Cookie", ignoreCase = true) && vals != null) {
                out.addAll(vals)
            }
        }
        return out
    }

    private fun readResponseBody(conn: HttpURLConnection, statusCode: Int): String {
        val rawStream = try {
            if (statusCode in 200..399) conn.inputStream else conn.errorStream
        } catch (_: Exception) {
            null
        } ?: return ""
        val isGzip = conn.contentEncoding.orEmpty().equals("gzip", ignoreCase = true)
        val stream = if (isGzip) GZIPInputStream(rawStream) else rawStream
        val bos = ByteArrayOutputStream()
        stream.use { input ->
            val buf = ByteArray(8192)
            var total = 0
            val maxBytes = 4 * 1024 * 1024
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                total += n
                if (total > maxBytes) break
                bos.write(buf, 0, n)
            }
        }
        return bos.toString(Charsets.UTF_8.name())
    }

    private fun isAllowedRequestUrl(urlStr: String): Boolean {
        if (allowLoopbackForTest) {
            val h = GdouSyncScript.extractHostLower(urlStr)
            if (h == "127.0.0.1" || h == "localhost") return true
        }
        return GdouJsBridge.isTrustedGdouOrigin(urlStr)
    }

    private fun resolveRedirectUrl(baseUrl: String, location: String): String {
        return try {
            URI(baseUrl).resolve(location).toString()
        } catch (_: Exception) {
            location
        }
    }

    private fun normalizeGdouRedirectUrl(url: String, channel: SyncChannel): String {
        // 1. Upgrade cleartext http://*.gdou.edu.cn -> https://*.gdou.edu.cn
        val upgraded = GdouJsBridge.upgradeGdouHttpToHttps(url) ?: url
        // 2. Normalize WebVPN /http/<auth_hex>/authserver/ -> /https/<auth_hex>/authserver/ and keep bare jw redirects inside WebVPN
        val authHttpPrefix = "${webVpnRootUrl()}/http/${GdouSyncScript.WEBVPN_AUTH_HEX}/"
        if (upgraded.startsWith(authHttpPrefix, ignoreCase = true)) {
            return "${webVpnRootUrl()}/https/${GdouSyncScript.WEBVPN_AUTH_HEX}/" +
                upgraded.substring(authHttpPrefix.length)
        }
        if (channel == SyncChannel.WEBVPN) {
            val jwPrefix = "https://jw.gdou.edu.cn"
            if (upgraded.equals(jwPrefix, ignoreCase = true)) {
                return "${webVpnRootUrl()}/https/${GdouSyncScript.WEBVPN_JW_HEX}/"
            }
            if (upgraded.startsWith("$jwPrefix/", ignoreCase = true)) {
                return "${webVpnRootUrl()}/https/${GdouSyncScript.WEBVPN_JW_HEX}" + upgraded.substring(jwPrefix.length)
            }
        }
        // 3. On WFW_LEGACY, keep every jw.gdou.edu.cn redirect inside wfw root so Path=/sso & Path=/ JSESSIONID cookies stay valid off-campus!
        if (channel == SyncChannel.WFW_LEGACY) {
            val jwPrefix = "https://jw.gdou.edu.cn"
            if (upgraded.equals(jwPrefix, ignoreCase = true)) {
                return "${wfwRootUrl()}/"
            }
            if (upgraded.startsWith("$jwPrefix/", ignoreCase = true)) {
                return wfwRootUrl() + upgraded.substring(jwPrefix.length)
            }
            val wfwNestedPrefix = "${wfwRootUrl()}/https://jw.gdou.edu.cn"
            if (upgraded.startsWith("$wfwNestedPrefix/", ignoreCase = true)) {
                return wfwRootUrl() + upgraded.substring(wfwNestedPrefix.length)
            }
        }
        return upgraded
    }

    private fun resolveInteractiveResumeUrl(channel: SyncChannel, finalUrl: String): String {
        val trimmed = finalUrl.trim()
        if (trimmed.isNotEmpty() && isAllowedRequestUrl(trimmed)) {
            val lower = trimmed.lowercase(Locale.US)
            val isProxiedWebVpnAuth = channel == SyncChannel.WEBVPN &&
                GdouSyncScript.extractHostLower(trimmed) == GdouSyncScript.extractHostLower(webVpnRootUrl()) &&
                lower.contains("/authserver/")
            if (GdouSyncScript.isMfaUrl(lower) ||
                lower.contains("/wengine-vpn/denied") ||
                lower.contains("/wengine-vpn/failed") ||
                isProxiedWebVpnAuth
            ) {
                return trimmed
            }
        }
        return channel.authStartUrl()
    }

    private fun buildJwUrl(channel: SyncChannel, relativePath: String): String {
        val clean = stripProxyPrefix(relativePath)
        val base = when {
            !customBaseUrlOverride.isNullOrBlank() -> customBaseUrlOverride.trimEnd('/')
            channel == SyncChannel.WEBVPN && !webVpnHostOverride.isNullOrBlank() ->
                "${webVpnHostOverride.trimEnd('/')}/https/${GdouSyncScript.WEBVPN_JW_HEX}"
            channel == SyncChannel.WFW_LEGACY && !wfwHostOverride.isNullOrBlank() ->
                wfwHostOverride.trimEnd('/')
            else -> channel.baseJwUrl(null)
        }
        return base + clean
    }

    private fun mapGradeItems(items: List<JsonObject>): List<GradeItem> {
        val out = mutableListOf<GradeItem>()
        val seen = HashSet<String>()
        for (it in items) {
            val courseName = it.str("kcmc")
            if (courseName.isEmpty()) continue
            val xnmRaw = it.str("xnm")
            val xnmText = it.str("xnmmc").ifEmpty {
                xnmRaw.toIntOrNull()?.let { y -> "$y-${y + 1}" } ?: "未知学年"
            }
            val semText = resolveSemesterLabel(it.str("xqmmc"), it.str("xqm"), "", "第一学期")
            val jd = it.doubleVal("jd")
            val xf = it.doubleVal("xf")
            val subParts = mutableListOf<String>()
            it.str("pscj").takeIf { s -> s.isNotEmpty() }?.let { s -> subParts.add("平时:$s") }
            it.str("qzcj").takeIf { s -> s.isNotEmpty() }?.let { s -> subParts.add("期中:$s") }
            it.str("sycj").takeIf { s -> s.isNotEmpty() }?.let { s -> subParts.add("实验:$s") }
            it.str("qmcj").takeIf { s -> s.isNotEmpty() }?.let { s -> subParts.add("期末:$s") }
            val score = it.str("cj").ifEmpty { it.str("bfzcj") }
            val examNature = resolveGradeExamNature(it.str("ksxz"), it.str("cxbj"), it.str("kcbj"))
            val key = "$xnmText|$semText|$courseName|$score|$examNature"
            if (!seen.add(key)) continue
            out.add(
                GradeItem(
                    academicYear = xnmText,
                    semester = semText,
                    courseName = courseName,
                    score = score,
                    gradePoint = jd,
                    credit = xf,
                    courseType = it.str("kcxzmc").ifEmpty { it.str("kclbmc") },
                    examNature = examNature,
                    teacher = it.str("jsxm").ifEmpty { it.str("rkjs") },
                    breakdown = subParts.joinToString(" · "),
                    isSimulated = false
                )
            )
        }
        return out
    }

    private fun normalizeEmptyRooms(rawItems: List<JsonObject>, fallbackCampus: String): LinkedHashMap<String, EmptyClassroomItem> {
        val map = LinkedHashMap<String, EmptyClassroomItem>()
        for (it in rawItems) {
            val roomName = it.str("cdmc").ifEmpty { it.str("cdbh") }
            if (roomName.isEmpty()) continue
            var building = it.str("jxlmc").ifEmpty { it.str("lh") }
            val roomType = it.str("cdlbmc").ifEmpty { "普通教室" }
            if (NON_STUDY_BLD_RE.containsMatchIn(building) ||
                NON_STUDY_TYPE_RE.containsMatchIn(roomType) ||
                NON_STUDY_ROOM_RE.containsMatchIn(roomName)
            ) {
                continue
            }
            if (building.isEmpty()) {
                building = BUILDING_PREFIX_RE.find(roomName)?.groupValues?.getOrNull(1) ?: "综合教学区"
            }
            val key = "$building|$roomName"
            if (!map.containsKey(key)) {
                map[key] = EmptyClassroomItem(
                    roomName = roomName,
                    building = building,
                    roomType = roomType,
                    seats = it.str("kwzws").ifEmpty { it.str("zws") },
                    campus = it.str("xqmc").ifEmpty { fallbackCampus }
                )
            }
        }
        return map
    }

    private fun JsonObject.str(key: String): String {
        val p = this[key] as? JsonPrimitive ?: return ""
        val raw = p.contentOrNull ?: return ""
        return if (raw == "null") "" else raw.trim()
    }

    private fun JsonObject.doubleVal(key: String): Double {
        val p = this[key] as? JsonPrimitive ?: return 0.0
        return p.doubleOrNull ?: p.contentOrNull?.trim()?.toDoubleOrNull() ?: 0.0
    }

    companion object {
        private const val MOBILE_CHROME_UA =
            "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

        private val CAS_TICKET_RE = Regex("""[?&]ticket=([^&#\s]+)""", RegexOption.IGNORE_CASE)
        private val TITLE_RE = Regex("""<title[^>]*>([\s\S]*?)</title>""", RegexOption.IGNORE_CASE)
        private val HEAD_RE = Regex("""<head\b[^>]*>[\s\S]*?</head>""", RegexOption.IGNORE_CASE)
        private val COMMENT_RE = Regex("""<!--[\s\S]*?-->""")
        private val SCRIPT_RE = Regex("""<script\b[^>]*>[\s\S]*?</script>""", RegexOption.IGNORE_CASE)
        private val STYLE_RE = Regex("""<style\b[^>]*>[\s\S]*?</style>""", RegexOption.IGNORE_CASE)
        private val NOSCRIPT_RE = Regex("""<noscript\b[^>]*>[\s\S]*?</noscript>""", RegexOption.IGNORE_CASE)
        private val TAG_RE = Regex("""<[^>]+>""")
        private val WS_RE = Regex("""\s+""")

        private val JW_MODULE_DOM_RE = Regex(
            """id\s*=\s*["'](?:xnm|xqm|xqh_id|cdNav|yhxx|tabGrid|ajaxForm)["']|clickMenu\s*\(""",
            RegexOption.IGNORE_CASE
        )
        private val AUTH_FORM_DOM_RE = Regex(
            """id\s*=\s*["']?(?:loginFromId|pwdFromId|phoneFromId|pwdEncryptSalt|cas-login|yhm)["']?|name\s*=\s*["']mm["']|wengine-vpn/js/css/login\.css|action\s*=\s*["'][^"']*/authserver/login|/wengine-vpn/(?:denied|failed)|cas_login=true""",
            RegexOption.IGNORE_CASE
        )
        private val STRONG_AUTH_TITLE_RE = Regex(
            """统一身份认证|web\s*vpn|webvpn|用户登录|访问被拒绝|访问出错|多因子|cas\s*login|登录""",
            RegexOption.IGNORE_CASE
        )
        private val STRONG_AUTH_BODY_RE = Regex(
            """统一身份认证平台|请先登录|登录超时|会话超时|会话已过期|会话已失效|重新登录|未在本地用户库注册|您没有权限访问该资源|该网站无法访问""",
            RegexOption.IGNORE_CASE
        )
        private val SHORT_AUTH_RAW_RE = Regex(
            """login_slogin|login_tickit|authserver/login|cas/login|请先登录|登录超时|会话超时|会话已过期|重新登录""",
            RegexOption.IGNORE_CASE
        )
        private val WEAK_AUTH_BODY_RE = Regex(
            """统一身份认证|用户登录|web\s*vpn|webvpn|统一认证""",
            RegexOption.IGNORE_CASE
        )
        private val WEAK_AUTH_RAW_RE = Regex(
            """cas_login|wengine-vpn/denied|wengine-vpn/failed""",
            RegexOption.IGNORE_CASE
        )
        private val EVAL_INTERCEPT_RE = Regex(
            """(?:完成|未通过|未完成|请先)[^<\n]{0,16}(?:教学评价|评教|问卷)|(?:教学评价|问卷调查|课程未通过)[^<\n]{0,20}(?:未通过|确认后方可|未完成)|确认后方可(?:进行|查询|进入|使用|操作)|你有课程未通过|未通过.*(?:评价|评教)|(?:评价|评教).*未通过"""
        )

        private val CLICK_MENU_RE = Regex("""clickMenu\(\s*['"]([A-Za-z0-9_]+)['"]\s*,\s*['"]([^'"]+)['"](?:\s*,\s*['"]([^'"]*)['"])?""")
        private val OPTION_RE = Regex("""<option\b([^>]*)>([\s\S]*?)</option>""", RegexOption.IGNORE_CASE)
        private val VALUE_ATTR_RE = Regex("""value\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)
        private val SELECTED_ATTR_RE = Regex("""\bselected\b""", RegexOption.IGNORE_CASE)

        private val NON_STUDY_BLD_RE = Regex("""线上教学|无楼号|体育课场地|田径运动场|体育场|体育馆|动物科学基地|动物医院|林学后山基地|林果楼|^工厂$""")
        private val NON_STUDY_TYPE_RE = Regex("""网络教室|运动场|操场|录像""")
        private val NON_STUDY_ROOM_RE = Regex("""网络教学平台|腾讯会议|钉钉直播|微信直播|超星平台|腾讯课堂|线上教学|^校外$|足球场|篮球场|排球场|网球场|羽毛球|游泳池|校道|田径运动场|台球室|乒乓球室|健身室|牛舍|鸡舍|羊舍|动物房|室外基地|后山基地|气象观测场|大厅前空地""")
        private val BUILDING_PREFIX_RE = Regex("""^(第二实验综合楼|轮机实验楼|水产科技楼|海科楼一|艺海楼[ABC]?|广学楼|明德楼|钟海楼|博文楼|兴教楼|兴海楼|兴农楼|望海楼|图海楼|海科楼|海博楼|海驰楼|海润楼|慎思楼|厚为楼|博学楼|明辨楼|海纳楼|海行楼|济航楼|厚德楼|铭德楼|海阳馆|济海楼|电教楼|实验楼|教学楼)""")
        private val NON_PRIME_ROOM_RE = Regex("""实验室|实训|机房|语音|画室|琴房|多功能厅|咖啡|活动中心""")
        private val NON_PRIME_TYPE_RE = Regex("""语音室|计算机房|多功能计算机房|设计教室|画室|绘图教室|艺术类|声乐|键盘|琴房|舞蹈|实验剧场""")
        private val NON_PRIME_BLD_RE = Regex("""工程训练中心|轮机实验楼|水产科技楼|第二实验综合楼|智能制造""")

        /**
         * 1x1 透明 PNG（用于认证 WebView 拦截 3.23MB one1.png / 492KB login_bg.png 等纯装饰背景图，大幅加速登录页打开）
         */
        val TRANSPARENT_1X1_PNG: ByteArray = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4.toByte(),
            0x89.toByte(), 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
            0x54, 0x78, 0x9C.toByte(), 0x63, 0x00, 0x01, 0x00, 0x00,
            0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4.toByte(), 0x00,
            0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(),
            0x42, 0x60, 0x82.toByte()
        )

        /**
         * 判断认证页静态请求是否为纯装饰超大背景图（绝不拦截验证码、二维码、校徽、CSS/JS）。
         */
        fun isHeavyDecorativeAuthAsset(url: String?): Boolean {
            val lower = url.orEmpty().lowercase(Locale.US)
            if (lower.isBlank()) return false
            return lower.contains("/gdouthemedz/static/dzimages/one") ||
                lower.contains("/gdouthemedz/static/web/images/login_bg") ||
                lower.contains("/gdouthemedz/static/dzimages/goleft") ||
                lower.contains("/gdouthemedz/static/dzimages/goright") ||
                lower.contains("/shiyongbanzhu.html")
        }

        internal data class HtmlParts(val raw: String, val title: String, val visibleText: String)

        internal fun extractHtmlParts(rawText: String?): HtmlParts {
            val raw = rawText.orEmpty()
            val title = TITLE_RE.find(raw)?.groupValues?.getOrNull(1)?.replace(WS_RE, " ")?.trim().orEmpty()
            val noHead = HEAD_RE.replace(raw, " ")
            val noScripts = NOSCRIPT_RE.replace(
                STYLE_RE.replace(
                    SCRIPT_RE.replace(
                        COMMENT_RE.replace(noHead, " "),
                        " "
                    ),
                    " "
                ),
                " "
            )
            val visible = TAG_RE.replace(noScripts, " ").replace(WS_RE, " ").trim()
            return HtmlParts(raw, title, visible)
        }

        fun hasJwModuleDom(rawText: String?): Boolean = JW_MODULE_DOM_RE.containsMatchIn(rawText.orEmpty())

        fun hasAuthFormDom(rawText: String?): Boolean = AUTH_FORM_DOM_RE.containsMatchIn(rawText.orEmpty())

        fun isStrongAuthExpiredHtml(rawText: String?): Boolean {
            val p = extractHtmlParts(rawText)
            val hasJw = hasJwModuleDom(p.raw)
            if (hasAuthFormDom(p.raw) && !hasJw) return true
            if (STRONG_AUTH_TITLE_RE.containsMatchIn(p.title) && !hasJw) return true
            if (STRONG_AUTH_BODY_RE.containsMatchIn(p.visibleText) && !hasJw) return true
            if (!hasJw && p.visibleText.length < 80 && SHORT_AUTH_RAW_RE.containsMatchIn(p.raw)) return true
            return false
        }

        fun isEvalInterceptHtml(rawText: String?): Boolean {
            if (isStrongAuthExpiredHtml(rawText)) return false
            val p = extractHtmlParts(rawText)
            return EVAL_INTERCEPT_RE.containsMatchIn(p.visibleText)
        }

        fun isAuthExpiredStatus(status: Int): Boolean = status == 401 || status == 403 || status == 901

        fun isAuthExpiredHtml(rawText: String?): Boolean {
            if (isStrongAuthExpiredHtml(rawText)) return true
            val p = extractHtmlParts(rawText)
            if (hasJwModuleDom(p.raw)) return false
            return WEAK_AUTH_BODY_RE.containsMatchIn(p.visibleText) || WEAK_AUTH_RAW_RE.containsMatchIn(p.raw)
        }

        fun classifyNonJsonHtml(rawText: String?, fallbackMsg: String): String {
            val t = rawText.orEmpty()
            if (isEvalInterceptHtml(t)) {
                return "需先在正方教务页确认通知或完成教学评价后再同步"
            }
            if (isAuthExpiredHtml(t)) {
                return "登录状态已过期，请重新登录"
            }
            if (Regex("无操作权限|无权限|未开放|非查询时间").containsMatchIn(t)) {
                return "教务当前暂未开放该查询入口"
            }
            return fallbackMsg.ifBlank { "教务返回了非数据页，请稍后重试" }
        }

        fun stripProxyPrefix(rawPath: String?): String {
            var s = rawPath.orEmpty().trim()
            if (s.isEmpty()) return "/"
            s = s.replace(Regex("""^https?://[^/]+""", RegexOption.IGNORE_CASE), "")
            s = s.replace(Regex("""^/https?://[^/]+""", RegexOption.IGNORE_CASE), "")
            s = s.replace(Regex("""^/(?:http|https|https-\d+|http-\d+)/[0-9a-f]{16,}""", RegexOption.IGNORE_CASE), "")
            return if (s.startsWith("/")) s else "/$s"
        }

        fun parseMenuEndpoints(html: String): DiscoveredMenuEndpoints {
            val gradeEps = LinkedHashSet<String>()
            val examEps = LinkedHashSet<String>()
            val levelExamEps = LinkedHashSet<String>()
            val scheduleIndexEps = LinkedHashSet<String>()
            val scheduleDataEps = LinkedHashSet<String>()
            val dynamicLinks = mutableListOf<DynamicMenuEntry>()
            val seenDynamic = HashSet<String>()

            CLICK_MENU_RE.findAll(html).forEach { m ->
                val code = m.groupValues[1]
                val path = stripProxyPrefix(m.groupValues[2])
                val title = m.groupValues.getOrNull(3).orEmpty().trim()
                val sep = if (path.contains("?")) "&" else "?"
                if (path.contains("djkscx")) {
                    levelExamEps.add("$path${sep}doType=query&gnmkdm=$code")
                } else if (path.contains("/cjcx/")) {
                    gradeEps.add("$path${sep}doType=query&gnmkdm=$code")
                }
                if (path.contains("/kwgl/") && path.contains("kscx")) {
                    examEps.add("$path${sep}doType=query&gnmkdm=$code")
                }
                if (path.contains("/kbcx/") && path.contains("xskbcx")) {
                    val hasGm = path.contains("gnmkdm=")
                    scheduleIndexEps.add(if (hasGm) path else "$path${sep}gnmkdm=$code&layout=default")
                    scheduleDataEps.add("/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=$code")
                }
                if (title.isNotEmpty() && seenDynamic.add("$code|$path")) {
                    val navPath = if (path.contains("gnmkdm=")) path else "$path${sep}gnmkdm=$code&layout=default"
                    dynamicLinks.add(DynamicMenuEntry(code, navPath, title))
                }
            }
            return DiscoveredMenuEndpoints(
                gradeEps = gradeEps.toList(),
                examEps = examEps.toList(),
                levelExamEps = levelExamEps.toList(),
                scheduleIndexEps = scheduleIndexEps.toList(),
                scheduleDataEps = scheduleDataEps.toList(),
                dynamicLinks = dynamicLinks
            )
        }

        data class ParsedSelectOptions(
            val options: List<TermOption>,
            val defaultIndex: Int,
            val selectedValue: String
        )

        fun parseSelectOptionsFromHtml(html: String, idOrName: String): ParsedSelectOptions {
            val selectRe = Regex(
                """<select\b[^>]*(?:id|name)\s*=\s*["']${Regex.escape(idOrName)}["'][^>]*>([\s\S]*?)</select>""",
                RegexOption.IGNORE_CASE
            )
            val match = selectRe.find(html) ?: return ParsedSelectOptions(emptyList(), 0, "")
            val inner = match.groupValues[1]
            val opts = mutableListOf<TermOption>()
            var defIdx = 0
            OPTION_RE.findAll(inner).forEach { om ->
                val attrs = om.groupValues[1]
                val vm = VALUE_ATTR_RE.find(attrs)
                val value = (vm?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() } ?: "").trim()
                if (value.isEmpty()) return@forEach
                val text = TAG_RE.replace(om.groupValues[2], "").replace("&nbsp;", " ").replace(WS_RE, " ").trim().ifEmpty { value }
                if (SELECTED_ATTR_RE.containsMatchIn(attrs)) {
                    defIdx = opts.size
                }
                opts.add(TermOption(value, text))
            }
            val selectedVal = opts.getOrNull(defIdx)?.value.orEmpty()
            return ParsedSelectOptions(opts, defIdx, selectedVal)
        }

        fun formatSemesterText(rawVal: String, rawText: String): String {
            val v = rawVal.trim()
            val t = rawText.trim()
            if (v == "3" || t == "1") return "第一学期"
            if (v == "12" || t == "2") return "第二学期"
            if (v == "16" || t == "3") return "第三学期"
            return t.ifEmpty { v }
        }

        fun parseWeeks(weekStr: String): List<Int> {
            if (weekStr.isBlank()) return emptyList()
            val groups = weekStr.replace(Regex("[，、;；]"), ",").split(",")
            val weeks = sortedSetOf<Int>()
            val rangeRe = Regex("""第?(\d+)\s*[-~～]\s*(\d+)\s*周?""")
            val singleRe = Regex("""第?(\d+)\s*周?""")
            for (g in groups) {
                val item = g.trim()
                val rm = rangeRe.find(item)
                val sm = if (rm == null) singleRe.find(item) else null
                val s: Int
                val e: Int
                if (rm != null) {
                    s = rm.groupValues[1].toIntOrNull() ?: continue
                    e = rm.groupValues[2].toIntOrNull() ?: continue
                } else if (sm != null) {
                    val n = sm.groupValues[1].toIntOrNull() ?: continue
                    s = n
                    e = n
                } else continue
                if (s < 1 || e < s || e > 40) continue
                val oddOnly = item.contains("(单)") || item.contains("（单）") || item.contains("单周")
                val evenOnly = item.contains("(双)") || item.contains("（双）") || item.contains("双周")
                for (w in s..e) {
                    if (oddOnly && w % 2 == 0) continue
                    if (evenOnly && w % 2 != 0) continue
                    weeks.add(w)
                }
            }
            return weeks.toList()
        }

        fun parseSectionRange(sectionStr: String): Pair<Int, Int>? {
            val m = Regex("""(\d+)\s*(?:[-~～]\s*(\d+))?""").find(sectionStr) ?: return null
            val s = m.groupValues[1].toIntOrNull() ?: return null
            val e = m.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: s
            if (s < 1 || e < s) return null
            return s to e
        }

        fun parseDayOfWeek(raw: String): Int? {
            raw.trim().toIntOrNull()?.takeIf { it in 1..7 }?.let { return it }
            val s = raw.trim()
            return when {
                s.contains("一") || s.contains("1") -> 1
                s.contains("二") || s.contains("2") -> 2
                s.contains("三") || s.contains("3") -> 3
                s.contains("四") || s.contains("4") -> 4
                s.contains("五") || s.contains("5") -> 5
                s.contains("六") || s.contains("6") -> 6
                s.contains("日") || s.contains("天") || s.contains("7") -> 7
                else -> null
            }
        }

        fun normalizeCourseName(rawName: String): String {
            val name = rawName.trim()
            val m = Regex("""^[（(]([^（）()]+)[）)]\s*""").find(name) ?: return name
            val rest = name.substring(m.range.last + 1)
            return if (rest.isNotEmpty()) "$rest(${m.groupValues[1]})" else name
        }

        fun normalizeCourseRemark(raw: String): String {
            val s = raw.replace(Regex("""[\u00a0\s]+"""), " ").trim()
            return if (s.isEmpty() || s == "无" || s == "-" || s == "--" || s == "null") "" else s
        }

        private fun mergeNoteText(a: String, b: String): String {
            val na = a.trim()
            val nb = b.trim()
            if (na.isEmpty()) return nb
            if (nb.isEmpty() || na.contains(nb)) return na
            if (nb.contains(na)) return nb
            return "$na · $nb"
        }

        fun mergeAndDistinctCourses(courses: List<CourseItem>): List<CourseItem> {
            if (courses.size <= 1) return courses
            val list = courses.map { it.copy(weeks = it.weeks.distinct().sorted()) }
                .sortedWith(
                    compareBy<CourseItem> { it.name }
                        .thenBy { it.teacher }
                        .thenBy { it.location }
                        .thenBy { it.dayOfWeek }
                        .thenBy { it.weeks.joinToString(",") }
                        .thenBy { it.startSection }
                )
            val step1 = mutableListOf<CourseItem>()
            var cur = list.first()
            for (i in 1 until list.size) {
                val nxt = list[i]
                val sameMeta = cur.name == nxt.name &&
                    cur.teacher == nxt.teacher &&
                    cur.location == nxt.location &&
                    cur.dayOfWeek == nxt.dayOfWeek &&
                    cur.weeks == nxt.weeks
                if (sameMeta && cur.endSection + 1 == nxt.startSection) {
                    cur = cur.copy(endSection = nxt.endSection, note = mergeNoteText(cur.note, nxt.note))
                } else if (sameMeta && cur.startSection == nxt.startSection && cur.endSection == nxt.endSection) {
                    cur = cur.copy(note = mergeNoteText(cur.note, nxt.note))
                } else {
                    step1.add(cur)
                    cur = nxt
                }
            }
            step1.add(cur)

            val sorted2 = step1.sortedWith(
                compareBy<CourseItem> { it.name }
                    .thenBy { it.teacher }
                    .thenBy { it.location }
                    .thenBy { it.dayOfWeek }
                    .thenBy { it.startSection }
                    .thenBy { it.endSection }
            )
            val step2 = mutableListOf<CourseItem>()
            cur = sorted2.first()
            for (i in 1 until sorted2.size) {
                val nxt = sorted2[i]
                val sameSlot = cur.name == nxt.name &&
                    cur.teacher == nxt.teacher &&
                    cur.location == nxt.location &&
                    cur.dayOfWeek == nxt.dayOfWeek &&
                    cur.startSection == nxt.startSection &&
                    cur.endSection == nxt.endSection
                if (sameSlot) {
                    cur = cur.copy(
                        weeks = (cur.weeks + nxt.weeks).distinct().sorted(),
                        note = mergeNoteText(cur.note, nxt.note)
                    )
                } else {
                    step2.add(cur)
                    cur = nxt
                }
            }
            step2.add(cur)
            return step2
        }

        private fun normalizeDate(raw: String): String? {
            val m = Regex("""(\d{4})[-/.年](\d{1,2})[-/.月](\d{1,2})""").find(raw) ?: return null
            return "${m.groupValues[1]}-${m.groupValues[2].padStart(2, '0')}-${m.groupValues[3].padStart(2, '0')}"
        }

        private fun findSemesterStartDate(el: JsonElement?): String? {
            if (el == null) return null
            when (el) {
                is JsonPrimitive -> return normalizeDate(el.contentOrNull.orEmpty())
                is JsonArray -> {
                    val w1 = el.firstOrNull { item ->
                        val o = item as? JsonObject ?: return@firstOrNull false
                        val zs = (o["zs"] as? JsonPrimitive)?.contentOrNull
                        val zsmc = (o["zsmc"] as? JsonPrimitive)?.contentOrNull
                        zs == "1" || zsmc == "1"
                    } ?: el.firstOrNull()
                    findSemesterStartDate(w1)?.let { return it }
                    for (item in el) {
                        findSemesterStartDate(item)?.let { return it }
                    }
                    return null
                }
                is JsonObject -> {
                    for (f in listOf("zrq", "zcrq", "rq", "ksrq")) {
                        (el[f] as? JsonPrimitive)?.contentOrNull?.let { s ->
                            normalizeDate(s)?.let { return it }
                        }
                    }
                    for (v in el.values) {
                        findSemesterStartDate(v)?.let { return it }
                    }
                    return null
                }
            }
        }

        private fun detectCampusFromKbList(kbList: List<JsonObject>): String {
            var huguang = 0
            var haibin = 0
            var yangjiang = 0
            val hgRe = Regex("""广学楼|明德楼|钟海楼|博文楼|兴海楼|兴教楼|兴农楼|望海楼|图海楼|艺海楼|海科楼|海博楼|水产科技楼|第二实验综合楼|主楼|科技楼""")
            val yjRe = Regex("""慎思楼|海纳楼|厚为楼|博学楼|明辨楼|海科楼一|海行楼|济航楼|海阳馆|海驰楼|海润楼|厚德楼|铭德楼""")
            val hbRe = Regex("""^实\d|海滨|轮机实验楼|电教楼|济海楼""")
            for (raw in kbList) {
                val xq = ((raw["xqmc"] as? JsonPrimitive)?.contentOrNull ?: (raw["cdxqmc"] as? JsonPrimitive)?.contentOrNull).orEmpty()
                val loc = (raw["cdmc"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                when {
                    xq.contains("湖光") -> huguang += 3
                    xq.contains("海滨") -> haibin += 3
                    xq.contains("阳江") -> yangjiang += 3
                }
                when {
                    hgRe.containsMatchIn(loc) -> huguang += 1
                    yjRe.containsMatchIn(loc) -> yangjiang += 1
                    hbRe.containsMatchIn(loc) -> haibin += 1
                }
            }
            val best = listOf("huguang" to huguang, "haibin" to haibin, "yangjiang" to yangjiang).maxByOrNull { it.second }
            return if (best != null && best.second > 0) best.first else ""
        }

        fun resolveSemesterLabel(xqmmc: String, xqm: String, fallbackXqm: String, defaultLabel: String): String {
            val xq = xqmmc.trim()
            if (xq == "1" || xq == "第一学期") return "第一学期"
            if (xq == "2" || xq == "第二学期") return "第二学期"
            if (xq == "3" || xq == "第三学期") return "第三学期"
            if (xq.isNotEmpty()) return xq
            val code = xqm.trim().ifEmpty { fallbackXqm.trim() }
            return when (code) {
                "3" -> "第一学期"
                "12" -> "第二学期"
                "16" -> "第三学期"
                else -> defaultLabel
            }
        }

        fun resolveGradeExamNature(ksxz: String, cxbj: String, kcbj: String): String {
            val rawKsxz = ksxz.trim()
            val rawCxbj = cxbj.trim()
            val rawKcbj = kcbj.trim()
            val isRetake = rawCxbj == "1" || rawCxbj == "是" || rawCxbj.contains("重修") || rawKcbj.contains("重修")
            if (isRetake && (rawKsxz.isEmpty() || rawKsxz == "正常考试" || rawKsxz == "初修")) return "重修"
            if (rawKcbj.isNotEmpty() && rawKcbj != "主修" && (rawKsxz.isEmpty() || rawKsxz == "正常考试")) return rawKcbj
            return rawKsxz.ifEmpty { "正常考试" }
        }

        private fun formatLevelExamDetail(it: JsonObject): String {
            fun s(k: String): String = (it[k] as? JsonPrimitive)?.contentOrNull?.takeUnless { v -> v == "null" }?.trim().orEmpty()
            val parts = mutableListOf<String>()
            s("tlcj").takeIf { it.isNotEmpty() }?.let { parts.add("听力:$it") }
            s("ydcj").takeIf { it.isNotEmpty() }?.let { parts.add("阅读:$it") }
            s("xzcj").takeIf { it.isNotEmpty() }?.let { parts.add("写作:$it") }
            s("zhcj").takeIf { it.isNotEmpty() }?.let { parts.add("综合:$it") }
            s("bscj").takeIf { it.isNotEmpty() }?.let { parts.add("笔试:$it") }
            s("kscj").takeIf { it.isNotEmpty() }?.let { parts.add("口/机试:$it") }
            val ksfs = s("ksfs")
            if (ksfs.isNotEmpty() && parts.none { it.contains(ksfs) }) parts.add(ksfs)
            return parts.joinToString(" · ")
        }

        fun getDefaultAcademicYear(today: LocalDate = LocalDate.now()): Int {
            return if (today.monthValue >= 8) today.year else today.year - 1
        }

        fun isDefaultFirstSemester(today: LocalDate = LocalDate.now()): Boolean {
            return today.monthValue >= 8 || today.monthValue == 1
        }

        private data class CourseSlotFingerprint(
            val name: String,
            val dayOfWeek: Int,
            val startSection: Int,
            val endSection: Int,
            val weeks: List<Int>,
            val location: String
        )

        /**
         * 轻量纯本地调课变动比对：仅比对教务同步课程（忽略用户手动加课），
         * 让「刷新课表 · 检查调课」可直接告诉学生是否发生换教室/调时间/增删课。
         */
        fun summarizeCourseRefreshDiff(
            previousCourses: List<CourseItem>,
            nextCourses: List<CourseItem>,
            practiceCount: Int = 0
        ): CourseRefreshDiffSummary {
            val prevSynced = previousCourses.filter { !it.isManual }
            val nextSynced = nextCourses.filter { !it.isManual }
            val practiceTag = if (practiceCount > 0) "（含实践 ${practiceCount} 门）" else ""

            if (prevSynced.isEmpty()) {
                return CourseRefreshDiffSummary(
                    hasChanges = nextSynced.isNotEmpty(),
                    addedCount = nextSynced.size,
                    message = "课表已同步：共 ${nextSynced.size} 条课程$practiceTag"
                )
            }

            fun toFp(c: CourseItem) = CourseSlotFingerprint(
                name = c.name.trim(),
                dayOfWeek = c.dayOfWeek,
                startSection = c.startSection,
                endSection = c.endSection,
                weeks = c.weeks.distinct().sorted(),
                location = c.location.trim()
            )

            val prevCounts = LinkedHashMap<CourseSlotFingerprint, Int>()
            for (c in prevSynced) {
                val fp = toFp(c)
                prevCounts[fp] = (prevCounts[fp] ?: 0) + 1
            }
            val nextCounts = LinkedHashMap<CourseSlotFingerprint, Int>()
            for (c in nextSynced) {
                val fp = toFp(c)
                nextCounts[fp] = (nextCounts[fp] ?: 0) + 1
            }

            val added = mutableListOf<CourseSlotFingerprint>()
            val removed = mutableListOf<CourseSlotFingerprint>()
            for ((fp, cnt) in nextCounts) {
                val diff = cnt - (prevCounts[fp] ?: 0)
                repeat(diff.coerceAtLeast(0)) { added.add(fp) }
            }
            for ((fp, cnt) in prevCounts) {
                val diff = cnt - (nextCounts[fp] ?: 0)
                repeat(diff.coerceAtLeast(0)) { removed.add(fp) }
            }

            if (added.isEmpty() && removed.isEmpty()) {
                return CourseRefreshDiffSummary(
                    hasChanges = false,
                    message = "课表已是最新，暂无调课或教室变动（共 ${nextSynced.size} 条）"
                )
            }

            // Pair up added & removed slots sharing the same course name as schedule/room adjustments
            val remByName = LinkedHashMap<String, MutableList<CourseSlotFingerprint>>()
            for (fp in removed) {
                remByName.getOrPut(fp.name) { mutableListOf() }.add(fp)
            }
            val pureAdded = mutableListOf<CourseSlotFingerprint>()
            val adjustedNames = LinkedHashSet<String>()
            var adjustedCount = 0
            for (fp in added) {
                val bucket = remByName[fp.name]
                if (bucket != null && bucket.isNotEmpty()) {
                    bucket.removeAt(0)
                    adjustedCount++
                    if (fp.name.isNotEmpty()) adjustedNames.add(fp.name)
                } else {
                    pureAdded.add(fp)
                }
            }
            val pureRemoved = remByName.values.flatten()

            val changedNames = LinkedHashSet<String>().apply {
                addAll(adjustedNames)
                pureAdded.forEach { if (it.name.isNotEmpty()) add(it.name) }
                pureRemoved.forEach { if (it.name.isNotEmpty()) add(it.name) }
            }.toList()

            val parts = mutableListOf<String>()
            if (adjustedCount > 0) parts.add("调整 ${adjustedCount} 处")
            if (pureAdded.isNotEmpty()) parts.add("新增 ${pureAdded.size} 条")
            if (pureRemoved.isNotEmpty()) parts.add("减少 ${pureRemoved.size} 条")

            val sampleText = when {
                changedNames.isEmpty() -> ""
                changedNames.size == 1 -> "（《${changedNames[0]}》）"
                else -> "（《${changedNames[0]}》等 ${changedNames.size} 门）"
            }

            return CourseRefreshDiffSummary(
                hasChanges = true,
                addedCount = pureAdded.size,
                adjustedCount = adjustedCount,
                removedCount = pureRemoved.size,
                changedCourseNames = changedNames,
                message = "发现调课变动：${parts.joinToString("、")}$sampleText，已更新并保留手动调课"
            )
        }

        private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")
    }
}
