package io.github.rex25415.haiyu.schedule.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale

@Serializable
data class CampusNoticeItem(
    val title: String,
    val date: String,
    val url: String,
    val categoryLabel: String = "教务通知"
)

@Serializable
data class CampusCalendarEntry(
    val title: String,
    val publishDate: String,
    val articleUrl: String,
    val sheetImageUrl: String = ""
)

@Serializable
data class CampusOfficialFeed(
    val notices: List<CampusNoticeItem> = emptyList(),
    val latestCalendar: CampusCalendarEntry? = null,
    val fetchedAtMillis: Long = 0L,
    val fromCache: Boolean = false
)

data class OfficialPortalLink(
    val id: String,
    val title: String,
    val subtitle: String,
    val url: String
)

/**
 * 广东海洋大学官方公开资讯与门户速达服务（免校园网直连 `jwc.gdou.edu.cn`）。
 * Strictly read-only public HTTPS requests with local TTL + SharedPreferences fallback cache.
 */
object CampusOfficialService {

    const val URL_SCHOOL_HOME = "https://www.gdou.edu.cn/"
    const val URL_JWC_HOME = "https://jwc.gdou.edu.cn/"
    const val URL_JWC_NOTICES = "https://jwc.gdou.edu.cn/jwdt/jwtz.htm"
    const val URL_JWC_CALENDAR = "https://jwc.gdou.edu.cn/jwdt/xl.htm"
    const val URL_ONE_STOP = "https://i.gdou.edu.cn/"
    const val URL_LOGISTICS = "https://zhhq.gdou.edu.cn/"
    const val URL_CHAOXING = "https://gdhydx.fanya.chaoxing.com/portal"
    const val URL_LIBRARY = "https://lib.gdou.edu.cn/"
    const val URL_WEBVPN = "https://webvpn.gdou.edu.cn/"

    val OFFICIAL_PORTALS = listOf(
        OfficialPortalLink(
            id = "calendar",
            title = "官方校历表",
            subtitle = "学年起止/放假周",
            url = URL_JWC_CALENDAR
        ),
        OfficialPortalLink(
            id = "one_stop",
            title = "一网通办",
            subtitle = "校内综合服务门户",
            url = URL_ONE_STOP
        ),
        OfficialPortalLink(
            id = "logistics",
            title = "智慧后勤",
            subtitle = "宿管报修/生活服务",
            url = URL_LOGISTICS
        ),
        OfficialPortalLink(
            id = "chaoxing",
            title = "学习通课程",
            subtitle = "广海在线教学平台",
            url = URL_CHAOXING
        ),
        OfficialPortalLink(
            id = "library",
            title = "馆藏图书",
            subtitle = "书目检索与资源",
            url = URL_LIBRARY
        ),
        OfficialPortalLink(
            id = "webvpn",
            title = "校外 VPN",
            subtitle = "校外访内网学术库",
            url = URL_WEBVPN
        )
    )

    private const val PREF_NAME = "haiyu_campus_official_cache"
    private const val KEY_FEED_JSON = "cached_feed_v2"
    private const val CACHE_TTL_MS = 15 * 60 * 1000L

    private val feedJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Volatile
    private var memoryFeed: CampusOfficialFeed? = null

