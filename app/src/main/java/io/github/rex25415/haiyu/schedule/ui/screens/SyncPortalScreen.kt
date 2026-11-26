package io.github.rex25415.haiyu.schedule.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rex25415.haiyu.schedule.model.AppSettings
import io.github.rex25415.haiyu.schedule.model.ClassroomDaySchedule
import io.github.rex25415.haiyu.schedule.model.CourseItem
import io.github.rex25415.haiyu.schedule.model.EmptyClassroomItem
import io.github.rex25415.haiyu.schedule.model.ExamItem
import io.github.rex25415.haiyu.schedule.model.GdouCampus
import io.github.rex25415.haiyu.schedule.model.GradeItem
import io.github.rex25415.haiyu.schedule.model.LevelExamItem
import io.github.rex25415.haiyu.schedule.model.PracticeCourseItem
import io.github.rex25415.haiyu.schedule.sync.CampusNetworkState
import io.github.rex25415.haiyu.schedule.sync.GdouNativeSyncEngine
import io.github.rex25415.haiyu.schedule.sync.GdouSyncScript
import io.github.rex25415.haiyu.schedule.sync.NativeCallResult
import io.github.rex25415.haiyu.schedule.sync.NetworkChannelProbe
import io.github.rex25415.haiyu.schedule.sync.NetworkProbeResult
import io.github.rex25415.haiyu.schedule.sync.QuickRefreshNativeOutcome
import io.github.rex25415.haiyu.schedule.sync.SessionCheckResult
import io.github.rex25415.haiyu.schedule.sync.SyncChannel
import io.github.rex25415.haiyu.schedule.sync.TermOptionsPayload
import io.github.rex25415.haiyu.schedule.ui.components.EmptyClassroomPane
import io.github.rex25415.haiyu.schedule.ui.components.WebSheetMode
import io.github.rex25415.haiyu.schedule.ui.components.WebSheetRequest
import kotlinx.coroutines.launch

data class ExternalAuthTrigger(
    val nonce: Int = 0,
    val channel: SyncChannel = SyncChannel.WEBVPN,
    val resumeUrl: String = GdouSyncScript.URL_DIRECT_CAS_WEBVPN,
    val reason: String = "",
    val isMfa: Boolean = false,
    val isWebVpnDenied: Boolean = false,
    val autoQuickRefreshAfterLogin: Boolean = false
)

private enum class PortalSessionUiState {
    CHECKING,
    READY,
    NEEDS_AUTH,
    NETWORK_ERROR
}

