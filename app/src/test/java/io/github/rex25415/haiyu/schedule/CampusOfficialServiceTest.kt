package io.github.rex25415.haiyu.schedule

import io.github.rex25415.haiyu.schedule.data.CampusOfficialService
import io.github.rex25415.haiyu.schedule.sync.GdouSyncScript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusOfficialServiceTest {

    @Test
    fun parseJwcNoticeList_extractsAndClassifiesOfficialItemsSafely() {
        val sampleHtml = """
            <ul>
              <li class="mainmenu"><a href="../jshj.htm">教师获奖</a><dl><dd><a href="../jshj/jxms.htm">教学名师</a></dd></dl>
            </ul>
            <div class="main_conRCb">
              <ul>
                <li id="line_u8_0"><a href="../info/1166/13334.htm"><span>2026-11-20</span><em>关于公布2026-2027学年第一学期第13周课程考核和巡查安排的通知</em></a></li>
                <li id="line_u8_0b"><a href="../info/1166/13304.htm"><span>2026-11-20</span><em>关于做好广东海洋大学2026年教师授课观摩竞赛决赛工作的通知</em></a></li>
                <li id="line_u8_1"><a href="../info/1166/13274.htm"><span>2026-11-12</span><em>关于2026-2027-1学期课程成绩录入的通知</em></a></li>
                <li id="line_u8_2"><span>2026-11-05</span><a href="../info/1166/13254.htm">关于2026-2027学年第二学期专业任选课的预选通知</a></li>
                <li id="line_u8_bad1"><a href="javascript:void(0)"><span>2026-11-01</span><em>无效脚本链接</em></a></li>
                <li id="line_u8_bad2"><a href="https://evil.gdou.edu.cn.attacker.com/info/1/2.htm"><span>2026-11-01</span><em>伪造子域名</em></a></li>
              </ul>
            </div>
        """.trimIndent()

        val parsed = CampusOfficialService.parseJwcNoticeList(sampleHtml)
        assertEquals(4, parsed.size)

        // Same-date notices keep official JWC page order rather than alphabetical shuffle
        assertEquals("2026-11-20", parsed[0].date)
        assertEquals("https://jwc.gdou.edu.cn/info/1166/13334.htm", parsed[0].url)
        assertEquals("考试/考核", parsed[0].categoryLabel)
        assertEquals("https://jwc.gdou.edu.cn/info/1166/13304.htm", parsed[1].url)

        assertEquals("成绩/学籍", parsed[2].categoryLabel)
        assertEquals("选课/重修", parsed[3].categoryLabel)
    }

    @Test
    fun parseJwcCalendarEntriesAndSheetImage_extractsOfficialCalendarAndAttachment() {
        val calendarListHtml = """
            <ul>
              <li><a href="../info/1167/11883.htm"><span>2026-07-13</span><em>2026-2027学年第一学期第一轮选课名单公布</em></a></li>
              <li><a href="../info/1167/11863.htm"><span>2026-07-09</span><em>广东海洋大学2026-2027学年校历表</em></a></li>
              <li><a href="../info/1167/10533.htm"><span>2025-04-23</span><em>广东海洋大学2025-2026学年校历表</em></a></li>
            </ul>
        """.trimIndent()

        val entries = CampusOfficialService.parseJwcCalendarEntries(calendarListHtml)
        assertEquals(2, entries.size)
        assertEquals("广东海洋大学2026-2027学年校历表", entries[0].title)
        assertEquals("https://jwc.gdou.edu.cn/info/1167/11863.htm", entries[0].articleUrl)

        val articleHtml = """
            <img src="../../images/jwc.png" />
            <div class="v_news_content">
              <img src="/virtual_attach_file.vsb?afc=test123&oid=1624059868&e=.png" />
            </div>
        """.trimIndent()

        val imgUrl = CampusOfficialService.parseCalendarSheetImageUrl(articleHtml, entries[0].articleUrl)
        assertNotNull(imgUrl)
        assertEquals(
            "https://jwc.gdou.edu.cn/virtual_attach_file.vsb?afc=test123&oid=1624059868&e=.png",
            imgUrl
        )

        assertNull(CampusOfficialService.parseCalendarSheetImageUrl("<img src='../../images/jwc.png'/>", entries[0].articleUrl))
    }

    @Test
    fun externalPortalAllowlist_rejectsUntrustedOrCleartextUrls() {
        assertTrue(CampusOfficialService.isAllowedExternalPortalUrl("https://jwc.gdou.edu.cn/jwdt/jwtz.htm"))
        assertTrue(CampusOfficialService.isAllowedExternalPortalUrl("https://i.gdou.edu.cn/"))
        assertTrue(CampusOfficialService.isAllowedExternalPortalUrl("https://zhhq.gdou.edu.cn/"))
        assertTrue(CampusOfficialService.isAllowedExternalPortalUrl("https://lib.gdou.edu.cn/"))
        assertTrue(CampusOfficialService.isAllowedExternalPortalUrl("https://webvpn.gdou.edu.cn/"))
        assertTrue(CampusOfficialService.isAllowedExternalPortalUrl("https://gdhydx.fanya.chaoxing.com/portal"))

        assertFalse(CampusOfficialService.isAllowedExternalPortalUrl("http://jwc.gdou.edu.cn/"))
        assertFalse(CampusOfficialService.isAllowedExternalPortalUrl("https://gdou.edu.cn.phish.com/"))
        assertFalse(CampusOfficialService.isAllowedExternalPortalUrl("javascript:alert(1)"))
        assertFalse(CampusOfficialService.isAllowedExternalPortalUrl(""))
    }

    @Test
    fun dynamicMenuNavigateScript_containsKeywordsAndMenuDiscoveryLogic() {
        val script = GdouSyncScript.buildDynamicMenuNavigateScript(
            keywords = listOf("评价", "评教"),
            targetLabel = "教学评价"
        )
        assertTrue(script.contains("\"评价\",\"评教\""))
        assertTrue(script.contains("index_initMenu.html?jsdm=xs"))
        assertTrue(script.contains("clickMenu\\("))
        assertTrue(script.contains("教学评价"))
        assertFalse(script.contains("\$COMMON"))
    }
}
