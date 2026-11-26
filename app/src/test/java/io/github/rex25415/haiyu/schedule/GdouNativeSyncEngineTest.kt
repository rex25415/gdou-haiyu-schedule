package io.github.rex25415.haiyu.schedule

import io.github.rex25415.haiyu.schedule.model.CourseItem
import io.github.rex25415.haiyu.schedule.sync.GdouNativeSyncEngine
import io.github.rex25415.haiyu.schedule.sync.InMemoryGdouCookieStore
import io.github.rex25415.haiyu.schedule.sync.NativeCallResult
import io.github.rex25415.haiyu.schedule.sync.NetworkChannelProbe
import io.github.rex25415.haiyu.schedule.sync.QuickRefreshNativeOutcome
import io.github.rex25415.haiyu.schedule.sync.SessionCheckResult
import io.github.rex25415.haiyu.schedule.sync.SyncChannel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 纯 JVM 端到端原生引擎集成测试（零外部依赖，基于标准 java.net.ServerSocket）：
 * 模拟 WebVPN 头部注入、CAS 302 静默换票、并发课表/成绩/考试分页同步、
 * 新生零成绩快跳、评教/挂科拦截、MFA 二次验证拦截与空教室交集查询。
 */
class GdouNativeSyncEngineTest {

    private data class MockHttpReq(
        val method: String,
        val pathWithQuery: String,
        val headers: Map<String, String>,
        val body: String
    ) {
        val path: String get() = pathWithQuery.substringBefore("?")
        val query: String get() = pathWithQuery.substringAfter("?", "")
    }

    private data class MockHttpResp(
        val status: Int,
        val body: String = "",
        val headers: List<Pair<String, String>> = emptyList()
    )

    private class LocalMiniHttpServer(
        private val handler: (MockHttpReq) -> MockHttpResp
    ) : AutoCloseable {
        private val socket = ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"))
        private val running = AtomicBoolean(true)
        private val pool = Executors.newCachedThreadPool()
        val baseUrl: String = "http://127.0.0.1:${socket.localPort}"

        init {
            pool.execute {
                while (running.get()) {
                    val client = try {
                        socket.accept()
                    } catch (_: Exception) {
                        break
                    }
                    pool.execute {
                        client.use { s ->
                            s.soTimeout = 5000
                            val input = BufferedInputStream(s.getInputStream())
                            val headerBuf = StringBuilder()
                            var prev = -1
                            var contentLen = 0
                            while (true) {
                                val line = readAsciiLine(input) ?: return@use
                                if (line.isEmpty()) break
                                headerBuf.append(line).append('\n')
                            }
                            val lines = headerBuf.toString().lines().filter { it.isNotEmpty() }
                            if (lines.isEmpty()) return@use
                            val firstParts = lines[0].split(" ")
                            if (firstParts.size < 2) return@use
                            val method = firstParts[0].trim()
                            val pathWithQuery = firstParts[1].trim()
                            val headers = LinkedHashMap<String, String>()
                            for (i in 1 until lines.size) {
                                val idx = lines[i].indexOf(':')
                                if (idx > 0) {
                                    val k = lines[i].substring(0, idx).trim().lowercase()
                                    val v = lines[i].substring(idx + 1).trim()
                                    headers[k] = v
                                    if (k == "content-length") contentLen = v.toIntOrNull() ?: 0
                                }
                            }
                            val bodyBytes = if (contentLen > 0) {
                                val buf = ByteArray(contentLen)
                                var read = 0
                                while (read < contentLen) {
                                    val n = input.read(buf, read, contentLen - read)
                                    if (n <= 0) break
                                    read += n
                                }
                                buf.copyOf(read)
                            } else ByteArray(0)

                            val resp = handler(
                                MockHttpReq(
                                    method = method,
                                    pathWithQuery = pathWithQuery,
                                    headers = headers,
                                    body = bodyBytes.toString(Charsets.UTF_8)
                                )
                            )
                            val payload = resp.body.toByteArray(Charsets.UTF_8)
                            val statusText = when (resp.status) {
                                200 -> "OK"
                                302 -> "Found"
                                404 -> "Not Found"
                                else -> "Status"
                            }
                            val sb = StringBuilder()
                            sb.append("HTTP/1.1 ${resp.status} $statusText\r\n")
                            for ((k, v) in resp.headers) {
                                sb.append("$k: $v\r\n")
                            }
                            sb.append("Content-Length: ${payload.size}\r\n")
                            sb.append("Connection: close\r\n\r\n")
                            val out = s.getOutputStream()
                            out.write(sb.toString().toByteArray(Charsets.UTF_8))
                            if (payload.isNotEmpty()) out.write(payload)
                            out.flush()
                            @Suppress("UNUSED_VALUE")
                            prev = 0
                        }
                    }
                }
            }
        }

        private fun readAsciiLine(input: BufferedInputStream): String? {
            val sb = StringBuilder()
            while (true) {
                val b = input.read()
                if (b == -1) return if (sb.isEmpty()) null else sb.toString()
                if (b == '\n'.code) {
                    if (sb.isNotEmpty() && sb[sb.length - 1] == '\r') sb.setLength(sb.length - 1)
                    return sb.toString()
                }
                sb.append(b.toChar())
            }
        }

        override fun close() {
            running.set(false)
            try { socket.close() } catch (_: Exception) {}
            pool.shutdownNow()
        }
    }

