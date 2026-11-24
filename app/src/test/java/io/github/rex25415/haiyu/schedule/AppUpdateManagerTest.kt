package io.github.rex25415.haiyu.schedule

import io.github.rex25415.haiyu.schedule.data.GdouTimeRules
import io.github.rex25415.haiyu.schedule.data.OccurrenceState
import io.github.rex25415.haiyu.schedule.model.AppDataSnapshot
import io.github.rex25415.haiyu.schedule.model.AppSettings
import io.github.rex25415.haiyu.schedule.model.CourseItem
import io.github.rex25415.haiyu.schedule.model.GdouCampus
import io.github.rex25415.haiyu.schedule.model.GridRowDensity
import io.github.rex25415.haiyu.schedule.sync.CampusNetworkState
import io.github.rex25415.haiyu.schedule.sync.GdouJsBridge
import io.github.rex25415.haiyu.schedule.sync.GdouSyncScript
import io.github.rex25415.haiyu.schedule.sync.NetworkChannelProbe
import io.github.rex25415.haiyu.schedule.update.AppReleaseInfo
import io.github.rex25415.haiyu.schedule.update.AppSignatureVerifier
import io.github.rex25415.haiyu.schedule.update.AppUpdateManager
import io.github.rex25415.haiyu.schedule.update.SignatureTrustState
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class AppUpdateManagerTest {

    private val lenientJson = Json { ignoreUnknownKeys = true }

    @Test
    fun semanticVersionComparisonHandlesEdgeCases() {
        assertTrue(AppUpdateManager.isNewerVersion("v2.5.0", "2.4.0"))
        assertTrue(AppUpdateManager.isNewerVersion("2.10.0", "2.9.9"))
        assertTrue(AppUpdateManager.isNewerVersion("v3.0", "2.9.9"))
        assertTrue(AppUpdateManager.isNewerVersion("2.5.1", "2.5.0"))
        assertTrue(AppUpdateManager.isNewerVersion("v3.4.0-beta.1", "3.3.0"))
        assertTrue(AppUpdateManager.isNewerVersion("v3.4.0-beta.2", "3.4.0-beta.1"))
        assertTrue(AppUpdateManager.isNewerVersion("v3.4.0", "3.4.0-beta.2"))

        assertFalse(AppUpdateManager.isNewerVersion("v2.5.0", "2.5.0"))
        assertFalse(AppUpdateManager.isNewerVersion("v3.4.0-beta.1", "3.4.0"))
        assertFalse(AppUpdateManager.isNewerVersion("2.4.9", "2.5.0"))
        assertFalse(AppUpdateManager.isNewerVersion("1.9.9", "2.0.0"))
        assertFalse(AppUpdateManager.isNewerVersion("", "2.5.0"))
    }

    @Test
    fun parseGitHubReleaseJsonExtractsApkAndApiDigest() {
        val sampleJson = """
            {
              "tag_name": "v2.5.0",
              "name": "海屿课表 v2.5.0",
              "html_url": "https://github.com/rex25415/gdou-haiyu-schedule/releases/tag/v2.5.0",
              "body": "## 更新日志\n- 新增应用内自动检查更新\n- 修复红线遮挡课程文字",
              "assets": [
                {
                  "name": "checksums.txt",
                  "size": 128,
                  "browser_download_url": "https://example.com/checksums.txt"
                },
                {
                  "name": "HaiyuSchedule-GDOU-v2.5.0.apk",
                  "size": 1750000,
                  "digest": "sha256:783ffb836dc2a65061d54414ead705a2f71fac3e6c9908f48ac4634f7f4997b7",
                  "browser_download_url": "https://github.com/rex25415/gdou-haiyu-schedule/releases/download/v2.5.0/HaiyuSchedule-GDOU-v2.5.0.apk"
                }
              ]
            }
        """.trimIndent()

        val info = AppUpdateManager.parseReleaseJson(sampleJson)
        assertNotNull(info)
        assertEquals("v2.5.0", info!!.tagName)
        assertEquals("2.5.0", info.versionName)
        assertEquals("HaiyuSchedule-GDOU-v2.5.0.apk", info.apkFileName)
        assertEquals(1750000L, info.apkSizeBytes)
        assertEquals(
            "783ffb836dc2a65061d54414ead705a2f71fac3e6c9908f48ac4634f7f4997b7",
            info.expectedSha256
        )
        assertTrue(info.releaseNotes.contains("新增应用内自动检查更新"))
    }

    @Test
    fun parseGitHubReleaseJsonFallsBackToBodySha256() {
        val sampleJson = """
            {
              "tag_name": "v2.6.0",
              "name": "v2.6.0",
              "body": "### 优化\n- 更流畅\n- SHA-256：`ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789`",
              "assets": [
                {
                  "name": "HaiyuSchedule-GDOU-v2.6.0.apk",
                  "size": 1800000,
                  "browser_download_url": "https://github.com/rex25415/gdou-haiyu-schedule/releases/download/v2.6.0/HaiyuSchedule-GDOU-v2.6.0.apk"
                }
              ]
            }
        """.trimIndent()

        val info = AppUpdateManager.parseReleaseJson(sampleJson)
        assertNotNull(info)
        assertEquals(
            "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
            info!!.expectedSha256
        )
    }

    @Test
    fun upgradeFromV240SettingsPreservesDataAndEnablesAutoUpdate() {
        val v240Json = """
            {
              "settings": {
                "campus": "HAIBIN",
                "showWeekend": true,
                "settingsVersion": 2,
                "semesterStartDate": "2026-08-31"
              }
            }
        """.trimIndent()
        val decoded = lenientJson.decodeFromString(AppDataSnapshot.serializer(), v240Json)
        assertEquals(GdouCampus.HAIBIN, decoded.settings.campus)
        assertTrue(decoded.settings.showWeekend)
        assertTrue(decoded.settings.autoCheckUpdates)
        assertEquals(0L, decoded.settings.lastUpdateCheckEpochMs)
        assertEquals("", decoded.settings.ignoredUpdateVersion)
    }

    @Test
    fun todayStatusesAndOverlapClustersShareConsistentOrdering() {
        val c1 = CourseItem(
            id = "c1",
            name = "高等数学 I x1",
            location = "教学楼121",
            dayOfWeek = 1,
            startSection = 3,
            endSection = 4,
            weeks = listOf(1)
        )
        val c2 = CourseItem(
            id = "c2",
            name = "工程数学",
            location = "实验楼405",
            dayOfWeek = 1,
            startSection = 3,
            endSection = 4,
            weeks = listOf(1)
        )
        val monday1037 = LocalDateTime.of(2026, 8, 31, 10, 37)
        val statuses = GdouTimeRules.getTodayCoursesWithStatus(
            courses = listOf(c1, c2),
            startDateStr = "2026-08-31",
            totalWeeks = 20,
            campus = GdouCampus.HAIBIN,
            now = monday1037
        )
        val clusters = GdouTimeRules.groupOverlappingCourses(listOf(c1, c2))
        assertEquals(2, statuses.count { it.state == OccurrenceState.ONGOING })
        assertEquals(clusters.first().courses.first().id, statuses.first().course.id)
    }

    @Test
    fun jsBridgeOriginGuardBlocksSpoofedAndCleartextUrls() {
        assertTrue(GdouJsBridge.isTrustedGdouOrigin("https://jw.gdou.edu.cn/kbcx/xskbcx_cxXskbcxIndex.html"))
        assertTrue(GdouJsBridge.isTrustedGdouOrigin("https://wfw.gdou.edu.cn/https://jw.gdou.edu.cn/"))
        assertTrue(GdouJsBridge.isTrustedGdouOrigin("https://gdou.edu.cn/"))

        assertFalse(GdouJsBridge.isTrustedGdouOrigin("http://jw.gdou.edu.cn/"))
        assertFalse(GdouJsBridge.isTrustedGdouOrigin("https://gdou.edu.cn.evil.com/"))
        assertFalse(GdouJsBridge.isTrustedGdouOrigin("https://evil.com/?next=jw.gdou.edu.cn"))
        assertFalse(GdouJsBridge.isTrustedGdouOrigin("file:///data/local/tmp/x.html"))
        assertFalse(GdouJsBridge.isTrustedGdouOrigin(null))

        assertEquals(
            "https://authserver.gdou.edu.cn/authserver/login?service=https%3A%2F%2Fjw.gdou.edu.cn",
            GdouJsBridge.upgradeGdouHttpToHttps("http://authserver.gdou.edu.cn/authserver/login?service=https%3A%2F%2Fjw.gdou.edu.cn")
        )
        assertEquals(null, GdouJsBridge.upgradeGdouHttpToHttps("http://gdou.edu.cn.evil.com/login"))
        assertEquals(null, GdouJsBridge.upgradeGdouHttpToHttps("https://authserver.gdou.edu.cn/"))
    }

    @Test
    fun downloadRoutesOnlyUseMirrorsWhenSha256IsPresent() {
        val verifiedRelease = AppReleaseInfo(
            tagName = "v2.6.0",
            versionName = "2.6.0",
            title = "v2.6.0",
            releaseNotes = "notes",
            htmlUrl = "https://github.com/rex25415/gdou-haiyu-schedule/releases/tag/v2.6.0",
            apkDownloadUrl = "https://github.com/rex25415/gdou-haiyu-schedule/releases/download/v2.6.0/HaiyuSchedule-GDOU-v2.6.0.apk",
            apkFileName = "HaiyuSchedule-GDOU-v2.6.0.apk",
            apkSizeBytes = 1700000L,
            expectedSha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        )
        val verifiedRoutes = AppUpdateManager.buildDownloadRoutes(verifiedRelease)
        assertEquals(4, verifiedRoutes.size)
        assertTrue(verifiedRoutes.all { it.requiresSha256 })
        assertEquals("GitHub 官方源", verifiedRoutes.last().label)

        val noShaRelease = verifiedRelease.copy(expectedSha256 = null)
        val directOnlyRoutes = AppUpdateManager.buildDownloadRoutes(noShaRelease)
        assertEquals(1, directOnlyRoutes.size)
        assertEquals(verifiedRelease.apkDownloadUrl, directOnlyRoutes.first().url)
        assertFalse(directOnlyRoutes.first().requiresSha256)
    }

    @Test
    fun networkChannelProbeRecommendsSmartFallback() {
        assertEquals(CampusNetworkState.ON_CAMPUS_DIRECT_READY, NetworkChannelProbe.classifyState(true, true))
        assertEquals(CampusNetworkState.OFF_CAMPUS_ONLY, NetworkChannelProbe.classifyState(false, true))
        assertEquals(CampusNetworkState.OFFLINE_OR_BLOCKED, NetworkChannelProbe.classifyState(false, false))

        assertTrue(NetworkChannelProbe.shouldAutoFallbackToOffCampus(currentlyOffCampus = false, state = CampusNetworkState.OFF_CAMPUS_ONLY))
        assertFalse(NetworkChannelProbe.shouldAutoFallbackToOffCampus(currentlyOffCampus = true, state = CampusNetworkState.OFF_CAMPUS_ONLY))
        assertFalse(NetworkChannelProbe.shouldAutoFallbackToOffCampus(currentlyOffCampus = false, state = CampusNetworkState.ON_CAMPUS_DIRECT_READY))
    }

    @Test
    fun parseStaticManifestAndFriendlyErrorsWorkForChina5G() {
        val manifest = """
            {
              "tagName": "v2.7.0",
              "versionName": "2.7.0",
              "title": "海屿课表 v2.7.0",
              "apkFileName": "HaiyuSchedule-GDOU-v2.7.0.apk",
              "apkSizeBytes": 1740000,
              "apkDownloadUrl": "https://github.com/rex25415/gdou-haiyu-schedule/releases/download/v2.7.0/HaiyuSchedule-GDOU-v2.7.0.apk",
              "expectedSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
              "htmlUrl": "https://github.com/rex25415/gdou-haiyu-schedule/releases/tag/v2.7.0",
              "releaseNotes": "- 修复非本周「回到本周」与校区徽标重叠\n- 通道开关升级为实心勾选分段栏"
            }
        """.trimIndent()
        val parsed = AppUpdateManager.parseStaticManifestJson(manifest)
        assertNotNull(parsed)
        assertEquals("2.7.0", parsed!!.versionName)
        assertEquals(1740000L, parsed.apkSizeBytes)
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", parsed.expectedSha256)
        assertTrue(parsed.releaseNotes.contains("回到本周"))

        val dnsErr = AppUpdateManager.friendlyNetworkError(java.net.UnknownHostException("Unable to resolve host \"github.com\""))
        assertTrue(dnsErr.contains("DNS"))
    }

    @Test
    fun signatureVerifierDistinguishesOriginalDebugAndRepackagedBuilds() {
        val original = AppSignatureVerifier.evaluateFingerprints(
            actualFingerprints = listOf(AppSignatureVerifier.EXPECTED_RELEASE_CERT_SHA256.uppercase()),
            isDebugBuild = false
        )
        assertEquals(SignatureTrustState.VERIFIED_ORIGINAL, original.state)
        assertEquals("9D7C62E6…98EC26CE", original.shortFingerprint)

        val localDebug = AppSignatureVerifier.evaluateFingerprints(
            actualFingerprints = listOf("1111111111111111111111111111111111111111111111111111111111111111"),
            isDebugBuild = true
        )
        assertEquals(SignatureTrustState.DEBUG_LOCAL_BUILD, localDebug.state)

        val repackaged = AppSignatureVerifier.evaluateFingerprints(
            actualFingerprints = listOf("deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef"),
            isDebugBuild = false
        )
        assertEquals(SignatureTrustState.THIRD_PARTY_MODIFIED, repackaged.state)
    }

    @Test
    fun offCampusSyncScriptKeepsWfwProxyPrefixAndPrewarmsExamsAndGrades() {
        val syncJs = GdouSyncScript.buildSyncScript("all", "2026", "3", "2026-2027 第一学期")
        assertTrue(syncJs.contains("if (host.includes(\"wfw.gdou.edu.cn\")) return \"/https://jw.gdou.edu.cn\";"))
        assertTrue(syncJs.contains("/kwgl/kscx_cxXsksxxIndex.html?gnmkdm=N358105&layout=default"))
        assertTrue(syncJs.contains("/cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005&layout=default"))
        assertTrue(syncJs.contains("/cjcx/cjcx_cxDcListByXs.html?doType=query&gnmkdm=N305005"))
        assertTrue(syncJs.contains("let _wfwSessionWarmed = false;"))
        assertTrue(syncJs.contains("let _cachedMenuEndpoints = null;"))

        val navJs = GdouSyncScript.buildQuickNavigateScript("kwgl/kscx_cxXsksxxIndex.html?gnmkdm=N358105&layout=default")
        assertTrue(navJs.contains("if (host.includes(\"wfw.gdou.edu.cn\")) return \"/https://jw.gdou.edu.cn\";"))
    }

    @Test
    fun upgradeFromV280SettingsPreservesDataAndPopulatesV290Defaults() {
        val v280Json = """
            {
              "settings": {
                "campus": "HAIBIN",
                "showWeekend": true,
                "settingsVersion": 2,
                "semesterStartDate": "2026-08-31",
                "preferOffCampusChannel": true
              }
            }
        """.trimIndent()
        val decoded = lenientJson.decodeFromString(AppDataSnapshot.serializer(), v280Json)
        assertTrue(decoded.settings.pinnedConflictKeys.isEmpty())
        assertEquals("", decoded.settings.scheduleBgFileName)
        assertEquals(0.92f, decoded.settings.courseCardAlpha, 0.001f)
        assertEquals(GridRowDensity.STANDARD, decoded.settings.gridRowDensity)
        assertTrue(decoded.settings.showExamsOnTimetable)
        assertFalse(decoded.settings.legacyCleanupDismissed)
        assertEquals("", decoded.settings.savedPartnerCode)
    }

    @Test
    fun shareTextMirrorLinkAndDiagnosticInfoAreCleanAndPrivacySafe() {
        val shareText = AppUpdateManager.buildAppShareText()
        assertTrue(shareText.contains("海屿空间"))
        assertTrue(shareText.contains(AppUpdateManager.DOWNLOAD_PAGE_URL))

        val mirrorDefault = AppUpdateManager.buildMirrorApkUrl("3.1.0")
        assertEquals(
            "https://gh-proxy.com/https://github.com/rex25415/gdou-haiyu-schedule/releases/download/v3.1.0/HaiyuSchedule-GDOU-v3.1.0.apk",
            mirrorDefault
        )
        val mirrorCustom = AppUpdateManager.buildMirrorApkUrl(
            "3.1.0",
            "https://github.com/rex25415/gdou-haiyu-schedule/releases/download/v3.1.0/Custom.apk"
        )
        assertEquals(
            "https://gh-proxy.com/https://github.com/rex25415/gdou-haiyu-schedule/releases/download/v3.1.0/Custom.apk",
            mirrorCustom
        )

        val diag = AppUpdateManager.buildDiagnosticInfo(
            versionName = "3.1.0",
            versionCode = 310,
            androidRelease = "15",
            sdkInt = 35,
            campusLabel = "海滨校区",
            densityLabel = "标准舒适",
            darkModeLabel = "跟随系统"
        )
        assertTrue(diag.contains("海屿空间 v3.1.0 (build 310)"))
        assertTrue(diag.contains("Android 15 (API 35)"))
        assertTrue(diag.contains("海滨校区"))
        assertTrue(AppUpdateManager.ISSUES_NEW_URL.endsWith("/issues/new/choose"))
    }

    @Test
    fun v310ModelBackwardCompatibilityAndExamSyncDiagnosticsWork() {
        val legacyCourseAndExamJson = """
            {
              "courses": [
                {"name": "电路原理", "dayOfWeek": 1, "startSection": 1, "endSection": 2, "weeks": [1, 2]}
              ],
              "exams": [
                {"courseName": "线性代数", "examTimeRaw": "2026-12-07(19:10-21:10)"}
              ]
            }
        """.trimIndent()
        val decoded = lenientJson.decodeFromString(AppDataSnapshot.serializer(), legacyCourseAndExamJson)
        assertEquals(0, decoded.courses.first().absenceCount)
        assertEquals(0, decoded.courses.first().bonusCount)
        assertFalse(decoded.exams.first().isPinned)

        val syncJs = GdouSyncScript.buildSyncScript("exams", "2026", "3", "2026-2027 第一学期")
        assertTrue(syncJs.contains("教务已联通，当前学期暂未公布考场安排"))
        assertTrue(syncJs.contains("classifyNonJsonHtml"))
        assertTrue(syncJs.contains("需先在上方教务页确认通知或完成评教"))
        assertTrue(syncJs.contains("queryExamsOnce(workingEp, \"\", \"\")"))
        assertTrue(syncJs.contains("scrapeExamsFromCurrentDom"))
        assertTrue(syncJs.contains("scrapeGradesFromCurrentDom"))
        assertTrue(syncJs.contains("sjkList"))
        assertTrue(syncJs.contains("syncLevelExamsGentle"))

        val roomJs = GdouSyncScript.buildQueryEmptyClassroomsScript("湖光", 13, 2, 1, 2, "", "2026", "3")
        assertTrue(roomJs.contains("/cdjy/cdjy_cxKxcdlb.html?doType=query&gnmkdm=N2155"))
        assertTrue(roomJs.contains("queryModel.showCount=1000"))
        assertTrue(roomJs.contains("#dm_cx"))
        assertTrue(roomJs.contains("BBF8A51ACEA2144EE053768926D2788B"))
        assertTrue(roomJs.contains("onEmptyClassroomsLoaded"))

        val domJs = GdouSyncScript.buildScrapeCurrentPageDomScript()
        assertTrue(domJs.contains("已从当前网页表格直接提取"))
        assertTrue(decoded.practiceCourses.isEmpty())
        assertTrue(decoded.levelExams.isEmpty())
        assertEquals(160.0, decoded.settings.creditTargets.totalRequired, 0.01)
        assertFalse(decoded.settings.compactScheduleBanner)
    }

    @Test
    fun emptyClassroomClassificationAndNaturalRoomSortingWork() {
        val guangxueRegular = io.github.rex25415.haiyu.schedule.model.EmptyClassroomItem(
            roomName = "广学楼105",
            building = "广学楼",
            roomType = "实验室", // GDOU tags non-multimedia standard rooms as "实验室"
            seats = "75",
            campus = "湖光校区"
        )
        val guangxueMulti = io.github.rex25415.haiyu.schedule.model.EmptyClassroomItem(
            roomName = "广学楼208(多)",
            building = "广学楼",
            roomType = "多媒体教室",
            seats = "78",
            campus = "湖光校区"
        )
        val haibinLab = io.github.rex25415.haiyu.schedule.model.EmptyClassroomItem(
            roomName = "实验楼308",
            building = "实验楼",
            roomType = "实验室",
            seats = "90",
            campus = "海滨校区"
        )
        val specialCoffeeLab = io.github.rex25415.haiyu.schedule.model.EmptyClassroomItem(
            roomName = "广东海洋大学商学院百瑞斯塔咖啡实验室",
            building = "济航楼",
            roomType = "实验室",
            seats = "36",
            campus = "阳江校区"
        )
        val computerRoom = io.github.rex25415.haiyu.schedule.model.EmptyClassroomItem(
            roomName = "海纳楼401(计)",
            building = "海纳楼",
            roomType = "计算机房",
            seats = "80",
            campus = "阳江校区"
        )
        assertTrue(io.github.rex25415.haiyu.schedule.ui.components.isPrimeSelfStudyClassroom(guangxueRegular))
        assertTrue(io.github.rex25415.haiyu.schedule.ui.components.isPrimeSelfStudyClassroom(guangxueMulti))
        assertTrue(io.github.rex25415.haiyu.schedule.ui.components.isPrimeSelfStudyClassroom(haibinLab))
        assertFalse(io.github.rex25415.haiyu.schedule.ui.components.isPrimeSelfStudyClassroom(specialCoffeeLab))
        assertFalse(io.github.rex25415.haiyu.schedule.ui.components.isPrimeSelfStudyClassroom(computerRoom))

        val rawRoomNames = listOf("广学楼1001", "广学楼208(多)", "广学楼105")
        val sorted = rawRoomNames.sortedBy { io.github.rex25415.haiyu.schedule.ui.components.naturalRoomSortKey(it) }
        assertEquals(listOf("广学楼105", "广学楼208(多)", "广学楼1001"), sorted)
    }

    @Test
    fun gradeRetakeAndMakeupDeduplicationProducesAuthenticGpaAndCredits() {
        val sampleGrades = listOf(
            io.github.rex25415.haiyu.schedule.model.GradeItem(
                id = "g1",
                academicYear = "2025-2026",
                semester = "第一学期",
                courseName = "大学生国家安全教育",
                score = "37",
                gradePoint = 0.0,
                credit = 1.0,
                courseType = "必修",
                examNature = "正常考试"
            ),
            io.github.rex25415.haiyu.schedule.model.GradeItem(
                id = "g2",
                academicYear = "2025-2026",
                semester = "第二学期",
                courseName = "大学生国家安全教育",
                score = "96",
                gradePoint = 4.6,
                credit = 1.0,
                courseType = "必修",
                examNature = "补考"
            ),
            io.github.rex25415.haiyu.schedule.model.GradeItem(
                id = "g3",
                academicYear = "2025-2026",
                semester = "第二学期",
                courseName = "大学物理Ⅱx1",
                score = "54",
                gradePoint = 0.0,
                credit = 3.0,
                courseType = "必修",
                examNature = "正常考试"
            ),
            io.github.rex25415.haiyu.schedule.model.GradeItem(
                id = "g4",
                academicYear = "2026-2027",
                semester = "第一学期",
                courseName = "大学物理Ⅱx1",
                score = "缺考",
                gradePoint = 0.0,
                credit = 3.0,
                courseType = "必修",
                examNature = "补考"
            )
        )

        val dedup = io.github.rex25415.haiyu.schedule.ui.components.deduplicateEffectiveGrades(sampleGrades)
        assertEquals(2, dedup.effectiveGrades.size)
        assertEquals(1, dedup.passedGrades.size)
        assertEquals(1.0, dedup.earnedCredits, 0.001)
        assertEquals(4.0, dedup.attemptedDistinctCredits, 0.001)
        assertEquals(4.0, dedup.duplicateCreditsRemoved, 0.001)
        assertEquals(2, dedup.duplicateCourseCount)
        assertEquals(1, dedup.makeupRecoveredCourses.size)
        assertEquals("g2", dedup.supersededByMap["g1"]?.bestAttempt?.id)
        assertTrue(dedup.supersededByMap["g1"]?.isBestPassed == true)
        assertEquals("g3", dedup.supersededByMap["g4"]?.bestAttempt?.id)
        assertFalse(dedup.supersededByMap["g4"]?.isBestPassed == true)

        val effectiveMetrics = io.github.rex25415.haiyu.schedule.ui.components.computeGradeMetrics(
            sampleGrades,
            io.github.rex25415.haiyu.schedule.ui.components.GpaCalcMode.EFFECTIVE_BEST
        )
        // Effective: (4.6*1.0 + 0.0*3.0) / 4.0 = 1.15; Score: (96*1.0 + 54*3.0) / 4.0 = 64.5
        assertEquals(1.15, effectiveMetrics.gpa, 0.001)
        assertEquals(64.5, effectiveMetrics.weightedAvgScore, 0.01)
        assertEquals(4.0, effectiveMetrics.totalCredits, 0.001)

        val passedMetrics = io.github.rex25415.haiyu.schedule.ui.components.computeGradeMetrics(
            sampleGrades,
            io.github.rex25415.haiyu.schedule.ui.components.GpaCalcMode.PASSED_ONLY
        )
        assertEquals(4.6, passedMetrics.gpa, 0.001)
        assertEquals(96.0, passedMetrics.weightedAvgScore, 0.01)
        assertEquals(1.0, passedMetrics.totalCredits, 0.001)

        val trainingGrade = io.github.rex25415.haiyu.schedule.model.GradeItem(
            academicYear = "2025-2026",
            semester = "第二学期",
            courseName = "海上求生训练",
            score = "良好",
            gradePoint = 3.5,
            credit = 1.0,
            courseType = "必修"
        )
        assertTrue(io.github.rex25415.haiyu.schedule.ui.components.isPracticeOrExperimentGrade(trainingGrade))
        assertFalse(io.github.rex25415.haiyu.schedule.ui.components.isPracticeOrExperimentGrade(sampleGrades[0]))
    }

    @Test
    fun inspectRoomDayScriptAndFavoriteSettingsCompatibilityWork() {
        val multiBlockJs = GdouSyncScript.buildQueryEmptyClassroomsScript(
            campusKeyword = "湖光",
            weekNumber = 12,
            dayOfWeek = 3,
            startSection = 1,
            endSection = 4,
            buildingKeyword = ""
        )
        assertTrue(multiBlockJs.contains("window.__haiyuRoomQuerySeq"))
        assertTrue(multiBlockJs.contains("for (const pair of blocks)"))
        assertTrue(multiBlockJs.contains("fetchEmptyRoomBlock"))
        assertTrue(multiBlockJs.contains("!m.has(key)"))
        assertFalse(multiBlockJs.contains(".every("))
        assertFalse(multiBlockJs.contains(".some("))
        assertTrue(multiBlockJs.contains("onEmptyClassroomsLoaded"))

        val dayScript = GdouSyncScript.buildInspectRoomDayScheduleScript(
            campusKeyword = "湖光",
            weekNumber = 12,
            dayOfWeek = 3,
            buildingName = "广学楼",
            roomName = "广学楼208(多)"
        )
        assertTrue(dayScript.contains("/cdjy/cdjy_cxKxcdlb.html?doType=query&gnmkdm=N2155"))
        assertTrue(dayScript.contains("window.__haiyuRoomInspectSeq"))
        assertTrue(dayScript.contains("dayBlocks.length"))
        assertFalse(dayScript.contains(".every("))
        assertTrue(dayScript.contains("onClassroomDayScheduleLoaded"))
        assertTrue(dayScript.contains("广学楼208(多)"))
        assertTrue(dayScript.contains("[[1,2],[3,4],[5,6],[7,8],[9,10]]"))
        assertTrue(dayScript.contains("\"208\""))

        val legacySettings = lenientJson.decodeFromString(
            AppSettings.serializer(),
            """{"campus":"HAIBIN","autoSyncOnLaunch":true}"""
        )
        assertTrue(legacySettings.favoriteBuildings.isEmpty())
        assertTrue(legacySettings.favoriteClassrooms.isEmpty())

        val roundTrip = lenientJson.decodeFromString(
            AppSettings.serializer(),
            lenientJson.encodeToString(
                AppSettings.serializer(),
                AppSettings(
                    campus = GdouCampus.HUGUANG,
                    favoriteBuildings = listOf("广学楼", "钟海楼"),
                    favoriteClassrooms = listOf("广学楼208(多)")
                )
            )
        )
        assertEquals(listOf("广学楼", "钟海楼"), roundTrip.favoriteBuildings)
        assertEquals(listOf("广学楼208(多)"), roundTrip.favoriteClassrooms)
    }

    @Test
    fun wallpaperScalingMathHandlesPortraitLandscapeAndEdgeSizes() {
        // Vertical 4000x6000 camera photo -> 960x1440 (preserves aspect ratio & stays <= 1440px)
        assertEquals(
            960 to 1440,
            io.github.rex25415.haiyu.schedule.data.computeScaledTargetDimensions(4000, 6000, 1440)
        )
        // Landscape 3840x2160 4K image -> 1440x810
        assertEquals(
            1440 to 810,
            io.github.rex25415.haiyu.schedule.data.computeScaledTargetDimensions(3840, 2160, 1440)
        )
        // Small 1080x1920 image -> unscaled 1080x1920? Wait: 1920 > 1440 -> 810x1440; 800x600 stays 800x600
        assertEquals(
            810 to 1440,
            io.github.rex25415.haiyu.schedule.data.computeScaledTargetDimensions(1080, 1920, 1440)
        )
        assertEquals(
            800 to 600,
            io.github.rex25415.haiyu.schedule.data.computeScaledTargetDimensions(800, 600, 1440)
        )
        // Invalid dimensions fall back safely instead of dividing by zero
        assertEquals(
            1440 to 1440,
            io.github.rex25415.haiyu.schedule.data.computeScaledTargetDimensions(0, -1, 1440)
        )
        assertEquals(1, io.github.rex25415.haiyu.schedule.data.computeBitmapInSampleSize(0, 0, 1440))
        assertEquals(4, io.github.rex25415.haiyu.schedule.data.computeBitmapInSampleSize(4000, 3000, 1440))
    }

    @Test
    fun cleanReleaseNotesStripsVerifyFooterAndTruncatesAtLineBoundary() {
        val sampleBody = buildString {
            appendLine("## 🌊 海屿空间 v3.3.0")
            appendLine("💬 致各位海大同学：这是精简的人话版更新内容。")
            appendLine("- 查空教室支持全天 5 大节逐节查看")
            appendLine("- 修复补考与重修重复计算 GPA 的问题")
            appendLine("---")
            appendLine("### 📦 安装与校验信息")
            appendLine("- SHA-256：`29a9198544edc63f324cc721d8a2729bb271aa653533c681a08c280331a9ca27`")
        }
        val cleaned = AppUpdateManager.cleanReleaseNotes(sampleBody)
        assertTrue(cleaned.contains("查空教室支持全天 5 大节逐节查看"))
        assertFalse(cleaned.contains("---"))
        assertFalse(cleaned.contains("安装与校验信息"))
        assertFalse(cleaned.contains("SHA-256"))

        val markedBody = """
            ## 🌊 海屿空间 v3.4.0
            技术引言（不应在弹窗显示）
            <!-- APP_NOTES_START -->
            - 新功能 A：桌面极简方卡优化
            - 新功能 B：自动清理临时缓存
            <!-- APP_NOTES_END -->
            ---
            ### 文件校验
            - 文件名：HaiyuSchedule-GDOU-v3.4.0.apk
        """.trimIndent()
        val markedClean = AppUpdateManager.cleanReleaseNotes(markedBody)
        assertEquals("- 新功能 A：桌面极简方卡优化\n- 新功能 B：自动清理临时缓存", markedClean)

        val longLines = (1..30).joinToString("\n") { i ->
            "- 第 $i 条完整更新说明：保证在任何屏幕宽度下都不会截断在半个词语中间。"
        }
        val truncated = AppUpdateManager.cleanReleaseNotes(longLines)
        assertTrue(truncated.length <= 900)
        assertTrue(truncated.endsWith("中间。"))
    }

    @Test
    fun staticManifestDynamicMirrorsAndExtendedDiagnosticsWork() {
        val manifest = """
            {
              "tagName": "v3.3.0",
              "versionName": "3.3.0",
              "apkDownloadUrl": "https://github.com/rex25415/gdou-haiyu-schedule/releases/download/v3.3.0/HaiyuSchedule-GDOU-v3.3.0.apk",
              "expectedSha256": "29a9198544edc63f324cc721d8a2729bb271aa653533c681a08c280331a9ca27",
              "mirrorPrefixes": [
                "https://custom-mirror.example.com",
                "http://insecure.example.com/",
                "https://ghfast.top/"
              ],
              "releaseNotes": "- 精简摘要\n---\n### 📦 安装与校验信息\n- SHA-256：29a9198544edc63f324cc721d8a2729bb271aa653533c681a08c280331a9ca27"
            }
        """.trimIndent()
        val parsed = AppUpdateManager.parseStaticManifestJson(manifest)
        assertNotNull(parsed)
        assertEquals(
            listOf("https://custom-mirror.example.com/", "https://ghfast.top/"),
            parsed!!.mirrorPrefixes
        )
        assertEquals("- 精简摘要", parsed.releaseNotes)

        val routes = AppUpdateManager.buildDownloadRoutes(parsed)
        assertEquals(3, routes.size)
        assertTrue(routes[0].url.startsWith("https://custom-mirror.example.com/https://github.com/"))
        assertTrue(routes[1].url.startsWith("https://ghfast.top/https://github.com/"))
        assertEquals("GitHub 官方源", routes[2].label)

        val extDiag = AppUpdateManager.buildDiagnosticInfo(
            versionName = "3.3.0",
            versionCode = 330,
            androidRelease = "17",
            sdkInt = 37,
            campusLabel = "湖光主校区",
            densityLabel = "紧凑一屏",
            darkModeLabel = "跟随系统",
            deviceModel = "Xiaomi 15",
            channelLabel = "校外免校园网"
        )
        assertTrue(extDiag.contains("机型：Xiaomi 15"))
        assertTrue(extDiag.contains("通道：校外免校园网"))
    }

    @Test
    fun releasesListParserRespectsDraftsAndBetaChannelOptIn() {
        val releasesJson = """
            [
              {
                "tag_name": "v3.5.0-draft.1",
                "draft": true,
                "prerelease": true,
                "body": "草稿不应展示",
                "assets": []
              },
              {
                "tag_name": "v3.4.0-beta.1",
                "draft": false,
                "prerelease": true,
                "name": "海屿空间 v3.4.0-beta.1",
                "body": "<!-- APP_NOTES_START -->\n- 预览版：测试全新课表导出\n<!-- APP_NOTES_END -->",
                "assets": [
                  {
                    "name": "HaiyuSchedule-GDOU-v3.4.0-beta.1.apk",
                    "size": 2012000,
                    "digest": "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                    "browser_download_url": "https://github.com/rex25415/gdou-haiyu-schedule/releases/download/v3.4.0-beta.1/HaiyuSchedule-GDOU-v3.4.0-beta.1.apk"
                  }
                ]
              },
              {
                "tag_name": "v3.3.0",
                "draft": false,
                "prerelease": false,
                "name": "海屿空间 v3.3.0",
                "body": "- 正式稳定版",
                "assets": []
              }
            ]
        """.trimIndent()

        val betaPick = AppUpdateManager.parseLatestReleaseFromListJson(releasesJson, includePrerelease = true)
        assertNotNull(betaPick)
        assertEquals("3.4.0-beta.1", betaPick!!.versionName)
        assertTrue(betaPick.releaseNotes.contains("预览版"))
        assertTrue(AppUpdateManager.isNewerVersion(betaPick.versionName, "3.3.0"))

        val stablePick = AppUpdateManager.parseLatestReleaseFromListJson(releasesJson, includePrerelease = false)
        assertNotNull(stablePick)
        assertEquals("3.3.0", stablePick!!.versionName)
    }
}
