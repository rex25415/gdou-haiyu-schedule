package io.github.rex25415.haiyu.schedule.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JsResult
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rex25415.haiyu.schedule.model.ExamItem
import io.github.rex25415.haiyu.schedule.model.GradeItem
import io.github.rex25415.haiyu.schedule.model.LevelExamItem
import io.github.rex25415.haiyu.schedule.sync.GdouJsBridge
import io.github.rex25415.haiyu.schedule.sync.GdouNativeSyncEngine
import io.github.rex25415.haiyu.schedule.sync.GdouSyncCallback
import io.github.rex25415.haiyu.schedule.sync.GdouSyncScript
import io.github.rex25415.haiyu.schedule.sync.SyncChannel
import io.github.rex25415.haiyu.schedule.sync.TermOptionsPayload
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicReference

enum class WebSheetMode {
    /** 极速一次性认证沙盒：拦截 3.7MB 无用装饰背景图，拿到门票 Cookie 瞬间销毁 WebView 交由原生引擎接管！ */
    AUTH_ONLY,
    /** 按需完整正方原网页浏览器（仅在用户主动去选课/评教/查看培养方案/网页表格提取时打开） */
    FULL_BROWSER
}

data class WebSheetRequest(
    val mode: WebSheetMode,
    val channel: SyncChannel,
    val startUrl: String,
    val titleHint: String,
    val preferDesktopUa: Boolean = false,
    val activateWeComTab: Boolean = false,
    val holdOnWebVpnLogin: Boolean = false,
    val preferLegacyWfw: Boolean = false
)