    @Test
    fun decorativeAuthAssetBlockerOnlyBlocksHeavyWallpapersNotCaptchasOrQr() {
        assertTrue(
            GdouNativeSyncEngine.isHeavyDecorativeAuthAsset(
                "https://webvpn.gdou.edu.cn/https/77726476/authserver/gdouThemeDz/static/dzimages/one1.png?vpn-1"
            )
        )
        assertTrue(
            GdouNativeSyncEngine.isHeavyDecorativeAuthAsset(
                "https://webvpn.gdou.edu.cn/https/77726476/authserver/gdouThemeDz/static/web/images/login_bg.png"
            )
        )
        assertFalse(
            GdouNativeSyncEngine.isHeavyDecorativeAuthAsset(
                "https://webvpn.gdou.edu.cn/https/77726476/authserver/captcha.html"
            )
        )
        assertFalse(
            GdouNativeSyncEngine.isHeavyDecorativeAuthAsset(
                "https://webvpn.gdou.edu.cn/https/77726476/authserver/qrCode/getQrCode"
            )
        )
        assertFalse(
            GdouNativeSyncEngine.isHeavyDecorativeAuthAsset(
                "https://webvpn.gdou.edu.cn/https/77726476/authserver/gdouThemeDz/static/dzimages/logo.png?vpn-1"
            )
        )
    }

    @Test
    fun webVpnInjectedHeadScriptNeverTriggersFalseAuthExpiryInNativeEngine() {
        val webvpnWrappedHtml = """
            <!DOCTYPE html><html><head><title>学生课表查询</title>
            <script>var __vpn_hostname_data = "webvpn.gdou.edu.cn"; var __vpn_js_file = "/wengine-vpn/js/main.js?ver=20211207";</script>
            <script src="/wengine-vpn/js/main.js?ver=20211207"></script>
            </head><body>
            <div id="cdNav"></div>
            <select id="xnm"><option value="2025">2025-2026</option><option value="2026" selected="selected">2026-2027</option></select>
            <select id="xqm"><option value="3" selected>1</option><option value="12">2</option></select>
            <script>function timeout(){ alert("会话已过期，请重新登录"); }</script>
            </body></html>
        """.trimIndent()
        assertFalse(GdouNativeSyncEngine.isStrongAuthExpiredHtml(webvpnWrappedHtml))
        assertFalse(GdouNativeSyncEngine.isAuthExpiredHtml(webvpnWrappedHtml))
        assertFalse(GdouNativeSyncEngine.isEvalInterceptHtml(webvpnWrappedHtml))

        val xnm = GdouNativeSyncEngine.parseSelectOptionsFromHtml(webvpnWrappedHtml, "xnm")
        val xqm = GdouNativeSyncEngine.parseSelectOptionsFromHtml(webvpnWrappedHtml, "xqm")
        assertEquals(2, xnm.options.size)
        assertEquals(1, xnm.defaultIndex)
        assertEquals("2026", xnm.selectedValue)
        assertEquals(2, xqm.options.size)
        assertEquals("3", xqm.selectedValue)

        val evalHtml = "<html><head><title>提示</title></head><body><p>你有课程未通过，请点击查看详情并确认后方可进行后续操作</p></body></html>"
        assertTrue(GdouNativeSyncEngine.isEvalInterceptHtml(evalHtml))
        assertFalse(GdouNativeSyncEngine.isAuthExpiredHtml(evalHtml))
    }