/**
 * 全原生 Material 3「教务中心」（教务同步 & 实时查空教室）：
 * - 与「学业中心 / 设置与工作台」完全同源的顶栏大标题、SingleChoiceSegmentedButtonRow、20dp 无框洁白卡片与宽屏约束；
 * - 零常驻 WebView：未登录时为纯净专注的广海官方连接引导页；认证通过原子切入分步建连过渡态（0 帧闪现）；
 * - 已登录态围绕「刷新课表 · 检查调课」智能比对与「一键全同步」，下半区仅保留唯一不重复的「访问广海正方教务官网」直连卡片。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SyncPortalScreen(
    nativeEngine: GdouNativeSyncEngine,
    settings: AppSettings,
    courses: List<CourseItem> = emptyList(),
    gradesCount: Int = 0,
    examsCount: Int = 0,
    requestedMode: Int = 0,
    modeRequestNonce: Int = 0,
    autoStartSyncOnEnter: Boolean = false,
    onConsumeAutoStartSync: () -> Unit = {},
    externalAuthTrigger: ExternalAuthTrigger = ExternalAuthTrigger(),
    onConsumeExternalAuthTrigger: () -> Unit = {},
    onOpenWebSheet: (
        request: WebSheetRequest,
        onAuthCompleted: (SyncChannel) -> Unit,
        onDismissed: (wasFullBrowser: Boolean) -> Unit
    ) -> Unit,
    onLoginStateChanged: (Boolean) -> Unit,
    onSyncStateChanged: (Boolean) -> Unit,
    onUpdateChannelPrefs: (preferOffCampus: Boolean, preferDesktopUa: Boolean, preferLegacyWfw: Boolean) -> Unit,
    onToggleFavoriteBuilding: (GdouCampus, String) -> Unit = { _, _ -> },
    onToggleFavoriteClassroom: (GdouCampus, String, String, String, String) -> Unit = { _, _, _, _, _ -> },
    onCoursesSynced: (List<CourseItem>, String?, Int, String, String, String, String, List<PracticeCourseItem>) -> GdouCampus?,
    onGradesSynced: (List<GradeItem>, List<LevelExamItem>) -> Unit,
    onExamsSynced: (List<ExamItem>) -> Unit,
    onGradeSkipReasonChanged: (String?) -> Unit = {},
    onShowMessage: (String) -> Unit,
    @Suppress("UNUSED_PARAMETER") onOpenCampusServices: () -> Unit = {},
    onSyncFinishedJumpSchedule: () -> Unit,
    onSyncFinishedJumpAcademic: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val preferredOffCampusChannel = SyncChannel.preferredOffCampus(settings.preferLegacyWfw)

    var activeChannel by remember {
        mutableStateOf(
            when {
                nativeEngine.lastReadyChannel != null -> nativeEngine.lastReadyChannel!!
                NetworkChannelProbe.shouldPreferOffCampusImmediately(context) ->
                    SyncChannel.preferredOffCampus(
                        settings.preferLegacyWfw || (nativeEngine.hasWfwSessionCookie() && !nativeEngine.hasWebVpnTicketCookie())
                    )
                settings.preferOffCampusChannel -> preferredOffCampusChannel
                else -> SyncChannel.ON_CAMPUS
            }
        )
    }
    var activePortalMode by remember { mutableIntStateOf(requestedMode.coerceIn(0, 1)) }
    LaunchedEffect(modeRequestNonce) {
        if (modeRequestNonce > 0) activePortalMode = requestedMode.coerceIn(0, 1)
    }

    var sessionState by remember { mutableStateOf(PortalSessionUiState.CHECKING) }
    var isPostAuthConnecting by remember { mutableStateOf(false) }
    var sessionLatencyMs by remember { mutableStateOf<Long?>(null) }
    var sessionStatusHint by remember { mutableStateOf("正在连接广海教务...") }
    var lastAuthResumeUrl by remember { mutableStateOf(activeChannel.authStartUrl()) }
    var lastWebVpnDenied by remember { mutableStateOf(false) }

    var networkProbe by remember {
        mutableStateOf(NetworkProbeResult(state = CampusNetworkState.PROBING, isOnWifi = false))
    }
    var probeTrigger by remember { mutableIntStateOf(0) }

    var isSyncing by remember { mutableStateOf(false) }
    var syncStatusText by remember { mutableStateOf<String?>(null) }
    var pendingSyncMode by remember { mutableStateOf("all") }
    var pendingAutoActionAfterAuth by remember { mutableStateOf<String?>(null) } // "quick" | "all" | "courses" | "exams" | "empty_room" | null
    var termOptionsDialog by remember { mutableStateOf<TermOptionsPayload?>(null) }
    var syncSuccessSummary by remember { mutableStateOf<String?>(null) }
    var evalInterceptBanner by remember { mutableStateOf<String?>(null) }
    var showSingleSyncMenu by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var diagnosticDialogText by remember { mutableStateOf<String?>(null) }

    // Empty Classroom state
    var emptyClassroomResults by remember { mutableStateOf<List<EmptyClassroomItem>?>(null) }
    var emptyClassroomSummary by remember { mutableStateOf("") }
    var inspectedClassroom by remember { mutableStateOf<ClassroomDaySchedule?>(null) }
    var isInspectingRoom by remember { mutableStateOf(false) }

    val isLoggedIntoJw = sessionState == PortalSessionUiState.READY

    LaunchedEffect(isLoggedIntoJw) { onLoginStateChanged(isLoggedIntoJw) }
    LaunchedEffect(isSyncing) { onSyncStateChanged(isSyncing) }

    fun extractGradeSkipReason(summary: String): String? {
        return Regex("成绩跳过\\(([^)]+)\\)").find(summary)?.groupValues?.getOrNull(1)?.trim()
    }

    fun openDiagnosticDialog() {
        diagnosticDialogText = buildString {
            appendLine("同步架构：纯 Kotlin 原生 HTTPS 并发引擎（0 常驻 WebView）")
            appendLine("当前通道：${activeChannel.label}")
            appendLine("会话状态：$sessionState (${sessionLatencyMs ?: "-"}ms)")
            appendLine(
                "网络探测：" + when (networkProbe.state) {
                    CampusNetworkState.PROBING -> "探测中"
                    CampusNetworkState.ON_CAMPUS_DIRECT_READY -> "校园网直连可用（教务 ${networkProbe.jwLatencyMs ?: "-"}ms · 认证 ${networkProbe.authLatencyMs ?: "-"}ms）"
                    CampusNetworkState.OFF_CAMPUS_ONLY -> "校外通道可用（WebVPN ${networkProbe.wfwLatencyMs ?: "-"}ms · 认证 ${networkProbe.authLatencyMs ?: "-"}ms）"
                    CampusNetworkState.OFFLINE_OR_BLOCKED -> "网络暂未连通"
                }
            )
            appendLine("当前学期：${settings.academicYearLabel} (${settings.lastSyncYearCode}/${settings.lastSyncSemesterCode})")
            appendLine("已缓存菜单数：${nativeEngine.getCachedMenuEndpoints(activeChannel)?.dynamicLinks?.size ?: 0}")
        }
    }

    lateinit var verifySessionNow: (
        channel: SyncChannel,
        forceReverify: Boolean,
        autoTriggerAction: Boolean,
        openAuthIfNeeded: Boolean,
        checkingHint: String
    ) -> Unit

    fun openAuthSheet(
        channel: SyncChannel,
        resumeUrl: String = channel.authStartUrl(),
        isMfa: Boolean = false,
        activateWeComTab: Boolean = false,
        holdOnWebVpnLogin: Boolean = false,
        autoActionAfter: String? = null
    ) {
        activeChannel = channel
        if (autoActionAfter != null) pendingAutoActionAfterAuth = autoActionAfter
        val title = when {
            isMfa -> "统一认证二次安全验证"
            activateWeComTab -> "WebVPN 企业微信扫码"
            channel == SyncChannel.WFW_LEGACY -> "校外兼容通道登录"
            channel == SyncChannel.ON_CAMPUS -> "校园网统一身份认证"
            else -> "广海统一身份认证"
        }
        onOpenWebSheet(
            WebSheetRequest(
                mode = WebSheetMode.AUTH_ONLY,
                channel = channel,
                startUrl = resumeUrl,
                titleHint = title,
                preferDesktopUa = isMfa || activateWeComTab,
                activateWeComTab = activateWeComTab,
                holdOnWebVpnLogin = holdOnWebVpnLogin
            ),
            { authedChannel ->
                activeChannel = authedChannel
                isPostAuthConnecting = true
                sessionState = PortalSessionUiState.CHECKING
                sessionStatusHint = "正在建立教务连接..."
                verifySessionNow(authedChannel, true, true, false, "正在建立教务连接...")
            },
            { _ -> }
        )
    }

    fun openFullBrowser(pathOrFullUrl: String, title: String) {
        val targetUrl = if (pathOrFullUrl.startsWith("https://", ignoreCase = true)) {
            pathOrFullUrl
        } else {
            val clean = GdouNativeSyncEngine.stripProxyPrefix(pathOrFullUrl)
            activeChannel.baseJwUrl() + clean
        }
        onOpenWebSheet(
            WebSheetRequest(
                mode = WebSheetMode.FULL_BROWSER,
                channel = activeChannel,
                startUrl = targetUrl,
                titleHint = title,
                // wfw proxy redirects Desktop UA to direct jw.gdou.edu.cn, so keep Mobile UA on WFW_LEGACY
                preferDesktopUa = if (activeChannel == SyncChannel.WFW_LEGACY) false else settings.preferDesktopUa
            ),
            {},
            { wasFullBrowser ->
                if (wasFullBrowser) {
                    verifySessionNow(activeChannel, false, false, false, "正在校验教务连接...")
                }
            }
        )
    }

    fun openDynamicModuleInBrowser(keywords: List<String>, label: String) {
        val menu = nativeEngine.getCachedMenuEndpoints(activeChannel)
        val matched = menu?.dynamicLinks?.firstOrNull { entry ->
            keywords.any { kw -> entry.title.contains(kw) }
        }
        if (matched != null) {
            openFullBrowser(matched.path, label)
        } else {
            openFullBrowser("/xtgl/index_initMenu.html?jsdm=xs", "广海正方教务官网")
        }
    }

    fun runQuickRefreshInWorkbench(channel: SyncChannel = activeChannel) {
        if (isSyncing) return
        val previousCoursesSnapshot = courses
        scope.launch {
            isSyncing = true
            syncStatusText = "正在刷新课表并检查调课..."
            val outcome = nativeEngine.quickRefreshCourses(
                context = context,
                preferredChannel = channel,
                savedYear = settings.lastSyncYearCode,
                savedSem = settings.lastSyncSemesterCode,
                savedTermLabel = settings.academicYearLabel,
                preferLegacyWfw = settings.preferLegacyWfw,
                onProgress = { msg -> syncStatusText = msg }
            )
            isSyncing = false
            syncStatusText = null
            when (outcome) {
                is QuickRefreshNativeOutcome.Success -> {
                    activeChannel = outcome.channel
                    if (outcome.channel == SyncChannel.WFW_LEGACY && !settings.preferLegacyWfw) {
                        onUpdateChannelPrefs(true, settings.preferDesktopUa, true)
                    } else if (outcome.channel == SyncChannel.WEBVPN && settings.preferLegacyWfw) {
                        onUpdateChannelPrefs(true, settings.preferDesktopUa, false)
                    }
                    sessionState = PortalSessionUiState.READY
                    evalInterceptBanner = null
                    val diff = GdouNativeSyncEngine.summarizeCourseRefreshDiff(
                        previousCourses = previousCoursesSnapshot,
                        nextCourses = outcome.courses,
                        practiceCount = outcome.practiceCourses.size
                    )
                    val detected = onCoursesSynced(
                        outcome.courses,
                        outcome.startDate,
                        outcome.totalWeeks,
                        outcome.termLabel,
                        outcome.yearCode,
                        outcome.semesterCode,
                        outcome.detectedCampusId,
                        outcome.practiceCourses
                    )
                    val campusChanged = detected != null && detected != settings.campus
                    onShowMessage(
                        if (campusChanged) "${diff.message} · 已切换「${detected!!.label}」作息"
                        else diff.message
                    )
                }
                is QuickRefreshNativeOutcome.NeedsLogin -> {
                    activeChannel = outcome.channel
                    sessionState = PortalSessionUiState.NEEDS_AUTH
                    lastWebVpnDenied = outcome.isWebVpnDenied
                    lastAuthResumeUrl = outcome.resumeUrl
                    sessionStatusHint = outcome.reason
                    openAuthSheet(
                        channel = outcome.channel,
                        resumeUrl = outcome.resumeUrl,
                        isMfa = outcome.isMfa,
                        autoActionAfter = "quick"
                    )
                }
                is QuickRefreshNativeOutcome.Error -> {
                    if (outcome.isEvalIntercept) evalInterceptBanner = outcome.message
                    onShowMessage(outcome.message)
                }
            }
        }
    }

    fun startSyncFlow(mode: String) {
        if (isSyncing) return
        pendingSyncMode = mode
        scope.launch {
            if (mode == "grades") {
                isSyncing = true
                syncStatusText = "正在同步历史成绩与绩点..."
                val res = nativeEngine.syncModules(activeChannel, "grades", "", "", "") { syncStatusText = it }
                isSyncing = false
                syncStatusText = null
                if (res.authExpired != null) {
                    sessionState = PortalSessionUiState.NEEDS_AUTH
                    lastWebVpnDenied = res.authExpired.isWebVpnDenied
                    openAuthSheet(activeChannel, res.authExpired.resumeUrl, isMfa = res.authExpired.isMfa)
                    return@launch
                }
                if (res.evalInterceptMessage != null) evalInterceptBanner = res.evalInterceptMessage
                val gOk = res.gradesResult as? NativeCallResult.Success
                if (gOk != null) {
                    onGradesSynced(gOk.data.grades, gOk.data.levelExams)
                    extractGradeSkipReason(res.summaryText)?.let { onGradeSkipReasonChanged(it) }
                    syncSuccessSummary = res.summaryText
                } else {
                    onShowMessage(res.errorText ?: "成绩同步失败")
                }
                return@launch
            }

            isSyncing = true
            syncStatusText = "正在读取学年学期..."
            when (val opts = nativeEngine.fetchAcademicOptions(activeChannel)) {
                is NativeCallResult.Success -> {
                    isSyncing = false
                    syncStatusText = null
                    sessionState = PortalSessionUiState.READY
                    termOptionsDialog = opts.data
                }
                is NativeCallResult.AuthExpired -> {
                    isSyncing = false
                    syncStatusText = null
                    sessionState = PortalSessionUiState.NEEDS_AUTH
                    lastWebVpnDenied = opts.isWebVpnDenied
                    lastAuthResumeUrl = opts.resumeUrl
                    sessionStatusHint = opts.message
                    openAuthSheet(activeChannel, opts.resumeUrl, isMfa = opts.isMfa, autoActionAfter = mode)
                }
                is NativeCallResult.EvalIntercept -> {
                    isSyncing = false
                    syncStatusText = null
                    evalInterceptBanner = opts.message
                    onShowMessage(opts.message)
                }
                is NativeCallResult.Failure -> {
                    isSyncing = false
                    syncStatusText = null
                    if (opts.isNetworkError && activeChannel == SyncChannel.ON_CAMPUS) {
                        probeTrigger++
                    }
                    onShowMessage(opts.message)
                }
            }
        }
    }

    verifySessionNow = { channel, forceReverify, autoTriggerAction, openAuthIfNeeded, checkingHint ->
        scope.launch {
            sessionState = PortalSessionUiState.CHECKING
            sessionStatusHint = checkingHint
            val result = nativeEngine.establishOrVerifySession(channel, forceReverify = forceReverify)
            isPostAuthConnecting = false
            when (result) {
                is SessionCheckResult.Ready -> {
                    activeChannel = result.channel
                    if (result.channel == SyncChannel.WFW_LEGACY && !settings.preferLegacyWfw) {
                        onUpdateChannelPrefs(true, settings.preferDesktopUa, true)
                    } else if (result.channel == SyncChannel.WEBVPN && settings.preferLegacyWfw) {
                        onUpdateChannelPrefs(true, settings.preferDesktopUa, false)
                    }
                    sessionState = PortalSessionUiState.READY
                    sessionLatencyMs = result.latencyMs
                    lastWebVpnDenied = false
                    sessionStatusHint = "连接正常"
                    if (autoTriggerAction) {
                        val pending = pendingAutoActionAfterAuth
                        pendingAutoActionAfterAuth = null
                        when (pending) {
                            "quick" -> runQuickRefreshInWorkbench(result.channel)
                            "empty_room" -> activePortalMode = 1
                            "all", "courses", "exams" -> startSyncFlow(pending)
                            else -> startSyncFlow("all")
                        }
                    } else {
                        // Silently warm academic year/semester picker in background -> 0ms instant open on tap!
                        scope.launch { nativeEngine.prefetchAcademicOptionsQuietly(result.channel) }
                    }
                }
                is SessionCheckResult.NeedsAuth -> {
                    activeChannel = result.channel
                    sessionState = PortalSessionUiState.NEEDS_AUTH
                    lastAuthResumeUrl = result.resumeUrl
                    lastWebVpnDenied = result.isWebVpnDenied
                    if (result.isWebVpnDenied && !settings.preferLegacyWfw) {
                        onUpdateChannelPrefs(true, settings.preferDesktopUa, true)
                    }
                    sessionStatusHint = result.reason
                    if (openAuthIfNeeded || (autoTriggerAction && (result.isMfa || result.isWebVpnDenied))) {
                        openAuthSheet(
                            channel = result.channel,
                            resumeUrl = result.resumeUrl,
                            isMfa = result.isMfa,
                            autoActionAfter = pendingAutoActionAfterAuth ?: "all"
                        )
                    }
                }
                is SessionCheckResult.NetworkError -> {
                    sessionState = PortalSessionUiState.NETWORK_ERROR
                    sessionStatusHint = result.message
                    if (channel == SyncChannel.ON_CAMPUS) {
                        probeTrigger++
                    }
                }
            }
        }
    }

    // Handle external login request once (e.g., home screen quick-refresh detected expired session)
    LaunchedEffect(externalAuthTrigger.nonce) {
        if (externalAuthTrigger.nonce > 0) {
            val trig = externalAuthTrigger
            onConsumeExternalAuthTrigger()
            activeChannel = trig.channel
            lastWebVpnDenied = trig.isWebVpnDenied
            lastAuthResumeUrl = trig.resumeUrl
            sessionState = PortalSessionUiState.NEEDS_AUTH
            if (trig.reason.isNotBlank()) {
                sessionStatusHint = trig.reason
            }
            openAuthSheet(
                channel = trig.channel,
                resumeUrl = trig.resumeUrl,
                isMfa = trig.isMfa,
                autoActionAfter = if (trig.autoQuickRefreshAfterLogin) "quick" else null
            )
        }
    }

    // Initial lightweight pure-HTTP session check (0ms instant when cookies empty!)
    LaunchedEffect(Unit) {
        if (externalAuthTrigger.nonce == 0) {
            if (autoStartSyncOnEnter) {
                onConsumeAutoStartSync()
                pendingAutoActionAfterAuth = "all"
                verifySessionNow(activeChannel, false, true, true, "正在连接广海教务...")
            } else {
                verifySessionNow(activeChannel, false, false, false, "正在连接广海教务...")
            }
        }
    }

    // Lightweight background network probe (cached 45s; skips dead campus socket on pure 4G/5G!)
    LaunchedEffect(probeTrigger) {
        val probe = NetworkChannelProbe.probeCached(
            context = context,
            maxAgeMs = 45_000L,
            forceRefresh = probeTrigger > 0
        )
        networkProbe = probe
        if (sessionState != PortalSessionUiState.READY) {
            if (NetworkChannelProbe.shouldAutoFallbackToOffCampus(activeChannel.isOffCampus, probe.state)) {
                val offTarget = SyncChannel.preferredOffCampus(settings.preferLegacyWfw)
                activeChannel = offTarget
                verifySessionNow(offTarget, true, false, false, "正在连接校外通道...")
            } else if (
                NetworkChannelProbe.shouldAutoRestoreOnCampus(
                    userPrefersOffCampus = settings.preferOffCampusChannel,
                    currentlyOffCampus = activeChannel.isOffCampus,
                    state = probe.state,
                    isLoggedIntoJw = false
                )
            ) {
                activeChannel = SyncChannel.ON_CAMPUS
                verifySessionNow(SyncChannel.ON_CAMPUS, true, false, false, "正在连接校园网教务...")
            }
        }
    }

    fun switchChannel(target: SyncChannel, persistChoice: Boolean = true) {
        activeChannel = target
        lastWebVpnDenied = false
        evalInterceptBanner = null
        if (persistChoice) {
            val nextLegacyWfw = when (target) {
                SyncChannel.WFW_LEGACY -> true
                SyncChannel.WEBVPN -> false
                SyncChannel.ON_CAMPUS -> settings.preferLegacyWfw
            }
            onUpdateChannelPrefs(target.isOffCampus, settings.preferDesktopUa, nextLegacyWfw)
        }
        verifySessionNow(target, true, false, false, "正在切换至${target.label}...")
    }

    fun togglePrimaryChannel() {
        val target = if (activeChannel == SyncChannel.ON_CAMPUS) {
            SyncChannel.preferredOffCampus(settings.preferLegacyWfw)
        } else {
            SyncChannel.ON_CAMPUS
        }
        switchChannel(target)
    }

    val shortChannelLabel = when (activeChannel) {
        SyncChannel.ON_CAMPUS -> "校园网直连"
        SyncChannel.WEBVPN -> "校外免校园网"
        SyncChannel.WFW_LEGACY -> "校外兼容(wfw)"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
        ) {
            // Unified Top Header matching AcademicScreen & SettingsScreen DNA
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "教务中心",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val (badgeBg, badgeFg, badgeLabel) = when (sessionState) {
                                PortalSessionUiState.READY -> Triple(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                    MaterialTheme.colorScheme.onPrimaryContainer,
                                    "已连接"
                                )
                                PortalSessionUiState.CHECKING -> Triple(
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                    "连接中"
                                )
                                PortalSessionUiState.NETWORK_ERROR -> Triple(
                                    MaterialTheme.colorScheme.errorContainer,
                                    MaterialTheme.colorScheme.onErrorContainer,
                                    "未连通"
                                )
                                PortalSessionUiState.NEEDS_AUTH -> Triple(
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                    "未登录"
                                )
                            }
                            Surface(
                                color = badgeBg,
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = badgeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = badgeFg,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
                                )
                            }
                        }

                        Box {
                            IconButton(
                                onClick = { showOverflowMenu = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "更多选项",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (activeChannel == SyncChannel.ON_CAMPUS) "切换至校外免校园网"
                                            else "切换至校园网直连"
                                        )
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.SwapHoriz, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        togglePrimaryChannel()
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (activeChannel == SyncChannel.WFW_LEGACY) "校外改用 WebVPN"
                                            else "校外改用兼容通道(wfw)"
                                        )
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.Public, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        val nextGateway = if (activeChannel == SyncChannel.WFW_LEGACY) {
                                            SyncChannel.WEBVPN
                                        } else {
                                            SyncChannel.WFW_LEGACY
                                        }
                                        switchChannel(nextGateway)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("重新检测连接") },
                                    leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        probeTrigger++
                                        verifySessionNow(activeChannel, true, false, false, "正在重新校验连接...")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("网络与引擎诊断") },
                                    leadingIcon = { Icon(Icons.Rounded.BugReport, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        openDiagnosticDialog()
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (isLoggedIntoJw) "退出当前教务账号" else "清除登录缓存",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Rounded.DeleteSweep,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        nativeEngine.clearAllCookiesAndCache()
                                        sessionState = PortalSessionUiState.NEEDS_AUTH
                                        lastWebVpnDenied = false
                                        evalInterceptBanner = null
                                        sessionStatusHint = "已清除登录缓存，请重新认证"
                                        onShowMessage("已清除教务会话与登录缓存")
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = activePortalMode == 0,
                            onClick = { activePortalMode = 0 },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Text(
                                text = "教务同步",
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        SegmentedButton(
                            selected = activePortalMode == 1,
                            onClick = { activePortalMode = 1 },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Text(
                                text = "查空教室",
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (activePortalMode == 1) {
                // 100% Native HTTP Empty Classroom Finder (ZERO WebView!)
                EmptyClassroomPane(
                    settings = settings,
                    courses = courses,
                    isLoggedIntoJw = isLoggedIntoJw,
                    isQuerying = isSyncing,
                    results = emptyClassroomResults,
                    summary = emptyClassroomSummary,
                    inspectedSchedule = inspectedClassroom,
                    isInspectingRoom = isInspectingRoom,
                    onGoToWebLogin = {
                        activePortalMode = 0
                        openAuthSheet(activeChannel, autoActionAfter = "empty_room")
                    },
                    onExecuteQuery = { campusKeyword, weekNumber, dayOfWeek, startSection, endSection, buildingKeyword ->
                        scope.launch {
                            isSyncing = true
                            syncStatusText = "正在查询${campusKeyword}校区空教室..."
                            when (
                                val res = nativeEngine.queryEmptyClassrooms(
                                    channel = activeChannel,
                                    campusKeyword = campusKeyword,
                                    weekNumber = weekNumber,
                                    dayOfWeek = dayOfWeek,
                                    startSection = startSection,
                                    endSection = endSection,
                                    buildingKeyword = buildingKeyword,
                                    fallbackAcademicYear = settings.lastSyncYearCode,
                                    fallbackSemesterCode = settings.lastSyncSemesterCode
                                )
                            ) {
                                is NativeCallResult.Success -> {
                                    emptyClassroomResults = res.data.rooms
                                    emptyClassroomSummary = res.data.summary
                                }
                                is NativeCallResult.AuthExpired -> {
                                    sessionState = PortalSessionUiState.NEEDS_AUTH
                                    onShowMessage(res.message)
                                    openAuthSheet(activeChannel, res.resumeUrl, isMfa = res.isMfa, autoActionAfter = "empty_room")
                                }
                                is NativeCallResult.EvalIntercept -> {
                                    evalInterceptBanner = res.message
                                    onShowMessage(res.message)
                                }
                                is NativeCallResult.Failure -> {
                                    onShowMessage(res.message)
                                }
                            }
                            isSyncing = false
                            syncStatusText = null
                        }
                    },
                    onInspectRoomDay = { campus, weekNumber, dayOfWeek, buildingName, roomName, seats, roomType ->
                        scope.launch {
                            isInspectingRoom = true
                            inspectedClassroom = ClassroomDaySchedule(
                                campus = campus,
                                building = buildingName,
                                roomName = roomName,
                                weekNumber = weekNumber,
                                dayOfWeek = dayOfWeek,
                                seats = seats,
                                roomType = roomType,
                                slots = emptyList()
                            )
                            when (
                                val res = nativeEngine.inspectRoomDaySchedule(
                                    channel = activeChannel,
                                    campusKeyword = campus.label.removeSuffix("校区"),
                                    weekNumber = weekNumber,
                                    dayOfWeek = dayOfWeek,
                                    buildingName = buildingName,
                                    roomName = roomName,
                                    fallbackAcademicYear = settings.lastSyncYearCode,
                                    fallbackSemesterCode = settings.lastSyncSemesterCode
                                )
                            ) {
                                is NativeCallResult.Success -> {
                                    inspectedClassroom = ClassroomDaySchedule(
                                        campus = campus,
                                        building = res.data.building.ifBlank { buildingName },
                                        roomName = res.data.roomName.ifBlank { roomName },
                                        weekNumber = res.data.weekNumber,
                                        dayOfWeek = res.data.dayOfWeek,
                                        seats = res.data.seats.ifBlank { seats },
                                        roomType = res.data.roomType.ifBlank { roomType },
                                        slots = res.data.slots
                                    )
                                }
                                is NativeCallResult.AuthExpired -> {
                                    inspectedClassroom = null
                                    sessionState = PortalSessionUiState.NEEDS_AUTH
                                    onShowMessage(res.message)
                                    openAuthSheet(activeChannel, res.resumeUrl, isMfa = res.isMfa, autoActionAfter = "empty_room")
                                }
                                is NativeCallResult.EvalIntercept -> {
                                    inspectedClassroom = null
                                    onShowMessage(res.message)
                                }
                                is NativeCallResult.Failure -> {
                                    inspectedClassroom = null
                                    onShowMessage(res.message)
                                }
                            }
                            isInspectingRoom = false
                        }
                    },
                    onDismissInspectedRoom = {
                        isInspectingRoom = false
                        inspectedClassroom = null
                    },
                    onToggleFavoriteBuilding = onToggleFavoriteBuilding,
                    onToggleFavoriteClassroom = onToggleFavoriteClassroom,
                    onOpenOriginalWeb = {
                        openFullBrowser("/cdjy/cdjy_cxKxcdlb.html?gnmkdm=N2155&layout=default", "正方空教室查询页")
                    },
                    onShowMessage = onShowMessage
                )
            } else {
                // Mode 0: Clean, Organic Native Sync Hub (ZERO WebView until requested!)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Contextual Eval / Academic-Notice Intercept Banner (only shown when actually blocked!)
                    if (evalInterceptBanner != null) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "教务要求先完成教学评价或确认学业通知，才可同步完整数据",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        evalInterceptBanner = null
                                        openDynamicModuleInBrowser(listOf("评价", "评教", "问卷"), "教学评价")
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("去处理", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "关闭",
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable { evalInterceptBanner = null }
                                )
                            }
                        }
                    }

                    // Contextual Campus-Wi-Fi faster-route tip (only when on off-campus channel while campus Wi-Fi is ready)
                    if (networkProbe.state == CampusNetworkState.ON_CAMPUS_DIRECT_READY && activeChannel.isOffCampus) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.78f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    switchChannel(SyncChannel.ON_CAMPUS)
                                    onShowMessage("已切至「校园网直连」")
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "已检测到校园网，点此切换「校园网直连」更快",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    } else if (networkProbe.state == CampusNetworkState.OFFLINE_OR_BLOCKED) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { probeTrigger++ }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "暂未连通学校服务器，请检查流量或校园网认证（点此重测）",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    when (sessionState) {
                        // ===== STATE 1: UNAUTHENTICATED / NETWORK ERROR (Pure focused welcome card!) =====
                        PortalSessionUiState.NEEDS_AUTH, PortalSessionUiState.NETWORK_ERROR -> {
                            Spacer(modifier = Modifier.height(6.dp))
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (sessionState == PortalSessionUiState.NETWORK_ERROR) {
                                                    MaterialTheme.colorScheme.errorContainer
                                                } else {
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (sessionState == PortalSessionUiState.NETWORK_ERROR) {
                                                Icons.Rounded.WifiOff
                                            } else {
                                                Icons.Rounded.School
                                            },
                                            contentDescription = null,
                                            tint = if (sessionState == PortalSessionUiState.NETWORK_ERROR) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            },
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = if (sessionState == PortalSessionUiState.NETWORK_ERROR) {
                                                "暂未连通学校网络"
                                            } else {
                                                "连接广海教务"
                                            },
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "同步本学期课表与调课变动、成绩绩点与考场安排",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }

                                    // Compact auto-matched channel switcher pill (replaces heavy double-bar!)
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        shape = RoundedCornerShape(999.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .clickable(enabled = !isSyncing) { togglePrimaryChannel() }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary)
                                            )
                                            Text(
                                                text = shortChannelLabel,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Icon(
                                                imageVector = Icons.Rounded.SwapHoriz,
                                                contentDescription = "切换网络通道",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }

                                    if (lastWebVpnDenied) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "学校 WebVPN 提示「未在本地用户库注册」，请使用下方「企业微信扫码」或「备用入口(wfw)」登录。",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                    } else if (sessionState == PortalSessionUiState.NETWORK_ERROR) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = sessionStatusHint,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    // Single prominent primary login CTA
                                    Button(
                                        onClick = {
                                            openAuthSheet(
                                                channel = activeChannel,
                                                resumeUrl = lastAuthResumeUrl.ifBlank { activeChannel.authStartUrl() },
                                                autoActionAfter = "all"
                                            )
                                        },
                                        enabled = !isSyncing,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Rounded.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (activeChannel == SyncChannel.ON_CAMPUS) "校园网统一身份认证登录" else "广海统一身份认证登录",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Subtle secondary login links (auto-upgraded to tonal buttons when WebVPN denied)
                                    if (lastWebVpnDenied) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            FilledTonalButton(
                                                onClick = {
                                                    openAuthSheet(
                                                        channel = SyncChannel.WEBVPN,
                                                        resumeUrl = "https://webvpn.gdou.edu.cn/login",
                                                        activateWeComTab = true,
                                                        holdOnWebVpnLogin = true,
                                                        autoActionAfter = "all"
                                                    )
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(40.dp)
                                            ) {
                                                Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text("企业微信扫码", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                            }
                                            FilledTonalButton(
                                                onClick = {
                                                    openAuthSheet(
                                                        channel = SyncChannel.WFW_LEGACY,
                                                        resumeUrl = GdouSyncScript.URL_DIRECT_CAS_WFW,
                                                        autoActionAfter = "all"
                                                    )
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(40.dp)
                                            ) {
                                                Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text("兼容通道(wfw)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                            }
                                        }
                                    } else {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    openAuthSheet(
                                                        channel = SyncChannel.WEBVPN,
                                                        resumeUrl = "https://webvpn.gdou.edu.cn/login",
                                                        activateWeComTab = true,
                                                        holdOnWebVpnLogin = true,
                                                        autoActionAfter = "all"
                                                    )
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(15.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("企业微信扫码", fontSize = 12.5.sp)
                                            }
                                            Text(
                                                text = "·",
                                                color = MaterialTheme.colorScheme.outlineVariant,
                                                modifier = Modifier.padding(horizontal = 2.dp)
                                            )
                                            TextButton(
                                                onClick = {
                                                    openAuthSheet(
                                                        channel = SyncChannel.WFW_LEGACY,
                                                        resumeUrl = GdouSyncScript.URL_DIRECT_CAS_WFW,
                                                        autoActionAfter = "all"
                                                    )
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text("兼容通道(wfw)", fontSize = 12.5.sp)
                                            }
                                        }
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "仅在广海官方页面认证，本地不保存学号与密码",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                    )
                                }
                                TextButton(
                                    onClick = { openDiagnosticDialog() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        text = "连不上？查看网络诊断",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // ===== STATE 2: CHECKING / POST-AUTH SEAMLESS TRANSITION (0-frame login button flicker!) =====
                        PortalSessionUiState.CHECKING -> {
                            Spacer(modifier = Modifier.height(6.dp))
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    if (isPostAuthConnecting) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "广海统一身份认证已通过",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Text(
                                                text = "正在建立教务连接并读取本学期...",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(26.dp),
                                            strokeWidth = 2.5.dp
                                        )
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = sessionStatusHint.ifBlank { "正在连接广海教务..." },
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = shortChannelLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ===== STATE 3: CONNECTED (Clean 2-Card Organic Hub: Sync Center + Single Official Website Entry!) =====
                        PortalSessionUiState.READY -> {
                            // Card 1: Core Sync Center (borderless 20dp surfaceContainerLowest matching SettingsCard!)
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Lightweight channel pill + last sync timestamp
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            shape = RoundedCornerShape(999.dp),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(999.dp))
                                                .clickable(enabled = !isSyncing) { togglePrimaryChannel() }
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(7.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.primary)
                                                )
                                                Text(
                                                    text = shortChannelLabel,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Icon(
                                                    imageVector = Icons.Rounded.SwapHoriz,
                                                    contentDescription = "切换通道",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = if (settings.lastSyncTime.isNotBlank()) {
                                                "上次同步 ${settings.lastSyncTime}"
                                            } else {
                                                "尚未同步"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Current Semester Local Data Overview Strip (tapping jumps straight to target Tab/SubTab!)
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = settings.academicYearLabel.ifBlank { "当前学期数据" },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                SyncOverviewMetricTile(
                                                    label = "课表",
                                                    value = "${courses.size} 条",
                                                    modifier = Modifier.weight(1f),
                                                    onClick = { onSyncFinishedJumpSchedule() }
                                                )
                                                SyncOverviewMetricTile(
                                                    label = "成绩",
                                                    value = "${gradesCount} 门",
                                                    modifier = Modifier.weight(1f),
                                                    onClick = { onSyncFinishedJumpAcademic(1) }
                                                )
                                                SyncOverviewMetricTile(
                                                    label = "考试",
                                                    value = "${examsCount} 场",
                                                    modifier = Modifier.weight(1f),
                                                    onClick = { onSyncFinishedJumpAcademic(2) }
                                                )
                                            }
                                        }
                                    }

                                    // Smart Primary Action: Quick Refresh (with调课 diff!) once courses exist; Full Sync for brand-new users!
                                    if (courses.isNotEmpty()) {
                                        Button(
                                            onClick = { runQuickRefreshInWorkbench(activeChannel) },
                                            enabled = !isSyncing,
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                        ) {
                                            Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("刷新课表 · 检查调课", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            FilledTonalButton(
                                                onClick = { startSyncFlow("all") },
                                                enabled = !isSyncing,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .weight(1.25f)
                                                    .height(42.dp)
                                            ) {
                                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(17.dp))
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text("一键全同步", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            }

                                            SingleSyncDropdownButton(
                                                expanded = showSingleSyncMenu,
                                                enabled = !isSyncing,
                                                modifier = Modifier.weight(0.9f),
                                                onToggle = { showSingleSyncMenu = it },
                                                onSelectMode = { mode ->
                                                    showSingleSyncMenu = false
                                                    startSyncFlow(mode)
                                                }
                                            )
                                        }
                                    } else {
                                        Button(
                                            onClick = { startSyncFlow("all") },
                                            enabled = !isSyncing,
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                        ) {
                                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("一键同步（课表 · 成绩 · 考试）", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            FilledTonalButton(
                                                onClick = { runQuickRefreshInWorkbench(activeChannel) },
                                                enabled = !isSyncing,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .weight(1.2f)
                                                    .height(42.dp)
                                            ) {
                                                Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(17.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("刷新课表", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            }

                                            SingleSyncDropdownButton(
                                                expanded = showSingleSyncMenu,
                                                enabled = !isSyncing,
                                                modifier = Modifier.weight(0.95f),
                                                onToggle = { showSingleSyncMenu = it },
                                                onSelectMode = { mode ->
                                                    showSingleSyncMenu = false
                                                    startSyncFlow(mode)
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Card 2: THE ONLY Official Website Direct Entry (matches SettingsScreen WorkbenchDirectoryRow DNA!)
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable {
                                        openFullBrowser("/xtgl/index_initMenu.html?jsdm=xs", "广海正方教务官网")
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.Public,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "访问广海正方教务官网",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "教学评价、自主选课、培养方案等官网事务",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "仅在广海官方页面认证，本地不保存学号与密码",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }

        // Floating Sync Progress Pill
        if (isSyncing && syncStatusText != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.inversePrimary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = syncStatusText!!,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    // Diagnostic Dialog
    diagnosticDialogText?.let { info ->
        AlertDialog(
            onDismissRequest = { diagnosticDialogText = null },
            title = { Text("教务引擎诊断信息") },
            text = { Text(info, style = MaterialTheme.typography.bodySmall) },
            confirmButton = { Button(onClick = { diagnosticDialogText = null }) { Text("知道了") } },
            dismissButton = {
                TextButton(
                    onClick = {
                        val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                        cm?.setPrimaryClip(android.content.ClipData.newPlainText("haiyu_jw_diag", info))
                        onShowMessage("已复制脱敏诊断信息")
                    }
                ) { Text("复制文本") }
            }
        )
    }

    // Term Picker Dialog
    val termOpts = termOptionsDialog
    if (termOpts != null) {
        var yearIdx by remember(termOpts) {
            mutableIntStateOf(termOpts.defaultYearIndex.coerceIn(0, (termOpts.yearOptions.size - 1).coerceAtLeast(0)))
        }
        var semIdx by remember(termOpts) {
            mutableIntStateOf(termOpts.defaultSemesterIndex.coerceIn(0, (termOpts.semesterOptions.size - 1).coerceAtLeast(0)))
        }
        AlertDialog(
            onDismissRequest = { termOptionsDialog = null },
            title = { Text("确认同步的学年与学期") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (termOpts.isFallback) {
                        Text(
                            text = "已自动推测当前学年学期，请核对：",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("选择学年", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        termOpts.yearOptions.forEachIndexed { idx, opt ->
                            val selected = yearIdx == idx
                            FilterChip(
                                selected = selected,
                                onClick = { yearIdx = idx },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                label = { Text(opt.text, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) }
                            )
                        }
                    }
                    Text("选择学期", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        termOpts.semesterOptions.forEachIndexed { idx, opt ->
                            val selected = semIdx == idx
                            FilterChip(
                                selected = selected,
                                onClick = { semIdx = idx },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                label = { Text(opt.text, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val yOpt = termOpts.yearOptions.getOrNull(yearIdx)
                        val sOpt = termOpts.semesterOptions.getOrNull(semIdx)
                        termOptionsDialog = null
                        if (yOpt != null && sOpt != null) {
                            val label = "${yOpt.text} ${sOpt.text}"
                            scope.launch {
                                isSyncing = true
                                syncStatusText = "正在同步 $label 数据..."
                                val outcome = nativeEngine.syncModules(
                                    channel = activeChannel,
                                    mode = pendingSyncMode,
                                    academicYear = yOpt.value,
                                    semesterCode = sOpt.value,
                                    termLabel = label,
                                    onProgress = { msg -> syncStatusText = msg }
                                )
                                isSyncing = false
                                syncStatusText = null

                                if (outcome.authExpired != null && outcome.summaryText.isBlank()) {
                                    sessionState = PortalSessionUiState.NEEDS_AUTH
                                    lastWebVpnDenied = outcome.authExpired.isWebVpnDenied
                                    openAuthSheet(
                                        channel = activeChannel,
                                        resumeUrl = outcome.authExpired.resumeUrl,
                                        isMfa = outcome.authExpired.isMfa,
                                        autoActionAfter = pendingSyncMode
                                    )
                                    return@launch
                                }

                                (outcome.coursesResult as? NativeCallResult.Success)?.data?.let { c ->
                                    onCoursesSynced(
                                        c.courses,
                                        c.startDate,
                                        c.totalWeeks,
                                        label,
                                        yOpt.value,
                                        sOpt.value,
                                        c.detectedCampusId,
                                        c.practiceCourses
                                    )
                                }
                                (outcome.gradesResult as? NativeCallResult.Success)?.data?.let { g ->
                                    onGradesSynced(g.grades, g.levelExams)
                                }
                                (outcome.examsResult as? NativeCallResult.Success)?.data?.let { ex ->
                                    onExamsSynced(ex)
                                }
                                if (outcome.evalInterceptMessage != null) {
                                    evalInterceptBanner = outcome.evalInterceptMessage
                                }
                                if (outcome.summaryText.isNotBlank()) {
                                    extractGradeSkipReason(outcome.summaryText)?.let { onGradeSkipReasonChanged(it) }
                                    syncSuccessSummary = outcome.summaryText
                                } else {
                                    onShowMessage(outcome.errorText ?: "同步未获取到有效数据")
                                }
                            }
                        }
                    }
                ) { Text("开始同步") }
            },
            dismissButton = {
                TextButton(onClick = { termOptionsDialog = null }) { Text("取消") }
            }
        )
    }

    // Sync Completion Summary Dialog (ONLY AlertDialog, ZERO overlapping Snackbar!)
    if (syncSuccessSummary != null) {
        val rawBody = syncSuccessSummary!!.removePrefix("同步完成：").trim()
        val bulletLines = rawBody.split(" · ").map { it.trim() }.filter { it.isNotEmpty() }
        val confirmLabel = when (pendingSyncMode) {
            "grades" -> "查看成绩"
            "exams" -> "查看考试"
            else -> "查看课表"
        }
        AlertDialog(
            onDismissRequest = { syncSuccessSummary = null },
            title = { Text("数据已离线保存") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    bulletLines.forEach { line ->
                        val isSkipped = line.contains("跳过") || line.contains("未获取")
                        Text(
                            text = (if (isSkipped) "• " else "✓ ") + line,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSkipped) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "已自动应用「${settings.campus.label}」作息规则并刷新桌面小组件。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        syncSuccessSummary = null
                        when (pendingSyncMode) {
                            "grades" -> onSyncFinishedJumpAcademic(1)
                            "exams" -> onSyncFinishedJumpAcademic(2)
                            else -> onSyncFinishedJumpSchedule()
                        }
                    }
                ) { Text(confirmLabel) }
            },
            dismissButton = {
                TextButton(onClick = { syncSuccessSummary = null }) { Text("留在此页") }
            }
        )
    }
}

@Composable
private fun SyncOverviewMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SingleSyncDropdownButton(
    expanded: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onToggle: (Boolean) -> Unit,
    onSelectMode: (String) -> Unit
) {
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { onToggle(true) },
            enabled = enabled,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
        ) {
            Text("单项", fontSize = 12.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Spacer(modifier = Modifier.width(2.dp))
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onToggle(false) }
        ) {
            DropdownMenuItem(
                text = { Text("指定学期同步课表") },
                leadingIcon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
                onClick = { onSelectMode("courses") }
            )
            DropdownMenuItem(
                text = { Text("仅同步成绩与 GPA") },
                leadingIcon = { Icon(Icons.Rounded.Assessment, contentDescription = null) },
                onClick = { onSelectMode("grades") }
            )
            DropdownMenuItem(
                text = { Text("仅同步考试时间与考场") },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.EventNote, contentDescription = null) },
                onClick = { onSelectMode("exams") }
            )
        }
    }
}
