package io.github.rex25415.haiyu.schedule.sync

import android.webkit.CookieManager
import java.net.URI
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 抽象 Cookie 存储接口：
 * - 生产环境通过 [AndroidWebViewCookieStore] 直接与 Android WebView [CookieManager] 共享全部会话（含 HttpOnly 票据），
 *   并且仅在后台 IO 线程触发 CookieManager 懒加载，彻底避免阻塞 UI 主线程。
 * - 单元测试环境使用 [InMemoryGdouCookieStore]，支持纯 JVM 端到端 HTTP 协议与重定向测试。
 */
interface GdouCookieStore {
    fun getCookieHeader(url: String): String?
    fun saveResponseCookies(url: String, setCookieHeaders: List<String>)
    fun clearCookieByPrefix(url: String, namePrefix: String)
    fun clearAll()
    fun flushAsync(minIntervalMs: Long = 5_000L)
}

class AndroidWebViewCookieStore : GdouCookieStore {

    private val lastFlushMs = AtomicLong(0L)

    private fun managerOrNull(): CookieManager? = try {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
        }
    } catch (_: Throwable) {
        null
    }

    override fun getCookieHeader(url: String): String? {
        return try {
            managerOrNull()?.getCookie(url)?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }

    override fun saveResponseCookies(url: String, setCookieHeaders: List<String>) {
        if (setCookieHeaders.isEmpty()) return
        val cm = managerOrNull() ?: return
        for (raw in setCookieHeaders) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) continue
            try {
                cm.setCookie(url, trimmed)
            } catch (_: Throwable) {
            }
        }
    }

    override fun clearCookieByPrefix(url: String, namePrefix: String) {
        val cm = managerOrNull() ?: return
        try {
            val existing = cm.getCookie(url).orEmpty()
            if (existing.isBlank()) return
            existing.split(";").forEach { part ->
                val cname = part.trim().substringBefore("=").trim()
                if (cname.startsWith(namePrefix, ignoreCase = true)) {
                    for (p in listOf("/", "/sso", "/sso/")) {
                        cm.setCookie(url, "$cname=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=$p")
                    }
                }
            }
        } catch (_: Throwable) {
        }
    }

    override fun clearAll() {
        val cm = managerOrNull() ?: return
        try {
            cm.removeAllCookies {
                flushAsync(0L)
            }
        } catch (_: Throwable) {
        }
    }

    override fun flushAsync(minIntervalMs: Long) {
        val now = System.currentTimeMillis()
        val prev = lastFlushMs.get()
        if (minIntervalMs > 0L && now - prev < minIntervalMs) return
        if (!lastFlushMs.compareAndSet(prev, now)) return
        Thread {
            try {
                managerOrNull()?.flush()
            } catch (_: Throwable) {
            }
        }.start()
    }
}

/**
 * 纯 JVM 内存 Cookie 桶（用于 JUnit 端到端协议测试及无 WebView 降级场景），支持 Domain/Path/Max-Age 匹配。
 */
class InMemoryGdouCookieStore : GdouCookieStore {

    private data class StoredCookie(
        val name: String,
        val value: String,
        val domain: String,
        val path: String,
        val hostOnly: Boolean
    )

    // key = "$domain|$path|$name"
    private val store = ConcurrentHashMap<String, StoredCookie>()

    override fun getCookieHeader(url: String): String? {
        val uri = try { URI(url) } catch (_: Exception) { return null }
        val reqHost = uri.host?.lowercase(Locale.US)?.trimEnd('.') ?: return null
        val reqPath = uri.path?.takeIf { it.isNotBlank() } ?: "/"
        val matched = store.values
            .filter { c ->
                val domainOk = if (c.hostOnly) {
                    reqHost == c.domain
                } else {
                    reqHost == c.domain || reqHost.endsWith(".${c.domain}")
                }
                val pathOk = reqPath == c.path ||
                    (c.path == "/") ||
                    (reqPath.startsWith(c.path) && (c.path.endsWith("/") || reqPath.getOrNull(c.path.length) == '/'))
                domainOk && pathOk
            }
            .sortedByDescending { it.path.length }
        if (matched.isEmpty()) return null
        val dedup = LinkedHashMap<String, String>()
        for (c in matched) {
            dedup.putIfAbsent(c.name, c.value)
        }
        return dedup.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    override fun saveResponseCookies(url: String, setCookieHeaders: List<String>) {
        val uri = try { URI(url) } catch (_: Exception) { return }
        val reqHost = uri.host?.lowercase(Locale.US)?.trimEnd('.') ?: return
        val defaultPath = uri.path?.let { p ->
            val idx = p.lastIndexOf('/')
            if (idx > 0) p.substring(0, idx) else "/"
        } ?: "/"

        for (raw in setCookieHeaders) {
            val parts = raw.split(";").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.isEmpty()) continue
            val nv = parts[0]
            val eqIdx = nv.indexOf('=')
            if (eqIdx <= 0) continue
            val name = nv.substring(0, eqIdx).trim()
            val value = nv.substring(eqIdx + 1).trim()
            var domain = reqHost
            var hostOnly = true
            var path = defaultPath
            var expired = false
            for (i in 1 until parts.size) {
                val attr = parts[i]
                val aEq = attr.indexOf('=')
                val k = (if (aEq >= 0) attr.substring(0, aEq) else attr).trim().lowercase(Locale.US)
                val v = if (aEq >= 0) attr.substring(aEq + 1).trim() else ""
                when (k) {
                    "domain" -> {
                        val cleaned = v.trimStart('.').lowercase(Locale.US)
                        if (cleaned.isNotEmpty() && (reqHost == cleaned || reqHost.endsWith(".$cleaned"))) {
                            domain = cleaned
                            hostOnly = false
                        }
                    }
                    "path" -> if (v.startsWith("/")) path = v
                    "max-age" -> {
                        val secs = v.toLongOrNull()
                        if (secs != null && secs <= 0L) expired = true
                    }
                }
            }
            val key = "$domain|$path|$name"
            if (expired) {
                store.remove(key)
            } else {
                store[key] = StoredCookie(name, value, domain, path, hostOnly)
            }
        }
    }

    override fun clearCookieByPrefix(url: String, namePrefix: String) {
        val uri = try { URI(url) } catch (_: Exception) { return }
        val reqHost = uri.host?.lowercase(Locale.US)?.trimEnd('.') ?: return
        val keys = store.entries
            .filter { (_, c) ->
                (c.domain == reqHost || reqHost.endsWith(".${c.domain}")) &&
                    c.name.startsWith(namePrefix, ignoreCase = true)
            }
            .map { it.key }
        keys.forEach { store.remove(it) }
    }

    override fun clearAll() {
        store.clear()
    }

    override fun flushAsync(minIntervalMs: Long) {
        // In-memory store needs no disk flush
    }
}