    @Test
    fun endToEndNativeEngineHandlesCasRedirectConcurrentSyncAndEmptyRooms() = runBlocking {
        val gradePostCounter = AtomicInteger(0)
        val termPageCounter = AtomicInteger(0)
        LocalMiniHttpServer { req ->
            when {
                req.path == "/xtgl/index_initMenu.html" -> {
                    val cookie = req.headers["cookie"].orEmpty()
                    if (!cookie.contains("JSESSIONID=JW_OK")) {
                        MockHttpResp(302, headers = listOf("Location" to "/cas_silent_ticket"))
                    } else {
                        MockHttpResp(
                            200,
                            body = """
                                <html><head><title>教学管理信息服务平台</title>
                                <script>var __vpn_hostname_data = "webvpn.gdou.edu.cn";</script></head>
                                <body><div id="cdNav"></div>
                                <a onclick="clickMenu('N2151','/kbcx/xskbcx_cxXskbcxIndex.html','个人课表查询')">课表</a>
                                <a onclick="clickMenu('N305005','/cjcx/cjcx_cxDgXscj.html','学生成绩查询')">成绩</a>
                                <a onclick="clickMenu('N358105','/kwgl/kscx_cxXsksxxIndex.html','考试信息查询')">考试</a>
                                <a onclick="clickMenu('N401605','/xspjgl/xspj_cxXspjIndex.html','学生评价')">学生评价</a>
                                </body></html>
                            """.trimIndent(),
                            headers = listOf("Content-Type" to "text/html;charset=UTF-8")
                        )
                    }
                }
                req.path == "/cas_silent_ticket" -> MockHttpResp(
                    302,
                    headers = listOf(
                        "Set-Cookie" to "JSESSIONID=JW_OK; Path=/; HttpOnly",
                        "Location" to "/xtgl/index_initMenu.html?jsdm=xs"
                    )
                )
                req.path == "/kbcx/xskbcx_cxXskbcxIndex.html" -> {
                    termPageCounter.incrementAndGet()
                    MockHttpResp(
                        200,
                        body = """
                            <html><head><script src="/wengine-vpn/js/main.js"></script></head><body>
                            <select id="xnm"><option value="2025">2025-2026</option><option value="2026" selected>2026-2027</option></select>
                            <select id="xqm"><option value="3" selected>1</option><option value="12">2</option></select>
                            </body></html>
                        """.trimIndent(),
                        headers = listOf("Content-Type" to "text/html;charset=UTF-8")
                    )
                }
                req.path == "/kbcx/xskbcx_cxXsgrkb.html" -> MockHttpResp(
                    200,
                    body = """
                        {
                          "kbList": [
                            {"kcmc":"(必修)海洋科学导论","xm":"张教授","cdmc":"钟海楼0203","xqj":"星期二","jcs":"1-2","zcd":"1-16周(单)","xqmc":"湖光校区","xf":"3.0"},
                            {"kcmc":"(必修)海洋科学导论","xm":"张教授","cdmc":"钟海楼0203","xqj":"2","jcs":"3-4","zcd":"1-16周(单)","xqmc":"湖光校区","xf":"3.0"}
                          ],
                          "sjkList": [
                            {"kcmc":"海洋认识实习","jsxm":"王老师","qsjsz":"第17-18周","xf":"1.5"}
                          ]
                        }
                    """.trimIndent(),
                    headers = listOf("Content-Type" to "application/json;charset=UTF-8")
                )
                req.path == "/kbcx/xskbcxZccx_cxZcByXnxq.html" -> MockHttpResp(
                    200,
                    body = """[{"zs":"1","zrq":"2026-08-31"},{"zs":"20","zrq":"2027-01-11"}]""",
                    headers = listOf("Content-Type" to "application/json;charset=UTF-8")
                )
                req.path.startsWith("/cjcx/") -> {
                    if (!req.query.contains("doType=query")) {
                        MockHttpResp(200, body = "<div id='cdNav'></div>")
                    } else if (req.path.contains("djkscx")) {
                        MockHttpResp(200, body = """{"items":[{"djksmc":"大学英语四级","cj":"542","ksrq":"2026-06"}],"totalPage":1,"totalResult":1}""")
                    } else {
                        gradePostCounter.incrementAndGet()
                        MockHttpResp(200, body = """{"items":[{"xnmmc":"2025-2026","xqmmc":"1","kcmc":"高等数学A","cj":"92","jd":"4.2","xf":"5.0","ksxz":"正常考试"}],"totalPage":1,"totalResult":1}""")
                    }
                }
                req.path.startsWith("/kwgl/") -> {
                    if (!req.query.contains("doType=query")) {
                        MockHttpResp(200, body = "<div id='cdNav'></div>")
                    } else {
                        MockHttpResp(200, body = """{"items":[{"kcmc":"海洋科学导论","kssj":"2027-01-05(09:00-11:00)","cdmc":"钟海楼0301","zwh":"18","cdxqmc":"湖光校区"}],"totalPage":1,"totalResult":1}""")
                    }
                }
                req.path == "/cdjy/cdjy_cxKxcdlb.html" -> {
                    if (!req.query.contains("doType=query")) {
                        MockHttpResp(
                            200,
                            body = """
                                <html><body>
                                <select id="xnm"><option value="2026" selected>2026-2027</option></select>
                                <select id="xqm"><option value="3" selected>1</option></select>
                                <select id="xqh_id"><option value="1" selected>湖光校区</option><option value="2">海滨校区</option></select>
                                </body></html>
                            """.trimIndent()
                        )
                    } else if (req.body.contains("jcd=3")) {
                        MockHttpResp(200, body = """{"items":[{"cdmc":"钟海楼0203","jxlmc":"钟海楼","cdlbmc":"多媒体教室","zws":"120","xqmc":"湖光校区"},{"cdmc":"钟海楼0205","jxlmc":"钟海楼","cdlbmc":"多媒体教室","zws":"90","xqmc":"湖光校区"}],"totalPage":1,"totalResult":2}""")
                    } else {
                        MockHttpResp(200, body = """{"items":[{"cdmc":"钟海楼0203","jxlmc":"钟海楼","cdlbmc":"多媒体教室","zws":"120","xqmc":"湖光校区"}],"totalPage":1,"totalResult":1}""")
                    }
                }
                else -> MockHttpResp(404)
            }
        }.use { server ->
            val engine = GdouNativeSyncEngine(
                cookieStore = InMemoryGdouCookieStore(),
                customBaseUrlOverride = server.baseUrl,
                allowLoopbackForTest = true
            )

            // 1. Headless CAS 302 silent ticket exchange sets JSESSIONID and parses dynamic menu links
            val session = engine.establishOrVerifySession(SyncChannel.WEBVPN, forceReverify = true)
            assertTrue("Expected Ready after silent 302 ticket, got $session", session is SessionCheckResult.Ready)
            val ready = session as SessionCheckResult.Ready
            assertTrue(ready.menuEndpoints.dynamicLinks.any { it.title.contains("评价") })
            assertEquals(SyncChannel.WEBVPN, engine.lastReadyChannel)

            // 2. Term options parsed accurately from WebVPN-wrapped HTML & served from in-memory cache on 2nd call
            val termRes = engine.fetchAcademicOptions(SyncChannel.WEBVPN)
            assertTrue(termRes is NativeCallResult.Success)
            val termData = (termRes as NativeCallResult.Success).data
            assertEquals("2026", termData.yearOptions[termData.defaultYearIndex].value)
            assertEquals("第一学期", termData.semesterOptions[termData.defaultSemesterIndex].text)
            val cachedTermRes = engine.fetchAcademicOptions(SyncChannel.WEBVPN)
            assertTrue(cachedTermRes is NativeCallResult.Success)
            assertEquals(1, termPageCounter.get())

            // 3. Concurrent full sync merges 1-2 & 3-4 -> 1-4 and retrieves practice, grades, level exams & exams
            val fullRes = engine.syncModules(SyncChannel.WEBVPN, "all", "2026", "3", "2026-2027 第一学期")
            assertTrue(fullRes.errorText == null)
            val cOk = (fullRes.coursesResult as NativeCallResult.Success).data
            assertEquals(1, cOk.courses.size)
            assertEquals("海洋科学导论(必修)", cOk.courses[0].name)
            assertEquals(1, cOk.courses[0].startSection)
            assertEquals(4, cOk.courses[0].endSection)
            assertEquals("2026-08-31", cOk.startDate)
            assertEquals("huguang", cOk.detectedCampusId)
            assertEquals(1, cOk.practiceCourses.size)

            val gOk = (fullRes.gradesResult as NativeCallResult.Success).data
            assertEquals(1, gOk.grades.size)
            assertEquals("高等数学A", gOk.grades[0].courseName)
            assertEquals(1, gOk.levelExams.size)
            assertEquals("大学英语四级", gOk.levelExams[0].examName)

            val eOk = (fullRes.examsResult as NativeCallResult.Success).data
            assertEquals(1, eOk.size)
            assertEquals("18", eOk[0].seatNumber)

            // 4. Zero-WebView quick refresh returns fresh courses cleanly
            val quick = engine.quickRefreshCourses(null, SyncChannel.WEBVPN, "2026", "3", "2026-2027 第一学期")
            assertTrue(quick is QuickRefreshNativeOutcome.Success)
            assertEquals(1, (quick as QuickRefreshNativeOutcome.Success).courses.size)

            // 5. Multi-block empty classroom query strictly intersects 1-2 & 3-4 -> only 钟海楼0203
            val roomRes = engine.queryEmptyClassrooms(SyncChannel.WEBVPN, "湖光", 13, 2, 1, 4, "")
            assertTrue(roomRes is NativeCallResult.Success)
            val rooms = (roomRes as NativeCallResult.Success).data.rooms
            assertEquals(1, rooms.size)
            assertEquals("钟海楼0203", rooms[0].roomName)
            assertNotNull(engine.getCachedMenuEndpoints(SyncChannel.WEBVPN))
        }
    }

