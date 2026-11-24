package io.github.rex25415.haiyu.schedule

import io.github.rex25415.haiyu.schedule.sync.GdouSyncScript
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
        assertFalse(GdouSyncScript.isStaleChannelError(useOffCampus = false, requestUrl = "https://jw.gdou.edu.cn/"))
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
    }
}