/**
 * 按需创建的轻量一次性认证 / 教务原网页浮层：
 * - 不常驻内存：关闭瞬间彻底执行 WebView.destroy() 释放 Chromium 资源；
 * - 认证模式下拦截 3.23MB one1.png 与 492KB login_bg.png 等纯装饰背景图，登录页体积骤降 84%；
 * - 认证通过拿到有效 Cookie 的那一毫秒立刻停止加载并关闭网页，用户完全无需等待 WebVPN 门户或正方旧网页渲染！
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EphemeralGdouWebSheet(
    request: WebSheetRequest,
    onAuthCompleted: (SyncChannel) -> Unit,
    onSwitchRequest: (WebSheetRequest) -> Unit,
    onDomGradesScraped: (List<GradeItem>, List<LevelExamItem>) -> Unit = { _, _ -> },
    onDomExamsScraped: (List<ExamItem>) -> Unit = {},
    onShowMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var activeChannel by remember(request) { mutableStateOf(request.channel) }
    var desktopMode by remember(request) { mutableStateOf(request.preferDesktopUa) }
    var currentUrl by remember(request) { mutableStateOf(request.startUrl) }
    var pageTitle by remember(request) { mutableStateOf(request.titleHint) }
    var loadProgress by remember(request) { mutableIntStateOf(0) }
    var hasCommittedFirstFrame by remember(request) { mutableStateOf(false) }
    var pageErrorMsg by remember(request) { mutableStateOf<String?>(null) }
    var domWebVpnDenied by remember(request) { mutableStateOf(false) }
    var pendingWeComTab by remember(request) { mutableStateOf(request.activateWeComTab) }
    var hasForwardedIntoVpnJw by remember(request) { mutableStateOf(false) }
    var hasAutoTriggeredCas by remember(request) {
        mutableStateOf(request.holdOnWebVpnLogin || request.startUrl.contains("cas_login=true", ignoreCase = true))
    }
    var hasAutoSwitchedMfa by remember(request) { mutableStateOf(false) }
    var hasAutoRetriedReset by remember(request) { mutableStateOf(false) }
    var authHandoffDispatched by remember(request) { mutableStateOf(false) }
    val defaultMobileUa = remember {
        try {
            WebSettings.getDefaultUserAgent(context)
        } catch (_: Throwable) {
            ""
        }
    }
    var jsAlertDialog by remember { mutableStateOf<Pair<String, JsResult>?>(null) }
    var jsConfirmDialog by remember { mutableStateOf<Pair<String, JsResult>?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pendingTask by remember { mutableStateOf<Runnable?>(null) }
    val trustedUrlRef = remember(request) { AtomicReference(request.startUrl) }

    fun cancelPendingTask() {
        pendingTask?.let { mainHandler.removeCallbacks(it) }
        pendingTask = null
    }

    fun suppressWebVpnFloat(wv: WebView?, targetUrl: String?) {
        if (!GdouSyncScript.extractHostLower(targetUrl).endsWith("webvpn.gdou.edu.cn")) return
        // WebVPN /login?cas_login=true sets Set-Cookie: show_vpn=1, so always pin show_vpn=0 on WebVPN frames
        try {
            CookieManager.getInstance().setCookie("https://webvpn.gdou.edu.cn/", "show_vpn=0; Path=/; Secure")
        } catch (_: Exception) {}
        wv?.evaluateJavascript(
            """
            (function(){
                try {
                    sessionStorage.setItem('hide_vpn_float_window', 'true');
                    document.cookie = 'show_vpn=0; path=/';
                    if (!document.getElementById('haiyu-hide-vpn')) {
                        var st = document.createElement('style');
                        st.id = 'haiyu-hide-vpn';
                        st.textContent = 'body>div[style*="99999"]{display:none!important;}';
                        (document.head || document.documentElement).appendChild(st);
                    }
                    var kids = document.body ? document.body.children : null;
                    if (kids) {
                        for (var i = 0; i < kids.length; i++) {
                            var d = kids[i];
                            if (d && d.style && String(d.style.zIndex) === '99999' && (d.textContent || '').indexOf('您正在使用VPN') !== -1) {
                                d.remove();
                            }
                        }
                    }
                } catch (_) {}
            })();
            """.trimIndent(),
            null
        )
    }

    fun hasDirectCasTgcCookie(): Boolean = try {
        CookieManager.getInstance()
            .getCookie("https://authserver.gdou.edu.cn/")
            .orEmpty()
            .contains("CASTGC=", ignoreCase = true)
    } catch (_: Throwable) {
        false
    }

    fun hasWebVpnTicketCookie(): Boolean = try {
        CookieManager.getInstance()
            .getCookie("https://webvpn.gdou.edu.cn/")
            .orEmpty()
            .contains("wengine_vpn_ticket", ignoreCase = true)
    } catch (_: Throwable) {
        false
    }

    fun clearStaleWebVpnTicketCookie() {
        try {
            val cm = CookieManager.getInstance()
            val existing = cm.getCookie("https://webvpn.gdou.edu.cn/").orEmpty()
            existing.split(";").forEach { part ->
                val cname = part.trim().substringBefore("=").trim()
                if (cname.startsWith("wengine_vpn_ticket", ignoreCase = true)) {
                    cm.setCookie("https://webvpn.gdou.edu.cn/", "$cname=; Max-Age=0; Path=/")
                }
            }
        } catch (_: Throwable) {}
    }

    fun ensureWfwProxyCookiesInWebView() {
        try {
            val cm = CookieManager.getInstance()
            cm.setCookie("https://wfw.gdou.edu.cn/", "urlss=\"https://jw.gdou.edu.cn/\"; Path=/; Secure")
            cm.setCookie("https://wfw.gdou.edu.cn/", "urlss_jw=\"https://jw.gdou.edu.cn/\"; Path=/; Secure")
        } catch (_: Throwable) {}
    }

    fun triggerInstantAuthHandoff(wv: WebView?, completedChannel: SyncChannel) {
        if (authHandoffDispatched) return
        authHandoffDispatched = true
        cancelPendingTask()
        mainHandler.post {
            try { wv?.stopLoading() } catch (_: Throwable) {}
            Thread {
                try { CookieManager.getInstance().flush() } catch (_: Throwable) {}
            }.start()
            onAuthCompleted(completedChannel)
        }
    }

    /**
     * 处理 WebVPN 门户根目录落地：
     * - 正常经 CAS 登录成功（已带 CASTGC）时直接交还原生引擎签票；
     * - 经企业微信扫码等仅打通网关却未登录 CAS 时，不关弹窗，直接在同弹窗内无感跳转反代教务以继续统一认证。
     */
    fun handleWebVpnPortalLanding(wv: WebView?, url: String?): Boolean {
        if (request.mode != WebSheetMode.AUTH_ONLY || authHandoffDispatched || activeChannel != SyncChannel.WEBVPN) {
            return false
        }
        return when (
            GdouSyncScript.resolveWebVpnPortalLandingAction(
                url = url,
                hasDirectCasTgc = hasDirectCasTgcCookie(),
                hasWebVpnTicket = hasWebVpnTicketCookie(),
                hasForwardedIntoVpnJw = hasForwardedIntoVpnJw
            )
        ) {
            GdouSyncScript.WebVpnPortalLandingAction.HANDOFF -> {
                triggerInstantAuthHandoff(wv, SyncChannel.WEBVPN)
                true
            }
            GdouSyncScript.WebVpnPortalLandingAction.FORWARD_TO_PROXIED_JW -> {
                hasForwardedIntoVpnJw = true
                pendingWeComTab = false
                mainHandler.post {
                    try {
                        if (desktopMode) {
                            desktopMode = false
                            wv?.settings?.apply {
                                if (defaultMobileUa.isNotBlank()) userAgentString = defaultMobileUa
                                useWideViewPort = false
                                loadWithOverviewMode = false
                            }
                        }
                        currentUrl = GdouSyncScript.URL_WEBVPN_JW_ENTRY
                        trustedUrlRef.set(GdouSyncScript.URL_WEBVPN_JW_ENTRY)
                        wv?.stopLoading()
                        wv?.loadUrl(GdouSyncScript.URL_WEBVPN_JW_ENTRY)
                    } catch (_: Throwable) {}
                    onShowMessage("WebVPN 网关扫码已通过，请继续完成统一身份认证")
                }
                true
            }
            GdouSyncScript.WebVpnPortalLandingAction.IGNORE -> false
        }
    }

    fun tryAutoFailoverWebVpnDenied(@Suppress("UNUSED_PARAMETER") wv: WebView?): Boolean {
        clearStaleWebVpnTicketCookie()
        return false
    }

    val isWebVpnAccessDenied = remember(currentUrl, pageTitle, pageErrorMsg, domWebVpnDenied) {
        if (!pageErrorMsg.isNullOrBlank()) return@remember false
        val lower = currentUrl.lowercase()
        lower.contains("webvpn.gdou.edu.cn") && (
            domWebVpnDenied ||
                lower.contains("/wengine-vpn/denied") ||
                lower.contains("/wengine-vpn/failed") ||
                pageTitle.contains("访问被拒绝")
            )
    }

    val scrapeBridgeCallback = remember {
        object : GdouSyncCallback {
            override fun onProgress(message: String) {}
            override fun onTermOptionsLoaded(options: TermOptionsPayload) {}
            override fun onCoursesSynced(
                courses: List<io.github.rex25415.haiyu.schedule.model.CourseItem>,
                startDate: String?,
                totalWeeks: Int,
                termLabel: String,
                yearCode: String,
                semesterCode: String,
                detectedCampusId: String,
                practiceCourses: List<io.github.rex25415.haiyu.schedule.model.PracticeCourseItem>
            ) {}
            override fun onGradesSynced(grades: List<GradeItem>, levelExams: List<LevelExamItem>) {
                mainHandler.post { onDomGradesScraped(grades, levelExams) }
            }
            override fun onExamsSynced(exams: List<ExamItem>) {
                mainHandler.post { onDomExamsScraped(exams) }
            }
            override fun onComplete(summary: String) {
                mainHandler.post { onShowMessage(summary) }
            }
            override fun onError(errorMessage: String) {
                mainHandler.post { onShowMessage(errorMessage) }
            }
        }
    }

    // Properly destroy WebView on sheet close so zero Chromium renderer / RAM lingers!
    DisposableEffect(request) {
        onDispose {
            cancelPendingTask()
            val wv = webViewRef
            webViewRef = null
            if (wv != null) {
                try {
                    wv.stopLoading()
                    wv.removeJavascriptInterface("HaiyuBridge")
                    wv.loadUrl("about:blank")
                    wv.clearHistory()
                    wv.removeAllViews()
                    wv.destroy()
                } catch (_: Throwable) {}
            }
            Thread {
                try { CookieManager.getInstance().flush() } catch (_: Throwable) {}
            }.start()
        }
    }

    BackHandler {
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            onDismiss()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
        ) {
            // Compact Header Bar (surface extends cleanly behind status bar)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onDismiss() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "关闭")
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = pageTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = if (request.mode == WebSheetMode.AUTH_ONLY) {
                                    "广海官方认证 · 完成后自动返回"
                                } else {
                                    currentUrl.removePrefix("https://").removePrefix("http://")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (request.mode == WebSheetMode.FULL_BROWSER) {
                            IconButton(
                                onClick = { webViewRef?.let { if (it.canGoBack()) it.goBack() } },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "后退")
                            }
                        }
                        if (activeChannel != SyncChannel.WFW_LEGACY && (request.mode == WebSheetMode.FULL_BROWSER || desktopMode)) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val next = !desktopMode
                                        desktopMode = next
                                        webViewRef?.let { wv ->
                                            wv.settings.userAgentString = if (next) {
                                                GdouSyncScript.DESKTOP_USER_AGENT
                                            } else {
                                                WebSettings.getDefaultUserAgent(context)
                                            }
                                            wv.settings.useWideViewPort = true
                                            wv.settings.loadWithOverviewMode = next
                                            wv.reload()
                                        }
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (desktopMode) Icons.Rounded.Computer else Icons.Rounded.PhoneAndroid,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = if (desktopMode) "电脑版" else "手机版",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = {
                                pageErrorMsg = null
                                webViewRef?.reload()
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
                        }
                    }

                    EphemeralProgressBar { loadProgress }
                }
            }

            if (isWebVpnAccessDenied) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "学校 WebVPN 提示「未在本地用户库注册」",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "统一认证账号已通过，但学校 WebVPN 本地库暂未同步你的学号权限。可先用「企业微信扫码」打通网关（扫完将自动继续统一认证），或连上校园网后切「校园网直连」：",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    onSwitchRequest(
                                        WebSheetRequest(
                                            mode = WebSheetMode.AUTH_ONLY,
                                            channel = SyncChannel.WEBVPN,
                                            startUrl = "https://webvpn.gdou.edu.cn/login",
                                            titleHint = "企业微信扫码打通网关",
                                            preferDesktopUa = true,
                                            activateWeComTab = true,
                                            holdOnWebVpnLogin = true
                                        )
                                    )
                                },
                                shape = RoundedCornerShape(9.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("企业微信扫码过网关", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = {
                                    onSwitchRequest(
                                        WebSheetRequest(
                                            mode = WebSheetMode.AUTH_ONLY,
                                            channel = SyncChannel.ON_CAMPUS,
                                            startUrl = GdouSyncScript.URL_ON_CAMPUS,
                                            titleHint = "校园网直连认证",
                                            preferDesktopUa = false
                                        )
                                    )
                                },
                                shape = RoundedCornerShape(9.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("切校园网直连", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clipToBounds()
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            val cm = CookieManager.getInstance()
                            cm.setAcceptCookie(true)
                            cm.setAcceptThirdPartyCookies(this, true)
                            if (activeChannel == SyncChannel.WEBVPN) {
                                try {
                                    cm.setCookie("https://webvpn.gdou.edu.cn/", "show_vpn=0; Path=/; Secure")
                                } catch (_: Exception) {}
                            } else if (activeChannel == SyncChannel.WFW_LEGACY) {
                                ensureWfwProxyCookiesInWebView()
                            }

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = false
                                allowContentAccess = false
                                builtInZoomControls = true
                                displayZoomControls = false
                                setSupportZoom(true)
                                setSupportMultipleWindows(false)
                                useWideViewPort = true
                                loadWithOverviewMode = desktopMode
                                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                userAgentString = if (desktopMode) {
                                    GdouSyncScript.DESKTOP_USER_AGENT
                                } else {
                                    WebSettings.getDefaultUserAgent(ctx)
                                }
                            }

                            if (request.mode == WebSheetMode.FULL_BROWSER) {
                                addJavascriptInterface(
                                    GdouJsBridge(
                                        callback = scrapeBridgeCallback,
                                        currentUrlProvider = { trustedUrlRef.get() }
                                    ),
                                    "HaiyuBridge"
                                )
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    loadProgress = newProgress
                                    if (newProgress >= 45) hasCommittedFirstFrame = true
                                }

                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    if (!title.isNullOrBlank()) pageTitle = title
                                }

                                override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                                    if (result != null) {
                                        jsAlertDialog = (message ?: "网页提示") to result
                                        return true
                                    }
                                    return super.onJsAlert(view, url, message, result)
                                }

                                override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                                    if (result != null) {
                                        jsConfirmDialog = (message ?: "确认操作？") to result
                                        return true
                                    }
                                    return super.onJsConfirm(view, url, message, result)
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    req: WebResourceRequest?
                                ): WebResourceResponse? {
                                    val reqUrl = req?.url?.toString()
                                    // Block 3.23MB one1.png & 492KB login_bg.png decorative wallpapers for near-instant login!
                                    if (GdouNativeSyncEngine.isHeavyDecorativeAuthAsset(reqUrl)) {
                                        return WebResourceResponse(
                                            "image/png",
                                            null,
                                            ByteArrayInputStream(GdouNativeSyncEngine.TRANSPARENT_1X1_PNG)
                                        )
                                    }
                                    return super.shouldInterceptRequest(view, req)
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    req: WebResourceRequest?
                                ): Boolean {
                                    val u = req?.url?.toString() ?: return false
                                    if (req.isForMainFrame == false && u.equals("about:blank", ignoreCase = true)) {
                                        return false
                                    }
                                    if (u.startsWith("https://", ignoreCase = true)) {
                                        if (
                                            req.isForMainFrame &&
                                            activeChannel == SyncChannel.WFW_LEGACY &&
                                            u.startsWith("https://jw.gdou.edu.cn/", ignoreCase = true)
                                        ) {
                                            ensureWfwProxyCookiesInWebView()
                                            val rewritten = "https://wfw.gdou.edu.cn" + u.substring("https://jw.gdou.edu.cn".length)
                                            mainHandler.post {
                                                currentUrl = rewritten
                                                trustedUrlRef.set(rewritten)
                                                view?.loadUrl(rewritten)
                                            }
                                            return true
                                        }
                                        if (
                                            req.isForMainFrame &&
                                            GdouSyncScript.shouldFastRedirectWebVpnLoginToCas(
                                                url = u,
                                                useOffCampus = activeChannel == SyncChannel.WEBVPN,
                                                preferLegacyWfw = activeChannel == SyncChannel.WFW_LEGACY,
                                                pendingWeComTab = pendingWeComTab,
                                                hasAutoTriggeredCas = hasAutoTriggeredCas
                                            )
                                        ) {
                                            hasAutoTriggeredCas = true
                                            mainHandler.post {
                                                currentUrl = GdouSyncScript.URL_DIRECT_CAS_WEBVPN
                                                trustedUrlRef.set(GdouSyncScript.URL_DIRECT_CAS_WEBVPN)
                                                view?.loadUrl(GdouSyncScript.URL_DIRECT_CAS_WEBVPN)
                                            }
                                            return true
                                        }
                                        return false
                                    }
                                    if (!req.isForMainFrame) return true
                                    val upgraded = GdouJsBridge.upgradeGdouHttpToHttps(u)
                                    if (upgraded != null) {
                                        view?.loadUrl(upgraded)
                                        return true
                                    }
                                    return true
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    cancelPendingTask()
                                    domWebVpnDenied = false
                                    if (!url.isNullOrBlank()) {
                                        currentUrl = url
                                        trustedUrlRef.set(url)
                                        pageErrorMsg = null
                                    }
                                    suppressWebVpnFloat(view, url)

                                    if (request.mode == WebSheetMode.AUTH_ONLY && !url.isNullOrBlank()) {
                                        val lowerStarted = url.lowercase()
                                        // 0) WebVPN local-user-DB denial (/wengine-vpn/denied): if first-party CASTGC is already saved,
                                        //    silently hand off to WFW_LEGACY with ZERO second password and ZERO red error screen!
                                        if (
                                            activeChannel == SyncChannel.WEBVPN &&
                                            (lowerStarted.contains("/wengine-vpn/denied") || lowerStarted.contains("/wengine-vpn/failed"))
                                        ) {
                                            if (tryAutoFailoverWebVpnDenied(view)) return
                                        }
                                        // 1) WebVPN portal root landing: CAS-backed hands off instantly; WeCom gateway-only forwards inside tunnel to proxied JW/CAS
                                        if (handleWebVpnPortalLanding(view, url)) {
                                            return
                                        }
                                        // 1b) Proxied JW page reached directly inside WebVPN -> HANDOFF INSTANTLY!
                                        if (
                                            activeChannel == SyncChannel.WEBVPN &&
                                            GdouSyncScript.resolveJwLoginState(url, pageTitle, null, domConfirmedLoggedIn = false)
                                        ) {
                                            triggerInstantAuthHandoff(view, SyncChannel.WEBVPN)
                                            return
                                        }
                                        // 2) On-campus direct landed on logged-in JW page -> HANDOFF INSTANTLY!
                                        if (
                                            activeChannel == SyncChannel.ON_CAMPUS &&
                                            GdouSyncScript.resolveJwLoginState(url, pageTitle, null, domConfirmedLoggedIn = false)
                                        ) {
                                            triggerInstantAuthHandoff(view, SyncChannel.ON_CAMPUS)
                                            return
                                        }
                                        // 3) CAS service param dropped & landed on authserver personal portal -> CASTGC already valid, hand off!
                                        if (GdouSyncScript.isAuthenticatedCasFallbackUrl(url) && hasDirectCasTgcCookie()) {
                                            triggerInstantAuthHandoff(view, activeChannel)
                                            return
                                        }
                                    } else if (
                                        request.mode == WebSheetMode.FULL_BROWSER &&
                                        activeChannel == SyncChannel.WEBVPN &&
                                        !url.isNullOrBlank() &&
                                        GdouSyncScript.isAuthenticatedWebVpnPortalUrl(url)
                                    ) {
                                        // Even in full-browser mode, skip the WebVPN resource list straight into Zhengfang JW
                                        mainHandler.post {
                                            currentUrl = GdouSyncScript.URL_WEBVPN_JW_ENTRY
                                            trustedUrlRef.set(GdouSyncScript.URL_WEBVPN_JW_ENTRY)
                                            view?.loadUrl(GdouSyncScript.URL_WEBVPN_JW_ENTRY)
                                        }
                                    }
                                }

                                override fun onPageCommitVisible(view: WebView?, url: String?) {
                                    super.onPageCommitVisible(view, url)
                                    hasCommittedFirstFrame = true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    hasCommittedFirstFrame = true
                                    val wv = view ?: return
                                    val finishedUrl = url.orEmpty()
                                    if (finishedUrl.isNotBlank()) {
                                        currentUrl = finishedUrl
                                        trustedUrlRef.set(finishedUrl)
                                    }
                                    suppressWebVpnFloat(wv, finishedUrl)

                                    // Check WebVPN local-user-DB denial text & auto-click WeCom QR tab
                                    if (GdouSyncScript.extractHostLower(finishedUrl).endsWith("webvpn.gdou.edu.cn")) {
                                        wv.evaluateJavascript(
                                            """(function(){ var t = (document.body && document.body.textContent) || ''; return (t.indexOf('未在本地用户库注册') !== -1 || t.indexOf('没有权限访问该资源') !== -1) ? 'DENIED' : 'OK'; })();"""
                                        ) { res ->
                                            if (res != null && res.contains("DENIED") && wv.url == finishedUrl) {
                                                if (!tryAutoFailoverWebVpnDenied(wv)) {
                                                    domWebVpnDenied = true
                                                }
                                            }
                                        }
                                        if (pendingWeComTab && Uri.parse(finishedUrl).path.orEmpty().trimEnd('/') == "/login") {
                                            pendingWeComTab = false
                                            wv.evaluateJavascript(
                                                """(function(){ var tab = document.getElementById('auth_web_wechat'); if (tab) { tab.click(); return 'CLICKED'; } return 'NO'; })();"""
                                            ) { res ->
                                                if (res != null && res.contains("CLICKED")) {
                                                    onShowMessage("已展开企业微信扫码登录，请扫码确认")
                                                }
                                            }
                                        }
                                    }

                                    // Auto-adapt MFA secondary verification page to Desktop viewport
                                    if (GdouSyncScript.isMfaUrl(finishedUrl)) {
                                        if (!desktopMode && !hasAutoSwitchedMfa) {
                                            hasAutoSwitchedMfa = true
                                            desktopMode = true
                                            wv.settings.userAgentString = GdouSyncScript.DESKTOP_USER_AGENT
                                            wv.settings.useWideViewPort = true
                                            wv.settings.loadWithOverviewMode = true
                                            wv.reload()
                                            onShowMessage("已自动适配二次验证页面，请完成企业微信/短信验证")
                                        } else {
                                            wv.evaluateJavascript(
                                                """
                                                (function(){
                                                    if(!document.getElementById('haiyu-mfa-viewport')){
                                                        var m = document.querySelector('meta[name=viewport]');
                                                        if(!m){ m = document.createElement('meta'); m.name='viewport'; document.head.appendChild(m); }
                                                        m.id = 'haiyu-mfa-viewport';
                                                        m.content = 'width=device-width, initial-scale=0.68, maximum-scale=2.0, user-scalable=yes';
                                                    }
                                                })();
                                                """.trimIndent(),
                                                null
                                            )
                                        }
                                        return
                                    }

                                    if (request.mode == WebSheetMode.AUTH_ONLY && !authHandoffDispatched) {
                                        val titleNow = wv.title ?: pageTitle
                                        // Check WebVPN portal root / personal portal / server-forwarded login_slogin DOM
                                        if (handleWebVpnPortalLanding(wv, finishedUrl)) {
                                            return
                                        }
                                        if (GdouSyncScript.isAuthenticatedCasFallbackUrl(finishedUrl) && hasDirectCasTgcCookie()) {
                                            triggerInstantAuthHandoff(wv, activeChannel)
                                            return
                                        }
                                        val host = GdouSyncScript.extractHostLower(finishedUrl)
                                        val path = try { Uri.parse(finishedUrl).path.orEmpty() } catch (_: Exception) { "" }
                                        val query = try { Uri.parse(finishedUrl).query.orEmpty() } catch (_: Exception) { "" }
                                        if (
                                            activeChannel == SyncChannel.WFW_LEGACY &&
                                            host == "wfw.gdou.edu.cn" &&
                                            !titleNow.contains("跳转") &&
                                            !titleNow.contains("统一身份认证") &&
                                            !titleNow.contains("登录") &&
                                            (hasDirectCasTgcCookie() || !query.contains("ticket="))
                                        ) {
                                            ensureWfwProxyCookiesInWebView()
                                            if (hasDirectCasTgcCookie()) {
                                                triggerInstantAuthHandoff(wv, SyncChannel.WFW_LEGACY)
                                                return
                                            }
                                            wv.evaluateJavascript(
                                                """(function(){ var txt = (document.body && document.body.textContent) || ''; var hasForm = !!document.querySelector('input[type=password]'); return (!hasForm && txt.indexOf('正在跳转到统一认证') === -1 && txt.indexOf('正在为您跳转') === -1) ? 'READY' : 'WAIT'; })();"""
                                            ) { res ->
                                                if (res != null && res.contains("READY")) {
                                                    triggerInstantAuthHandoff(wv, SyncChannel.WFW_LEGACY)
                                                }
                                            }
                                            return
                                        }
                                        if (GdouSyncScript.resolveJwLoginState(finishedUrl, titleNow, null, domConfirmedLoggedIn = false)) {
                                            triggerInstantAuthHandoff(wv, activeChannel)
                                            return
                                        }
                                        if (GdouSyncScript.isUrlOnPortalLoginPath(finishedUrl) || GdouSyncScript.isBareJwEntryUrl(finishedUrl)) {
                                            wv.evaluateJavascript(GdouSyncScript.DOM_PORTAL_PROBE_SCRIPT) { res ->
                                                if (res != null && res.contains("LOGGED_IN")) {
                                                    triggerInstantAuthHandoff(wv, activeChannel)
                                                }
                                            }
                                        }
                                    }
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    req: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    if (req?.isForMainFrame != true) return
                                    val desc = error?.description?.toString().orEmpty()
                                    val reqUrl = req.url?.toString().orEmpty()
                                    if (desc.contains("ERR_ABORTED", ignoreCase = true)) return
                                    val upgraded = GdouJsBridge.upgradeGdouHttpToHttps(reqUrl)
                                    if (upgraded != null && desc.contains("CLEARTEXT", ignoreCase = true)) {
                                        mainHandler.post { view?.loadUrl(upgraded) }
                                        return
                                    }
                                    if (!hasAutoRetriedReset && (
                                            desc.contains("ERR_CONNECTION_CLOSED", ignoreCase = true) ||
                                                desc.contains("ERR_EMPTY_RESPONSE", ignoreCase = true) ||
                                                desc.contains("ERR_CONNECTION_RESET", ignoreCase = true)
                                            )
                                    ) {
                                        hasAutoRetriedReset = true
                                        try {
                                            val cm = CookieManager.getInstance()
                                            val reqHost = GdouSyncScript.extractHostLower(reqUrl)
                                            val origin = if (reqHost == "jw.gdou.edu.cn" || reqHost == "wfw.gdou.edu.cn") {
                                                "https://$reqHost/"
                                            } else null
                                            if (origin != null) {
                                                cm.getCookie(origin).orEmpty().split(";").forEach { part ->
                                                    val cname = part.trim().substringBefore("=").trim()
                                                    if (cname.startsWith("ADCCookie", ignoreCase = true)) {
                                                        cm.setCookie(origin, "$cname=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/")
                                                    }
                                                }
                                            }
                                        } catch (_: Throwable) {}
                                        mainHandler.postDelayed({ view?.loadUrl(reqUrl) }, 260L)
                                        return
                                    }
                                    pageErrorMsg = "页面加载失败（${desc.ifBlank { "网络超时或无法连接" }}）"
                                }

                                override fun onReceivedSslError(
                                    view: WebView?,
                                    handler: SslErrorHandler?,
                                    error: SslError?
                                ) {
                                    handler?.cancel()
                                    mainHandler.post {
                                        pageErrorMsg = "检测到 HTTPS 安全证书异常，已自动拦截以保护账号安全。"
                                    }
                                }
                            }

                            webViewRef = this
                            loadUrl(request.startUrl)
                        }
                    }
                )

                if (!hasCommittedFirstFrame && pageErrorMsg == null && !isWebVpnAccessDenied) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.5.dp
                            )
                            Text(
                                text = if (request.mode == WebSheetMode.AUTH_ONLY) {
                                    "正在打开广海统一身份认证..."
                                } else {
                                    "正在加载广海正方教务官网..."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (pageErrorMsg != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WifiOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(38.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("认证页加载受阻", style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = pageErrorMsg!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = {
                                        pageErrorMsg = null
                                        webViewRef?.reload()
                                    }) { Text("重试") }
                                    Button(onClick = {
                                        val nextCh = if (activeChannel == SyncChannel.ON_CAMPUS) {
                                            SyncChannel.WEBVPN
                                        } else {
                                            SyncChannel.ON_CAMPUS
                                        }
                                        onSwitchRequest(
                                            WebSheetRequest(
                                                mode = request.mode,
                                                channel = nextCh,
                                                startUrl = nextCh.authStartUrl(),
                                                titleHint = if (nextCh == SyncChannel.ON_CAMPUS) "校园网直连认证" else "校外免校园网认证",
                                                preferLegacyWfw = false
                                            )
                                        )
                                    }) {
                                        Text(if (activeChannel == SyncChannel.ON_CAMPUS) "切校外免校园网" else "切校园网直连")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom scrape helper bar ONLY in FULL_BROWSER mode when actually viewing a grades/exams/schedule table page
            val isScrapeableTablePage = remember(currentUrl, request.mode) {
                if (request.mode != WebSheetMode.FULL_BROWSER) return@remember false
                val lower = currentUrl.lowercase()
                lower.contains("/cjcx/") || lower.contains("/kwgl/") || lower.contains("/kbcx/")
            }
            if (isScrapeableTablePage) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = {
                                webViewRef?.evaluateJavascript(GdouSyncScript.buildScrapeCurrentPageDomScript(), null)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Explore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("从当前网页表格直接提取", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = { onDismiss() },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("完成返回", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    jsAlertDialog?.let { (msg, res) ->
        AlertDialog(
            onDismissRequest = { res.cancel(); jsAlertDialog = null },
            title = { Text("教务页面提示") },
            text = { Text(msg) },
            confirmButton = { Button(onClick = { res.confirm(); jsAlertDialog = null }) { Text("确定") } }
        )
    }

    jsConfirmDialog?.let { (msg, res) ->
        AlertDialog(
            onDismissRequest = { res.cancel(); jsConfirmDialog = null },
            title = { Text("教务页面确认") },
            text = { Text(msg) },
            confirmButton = { Button(onClick = { res.confirm(); jsConfirmDialog = null }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { res.cancel(); jsConfirmDialog = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun EphemeralProgressBar(progressProvider: () -> Int) {
    val progress = progressProvider()
    if (progress in 1..99) {
        LinearProgressIndicator(
            progress = { progress / 100f },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