    @Test
    fun endToEndNativeEngineDetectsMfaAndZeroGradesFastSkipCleanly() = runBlocking {
        val gradePosts = AtomicInteger(0)
        LocalMiniHttpServer { req ->
            when {
                req.path == "/xtgl/index_initMenu.html" -> MockHttpResp(
                    302,
                    headers = listOf("Location" to "/reauthCheck/reAuthLoginView.do")
                )
                req.path.startsWith("/cjcx/") -> {
                    if (!req.query.contains("doType=query")) {
                        MockHttpResp(200, body = "<div id='cdNav'></div>")
                    } else {
                        gradePosts.incrementAndGet()
                        MockHttpResp(200, body = """{"items":[],"totalPage":1,"totalResult":0}""")
                    }
                }
                else -> MockHttpResp(200, body = "<div id='cdNav'></div>")
            }
        }.use { server ->
            val engine = GdouNativeSyncEngine(
                cookieStore = InMemoryGdouCookieStore(),
                customBaseUrlOverride = server.baseUrl,
                allowLoopbackForTest = true
            )
            val mfaRes = engine.establishOrVerifySession(SyncChannel.WEBVPN, forceReverify = true)
            assertTrue(mfaRes is SessionCheckResult.NeedsAuth)
            val needs = mfaRes as SessionCheckResult.NeedsAuth
            assertTrue(needs.isMfa)
            assertTrue(needs.resumeUrl.contains("reauthCheck", ignoreCase = true))

            // Verify zero-total freshman grades never loops 18 extra yearly queries
            val zeroGrades = engine.syncGradesAndLevelExams(SyncChannel.WEBVPN)
            assertTrue(zeroGrades is NativeCallResult.Failure)
            assertTrue("Expected <= 8 grade endpoint probes on totalResult=0, got ${gradePosts.get()}", gradePosts.get() <= 8)
        }
    }