    private val ANCHOR_NOTICE_RE = Regex(
        """<a\b[^>]*href=["']([^"']+)["'][^>]*>(.*?)</a>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val SPAN_DATE_RE = Regex("""<span[^>]*>\s*(\d{4}[-/]\d{1,2}[-/]\d{1,2})\s*</span>""", RegexOption.IGNORE_CASE)
    private val EM_TITLE_RE = Regex("""<em[^>]*>(.*?)</em>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val DATE_TEXT_RE = Regex("""\b(\d{4}[-/]\d{1,2}[-/]\d{1,2})\b""")
    private val HTML_TAG_RE = Regex("""<[^>]+>""")
    private val WHITESPACE_RE = Regex("""[\s\u200B\uFEFF]+""")
    private val IMG_SRC_RE = Regex("""<img[^>]+src=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)

    fun loadCachedFeed(context: Context): CampusOfficialFeed? {
        memoryFeed?.let { return it }
        return try {
            val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_FEED_JSON, null).orEmpty()
            if (raw.isBlank()) null
            else feedJson.decodeFromString(CampusOfficialFeed.serializer(), raw).copy(fromCache = true).also {
                memoryFeed = it
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun saveCachedFeed(context: Context, feed: CampusOfficialFeed) {
        memoryFeed = feed
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_FEED_JSON, feedJson.encodeToString(feed)).apply()
        } catch (_: Exception) {
        }
    }

    suspend fun fetchOfficialFeed(
        context: Context,
        forceRefresh: Boolean = false
    ): Result<CampusOfficialFeed> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = loadCachedFeed(context)
        if (!forceRefresh && existing != null && existing.notices.isNotEmpty() &&
            now - existing.fetchedAtMillis < CACHE_TTL_MS
        ) {
            return@withContext Result.success(existing)
        }

        try {
            val (notices, calEntry) = coroutineScope {
                val noticesDeferred = async {
                    fetchHtml(URL_JWC_NOTICES)?.let { html ->
                        parseJwcNoticeList(html, URL_JWC_NOTICES)
                    }.orEmpty()
                }
                val calendarDeferred = async {
                    fetchHtml(URL_JWC_CALENDAR)?.let { html ->
                        parseJwcCalendarEntries(html, URL_JWC_CALENDAR).firstOrNull()
                    }
                }
                noticesDeferred.await() to calendarDeferred.await()
            }

            val enrichedCal = if (calEntry != null) {
                val sheetImg = fetchHtml(calEntry.articleUrl)?.let { articleHtml ->
                    parseCalendarSheetImageUrl(articleHtml, calEntry.articleUrl)
                }.orEmpty()
                calEntry.copy(sheetImageUrl = sheetImg)
            } else {
                existing?.latestCalendar
            }

            if (notices.isEmpty() && enrichedCal == null) {
                return@withContext if (existing != null && existing.notices.isNotEmpty()) {
                    Result.success(existing.copy(fromCache = true))
                } else {
                    Result.failure(IllegalStateException("暂未获取到教务部最新公告，请检查网络连接后重试"))
                }
            }

            val freshFeed = CampusOfficialFeed(
                notices = notices.ifEmpty { existing?.notices.orEmpty() },
                latestCalendar = enrichedCal,
                fetchedAtMillis = now,
                fromCache = false
            )
            saveCachedFeed(context, freshFeed)
            Result.success(freshFeed)
        } catch (e: Exception) {
            if (existing != null && existing.notices.isNotEmpty()) {
                Result.success(existing.copy(fromCache = true))
            } else {
                Result.failure(e)
            }
        }
    }

    private fun fetchHtml(urlStr: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = 6000
                readTimeout = 7000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) HaiyuSchedule")
                setRequestProperty("Accept", "text/html,application/xhtml+xml")
            }
            if (conn.responseCode !in 200..299) return null
            val bytes = conn.inputStream.use { it.readBytes() }
            bytes.toString(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    internal fun parseJwcNoticeList(
        html: String,
        baseUrl: String = URL_JWC_NOTICES
    ): List<CampusNoticeItem> {
        if (html.isBlank()) return emptyList()
        val out = ArrayList<CampusNoticeItem>()
        val seenUrls = HashSet<String>()

        for (match in ANCHOR_NOTICE_RE.findAll(html)) {
            val rawHref = match.groupValues[1].trim()
            if (!rawHref.contains("info/") && !rawHref.endsWith(".htm", ignoreCase = true)) continue
            val resolvedUrl = resolveTrustedGdouHttpsUrl(baseUrl, rawHref) ?: continue
            if (!resolvedUrl.contains("/info/")) continue

            val innerHtml = match.groupValues[2]
            val contextStart = (match.range.first - 180).coerceAtLeast(0)
            val preSnippet = html.substring(contextStart, match.range.first).substringAfterLast("<li")
            val date = SPAN_DATE_RE.find(innerHtml)?.groupValues?.getOrNull(1)?.replace('/', '-')
                ?: DATE_TEXT_RE.find(innerHtml)?.groupValues?.getOrNull(1)?.replace('/', '-')
                ?: SPAN_DATE_RE.find(preSnippet)?.groupValues?.getOrNull(1)?.replace('/', '-')
                ?: DATE_TEXT_RE.find(preSnippet)?.groupValues?.getOrNull(1)?.replace('/', '-')
                ?: ""

            val rawTitleHtml = EM_TITLE_RE.find(innerHtml)?.groupValues?.getOrNull(1)
                ?: innerHtml.replace(SPAN_DATE_RE, "")
            val cleanTitle = cleanHtmlText(rawTitleHtml)
            if (cleanTitle.length < 4 || !seenUrls.add(resolvedUrl)) continue

            out.add(
                CampusNoticeItem(
                    title = cleanTitle,
                    date = normalizeDateString(date),
                    url = resolvedUrl,
                    categoryLabel = classifyNoticeCategory(cleanTitle)
                )
            )
        }

        return out
            .withIndex()
            .sortedWith(compareByDescending<IndexedValue<CampusNoticeItem>> { it.value.date }.thenBy { it.index })
            .map { it.value }
            .take(24)
    }

    internal fun parseJwcCalendarEntries(
        html: String,
        baseUrl: String = URL_JWC_CALENDAR
    ): List<CampusCalendarEntry> {
        if (html.isBlank()) return emptyList()
        val entries = ArrayList<CampusCalendarEntry>()
        for (item in parseJwcNoticeList(html, baseUrl)) {
            if (item.title.contains("校历")) {
                entries.add(
                    CampusCalendarEntry(
                        title = item.title,
                        publishDate = item.date,
                        articleUrl = item.url
                    )
                )
            }
        }
        return entries.sortedByDescending { it.publishDate }
    }

    internal fun parseCalendarSheetImageUrl(
        articleHtml: String,
        articleUrl: String = URL_JWC_CALENDAR
    ): String? {
        if (articleHtml.isBlank()) return null
        val candidates = IMG_SRC_RE.findAll(articleHtml)
            .mapNotNull { it.groupValues.getOrNull(1)?.trim() }
            .filter { src ->
                src.contains("virtual_attach_file", ignoreCase = true) ||
                    src.contains("_mediafile", ignoreCase = true) ||
                    src.contains("upload", ignoreCase = true)
            }
            .toList()
        val chosen = candidates.firstOrNull { !it.endsWith("/3.jpg") && !it.endsWith("jwc.png") } ?: return null
        return resolveTrustedGdouHttpsUrl(articleUrl, chosen)
    }

    internal fun classifyNoticeCategory(title: String): String {
        val t = title.trim()
        return when {
            Regex("考核|考试|考场|巡查|重考|补考|四、?六级|等级考|监考").containsMatchIn(t) -> "考试/考核"
            Regex("选课|预选|任选|公选|辅修|重修|退选|改选").containsMatchIn(t) -> "选课/重修"
            Regex("成绩|绩点|学籍|转专业|毕业|学位|核对").containsMatchIn(t) -> "成绩/学籍"
            Regex("校历|放假|调休|停课").containsMatchIn(t) -> "校历/调休"
            else -> "教学通知"
        }
    }

    fun isAllowedExternalPortalUrl(url: String?): Boolean {
        val raw = url?.trim().orEmpty()
        if (raw.isEmpty()) return false
        return try {
            val uri = URI(raw)
            val scheme = uri.scheme?.lowercase(Locale.US) ?: return false
            val host = uri.host?.lowercase(Locale.US)?.trimEnd('.') ?: return false
            scheme == "https" && (
                host == "gdou.edu.cn" ||
                    host.endsWith(".gdou.edu.cn") ||
                    host == "gdhydx.fanya.chaoxing.com"
                )
        } catch (_: Exception) {
            false
        }
    }

    internal fun resolveTrustedGdouHttpsUrl(baseUrl: String, href: String): String? {
        val raw = href.trim()
        if (raw.isEmpty() || raw.startsWith("javascript:", ignoreCase = true) || raw.startsWith("#")) {
            return null
        }
        return try {
            val resolved = URI(baseUrl).resolve(raw)
            val host = resolved.host?.lowercase(Locale.US)?.trimEnd('.') ?: return null
            if (host != "gdou.edu.cn" && !host.endsWith(".gdou.edu.cn")) return null
            val scheme = when (resolved.scheme?.lowercase(Locale.US)) {
                "https", "http" -> "https"
                else -> return null
            }
            URI(
                scheme,
                resolved.userInfo,
                host,
                resolved.port,
                resolved.rawPath,
                resolved.rawQuery,
                null
            ).toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun cleanHtmlText(raw: String): String {
        val noTags = HTML_TAG_RE.replace(raw, "")
        val decoded = noTags
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
        return WHITESPACE_RE.replace(decoded, " ").trim()
    }

    private fun normalizeDateString(raw: String): String {
        val parts = raw.trim().split('-', '/')
        if (parts.size != 3) return raw.trim()
        val y = parts[0].padStart(4, '0')
        val m = parts[1].padStart(2, '0')
        val d = parts[2].padStart(2, '0')
        return "$y-$m-$d"
    }
}
