package io.github.rex25415.haiyu.schedule

import io.github.rex25415.haiyu.schedule.sync.CampusNetworkState
import io.github.rex25415.haiyu.schedule.sync.GdouSyncScript
import io.github.rex25415.haiyu.schedule.sync.NetworkChannelProbe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GdouSyncScriptTest {

    @Test
    fun resolveJwLoginState_allowsForwardedPortalWhenDomConfirmedLoggedIn() {
        val sloginUrl = "https://wfw.gdou.edu.cn/https://jw.gdou.edu.cn/xtgl/login_slogin.html"
        val tickitUrl = "https://wfw.gdou.edu.cn/https://jw.gdou.edu.cn/xtgl/login_tickitLogin.html"

        assertTrue(
            GdouSyncScript.resolveJwLoginState(
                url = sloginUrl,
                pageTitle = "教学管理信息服务平台",
                pageErrorMsg = null,
                domConfirmedLoggedIn = true
            )
        )
        assertTrue(
            GdouSyncScript.resolveJwLoginState(
                url = tickitUrl,
                pageTitle = "教学管理信息服务平台",
                pageErrorMsg = null,
                domConfirmedLoggedIn = true
            )
        )
        assertFalse(
            GdouSyncScript.resolveJwLoginState(
                url = sloginUrl,
                pageTitle = "教学管理信息服务平台",
                pageErrorMsg = null,
                domConfirmedLoggedIn = false
            )
        )
    }

    @Test
    fun resolveJwLoginState_rejectsSsoMfaErrorAndLoginTitle() {
        assertFalse(
            GdouSyncScript.resolveJwLoginState(
                url = "https://authserver.gdou.edu.cn/authserver/login?service=https%3A%2F%2Fwfw.gdou.edu.cn%2F",
                pageTitle = "统一身份认证",
                pageErrorMsg = null,
                domConfirmedLoggedIn = true
            )
        )
        assertFalse(
            GdouSyncScript.resolveJwLoginState(
                url = "https://authserver.gdou.edu.cn/authserver/reauthCheck.html",
                pageTitle = "安全验证",
                pageErrorMsg = null,
                domConfirmedLoggedIn = true
            )
        )
        assertFalse(
            GdouSyncScript.resolveJwLoginState(
                url = "https://wfw.gdou.edu.cn/https://jw.gdou.edu.cn/xtgl/login_slogin.html",
                pageTitle = "用户登录",
                pageErrorMsg = null,
                domConfirmedLoggedIn = true
            )
        )
        assertFalse(
            GdouSyncScript.resolveJwLoginState(
                url = "https://wfw.gdou.edu.cn/https://jw.gdou.edu.cn/xtgl/index_initMenu.html",
                pageTitle = "教学管理信息服务平台",
                pageErrorMsg = "页面加载失败",
                domConfirmedLoggedIn = true
            )
        )
        assertTrue(
            GdouSyncScript.resolveJwLoginState(
                url = "https://wfw.gdou.edu.cn/https://jw.gdou.edu.cn/xtgl/index_initMenu.html",
                pageTitle = "教学管理信息服务平台",
                pageErrorMsg = null,
                domConfirmedLoggedIn = false
            )
        )
    }

    @Test
    fun staleChannelError_filtersAbandonedChannelOnly() {
        assertEquals(
            "wfw.gdou.edu.cn",
            GdouSyncScript.extractHostLower("https://wfw.gdou.edu.cn/https://jw.gdou.edu.cn/xtgl/login_slogin.html")
        )
        assertTrue(GdouSyncScript.isStaleChannelError(useOffCampus = true, requestUrl = "https://jw.gdou.edu.cn/"))
        assertFalse(
            GdouSyncScript.isStaleChannelError(
                useOffCampus = true,
                requestUrl = "https://wfw.gdou.edu.cn/https://jw.gdou.edu.cn/xtgl/login_slogin.html"
            )
        )
        assertTrue(GdouSyncScript.isStaleChannelError(useOffCampus = false, requestUrl = "https://wfw.gdou.edu.cn/"))
        assertTrue(GdouSyncScript.isStaleChannelError(useOffCampus = false, requestUrl = "https://webvpn.gdou.edu.cn/"))
        assertFalse(GdouSyncScript.isStaleChannelError(useOffCampus = false, requestUrl = "https://jw.gdou.edu.cn/"))
    }

    @Test
    fun resolveJwLoginState_supportsOfficialWebVpnAndRejectsGateOrPortalRoot() {
        val webVpnLoginUrl = "https://webvpn.gdou.edu.cn/login"
        val webVpnCasUrl = GdouSyncScript.URL_WEBVPN_CAS_LOGIN
        val webVpnRootUrl = "https://webvpn.gdou.edu.cn/"
        val webVpnAuthProxyUrl =
            "https://webvpn.gdou.edu.cn/https/${GdouSyncScript.WEBVPN_AUTH_HEX}/authserver/login?service=https%3A%2F%2Fwebvpn.gdou.edu.cn%2Flogin%3Fcas_login%3Dtrue"
        val webVpnJwSloginUrl =
            "https://webvpn.gdou.edu.cn/https/${GdouSyncScript.WEBVPN_JW_HEX}/xtgl/login_slogin.html"
        val webVpnJwHomeUrl =
            "https://webvpn.gdou.edu.cn/https/${GdouSyncScript.WEBVPN_JW_HEX}/xtgl/index_initMenu.html"

        assertFalse(GdouSyncScript.resolveJwLoginState(webVpnLoginUrl, "WEB VPN", null, true))
        assertFalse(GdouSyncScript.resolveJwLoginState(webVpnCasUrl, "WEB VPN", null, true))
        assertFalse(GdouSyncScript.resolveJwLoginState(webVpnRootUrl, "WebVPN资源门户", null, true))
        assertFalse(GdouSyncScript.resolveJwLoginState(webVpnAuthProxyUrl, "统一身份认证平台", null, true))
        assertFalse(GdouSyncScript.resolveJwLoginState(webVpnJwSloginUrl, "教学管理信息服务平台", null, false))
        assertTrue(GdouSyncScript.resolveJwLoginState(webVpnJwSloginUrl, "教学管理信息服务平台", null, true))
        assertTrue(GdouSyncScript.resolveJwLoginState(webVpnJwHomeUrl, "教学管理信息服务平台", null, false))
    }

    @Test
    fun syncScripts_includeDomAwareLoginCheck() {
        val termScript = GdouSyncScript.buildFetchTermOptionsScript()
        val syncScript = GdouSyncScript.buildSyncScript(
            mode = "all",
            academicYear = "2026",
            semesterCode = "3",
            termLabel = "2026-2027-1"
        )
        assertTrue(termScript.contains("hasLoggedInPortalDom"))
        assertTrue(termScript.contains("login_tickit"))
        assertTrue(syncScript.contains("hasLoggedInPortalDom"))
        assertTrue(GdouSyncScript.DOM_PORTAL_PROBE_SCRIPT.contains("LOGGED_IN"))
    }

    @Test
    fun urlClassifiersAndEmptyClassroomScriptsHandleAllPortalVariants() {
        assertTrue(GdouSyncScript.isMfaUrl("https://authserver.gdou.edu.cn/authserver/reauthCheck.html"))
        assertTrue(GdouSyncScript.isMfaUrl("https://authserver.gdou.edu.cn/authserver/reauthLoginView.html"))
        assertFalse(GdouSyncScript.isMfaUrl("https://jw.gdou.edu.cn/xtgl/index_initMenu.html"))

        assertTrue(GdouSyncScript.isUrlOnPortalLoginPath("https://wfw.gdou.edu.cn/https/foo/xtgl/login_slogin.html"))
        assertTrue(GdouSyncScript.isUrlOnPortalLoginPath("https://jw.gdou.edu.cn/xtgl/login_tickitLogin.html"))
        assertFalse(GdouSyncScript.isUrlOnPortalLoginPath("https://jw.gdou.edu.cn/kbcx/xskbcx_cxXskbcxIndex.html"))

        val roomQueryJs = GdouSyncScript.buildQueryEmptyClassroomsScript(
            campusKeyword = "海滨",
            weekNumber = 13,
            dayOfWeek = 3,
            startSection = 3,
            endSection = 4,
            buildingKeyword = "教学楼"
        )
        assertTrue(roomQueryJs.contains("resolveEmptyRoomQueryContext"))
        assertTrue(roomQueryJs.contains("__haiyuRoomQuerySeq"))
        assertTrue(roomQueryJs.contains("ensureWfwJwSessionIfNeeded"))
        assertTrue(roomQueryJs.contains("zcdMask"))
        assertTrue(roomQueryJs.contains("fetchPaginatedJwItems"))
        assertTrue(roomQueryJs.contains("第二实验综合楼|轮机实验楼|水产科技楼|海科楼一"))
        assertFalse(roomQueryJs.contains("\$COMMON"))
    }

    @Test
    fun upgradedSyncAndDomScriptsContainPaginationRetakeAndMenuIsolationGuards() {
        val syncJs = GdouSyncScript.buildSyncScript(
            mode = "all",
            academicYear = "2026",
            semesterCode = "16",
            termLabel = "2026-2027 第三学期"
        )
        assertTrue(syncJs.contains("fetchPaginatedJwItems"))
        assertTrue(syncJs.contains("classifyNonJsonHtml"))
        assertTrue(syncJs.contains("levelExamEps"))
        assertTrue(syncJs.contains("resolveGradeExamNature"))
        assertTrue(syncJs.contains("resolveSemesterLabel"))
        assertTrue(syncJs.contains("offset < 6"))
        assertTrue(syncJs.contains("normalizeCourseRemark(raw.xkbz)"))
        assertTrue(syncJs.contains("maxWeekNo"))
        assertFalse(syncJs.contains("\$COMMON"))

        val domJs = GdouSyncScript.buildScrapeCurrentPageDomScript()
        assertTrue(domJs.contains("scrapeLevelExamsFromCurrentDom"))
        assertTrue(domJs.contains("scrapeGradesFromCurrentDom"))
        assertTrue(domJs.contains("scrapeExamsFromCurrentDom"))
        assertFalse(domJs.contains("\$COMMON"))
    }

    @Test
    fun ssoHandshakePathIsNeverTreatedAsLoggedInWithoutDomConfirmation() {
        val ssoUrl = "https://jw.gdou.edu.cn/sso/jziotlogin"
        assertTrue(GdouSyncScript.isUrlOnPortalLoginPath(ssoUrl))
        assertFalse(
            GdouSyncScript.resolveJwLoginState(
                url = ssoUrl,
                pageTitle = "广东海洋大学教务系统",
                pageErrorMsg = null,
                domConfirmedLoggedIn = false
            )
        )
    }

    @Test
    fun networkChannelProbeFiltersCaptivePortalsAndChecksAuthHealth() {
        assertFalse(NetworkChannelProbe.isValidProbeResponse(302, "http://172.16.10.1/portal/login"))
        assertFalse(NetworkChannelProbe.isValidProbeResponse(404, null))
        assertFalse(NetworkChannelProbe.isValidProbeResponse(407, null))
        assertFalse(NetworkChannelProbe.isValidProbeResponse(502, null))
        assertTrue(NetworkChannelProbe.isValidProbeResponse(302, "/xtgl/login_slogin.html"))
        assertTrue(
            NetworkChannelProbe.isValidProbeResponse(
                302,
                "http://authserver.gdou.edu.cn/authserver/login?service=https%3A%2F%2Fjw.gdou.edu.cn%2Fsso%2Fjziotlogin"
            )
        )
        assertEquals(
            CampusNetworkState.ON_CAMPUS_DIRECT_READY,
            NetworkChannelProbe.classifyState(jwReachable = true, wfwReachable = true, authReachable = true)
        )
        assertEquals(
            CampusNetworkState.OFF_CAMPUS_ONLY,
            NetworkChannelProbe.classifyState(jwReachable = true, wfwReachable = true, authReachable = false)
        )
        assertFalse(NetworkChannelProbe.shouldRetryJw(jwTimedOut = true, wfwLatencyMs = 45L))
        assertTrue(NetworkChannelProbe.shouldRetryJw(jwTimedOut = true, wfwLatencyMs = 45L, jwTcpConnectTimedOut = false))
        assertTrue(NetworkChannelProbe.shouldRetryJw(jwTimedOut = true, wfwLatencyMs = 700L))
        assertTrue(NetworkChannelProbe.shouldRetryJw(jwTimedOut = false, wfwLatencyMs = 45L))
        assertTrue(NetworkChannelProbe.shouldRetryJw(jwTimedOut = true, wfwLatencyMs = null))

        // On-campus auto-restore only kicks in when user explicitly prefers campus direct and isn't logged in yet
        assertTrue(
            NetworkChannelProbe.shouldAutoRestoreOnCampus(
                userPrefersOffCampus = false,
                currentlyOffCampus = true,
                state = CampusNetworkState.ON_CAMPUS_DIRECT_READY,
                isLoggedIntoJw = false
            )
        )
        assertFalse(
            NetworkChannelProbe.shouldAutoRestoreOnCampus(
                userPrefersOffCampus = true,
                currentlyOffCampus = true,
                state = CampusNetworkState.ON_CAMPUS_DIRECT_READY,
                isLoggedIntoJw = false
            )
        )
        assertFalse(
            NetworkChannelProbe.shouldAutoRestoreOnCampus(
                userPrefersOffCampus = false,
                currentlyOffCampus = true,
                state = CampusNetworkState.ON_CAMPUS_DIRECT_READY,
                isLoggedIntoJw = true
            )
        )
        assertFalse(
            NetworkChannelProbe.shouldAutoRestoreOnCampus(
                userPrefersOffCampus = false,
                currentlyOffCampus = true,
                state = CampusNetworkState.OFF_CAMPUS_ONLY,
                isLoggedIntoJw = false
            )
        )
    }

    @Test
    fun quickRefreshScriptDistinguishesNetworkErrorsFromAuthExpiryAndInteractiveUrls() {
        val quickJs = GdouSyncScript.buildQuickRefreshCoursesScript(
            academicYear = "2026",
            semesterCode = "3",
            termLabel = "2026-2027 第一学期"
        )
        assertTrue(quickJs.contains("courseResult.authExpired === true"))
        assertTrue(quickJs.contains("isAuthExpiredStatus"))
        assertTrue(quickJs.contains("901"))
        assertTrue(quickJs.contains("redirect: \"manual\""))
        assertTrue(quickJs.contains("fetchWithTimeout"))
        assertTrue(quickJs.contains("isAuthExpiredHtml"))
        assertTrue(quickJs.contains("isAuthRedirectUrl"))
        assertTrue(quickJs.contains("networkError: true"))
        // Must NEVER turn status=0 network failures into fake HTTP 500
        assertFalse(quickJs.contains("res.status || 500"))
        assertFalse(quickJs.contains("\$COMMON"))

        assertTrue(GdouSyncScript.mightBeInteractiveLoginUrl("https://authserver.gdou.edu.cn/authserver/login?service=https%3A%2F%2Fjw.gdou.edu.cn%2Fsso%2Fjziotlogin"))
        assertTrue(GdouSyncScript.mightBeInteractiveLoginUrl("https://webvpn.gdou.edu.cn/login"))
        assertTrue(GdouSyncScript.mightBeInteractiveLoginUrl("https://jw.gdou.edu.cn/xtgl/login_slogin.html"))
        assertFalse(GdouSyncScript.mightBeInteractiveLoginUrl("https://jw.gdou.edu.cn/xtgl/index_initMenu.html?jsdm=xs"))
        assertFalse(GdouSyncScript.mightBeInteractiveLoginUrl(GdouSyncScript.URL_WEBVPN_JW_ENTRY))

        // Bare entry URLs without DOM confirmation must NOT impersonate a settled session during transition frames
        assertTrue(GdouSyncScript.isBareJwEntryUrl(GdouSyncScript.URL_ON_CAMPUS))
        assertTrue(GdouSyncScript.isBareJwEntryUrl(GdouSyncScript.URL_WEBVPN_JW_ENTRY))
        assertFalse(GdouSyncScript.isBareJwEntryUrl("https://webvpn.gdou.edu.cn/https/${GdouSyncScript.WEBVPN_JW_HEX}/xtgl/index_initMenu.html?jsdm=xs"))
        assertFalse(
            GdouSyncScript.resolveJwLoginState(
                url = GdouSyncScript.URL_WEBVPN_JW_ENTRY,
                pageTitle = "广东海洋大学教务系统",
                pageErrorMsg = null,
                domConfirmedLoggedIn = false
            )
        )
        assertTrue(
            GdouSyncScript.resolveJwLoginState(
                url = GdouSyncScript.URL_WEBVPN_JW_ENTRY,
                pageTitle = "教学管理信息服务平台",
                pageErrorMsg = null,
                domConfirmedLoggedIn = true
            )
        )
    }

    @Test
    fun webVpnOffCampusGuardsAvoidFalseExpiryAndPrototypeBugs() {
        val quickJs = GdouSyncScript.buildQuickRefreshCoursesScript("2026", "3", "2026-2027 第一学期")
        val gradesJs = GdouSyncScript.buildSyncScript("grades", "2026", "3", "2026-2027 第一学期")
        val roomJs = GdouSyncScript.buildQueryEmptyClassroomsScript("海滨", 13, 3, 5, 6, "")

        // 1. WebVPN fetchJwTextWithFallback must never append naked root paths (which always 302 -> /login)
        val webvpnBranch = Regex("""else if \(host\.includes\("webvpn\.gdou\.edu\.cn"\)\)\s*\{([^}]*)\}""")
            .find(gradesJs)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
        assertTrue(webvpnBranch.contains(GdouSyncScript.WEBVPN_JW_HEX))
        assertFalse(webvpnBranch.contains("const naked"))

        // 2. isAuthExpiredHtml must detect WebVPN login gate HTML & guard non-JSON fetches too
        assertTrue(quickJs.contains("wengine-vpn"))
        assertTrue(quickJs.contains("cas_login"))
        assertTrue(quickJs.contains("!isAuthExpiredHtml(txt) && !trimmed.includes(\"loginFromId\")"))

        // 3. Empty classroom helper must stay free of Array.prototype.find for Zhengfang Prototype.js pages
        assertFalse(roomJs.contains(".find("))

        // 4. WebVPN denied page (/wengine-vpn/denied) must never look logged in and must end background SSO wait cleanly
        val deniedUrl = "https://webvpn.gdou.edu.cn/wengine-vpn/denied"
        assertFalse(
            GdouSyncScript.resolveJwLoginState(
                url = deniedUrl,
                pageTitle = "访问被拒绝",
                pageErrorMsg = null,
                domConfirmedLoggedIn = true
            )
        )
        assertTrue(GdouSyncScript.mightBeInteractiveLoginUrl(deniedUrl))
        assertTrue(quickJs.contains("访问被拒绝"))
    }

    @Test
    fun syncAuditFixesPreventHttpErrorFalseLoginDoublePrefixAndPrototypeBugs() {
        val termJs = GdouSyncScript.buildFetchTermOptionsScript()
        val syncJs = GdouSyncScript.buildSyncScript("all", "2026", "3", "2026-2027 第一学期")
        val quickJs = GdouSyncScript.buildQuickRefreshCoursesScript("2026", "3", "2026-2027 第一学期")

        // 1. 502/503/504 gateway error titles must NEVER impersonate logged-in JW state
        val webvpnJwHome = "https://webvpn.gdou.edu.cn/https/${GdouSyncScript.WEBVPN_JW_HEX}/xtgl/index_initMenu.html"
        assertFalse(GdouSyncScript.resolveJwLoginState(webvpnJwHome, "502 Bad Gateway", null, true))
        assertFalse(GdouSyncScript.resolveJwLoginState(webvpnJwHome, "503 Service Temporarily Unavailable", null, true))
        assertFalse(GdouSyncScript.resolveJwLoginState(webvpnJwHome, "504 Gateway Time-out", null, true))
        assertFalse(GdouSyncScript.resolveJwLoginState(webvpnJwHome, "访问出错", null, true))

        // 2. Proxy prefix stripping, weekday/punctuation parsing, and no Prototype-unsafe .findIndex(
        assertTrue(syncJs.contains("stripProxyPrefix"))
        assertTrue(syncJs.contains("isStrongAuthExpiredHtml"))
        assertTrue(syncJs.contains("scheduleDataEps"))
        assertTrue(syncJs.contains("parseDayOfWeek"))
        assertTrue(syncJs.contains("okModules === 0"))
        assertTrue(quickJs.contains("cache = \"no-store\""))
        assertFalse(termJs.contains(".findIndex("))
        assertFalse(syncJs.contains(".findIndex("))
        assertFalse(quickJs.contains(".find("))

        // 3. Authenticated WebVPN portal root URL classifier (auto-skip portal list straight to JW)
        assertTrue(GdouSyncScript.isAuthenticatedWebVpnPortalUrl("https://webvpn.gdou.edu.cn/"))
        assertTrue(GdouSyncScript.isAuthenticatedWebVpnPortalUrl("https://webvpn.gdou.edu.cn/?cas_login=true"))
        assertTrue(GdouSyncScript.isAuthenticatedWebVpnPortalUrl("https://webvpn.gdou.edu.cn/portal/#/index"))
        assertTrue(GdouSyncScript.isAuthenticatedWebVpnPortalUrl("https://webvpn.gdou.edu.cn/en"))
        assertFalse(GdouSyncScript.isAuthenticatedWebVpnPortalUrl("https://webvpn.gdou.edu.cn/login"))
        assertFalse(GdouSyncScript.isAuthenticatedWebVpnPortalUrl("https://webvpn.gdou.edu.cn/login?cas_login=true&ticket=ST-1"))
        assertFalse(GdouSyncScript.isAuthenticatedWebVpnPortalUrl("https://webvpn.gdou.edu.cn/wengine-vpn/denied"))
        assertFalse(
            GdouSyncScript.isAuthenticatedWebVpnPortalUrl(
                "https://webvpn.gdou.edu.cn/https/${GdouSyncScript.WEBVPN_AUTH_HEX}/authserver/login"
            )
        )
        assertFalse(GdouSyncScript.isAuthenticatedWebVpnPortalUrl(GdouSyncScript.URL_WEBVPN_JW_ENTRY))

        // 4. WebVPN-injected <head> scripts must NEVER trigger false auth-expiry on valid Zhengfang HTML
        assertTrue(termJs.contains("extractHtmlParts"))
        assertTrue(termJs.contains("hasJwModuleDom"))
        assertTrue(syncJs.contains("extractHtmlParts"))

        // 5. Fast off-campus WebVPN /login -> ?cas_login=true skip & concurrent full sync optimizations
        assertTrue(
            GdouSyncScript.shouldFastRedirectWebVpnLoginToCas(
                url = "https://webvpn.gdou.edu.cn/login",
                useOffCampus = true,
                preferLegacyWfw = false,
                pendingWeComTab = false,
                hasAutoTriggeredCas = false
            )
        )
        assertFalse(
            GdouSyncScript.shouldFastRedirectWebVpnLoginToCas(
                url = "https://webvpn.gdou.edu.cn/login?error=1",
                useOffCampus = true,
                preferLegacyWfw = false,
                pendingWeComTab = false,
                hasAutoTriggeredCas = false
            )
        )
        assertFalse(
            GdouSyncScript.shouldFastRedirectWebVpnLoginToCas(
                url = "https://webvpn.gdou.edu.cn/login",
                useOffCampus = true,
                preferLegacyWfw = false,
                pendingWeComTab = true,
                hasAutoTriggeredCas = false
            )
        )
        assertFalse(
            GdouSyncScript.shouldFastRedirectWebVpnLoginToCas(
                url = "https://webvpn.gdou.edu.cn/login",
                useOffCampus = true,
                preferLegacyWfw = false,
                pendingWeComTab = false,
                hasAutoTriggeredCas = true
            )
        )
        assertTrue(syncJs.contains("confirmedZeroTotal"))
        assertTrue(syncJs.contains("document.body.textContent"))

        // 6. Direct CAS post-login personal portal fallback classifier
        assertTrue(GdouSyncScript.isAuthenticatedCasFallbackUrl("https://authserver.gdou.edu.cn/authserver/index.do"))
        assertTrue(GdouSyncScript.isAuthenticatedCasFallbackUrl("https://webvpn.gdou.edu.cn/https/${GdouSyncScript.WEBVPN_AUTH_HEX}/authserver/index.do"))
        assertTrue(GdouSyncScript.isAuthenticatedCasFallbackUrl("https://i.gdou.edu.cn/index.html#/"))
        assertFalse(GdouSyncScript.isAuthenticatedCasFallbackUrl("https://authserver.gdou.edu.cn/authserver/login"))
        assertFalse(GdouSyncScript.isAuthenticatedCasFallbackUrl("https://authserver.gdou.edu.cn/authserver/reauthCheck.html"))
        assertFalse(GdouSyncScript.isAuthenticatedCasFallbackUrl("https://i.gdou.edu.cn/login"))
    }
}