    @Test
    fun courseRefreshDiffDetectsRoomAndTimeChangesWhileIgnoringManualCourses() {
        val base = listOf(
            CourseItem(
                name = "高等数学A",
                location = "广学楼101",
                dayOfWeek = 1,
                startSection = 1,
                endSection = 2,
                weeks = listOf(1, 2, 3, 4)
            ),
            CourseItem(
                name = "自习计划",
                location = "图书馆",
                dayOfWeek = 6,
                startSection = 3,
                endSection = 4,
                weeks = listOf(1, 2),
                isManual = true
            )
        )

        // 1. No change (manual course excluded cleanly)
        val same = GdouNativeSyncEngine.summarizeCourseRefreshDiff(
            previousCourses = base,
            nextCourses = listOf(base[0].copy(id = "fresh-id"))
        )
        assertFalse(same.hasChanges)
        assertTrue(same.message.contains("暂无调课或教室变动"))

        // 2. Room / time adjusted + new course added
        val updated = GdouNativeSyncEngine.summarizeCourseRefreshDiff(
            previousCourses = base,
            nextCourses = listOf(
                base[0].copy(id = "fresh-1", location = "钟海楼0203"),
                CourseItem(
                    name = "线性代数",
                    location = "明德楼205",
                    dayOfWeek = 3,
                    startSection = 3,
                    endSection = 4,
                    weeks = listOf(1, 2, 3)
                )
            )
        )
        assertTrue(updated.hasChanges)
        assertEquals(1, updated.adjustedCount)
        assertEquals(1, updated.addedCount)
        assertEquals(0, updated.removedCount)
        assertTrue(updated.message.contains("高等数学A"))
        assertTrue(updated.message.contains("调整 1 处"))
        assertTrue(updated.message.contains("新增 1 条"))

        // 3. First-time sync from empty schedule
        val firstSync = GdouNativeSyncEngine.summarizeCourseRefreshDiff(
            previousCourses = emptyList(),
            nextCourses = listOf(base[0]),
            practiceCount = 2
        )
        assertTrue(firstSync.hasChanges)
        assertTrue(firstSync.message.contains("含实践 2 门"))
    }

