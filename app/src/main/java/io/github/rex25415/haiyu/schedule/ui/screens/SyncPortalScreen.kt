package io.github.rex25415.haiyu.schedule.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JsResult
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rex25415.haiyu.schedule.model.AppSettings
import io.github.rex25415.haiyu.schedule.model.ClassroomDaySchedule
import io.github.rex25415.haiyu.schedule.model.ClassroomSlotStatus
import io.github.rex25415.haiyu.schedule.model.CourseItem
import io.github.rex25415.haiyu.schedule.model.EmptyClassroomItem
import io.github.rex25415.haiyu.schedule.model.GdouCampus
import io.github.rex25415.haiyu.schedule.model.ExamItem
import io.github.rex25415.haiyu.schedule.model.GradeItem
import io.github.rex25415.haiyu.schedule.model.LevelExamItem
import io.github.rex25415.haiyu.schedule.model.PracticeCourseItem
import io.github.rex25415.haiyu.schedule.sync.CampusNetworkState
import io.github.rex25415.haiyu.schedule.sync.GdouJsBridge
import io.github.rex25415.haiyu.schedule.sync.GdouSyncCallback
import io.github.rex25415.haiyu.schedule.sync.GdouSyncScript
import io.github.rex25415.haiyu.schedule.sync.NetworkChannelProbe
import io.github.rex25415.haiyu.schedule.sync.NetworkProbeResult
import io.github.rex25415.haiyu.schedule.sync.TermOptionsPayload
import io.github.rex25415.haiyu.schedule.ui.components.EmptyClassroomPane
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalLayoutApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SyncPortalScreen(
    isVisible: Boolean,
    settings: AppSettings,
    courses: List<CourseItem> = emptyList(),
    quickRefreshTrigger: Int,
    requestedMode: Int = 0,
    modeRequestNonce: Int = 0,
    onLoginStateChanged: (Boolean) -> Unit,
    onSyncStateChanged: (Boolean) -> Unit,
    onQuickRefreshNeedsLogin: (String) -> Unit,
    onUpdateChannelPrefs: (preferOffCampus: Boolean, preferDesktopUa: Boolean) -> Unit,
    onToggleFavoriteBuilding: (GdouCampus, String) -> Unit = { _, _ -> },
    onToggleFavoriteClassroom: (GdouCampus, String, String, String, String) -> Unit = { _, _, _, _, _ -> },
    onCoursesSynced: (List<CourseItem>, String?, Int, String, String, String, String, List<PracticeCourseItem>) -> Unit,
    onGradesSynced: (List<GradeItem>, List<LevelExamItem>) -> Unit,
    onExamsSynced: (List<ExamItem>) -> Unit,
    onShowMessage: (String) -> Unit,
    onNavigateBackToSchedule: () -> Unit = {},
    onSyncFinishedJumpSchedule: () -> Unit
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val latestVisible by rememberUpdatedState(isVisible)
    val latestSettings by rememberUpdatedState(settings)

    var useOffCampus by remember { mutableStateOf(settings.preferOffCampusChannel) }
    var desktopMode by remember { mutableStateOf(settings.preferDesktopUa) }
    var currentUrl by remember {
        mutableStateOf(if (settings.preferOffCampusChannel) GdouSyncScript.URL_OFF_CAMPUS else GdouSyncScript.URL_ON_CAMPUS)
    }
    var pageTitle by remember { mutableStateOf("广东海洋大学教务系统") }
    var loadProgress by remember { mutableIntStateOf(0) }
    var pageErrorMsg by remember { mutableStateOf<String?>(null) }
    var domConfirmedLoggedIn by remember { mutableStateOf(false) }

    // Calm state guards to completely prevent jumping / redirect thrashing
    var hasAutoEnteredWfw by remember { mutableStateOf(false) }
    var hasAutoSwitchedForMfa by remember { mutableStateOf(false) }
    var hasAutoRecoveredRedirectLoop by remember { mutableStateOf(false) }
    var hasAutoFallbackOffCampus by remember { mutableStateOf(false) }
    var pendingWfwEnterRunnable by remember { mutableStateOf<Runnable?>(null) }
    var pendingMfaAdaptRunnable by remember { mutableStateOf<Runnable?>(null) }
    var pendingDomProbeRunnable by remember { mutableStateOf<Runnable?>(null) }

    // Thread-safe URL mirror for GdouJsBridge strict HTTPS origin guard (Binder thread safe!)
    val trustedUrlRef = remember {
        AtomicReference(
            if (settings.preferOffCampusChannel) GdouSyncScript.URL_OFF_CAMPUS else GdouSyncScript.URL_ON_CAMPUS
        )
    }

    // Real-time campus vs off-campus network probe state
    var networkProbe by remember {
        mutableStateOf(NetworkProbeResult(state = CampusNetworkState.PROBING, isOnWifi = false))
    }
    var probeTrigger by remember { mutableIntStateOf(0) }

    var isSyncing by remember { mutableStateOf(false) }
    var isSilentQuickRefresh by remember { mutableStateOf(false) }
    var syncStatusText by remember { mutableStateOf<String?>(null) }
    var pendingSyncMode by remember { mutableStateOf("all") }
    var termOptionsDialog by remember { mutableStateOf<TermOptionsPayload?>(null) }
    var showQuickNavMenu by remember { mutableStateOf(false) }
    var showSingleSyncMenu by remember { mutableStateOf(false) }
    var syncSuccessSummary by remember { mutableStateOf<String?>(null) }
    var jsAlertDialog by remember { mutableStateOf<Pair<String, JsResult>?>(null) }
    var jsConfirmDialog by remember { mutableStateOf<Pair<String, JsResult>?>(null) }
    var diagnosticDialogText by remember { mutableStateOf<String?>(null) }
    var activePortalMode by remember { mutableIntStateOf(requestedMode.coerceIn(0, 1)) }
    LaunchedEffect(modeRequestNonce) {
        if (modeRequestNonce > 0) {
            activePortalMode = requestedMode.coerceIn(0, 1)
        }
    }
    var emptyClassroomResults by remember { mutableStateOf<List<EmptyClassroomItem>?>(null) }
    var emptyClassroomSummary by remember { mutableStateOf("") }
    var inspectedClassroom by remember { mutableStateOf<ClassroomDaySchedule?>(null) }
    var isInspectingRoom by remember { mutableStateOf(false) }
    var pendingInspectFallbackMeta by remember {
        mutableStateOf<Triple<GdouCampus, String, String>?>(null) // campus, seats, roomType
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    fun cancelPendingTransitions() {
        pendingWfwEnterRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingWfwEnterRunnable = null
        pendingMfaAdaptRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingMfaAdaptRunnable = null
        pendingDomProbeRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingDomProbeRunnable = null
    }

    fun isMfaUrl(url: String?): Boolean = GdouSyncScript.isMfaUrl(url)

    val isLoggedIntoJw = remember(currentUrl, pageTitle, pageErrorMsg, domConfirmedLoggedIn) {
        GdouSyncScript.resolveJwLoginState(
            url = currentUrl,
            pageTitle = pageTitle,
            pageErrorMsg = pageErrorMsg,
            domConfirmedLoggedIn = domConfirmedLoggedIn
        )
    }

    val isSittingOnWfwRoot = remember(currentUrl, isLoggedIntoJw) {
        if (!isLoggedIntoJw) return@remember false
        val uri = try { Uri.parse(currentUrl) } catch (_: Exception) { null }
        val host = uri?.host.orEmpty().lowercase()
        val path = uri?.path.orEmpty()
        host == "wfw.gdou.edu.cn" && (path.isEmpty() || path == "/" || path == "/index.html")
    }

    LaunchedEffect(isLoggedIntoJw) {
        onLoginStateChanged(isLoggedIntoJw)
    }

    LaunchedEffect(isSyncing) {
        onSyncStateChanged(isSyncing)
    }

    val syncCallback = remember {
        object : GdouSyncCallback {
            override fun onProgress(message: String) {
                mainHandler.post { syncStatusText = message }
            }

            override fun onTermOptionsLoaded(options: TermOptionsPayload) {
                mainHandler.post {
                    isSyncing = false
                    isSilentQuickRefresh = false
                    syncStatusText = null
                    termOptionsDialog = options
                }
            }

            override fun onCoursesSynced(
                courses: List<CourseItem>,
                startDate: String?,
                totalWeeks: Int,
                termLabel: String,
                yearCode: String,
                semesterCode: String,
                detectedCampusId: String,
                practiceCourses: List<PracticeCourseItem>
            ) {
                mainHandler.post {
                    onCoursesSynced(courses, startDate, totalWeeks, termLabel, yearCode, semesterCode, detectedCampusId, practiceCourses)
                }
            }

            override fun onGradesSynced(grades: List<GradeItem>, levelExams: List<LevelExamItem>) {
                mainHandler.post { onGradesSynced(grades, levelExams) }
            }

            override fun onExamsSynced(exams: List<ExamItem>) {
                mainHandler.post { onExamsSynced(exams) }
            }

            override fun onEmptyClassroomsLoaded(rooms: List<EmptyClassroomItem>, summary: String) {
                mainHandler.post {
                    isSyncing = false
                    syncStatusText = null
                    emptyClassroomResults = rooms
                    emptyClassroomSummary = summary
                    activePortalMode = 1
                }
            }

            override fun onClassroomDayScheduleLoaded(
                campusLabel: String,
                building: String,
                roomName: String,
                weekNumber: Int,
                dayOfWeek: Int,
                seats: String,
                roomType: String,
                slots: List<ClassroomSlotStatus>
            ) {
                mainHandler.post {
                    isInspectingRoom = false
                    val meta = pendingInspectFallbackMeta
                    val resolvedCampus = meta?.first ?: when {
                        campusLabel.contains("海滨") -> GdouCampus.HAIBIN
                        campusLabel.contains("阳江") -> GdouCampus.YANGJIANG
                        else -> GdouCampus.HUGUANG
                    }
                    val finalSeats = seats.ifBlank { meta?.second.orEmpty() }
                    val finalType = roomType.ifBlank { meta?.third.orEmpty() }
                    inspectedClassroom = ClassroomDaySchedule(
                        campus = resolvedCampus,
                        building = building,
                        roomName = roomName,
                        weekNumber = weekNumber,
                        dayOfWeek = dayOfWeek,
                        seats = finalSeats,
                        roomType = finalType,
                        slots = slots
                    )
                }
            }

            override fun onComplete(summary: String) {
                mainHandler.post {
                    val wasSilent = isSilentQuickRefresh || !latestVisible
                    isSyncing = false
                    isSilentQuickRefresh = false
                    syncStatusText = null
                    if (wasSilent) {
                        onShowMessage(summary)
                    } else {
                        syncSuccessSummary = summary
                        onShowMessage(summary)
                    }
                }
            }

            override fun onError(errorMessage: String) {
                mainHandler.post {
                    isSyncing = false
                    isInspectingRoom = false
                    isSilentQuickRefresh = false
                    syncStatusText = null
                    onShowMessage(errorMessage)
                }
            }

            override fun onQuickRefreshNeedsLogin(reason: String) {
                mainHandler.post {
                    isSyncing = false
                    isSilentQuickRefresh = false
                    syncStatusText = null
                    onQuickRefreshNeedsLogin(reason)
                }
            }
        }
    }

    fun startSyncFlow(mode: String) {
        val wv = webViewRef ?: return
        isSilentQuickRefresh = false
        pendingSyncMode = mode
        if (mode == "grades") {
            isSyncing = true
            syncStatusText = "正在拉取全部历史成绩与绩点..."
            wv.evaluateJavascript(
                GdouSyncScript.buildSyncScript("grades", "", "", ""),
                null
            )
            return
        }
        isSyncing = true
        syncStatusText = "正在读取教务学年学期..."
        wv.evaluateJavascript(GdouSyncScript.buildFetchTermOptionsScript(), null)
    }

    fun startQuickRefreshCourses(silent: Boolean) {
        val wv = webViewRef
        if (wv == null) {
            onQuickRefreshNeedsLogin("请先在教务页完成登录，即可随时一键刷新课表")
            return
        }
        isSilentQuickRefresh = silent
        isSyncing = true
        syncStatusText = "正在刷新最新课表与调课变化..."
        wv.evaluateJavascript(
            GdouSyncScript.buildQuickRefreshCoursesScript(
                academicYear = latestSettings.lastSyncYearCode,
                semesterCode = latestSettings.lastSyncSemesterCode,
                termLabel = latestSettings.academicYearLabel
            ),
            null
        )
    }

    // Listen to one-tap quick refresh requests from ScheduleScreen
    LaunchedEffect(quickRefreshTrigger) {
        if (quickRefreshTrigger > 0) {
            startQuickRefreshCourses(silent = !latestVisible)
        }
    }

    fun switchChannel(offCampus: Boolean) {
        cancelPendingTransitions()
        useOffCampus = offCampus
        domConfirmedLoggedIn = false
        hasAutoEnteredWfw = false
        hasAutoSwitchedForMfa = false
        hasAutoRecoveredRedirectLoop = false
        pageErrorMsg = null
        onUpdateChannelPrefs(offCampus, desktopMode)
        val url = if (offCampus) GdouSyncScript.URL_OFF_CAMPUS else GdouSyncScript.URL_ON_CAMPUS
        currentUrl = url
        trustedUrlRef.set(url)
        webViewRef?.stopLoading()
        webViewRef?.loadUrl(url)
    }

    // Smart On-Campus / Off-Campus probe when Sync tab is visible (lightweight single HEAD)
    LaunchedEffect(isVisible, probeTrigger) {
        if (!isVisible) return@LaunchedEffect
        val result = NetworkChannelProbe.probe(context)
        networkProbe = result
        if (NetworkChannelProbe.shouldAutoFallbackToOffCampus(useOffCampus, result.state) &&
            !hasAutoFallbackOffCampus &&
            !isLoggedIntoJw
        ) {
            hasAutoFallbackOffCampus = true
            switchChannel(true)
            onShowMessage("当前为校外网络，已自动切至「校外通用(免校园网)」")
        }
    }

    fun toggleDesktopUa() {
        cancelPendingTransitions()
        val next = !desktopMode
        desktopMode = next
        pageErrorMsg = null
        onUpdateChannelPrefs(useOffCampus, next)
        webViewRef?.let { wv ->
            wv.settings.userAgentString = if (next) {
                GdouSyncScript.DESKTOP_USER_AGENT
            } else {
                WebSettings.getDefaultUserAgent(context)
            }
            wv.settings.useWideViewPort = next
            wv.settings.loadWithOverviewMode = next
            wv.reload()
        }
        onShowMessage(if (next) "已切换电脑排版" else "已切换手机排版（登录页推荐）")
    }

    LaunchedEffect(isVisible, webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (isVisible) {
            wv.onResume()
        } else {
            wv.onPause()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cancelPendingTransitions()
            CookieManager.getInstance().flush()
        }
    }

    // Android 16 Predictive-Back friendly handling inside Sync WebView
    BackHandler(enabled = isVisible) {
        if (activePortalMode == 1) {
            activePortalMode = 0
        } else {
            val wv = webViewRef
            if (wv != null && wv.canGoBack()) {
                wv.goBack()
            } else {
                onNavigateBackToSchedule()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        // Primary Dual-Mode Switcher (Web Sync Station <-> Native Empty Classroom Finder)
        if (isVisible) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    0 to (Icons.Rounded.Public to "教务登录与同步"),
                    1 to (Icons.Rounded.MeetingRoom to "查空教室")
                ).forEach { (idx, iconAndTitle) ->
                    val sel = activePortalMode == idx
                    Surface(
                        color = if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { activePortalMode = idx }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = iconAndTitle.first,
                                contentDescription = null,
                                tint = if (sel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = iconAndTitle.second,
                                fontSize = 12.sp,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                color = if (sel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
        }

        // Compact Top Browser Bar (only in Web Sync mode)
        if (isVisible && activePortalMode == 0) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { webViewRef?.let { if (it.canGoBack()) it.goBack() } },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
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
                            text = currentUrl.removePrefix("https://").removePrefix("http://"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { showQuickNavMenu = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Rounded.Explore, contentDescription = "教务直达")
                        }
                        DropdownMenu(
                            expanded = showQuickNavMenu,
                            onDismissRequest = { showQuickNavMenu = false }
                        ) {
                            val shortcuts = listOf(
                                "进入广海教务主页" to "__WFW_JW__",
                                "查空教室（自习推荐）" to "cdjy/cdjy_cxKxcdlb.html?gnmkdm=N2155&layout=default",
                                "个人课表查询页" to "kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default",
                                "学生成绩查询页" to "cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005&layout=default",
                                "考试信息查询页" to "kwgl/kscx_cxXsksxxIndex.html?gnmkdm=N358105&layout=default"
                            )
                            shortcuts.forEach { (label, path) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        showQuickNavMenu = false
                                        if (path == "__WFW_JW__") {
                                            val entry = if (useOffCampus) GdouSyncScript.URL_WFW_JW_ENTRY else GdouSyncScript.URL_ON_CAMPUS
                                            webViewRef?.loadUrl(entry)
                                        } else if (!isLoggedIntoJw) {
                                            onShowMessage("请先完成广海教务登录，再打开「$label」")
                                        } else {
                                            webViewRef?.evaluateJavascript(
                                                GdouSyncScript.buildQuickNavigateScript(path),
                                                null
                                            )
                                        }
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("查看当前页诊断信息") },
                                leadingIcon = { Icon(Icons.Rounded.BugReport, contentDescription = null) },
                                onClick = {
                                    showQuickNavMenu = false
                                    val wvUrl = webViewRef?.url.orEmpty()
                                    val cookies = try {
                                        val raw = CookieManager.getInstance().getCookie(wvUrl).orEmpty()
                                        raw.split(";").mapNotNull { it.trim().substringBefore("=", "").takeIf(String::isNotBlank) }.joinToString(", ")
                                    } catch (_: Exception) { "无" }
                                    diagnosticDialogText = buildString {
                                        appendLine("当前通道：${if (useOffCampus) "校外通用(免校园网)" else "校园网直连"}")
                                        appendLine(
                                            "网络探测：" + when (networkProbe.state) {
                                                CampusNetworkState.PROBING -> "探测中"
                                                CampusNetworkState.ON_CAMPUS_DIRECT_READY -> "校园网直连可用（${networkProbe.jwLatencyMs ?: "-"}ms）"
                                                CampusNetworkState.OFF_CAMPUS_ONLY -> "校外网络（wfw ${networkProbe.wfwLatencyMs ?: "-"}ms）"
                                                CampusNetworkState.OFFLINE_OR_BLOCKED -> "网络未连通或校园网未认证"
                                            }
                                        )
                                        appendLine("页面排版：${if (desktopMode) "电脑版" else "手机版"}")
                                        appendLine("页面标题：$pageTitle")
                                        appendLine("实际URL：$wvUrl")
                                        appendLine("加载进度：$loadProgress%")
                                        appendLine("识别已登录：$isLoggedIntoJw")
                                        appendLine("桥接域名白名单校验：${GdouJsBridge.isTrustedGdouOrigin(wvUrl)}")
                                        appendLine("Cookie键名(已脱敏)：${cookies.ifBlank { "空" }}")
                                        if (pageErrorMsg != null) appendLine("最近报错：$pageErrorMsg")
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("清除登录缓存并重登", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.DeleteSweep,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showQuickNavMenu = false
                                    cancelPendingTransitions()
                                    hasAutoSwitchedForMfa = false
                                    hasAutoEnteredWfw = false
                                    CookieManager.getInstance().removeAllCookies {
                                        CookieManager.getInstance().flush()
                                        mainHandler.post {
                                            webViewRef?.clearCache(true)
                                            switchChannel(useOffCampus)
                                            onShowMessage("已清除登录缓存，可重新登录")
                                        }
                                    }
                                }
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            cancelPendingTransitions()
                            pageErrorMsg = null
                            webViewRef?.reload()
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新网页")
                    }
                }

                // Unmistakable High-Contrast 2-Segment Channel Switcher (solid primary + checkmark = active!)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            // Segment 1: Off-Campus Universal Channel
                            Surface(
                                color = if (useOffCampus) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(9.dp),
                                modifier = Modifier
                                    .weight(1.18f)
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .clickable { if (!useOffCampus) switchChannel(true) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                ) {
                                    if (useOffCampus) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                    }
                                    Text(
                                        text = "校外免校园网",
                                        fontSize = 11.5.sp,
                                        fontWeight = if (useOffCampus) FontWeight.Bold else FontWeight.Medium,
                                        color = if (useOffCampus) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Segment 2: On-Campus Direct Channel
                            Surface(
                                color = if (!useOffCampus) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(9.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .clickable { if (useOffCampus) switchChannel(false) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                ) {
                                    if (!useOffCampus) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                    }
                                    Text(
                                        text = "校园网直连",
                                        fontSize = 11.5.sp,
                                        fontWeight = if (!useOffCampus) FontWeight.Bold else FontWeight.Medium,
                                        color = if (!useOffCampus) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Neutral UA mode chip (single clean toggle with icon + label, distinct from channel switcher)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            0.8.dp,
                            MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { toggleDesktopUa() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
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
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Dedicated non-confusing slim network banner ONLY when actionable (never clips or mimics a channel tab!)
        if (networkProbe.state == CampusNetworkState.ON_CAMPUS_DIRECT_READY && useOffCampus && !isLoggedIntoJw) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        switchChannel(false)
                        onShowMessage("已切至「校园网直连」，加载更快更稳")
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "检测到当前处于校园网，点此切「校园网直连」加载更快",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        } else if (networkProbe.state == CampusNetworkState.OFFLINE_OR_BLOCKED) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { probeTrigger++ }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "当前网络暂未连通学校服务器（请检查流量或先登录校园网认证），点此重测",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        if (loadProgress in 1..99) {
            LinearProgressIndicator(
                progress = { loadProgress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Calm helper strip when sitting on off-campus portal root (gives user clear control)
        if (isSittingOnWfwRoot) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        cancelPendingTransitions()
                        hasAutoEnteredWfw = true
                        webViewRef?.loadUrl(GdouSyncScript.URL_WFW_JW_ENTRY)
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "已登录校外通道，点此直接进入广海教务系统",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        } // end if (activePortalMode == 0) header & helper banners

        // Spacious WebView / Native Empty-Classroom Area (WebView stays mounted to preserve cookies!)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clipToBounds()
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    view.visibility = if (isVisible && activePortalMode == 0) View.VISIBLE else View.GONE
                    // Pause background WebView JS/rendering timers when hidden (saves battery & RAM!)
                    if ((isVisible && activePortalMode == 0) || isSyncing) {
                        view.onResume()
                    } else {
                        view.onPause()
                    }
                },
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        val cm = CookieManager.getInstance()
                        cm.setAcceptCookie(true)
                        cm.setAcceptThirdPartyCookies(this, true)

                        this.settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowFileAccess = false
                            allowContentAccess = false
                            builtInZoomControls = true
                            displayZoomControls = false
                            setSupportZoom(true)
                            setSupportMultipleWindows(false)
                            useWideViewPort = desktopMode
                            loadWithOverviewMode = desktopMode
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            userAgentString = if (desktopMode) {
                                GdouSyncScript.DESKTOP_USER_AGENT
                            } else {
                                WebSettings.getDefaultUserAgent(ctx)
                            }
                        }

                        addJavascriptInterface(
                            GdouJsBridge(
                                callback = syncCallback,
                                currentUrlProvider = { trustedUrlRef.get() }
                            ),
                            "HaiyuBridge"
                        )

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                if (!title.isNullOrBlank()) pageTitle = title
                            }

                            override fun onJsAlert(
                                view: WebView?,
                                url: String?,
                                message: String?,
                                result: JsResult?
                            ): Boolean {
                                if (result != null) {
                                    jsAlertDialog = (message ?: "网页提示") to result
                                    return true
                                }
                                return super.onJsAlert(view, url, message, result)
                            }

                            override fun onJsConfirm(
                                view: WebView?,
                                url: String?,
                                message: String?,
                                result: JsResult?
                            ): Boolean {
                                if (result != null) {
                                    jsConfirmDialog = (message ?: "确认操作？") to result
                                    return true
                                }
                                return super.onJsConfirm(view, url, message, result)
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val u = request?.url?.toString() ?: return false
                                if (u.startsWith("https://", ignoreCase = true)) return false
                                // Transparently upgrade GDOU internal http:// redirects (e.g. jziotlogin -> http://authserver) to HTTPS!
                                val upgraded = GdouJsBridge.upgradeGdouHttpToHttps(u)
                                if (upgraded != null) {
                                    view?.loadUrl(upgraded)
                                    return true
                                }
                                // Strict HTTPS-only navigation; block non-GDOU cleartext http://, file://, javascript:, intent://
                                return true
                            }

                            private fun probePortalDomLoginState(wv: WebView, expectedUrl: String, allowRetry: Boolean) {
                                wv.evaluateJavascript(GdouSyncScript.DOM_PORTAL_PROBE_SCRIPT) { res ->
                                    if (wv.url != expectedUrl && currentUrl != expectedUrl) return@evaluateJavascript
                                    val loggedIn = res != null && res.contains("LOGGED_IN")
                                    if (loggedIn) {
                                        domConfirmedLoggedIn = true
                                        pageErrorMsg = null
                                        wv.title?.takeIf {
                                            it.isNotBlank() && !it.startsWith("http", ignoreCase = true)
                                        }?.let { pageTitle = it }
                                    } else if (allowRetry) {
                                        val retryTask = Runnable {
                                            if (wv.url == expectedUrl || currentUrl == expectedUrl) {
                                                probePortalDomLoginState(wv, expectedUrl, allowRetry = false)
                                            }
                                        }
                                        pendingDomProbeRunnable?.let { mainHandler.removeCallbacks(it) }
                                        pendingDomProbeRunnable = retryTask
                                        mainHandler.postDelayed(retryTask, 450L)
                                    }
                                }
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                // Always cancel any delayed auto-transition whenever a real navigation starts,
                                // eliminating race conditions and unexpected jumping!
                                cancelPendingTransitions()
                                domConfirmedLoggedIn = false
                                if (!url.isNullOrBlank()) {
                                    currentUrl = url
                                    trustedUrlRef.set(url)
                                    pageErrorMsg = null
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (!url.isNullOrBlank()) {
                                    currentUrl = url
                                    trustedUrlRef.set(url)
                                }
                                CookieManager.getInstance().flush()
                                val wv = view ?: return
                                val finishedUrl = url.orEmpty()

                                // 1. Calm single-shot MFA page adaptation (after redirect chain settles, never interrupts POST/302)
                                if (isMfaUrl(finishedUrl)) {
                                    if (!desktopMode && !hasAutoSwitchedForMfa) {
                                        val adaptTask = Runnable {
                                            if (isMfaUrl(wv.url) && !desktopMode && !hasAutoSwitchedForMfa) {
                                                hasAutoSwitchedForMfa = true
                                                desktopMode = true
                                                wv.settings.userAgentString = GdouSyncScript.DESKTOP_USER_AGENT
                                                wv.settings.useWideViewPort = true
                                                wv.settings.loadWithOverviewMode = true
                                                wv.reload()
                                                onShowMessage("已自动适配二次验证页面，请完成企业微信/短信验证")
                                            }
                                        }
                                        pendingMfaAdaptRunnable = adaptTask
                                        mainHandler.postDelayed(adaptTask, 350L)
                                    } else {
                                        // Make desktop MFA page nicely centered on portrait phone screens
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

                                if (
                                    finishedUrl.startsWith("https://", ignoreCase = true) ||
                                    finishedUrl.startsWith("http://", ignoreCase = true)
                                ) {
                                    wv.title?.takeIf {
                                        it.isNotBlank() && !it.startsWith("http", ignoreCase = true)
                                    }?.let { pageTitle = it }
                                    if (GdouSyncScript.isUrlOnPortalLoginPath(finishedUrl)) {
                                        probePortalDomLoginState(wv, finishedUrl, allowRetry = true)
                                    }
                                }

                                // 2. Debounced, verified one-shot entry from WFW root into JW portal
                                // Waits 1100ms so any server-side JS redirect to CAS/MFA finishes first without fighting!
                                if (useOffCampus && !hasAutoEnteredWfw && finishedUrl.isNotBlank()) {
                                    val uri = try { Uri.parse(finishedUrl) } catch (_: Exception) { null }
                                    val host = uri?.host.orEmpty().lowercase()
                                    val path = uri?.path.orEmpty()
                                    val query = uri?.query.orEmpty()
                                    val title = wv.title.orEmpty()
                                    val isStrictWfwRoot = host == "wfw.gdou.edu.cn" &&
                                        (path.isEmpty() || path == "/" || path == "/index.html") &&
                                        !query.contains("ticket=") &&
                                        !title.contains("跳转") &&
                                        !title.contains("统一身份认证") &&
                                        !title.contains("多因子") &&
                                        !title.contains("登录")
                                    if (isStrictWfwRoot) {
                                        val enterTask = Runnable {
                                            wv.evaluateJavascript(
                                                """
                                                (function(){
                                                    var h = (location.hostname || '').toLowerCase();
                                                    var p = location.pathname || '';
                                                    var txt = (document.body && document.body.innerText) ? document.body.innerText.slice(0, 400) : '';
                                                    var isRoot = (h === 'wfw.gdou.edu.cn') && (p === '' || p === '/' || p === '/index.html');
                                                    var looksAuth = txt.includes('统一身份认证') || txt.includes('多因子') || txt.includes('正在跳转') || !!document.querySelector('input[type=password]');
                                                    return (isRoot && !looksAuth) ? 'READY' : 'WAIT';
                                                })();
                                                """.trimIndent()
                                            ) { res ->
                                                if (res != null && res.contains("READY") && !hasAutoEnteredWfw) {
                                                    hasAutoEnteredWfw = true
                                                    wv.loadUrl(GdouSyncScript.URL_WFW_JW_ENTRY)
                                                }
                                            }
                                        }
                                        pendingWfwEnterRunnable = enterTask
                                        mainHandler.postDelayed(enterTask, 1100L)
                                    }
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                if (request?.isForMainFrame == true) {
                                    val desc = error?.description?.toString().orEmpty()
                                    val reqUrl = request.url?.toString().orEmpty()
                                    if (desc.contains("ERR_ABORTED", ignoreCase = true)) {
                                        return
                                    }
                                    if (GdouSyncScript.isStaleChannelError(useOffCampus, reqUrl)) {
                                        return
                                    }
                                    val upgradedHttp = GdouJsBridge.upgradeGdouHttpToHttps(reqUrl)
                                    if (upgradedHttp != null && desc.contains("CLEARTEXT", ignoreCase = true)) {
                                        mainHandler.post {
                                            pageErrorMsg = null
                                            view?.loadUrl(upgradedHttp)
                                        }
                                        return
                                    }
                                    val isRedirectLoop = desc.contains("ERR_TOO_MANY_REDIRECTS", ignoreCase = true) ||
                                        reqUrl.contains("auth_error", ignoreCase = true)
                                    if (isRedirectLoop && !hasAutoRecoveredRedirectLoop) {
                                        hasAutoRecoveredRedirectLoop = true
                                        hasAutoEnteredWfw = false
                                        hasAutoSwitchedForMfa = false
                                        CookieManager.getInstance().removeAllCookies {
                                            CookieManager.getInstance().flush()
                                            mainHandler.post {
                                                pageErrorMsg = null
                                                view?.clearCache(true)
                                                val freshUrl = if (useOffCampus) GdouSyncScript.URL_OFF_CAMPUS else GdouSyncScript.URL_ON_CAMPUS
                                                view?.loadUrl(freshUrl)
                                                onShowMessage("检测到旧会话重定向异常，已自动清理缓存并重连")
                                            }
                                        }
                                        return
                                    }
                                    val isUnreachableOnCampus = !useOffCampus && !hasAutoFallbackOffCampus && (
                                        desc.contains("ERR_NAME_NOT_RESOLVED", ignoreCase = true) ||
                                            desc.contains("ERR_CONNECTION_TIMED_OUT", ignoreCase = true) ||
                                            desc.contains("ERR_TIMED_OUT", ignoreCase = true) ||
                                            desc.contains("ERR_ADDRESS_UNREACHABLE", ignoreCase = true) ||
                                            desc.contains("ERR_CONNECTION_REFUSED", ignoreCase = true)
                                        )
                                    if (isUnreachableOnCampus) {
                                        hasAutoFallbackOffCampus = true
                                        mainHandler.post {
                                            switchChannel(true)
                                            onShowMessage("校园网直连暂不可达，已自动切至「校外通用(免校园网)」")
                                        }
                                        return
                                    }
                                    pageErrorMsg = "页面加载失败（${desc.ifBlank { "网络超时或无法连接" }}）"
                                }
                            }

                            override fun onReceivedSslError(
                                view: WebView?,
                                handler: SslErrorHandler?,
                                error: SslError?
                            ) {
                                // Strict TLS security: NEVER silently bypass SSL errors (prevents MITM attacks)
                                handler?.cancel()
                                mainHandler.post {
                                    pageErrorMsg = "检测到 HTTPS 安全证书异常，为保护账号安全已自动拦截本次连接（请检查网络代理或先完成校园网认证）。"
                                }
                            }
                        }

                        webViewRef = this
                        loadUrl(currentUrl)
                    }
                }
            )

            // Network Error Helper Overlay
            if (pageErrorMsg != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f)),
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
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("当前通道连接不上学校教务", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${pageErrorMsg!!}\n如使用手机流量或校外网络，建议用「校外通用(免校园网)」；在校内 Wi-Fi 下也可切到「校园网直连」。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        cancelPendingTransitions()
                                        hasAutoEnteredWfw = false
                                        hasAutoSwitchedForMfa = false
                                        hasAutoRecoveredRedirectLoop = false
                                        CookieManager.getInstance().removeAllCookies {
                                            CookieManager.getInstance().flush()
                                            mainHandler.post {
                                                pageErrorMsg = null
                                                webViewRef?.clearCache(true)
                                                switchChannel(useOffCampus)
                                            }
                                        }
                                    }
                                ) { Text("清缓存重连") }
                                Button(
                                    onClick = { switchChannel(!useOffCampus) }
                                ) {
                                    Text(if (useOffCampus) "切到校园网直连" else "切到校外通用")
                                }
                            }
                        }
                    }
                }
            }

            // Sync Progress Floating Pill
            if (isSyncing && syncStatusText != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(14.dp)
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

            if (isVisible && activePortalMode == 1) {
                EmptyClassroomPane(
                    settings = settings,
                    courses = courses,
                    isLoggedIntoJw = isLoggedIntoJw,
                    isQuerying = isSyncing,
                    results = emptyClassroomResults,
                    summary = emptyClassroomSummary,
                    inspectedSchedule = inspectedClassroom,
                    isInspectingRoom = isInspectingRoom,
                    onGoToWebLogin = { activePortalMode = 0 },
                    onExecuteQuery = { campusKeyword, weekNumber, dayOfWeek, startSection, endSection, buildingKeyword ->
                        isSyncing = true
                        syncStatusText = "正在查询「${campusKeyword}校区」空闲教室..."
                        webViewRef?.evaluateJavascript(
                            GdouSyncScript.buildQueryEmptyClassroomsScript(
                                campusKeyword = campusKeyword,
                                weekNumber = weekNumber,
                                dayOfWeek = dayOfWeek,
                                startSection = startSection,
                                endSection = endSection,
                                buildingKeyword = buildingKeyword,
                                fallbackAcademicYear = settings.lastSyncYearCode,
                                fallbackSemesterCode = settings.lastSyncSemesterCode
                            ),
                            null
                        )
                    },
                    onInspectRoomDay = { campus, weekNumber, dayOfWeek, buildingName, roomName, seats, roomType ->
                        isInspectingRoom = true
                        pendingInspectFallbackMeta = Triple(campus, seats, roomType)
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
                        webViewRef?.evaluateJavascript(
                            GdouSyncScript.buildInspectRoomDayScheduleScript(
                                campusKeyword = campus.label.removeSuffix("校区"),
                                weekNumber = weekNumber,
                                dayOfWeek = dayOfWeek,
                                buildingName = buildingName,
                                roomName = roomName,
                                fallbackAcademicYear = settings.lastSyncYearCode,
                                fallbackSemesterCode = settings.lastSyncSemesterCode
                            ),
                            null
                        )
                    },
                    onDismissInspectedRoom = {
                        isInspectingRoom = false
                        inspectedClassroom = null
                    },
                    onToggleFavoriteBuilding = onToggleFavoriteBuilding,
                    onToggleFavoriteClassroom = onToggleFavoriteClassroom,
                    onOpenOriginalWeb = {
                        activePortalMode = 0
                        if (!isLoggedIntoJw) {
                            onShowMessage("请先登录教务系统")
                        } else {
                            webViewRef?.evaluateJavascript(
                                GdouSyncScript.buildQuickNavigateScript("cdjy/cdjy_cxKxcdlb.html?gnmkdm=N2155&layout=default"),
                                null
                            )
                        }
                    },
                    onShowMessage = onShowMessage
                )
            }
        }

        // Sleek Single-Row Bottom Sync Action Dock (only in Web Sync mode)
        if (isVisible && activePortalMode == 0) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { startSyncFlow("all") },
                    enabled = !isSyncing,
                    colors = if (isLoggedIntoJw) {
                        ButtonDefaults.buttonColors()
                    } else {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (isLoggedIntoJw) Icons.Rounded.CheckCircle else Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isLoggedIntoJw) "一键全同步" else "登录后点此一键同步",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Instant One-Tap Refresh Schedule button right in the dock
                FilledTonalButton(
                    onClick = { startQuickRefreshCourses(silent = false) },
                    enabled = !isSyncing,
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.height(42.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("刷课表", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Box {
                    FilledTonalIconButton(
                        onClick = { showSingleSyncMenu = true },
                        enabled = !isSyncing,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "单项同步与工具")
                    }
                    DropdownMenu(
                        expanded = showSingleSyncMenu,
                        onDismissRequest = { showSingleSyncMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("指定学期同步课表") },
                            onClick = {
                                showSingleSyncMenu = false
                                startSyncFlow("courses")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("仅同步历史成绩与 GPA") },
                            onClick = {
                                showSingleSyncMenu = false
                                startSyncFlow("grades")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("仅同步考试时间与考场") },
                            onClick = {
                                showSingleSyncMenu = false
                                startSyncFlow("exams")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("从当前网页表格直接提取") },
                            leadingIcon = { Icon(Icons.Rounded.Explore, contentDescription = null) },
                            onClick = {
                                showSingleSyncMenu = false
                                if (!isLoggedIntoJw) {
                                    onShowMessage("请先登录并进入正方成绩或考试查询表格页")
                                } else {
                                    isSyncing = true
                                    syncStatusText = "正在从当前网页表格直接提取..."
                                    webViewRef?.evaluateJavascript(
                                        GdouSyncScript.buildScrapeCurrentPageDomScript(),
                                        null
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
        } // end if (activePortalMode == 0) bottom sync dock
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

    diagnosticDialogText?.let { info ->
        AlertDialog(
            onDismissRequest = { diagnosticDialogText = null },
            title = { Text("当前教务页诊断信息") },
            text = { Text(info, style = MaterialTheme.typography.bodySmall) },
            confirmButton = { Button(onClick = { diagnosticDialogText = null }) { Text("知道了") } },
            dismissButton = {
                TextButton(
                    onClick = {
                        val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                        cm?.setPrimaryClip(android.content.ClipData.newPlainText("haiyu_jw_diag", info))
                        onShowMessage("已复制脱敏教务诊断信息")
                    }
                ) { Text("复制诊断文本") }
            }
        )
    }

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
                            isSyncing = true
                            syncStatusText = "正在抓取 $label 数据..."
                            webViewRef?.evaluateJavascript(
                                GdouSyncScript.buildSyncScript(
                                    mode = pendingSyncMode,
                                    academicYear = yOpt.value,
                                    semesterCode = sOpt.value,
                                    termLabel = label
                                ),
                                null
                            )
                        }
                    }
                ) { Text("开始同步") }
            },
            dismissButton = {
                TextButton(onClick = { termOptionsDialog = null }) { Text("取消") }
            }
        )
    }

    if (syncSuccessSummary != null) {
        val rawBody = syncSuccessSummary!!.removePrefix("同步完成：").trim()
        val bulletLines = rawBody.split(" · ").map { it.trim() }.filter { it.isNotEmpty() }
        AlertDialog(
            onDismissRequest = { syncSuccessSummary = null },
            title = { Text("广海数据已离线保存") },
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
                        onSyncFinishedJumpSchedule()
                    }
                ) { Text("查看课表") }
            },
            dismissButton = {
                TextButton(onClick = { syncSuccessSummary = null }) { Text("留在此页") }
            }
        )
    }
}