    @Test
    fun cellularOnlyClassifierAndZeroRttEmptyCookieFastPathWorkCleanly() = runBlocking {
        // Pure 4G/5G -> true; Wi-Fi or VPN present -> false
        assertTrue(NetworkChannelProbe.classifyCellularOnly(hasWifiOrEthernet = false, hasCellular = true, hasVpn = false))
        assertFalse(NetworkChannelProbe.classifyCellularOnly(hasWifiOrEthernet = true, hasCellular = true, hasVpn = false))
        assertFalse(NetworkChannelProbe.classifyCellularOnly(hasWifiOrEthernet = false, hasCellular = true, hasVpn = true))
        assertFalse(NetworkChannelProbe.classifyCellularOnly(hasWifiOrEthernet = false, hasCellular = false, hasVpn = false))

        // 0-RTT empty cookie fast-path returns NeedsAuth instantly without hitting the network
        val engine = GdouNativeSyncEngine(cookieStore = InMemoryGdouCookieStore())
        val start = System.currentTimeMillis()
        val webVpnRes = engine.establishOrVerifySession(SyncChannel.WEBVPN, forceReverify = true)
        val onCampusRes = engine.establishOrVerifySession(SyncChannel.ON_CAMPUS, forceReverify = true)
        val wfwRes = engine.establishOrVerifySession(SyncChannel.WFW_LEGACY, forceReverify = true)
        val elapsed = System.currentTimeMillis() - start
        assertTrue(webVpnRes is SessionCheckResult.NeedsAuth)
        assertTrue(onCampusRes is SessionCheckResult.NeedsAuth)
        assertTrue(wfwRes is SessionCheckResult.NeedsAuth)
        assertTrue("Expected 0-RTT empty-cookie NeedsAuth (<200ms), got ${elapsed}ms", elapsed < 200L)
    }

    @Test
    fun webVpnDeniedAutoFailsOverToWfwViaDirectCasTicketExchangeWithoutSecondPassword() = runBlocking {
        val casMintedServices = mutableListOf<String>()
        val wfwSawPathSsoCookie = AtomicBoolean(false)
        val wfwSawUrlssCookie = AtomicBoolean(false)

        // Mock direct public authserver.gdou.edu.cn
        LocalMiniHttpServer { req ->
            val cookie = req.headers["cookie"].orEmpty()
            if (req.path == "/authserver/login" && cookie.contains("CASTGC=TGT-STUDENT-OK")) {
                val rawService = req.query.substringAfter("service=", "").substringBefore("&")
                val decoded = java.net.URLDecoder.decode(rawService, "UTF-8")
                casMintedServices.add(decoded)
                val sep = if (decoded.contains("?")) "&" else "?"
                MockHttpResp(
                    302,
                    headers = listOf("Location" to "${decoded}${sep}ticket=ST-VALID-999")
                )
            } else {
                MockHttpResp(200, body = "<html><title>统一身份认证平台</title><form id='pwdFromId'></form></html>")
            }
        }.use { casServer ->
            // Mock WebVPN rejecting this student ID with /wengine-vpn/denied ("未在本地用户库注册")
            LocalMiniHttpServer { req ->
                when {
                    req.path == "/login" && req.query.contains("ticket=ST-VALID-999") -> MockHttpResp(
                        302,
                        headers = listOf(
                            "Set-Cookie" to "wengine_vpn_ticket=wrdvpn1-DENIED; Path=/; HttpOnly",
                            "Location" to "/wengine-vpn/denied"
                        )
                    )
                    req.path == "/wengine-vpn/denied" -> MockHttpResp(
                        200,
                        body = "<html><title>访问被拒绝</title><body>未在本地用户库注册，请联系管理员</body></html>"
                    )
                    else -> MockHttpResp(302, headers = listOf("Location" to "/login"))
                }
            }.use { webVpnServer ->
                // Mock wfw.gdou.edu.cn raw proxy with exact Path=/sso + Path=/ JSESSIONID and https://jw.gdou.edu.cn redirect!
                LocalMiniHttpServer { req ->
                    val cookie = req.headers["cookie"].orEmpty()
                    when {
                        req.path == "/" && req.query.contains("ticket=ST-VALID-999") -> MockHttpResp(
                            302,
                            headers = listOf(
                                "Set-Cookie" to "WFWSESSID=WFW-7DAY-OK; Path=/; Max-Age=604800; HttpOnly",
                                "Location" to "/"
                            )
                        )
                        req.path == "/" -> MockHttpResp(200, body = "<html><title>微门户</title><body>OK</body></html>")
                        req.path == "/sso/jziotlogin" && req.query.contains("ticket=ST-VALID-999") -> {
                            if (cookie.contains("urlss=")) wfwSawUrlssCookie.set(true)
                            MockHttpResp(
                                302,
                                headers = listOf(
                                    "Set-Cookie" to "JSESSIONID=SSO-STEP1; Path=/sso; HttpOnly",
                                    // Real Zhengfang redirects to absolute https://jw.gdou.edu.cn/sso/jziotlogin!
                                    "Location" to "https://jw.gdou.edu.cn/sso/jziotlogin"
                                )
                            )
                        }
                        req.path == "/sso/jziotlogin" -> {
                            if (cookie.contains("JSESSIONID=SSO-STEP1")) {
                                wfwSawPathSsoCookie.set(true)
                                MockHttpResp(
                                    302,
                                    headers = listOf(
                                        "Set-Cookie" to "JSESSIONID=JW-FULL-OK; Path=/; HttpOnly",
                                        "Location" to "https://jw.gdou.edu.cn/xtgl/index_initMenu.html?jsdm=xs"
                                    )
                                )
                            } else {
                                MockHttpResp(200, body = "<html><title>统一身份认证平台</title><form id='pwdFromId'></form></html>")
                            }
                        }
                        req.path == "/xtgl/index_initMenu.html" -> {
                            if (cookie.contains("JSESSIONID=JW-FULL-OK")) {
                                MockHttpResp(
                                    200,
                                    body = """<html><title>教学管理信息服务平台</title><body><div id="cdNav"></div><a onclick="clickMenu('N2151','/kbcx/xskbcx_cxXskbcxIndex.html','个人课表查询')">课表</a></body></html>"""
                                )
                            } else {
                                MockHttpResp(200, body = "<html><title>统一身份认证平台</title><form id='pwdFromId'></form></html>")
                            }
                        }
                        req.path == "/kbcx/xskbcx_cxXsgrkb.html" -> MockHttpResp(
                            200,
                            body = """{"kbList":[{"kcmc":"高等数学","xm":"李老师","cdmc":"广学楼101","xqj":"1","jcs":"1-2","zcd":"1-16周","xqmc":"湖光校区"}],"sjkList":[]}"""
                        )
                        else -> MockHttpResp(404)
                    }
                }.use { wfwServer ->
                    val store = InMemoryGdouCookieStore()
                    // Student logged in ONCE on direct authserver.gdou.edu.cn -> CASTGC is present
                    store.saveResponseCookies(
                        "${casServer.baseUrl}/authserver/login",
                        listOf("CASTGC=TGT-STUDENT-OK; Path=/authserver; HttpOnly")
                    )
                    val engine = GdouNativeSyncEngine(
                        cookieStore = store,
                        allowLoopbackForTest = true,
                        casLoginBaseOverride = "${casServer.baseUrl}/authserver/login",
                        webVpnHostOverride = webVpnServer.baseUrl,
                        wfwHostOverride = wfwServer.baseUrl
                    )

                    // Asking for WEBVPN hits /wengine-vpn/denied -> seamlessly auto-fails over to WFW_LEGACY!
                    val outcome = engine.quickRefreshCourses(
                        context = null,
                        preferredChannel = SyncChannel.WEBVPN,
                        savedYear = "2026",
                        savedSem = "3",
                        savedTermLabel = "2026-2027 第一学期",
                        preferLegacyWfw = false
                    )
                    assertTrue("Expected seamless WFW_LEGACY Success on WebVPN denial, got $outcome", outcome is QuickRefreshNativeOutcome.Success)
                    val ok = outcome as QuickRefreshNativeOutcome.Success
                    assertEquals(SyncChannel.WFW_LEGACY, ok.channel)
                    assertEquals(1, ok.courses.size)
                    assertEquals("高等数学", ok.courses[0].name)
                    assertTrue("Expected urlss cookie sent to wfw", wfwSawUrlssCookie.get())
                    assertTrue("Expected Path=/sso JSESSIONID preserved across wfw redirect", wfwSawPathSsoCookie.get())
                    assertFalse("Stale denied wengine_vpn_ticket must be cleared", engine.hasWebVpnTicketCookie())
                    assertTrue("WFW session cookie should be established", engine.hasWfwSessionCookie())
                    assertTrue(casMintedServices.any { it.contains("webvpn") || it.contains("cas_login=true") })
                    assertTrue(casMintedServices.any { it == "https://jw.gdou.edu.cn/sso/jziotlogin" })

                    // Cold-restart simulation with preferLegacyWfw = true goes straight to WFW_LEGACY without ever touching WebVPN again!
                    casMintedServices.clear()
                    val coldEngine = GdouNativeSyncEngine(
                        cookieStore = store,
                        allowLoopbackForTest = true,
                        casLoginBaseOverride = "${casServer.baseUrl}/authserver/login",
                        webVpnHostOverride = webVpnServer.baseUrl,
                        wfwHostOverride = wfwServer.baseUrl
                    )
                    val secondOutcome = coldEngine.quickRefreshCourses(
                        context = null,
                        preferredChannel = SyncChannel.WEBVPN,
                        savedYear = "2026",
                        savedSem = "3",
                        savedTermLabel = "2026-2027 第一学期",
                        preferLegacyWfw = true
                    )
                    assertTrue(secondOutcome is QuickRefreshNativeOutcome.Success)
                    assertEquals(SyncChannel.WFW_LEGACY, (secondOutcome as QuickRefreshNativeOutcome.Success).channel)
                    assertTrue("Should not hit WebVPN again when preferLegacyWfw=true", casMintedServices.none { it.contains("cas_login=true") })
                }
            }
        }
    }

    @Test
    fun agedExpiredWebVpnTicketIsClearedBeforeReturningNeedsAuth() = runBlocking {
        LocalMiniHttpServer { req ->
            when {
                req.path.startsWith("/https/") -> MockHttpResp(
                    302,
                    headers = listOf(
                        "Location" to "/login",
                        "Set-Cookie" to "wengine_vpn_ticket=wrdvpn1-GUEST; Path=/; HttpOnly"
                    )
                )
                else -> MockHttpResp(200, body = "<html><title>WEB VPN</title><form id='form'></form></html>")
            }
        }.use { webVpnServer ->
            val store = InMemoryGdouCookieStore()
            store.saveResponseCookies(
                "${webVpnServer.baseUrl}/",
                listOf("wengine_vpn_ticket=wrdvpn1-AGED-EXPIRED; Path=/; HttpOnly")
            )
            val engine = GdouNativeSyncEngine(
                cookieStore = store,
                allowLoopbackForTest = true,
                casLoginBaseOverride = "${webVpnServer.baseUrl}/authserver/login",
                webVpnHostOverride = webVpnServer.baseUrl
            )
            assertTrue(engine.hasWebVpnTicketCookie())
            val res = engine.establishOrVerifySession(SyncChannel.WEBVPN, forceReverify = true)
            assertTrue("Expected NeedsAuth for aged expired ticket, got $res", res is SessionCheckResult.NeedsAuth)
            assertFalse("Stale/guest wengine_vpn_ticket must be cleared before interactive re-auth", engine.hasWebVpnTicketCookie())
        }
    }
}
