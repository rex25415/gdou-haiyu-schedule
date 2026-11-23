package io.github.rex25415.haiyu.schedule.sync

/**
 * 广东海洋大学新版正方教务系统 (v9) 全能同步脚本生成器
 * 适配：
 *  1. 校外通用通道（免校园网 / 免 VPN）: https://wfw.gdou.edu.cn/ -> https://wfw.gdou.edu.cn/https://jw.gdou.edu.cn/
 *  2. 校园网直连通道: https://jw.gdou.edu.cn/
 * 支持同步：个人课表 + 校历开学日期/总周数 + 历史成绩/绩点 + 考试时间与考场安排
 */
object GdouSyncScript {

    const val URL_OFF_CAMPUS = "https://wfw.gdou.edu.cn/"
    const val URL_WFW_JW_ENTRY = "https://wfw.gdou.edu.cn/https://jw.gdou.edu.cn/"
    const val URL_ON_CAMPUS = "https://jw.gdou.edu.cn/"

    const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    /**
     * 拉取教务系统中的可选学年与学期列表（供用户在原生弹窗中确认或直接一键同步）
     */
    fun buildFetchTermOptionsScript(): String = """
        (async function() {
            $COMMON_HELPERS
            try {
                if (!checkOnJwDomain()) {
                    HaiyuBridge.onSyncError("请先完成广东海洋大学教务系统登录（进入教务首页或课表页后再点击同步）");
                    return;
                }
                HaiyuBridge.onSyncProgress("正在读取教务学年学期选项...");
                const opts = await fetchAcademicOptions();
                if (opts) {
                    HaiyuBridge.onTermOptionsLoaded(JSON.stringify(opts));
                } else {
                    const y = getDefaultAcademicYear();
                    const isFirst = new Date().getMonth() >= 7 || new Date().getMonth() === 0;
                    const fallback = {
                        yearOptions: [
                            { value: String(y), text: y + "-" + (Number(y) + 1) },
                            { value: String(Number(y) - 1), text: (Number(y) - 1) + "-" + y },
                            { value: String(Number(y) - 2), text: (Number(y) - 2) + "-" + (Number(y) - 1) }
                        ],
                        semesterOptions: [
                            { value: "3", text: "第一学期" },
                            { value: "12", text: "第二学期" }
                        ],
                        defaultYearIndex: 0,
                        defaultSemesterIndex: isFirst ? 0 : 1,
                        isFallback: true
                    };
                    HaiyuBridge.onTermOptionsLoaded(JSON.stringify(fallback));
                }
            } catch (err) {
                HaiyuBridge.onSyncError("读取学年选项失败：" + (err && err.message ? err.message : String(err)));
            }
        })();
    """.trimIndent()

    /**
     * 执行同步：mode 可为 "courses" | "grades" | "exams" | "all"
     */
    fun buildSyncScript(
        mode: String,
        academicYear: String,
        semesterCode: String,
        termLabel: String
    ): String {
        val safeYear = academicYear.replace("\"", "")
        val safeSem = semesterCode.replace("\"", "")
        val safeLabel = termLabel.replace("\"", "")
        return """
        (async function() {
            $COMMON_HELPERS
            const MODE = "$mode";
            const XNM = "$safeYear";
            const XQM = "$safeSem";
            const TERM_LABEL = "$safeLabel";

            try {
                if (!checkOnJwDomain()) {
                    HaiyuBridge.onSyncError("当前页面还不是广海教务系统，请先完成登录后再点击同步。");
                    return;
                }

                const summary = [];

                if (MODE === "courses" || MODE === "all") {
                    HaiyuBridge.onSyncProgress("正在拉取广海课表与校历数据...");
                    const courseResult = await syncCoursesAndCalendar(XNM, XQM);
                    if (courseResult.ok) {
                        HaiyuBridge.onCoursesSynced(JSON.stringify({
                            courses: courseResult.courses,
                            semesterStartDate: courseResult.startDate || "",
                            totalWeeks: courseResult.totalWeeks || 20,
                            termLabel: TERM_LABEL,
                            academicYear: XNM,
                            semesterCode: XQM,
                            detectedCampus: courseResult.detectedCampus || ""
                        }));
                        summary.push("课表 " + courseResult.courses.length + " 条");
                    } else if (MODE === "courses") {
                        HaiyuBridge.onSyncError(courseResult.error);
                        return;
                    } else {
                        summary.push("课表未获取到(" + courseResult.error + ")");
                    }
                }

                if (MODE === "grades" || MODE === "all") {
                    HaiyuBridge.onSyncProgress("正在拉取全部历史成绩与绩点...");
                    const gradeResult = await syncGrades();
                    if (gradeResult.ok) {
                        HaiyuBridge.onGradesSynced(JSON.stringify({ grades: gradeResult.grades }));
                        summary.push("成绩 " + gradeResult.grades.length + " 门");
                    } else if (MODE === "grades") {
                        HaiyuBridge.onSyncError(gradeResult.error);
                        return;
                    } else {
                        summary.push("成绩跳过(" + gradeResult.error + ")");
                    }
                }

                if (MODE === "exams" || MODE === "all") {
                    HaiyuBridge.onSyncProgress("正在拉取考试时间与考场座位...");
                    const examResult = await syncExams(XNM, XQM);
                    if (examResult.ok) {
                        HaiyuBridge.onExamsSynced(JSON.stringify({ exams: examResult.exams }));
                        summary.push("考试 " + examResult.exams.length + " 场");
                    } else if (MODE === "exams") {
                        HaiyuBridge.onSyncError(examResult.error);
                        return;
                    } else {
                        summary.push("考试跳过(" + examResult.error + ")");
                    }
                }

                HaiyuBridge.onSyncComplete("同步完成：" + summary.join(" · "));
            } catch (err) {
                HaiyuBridge.onSyncError("同步异常：" + (err && err.message ? err.message : String(err)));
            }
        })();
        """.trimIndent()
    }

    /**
     * 顺手一键刷新课表（已记住上次同步的学年/学期时直接后台拉取最新课表与调课变化，无需重复选学期）
     */
    fun buildQuickRefreshCoursesScript(
        academicYear: String,
        semesterCode: String,
        termLabel: String
    ): String {
        val safeYear = academicYear.replace("\"", "")
        val safeSem = semesterCode.replace("\"", "")
        val safeLabel = termLabel.replace("\"", "")
        return """
        (async function() {
            $COMMON_HELPERS
            const XNM = "$safeYear";
            const XQM = "$safeSem";
            const TERM_LABEL = "$safeLabel";
            try {
                if (!checkOnJwDomain()) {
                    HaiyuBridge.onQuickRefreshNeedsLogin("教务尚未登录或登录态已过期，请完成登录后刷新课表");
                    return;
                }
                HaiyuBridge.onSyncProgress("正在刷新最新课表与校历...");
                let targetYear = XNM;
                let targetSem = XQM;
                let targetLabel = TERM_LABEL;
                if (!targetYear || !targetSem) {
                    const opts = await fetchAcademicOptions();
                    if (opts && opts.yearOptions && opts.yearOptions.length > 0 && opts.semesterOptions && opts.semesterOptions.length > 0) {
                        const yOpt = opts.yearOptions[opts.defaultYearIndex || 0] || opts.yearOptions[0];
                        const sOpt = opts.semesterOptions[opts.defaultSemesterIndex || 0] || opts.semesterOptions[0];
                        targetYear = yOpt.value;
                        targetSem = sOpt.value;
                        targetLabel = yOpt.text + " " + sOpt.text;
                    } else {
                        targetYear = getDefaultAcademicYear();
                        const isFirst = new Date().getMonth() >= 7 || new Date().getMonth() === 0;
                        targetSem = isFirst ? "3" : "12";
                        targetLabel = targetYear + "-" + (Number(targetYear) + 1) + (isFirst ? " 第一学期" : " 第二学期");
                    }
                }
                const courseResult = await syncCoursesAndCalendar(targetYear, targetSem);
                if (courseResult.ok) {
                    HaiyuBridge.onCoursesSynced(JSON.stringify({
                        courses: courseResult.courses,
                        semesterStartDate: courseResult.startDate || "",
                        totalWeeks: courseResult.totalWeeks || 20,
                        termLabel: targetLabel,
                        academicYear: targetYear,
                        semesterCode: targetSem,
                        detectedCampus: courseResult.detectedCampus || ""
                    }));
                    HaiyuBridge.onSyncComplete("课表已刷新：最新 " + courseResult.courses.length + " 条课程（已自动保留手动调课）");
                } else {
                    const errText = String(courseResult.error || "");
                    if (errText.includes("过期") || errText.includes("登录") || errText.includes("HTTP 401") || errText.includes("HTTP 403") || errText.includes("HTTP 500")) {
                        HaiyuBridge.onQuickRefreshNeedsLogin("教务登录态可能已过期，请在教务页重新登录后刷新");
                    } else {
                        HaiyuBridge.onSyncError(errText || "刷新课表失败");
                    }
                }
            } catch (err) {
                HaiyuBridge.onSyncError("刷新课表异常：" + (err && err.message ? err.message : String(err)));
            }
        })();
        """.trimIndent()
    }

    /**
     * 在当前登录态下快捷跳转到教务系统指定模块页面
     */
    fun buildQuickNavigateScript(modulePath: String): String {
        val safePath = modulePath.trimStart('/').replace("\"", "")
        return """
        (function() {
            $BASE_PREFIX_HELPER
            const target = window.location.origin + getJwBasePrefix() + "/$safePath";
            window.location.href = target;
        })();
        """.trimIndent()
    }

    private const val BASE_PREFIX_HELPER = """
        function getJwBasePrefix() {
            const host = (window.location && window.location.hostname || "").toLowerCase();
            const path = (window.location && window.location.pathname) || "";
            const proxyPrefix = path.match(/^\/(?:https?:\/\/[^/]+|(?:http|https|https-443)\/[^/]+)/i);
            if (proxyPrefix) return proxyPrefix[0];
            // On wfw.gdou.edu.cn, NEVER fall back to naked "" even when redirected to /xtgl/dl_loginForward.html!
            if (host.includes("wfw.gdou.edu.cn")) return "/https://jw.gdou.edu.cn";
            const anchors = ["/kbcx/", "/cjcx/", "/kwgl/", "/xtgl/", "/xsxk/", "/xspjgl/", "/cdjy/"];
            for (const anchor of anchors) {
                const idx = path.indexOf(anchor);
                if (idx >= 0) return path.slice(0, idx);
            }
            return "";
        }
        function buildJwUrl(relativePath) {
            const clean = relativePath.startsWith("/") ? relativePath : ("/" + relativePath);
            return window.location.origin + getJwBasePrefix() + clean;
        }
        async function fetchWithRetry(url, init) {
            let lastResp = null;
            for (let attempt = 0; attempt < 2; attempt++) {
                const resp = await fetch(url, init);
                lastResp = resp;
                if (resp.status !== 503) return resp;
                await new Promise(r => setTimeout(r, 400));
            }
            return lastResp;
        }
        let _wfwSessionWarmed = false;
        async function ensureWfwJwSessionIfNeeded() {
            if (_wfwSessionWarmed) return;
            const host = (window.location && window.location.hostname || "").toLowerCase();
            if (!host.includes("wfw.gdou.edu.cn")) return;
            _wfwSessionWarmed = true;
            try {
                await fetchWithRetry(window.location.origin + "/https://jw.gdou.edu.cn/", {
                    method: "GET",
                    credentials: "include"
                });
            } catch (_) {}
        }
        async function fetchJwTextWithFallback(relativePath, init, expectJson) {
            const clean = relativePath.startsWith("/") ? relativePath : ("/" + relativePath);
            const host = (window.location && window.location.hostname || "").toLowerCase();
            const candidates = [];
            if (host.includes("wfw.gdou.edu.cn")) {
                candidates.push(window.location.origin + "/https://jw.gdou.edu.cn" + clean);
                const p = buildJwUrl(clean);
                if (!candidates.includes(p)) candidates.push(p);
                const naked = window.location.origin + clean;
                if (!candidates.includes(naked)) candidates.push(naked);
            } else {
                candidates.push(buildJwUrl(clean));
            }
            let lastStatus = 0;
            let lastText = "";
            for (const url of candidates) {
                try {
                    const resp = await fetchWithRetry(url, init);
                    lastStatus = resp.status;
                    if (!resp.ok) continue;
                    const txt = await resp.text();
                    lastText = txt;
                    const trimmed = txt.trim();
                    if (expectJson) {
                        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                            return { ok: true, status: resp.status, text: txt };
                        }
                    } else {
                        if (trimmed.length > 0 && !trimmed.includes("统一身份认证") && !trimmed.includes("loginFromId")) {
                            return { ok: true, status: resp.status, text: txt };
                        }
                    }
                } catch (_) {}
            }
            return { ok: false, status: lastStatus, text: lastText };
        }
        async function fetchJwWithFallback(relativePath, init) {
            const res = await fetchJwTextWithFallback(relativePath, init, false);
            return {
                ok: res.ok,
                status: res.status || 500,
                text: async () => res.text,
                json: async () => JSON.parse(res.text)
            };
        }
        async function fetchJwJsonWithFallback(relativePath, init) {
            const res = await fetchJwTextWithFallback(relativePath, init, true);
            return {
                ok: res.ok,
                status: res.status || 500,
                text: async () => res.text,
                json: async () => JSON.parse(res.text)
            };
        }
    """

    private const val COMMON_HELPERS = """
        $BASE_PREFIX_HELPER

        function checkOnJwDomain() {
            const host = (window.location && window.location.hostname || "").toLowerCase();
            const href = (window.location && window.location.href || "").toLowerCase();
            const title = (document && document.title || "").toLowerCase();
            if (!host.includes("gdou.edu.cn")) return false;
            if (
                host.startsWith("sso.") ||
                host.startsWith("authserver.") ||
                href.includes("/authserver/") ||
                href.includes("/cas/login") ||
                href.includes("login_slogin.html") ||
                href.includes("/login") ||
                title.includes("统一身份认证") ||
                title.includes("登录")
            ) {
                return false;
            }
            return true;
        }

        function getDefaultAcademicYear() {
            const now = new Date();
            const y = now.getFullYear();
            return (now.getMonth() >= 7 ? y : y - 1).toString();
        }

        function parseSelectOptions(selectEl) {
            if (!selectEl) return { options: [], defaultIndex: 0 };
            const options = [];
            let defaultIndex = 0;
            Array.from(selectEl.querySelectorAll("option")).forEach(opt => {
                const value = String(opt.value || "").trim();
                if (!value) return;
                const text = String(opt.textContent || "").trim() || value;
                if (opt.selected) defaultIndex = options.length;
                options.push({ value, text });
            });
            const curVal = String(selectEl.value || "").trim();
            const curIdx = options.findIndex(o => o.value === curVal);
            if (curIdx !== -1) defaultIndex = curIdx;
            return { options, defaultIndex };
        }

        function formatSemesterText(rawVal, rawText) {
            const v = String(rawVal || "").trim();
            const t = String(rawText || "").trim();
            if (v === "3" || t === "1") return "第一学期";
            if (v === "12" || t === "2") return "第二学期";
            if (v === "16" || t === "3") return "第三学期";
            return t || v;
        }

        async function fetchAcademicOptions() {
            try {
                await ensureWfwJwSessionIfNeeded();
                const resp = await fetchJwWithFallback("/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default", {
                    method: "GET",
                    credentials: "include"
                });
                if (!resp.ok) return null;
                const html = await resp.text();
                const doc = new DOMParser().parseFromString(html, "text/html");
                const yearData = parseSelectOptions(doc.querySelector("#xnm"));
                const semData = parseSelectOptions(doc.querySelector("#xqm"));
                if (yearData.options.length === 0 || semData.options.length === 0) return null;
                const idx = yearData.defaultIndex;
                const start = Math.max(0, idx - 3);
                const end = Math.min(yearData.options.length, idx + 3);
                const prettySemOptions = semData.options.map(o => ({
                    value: o.value,
                    text: formatSemesterText(o.value, o.text)
                }));
                return {
                    yearOptions: yearData.options.slice(start, end),
                    semesterOptions: prettySemOptions,
                    defaultYearIndex: idx - start,
                    defaultSemesterIndex: semData.defaultIndex,
                    isFallback: false
                };
            } catch (e) {
                return null;
            }
        }

        let _cachedMenuEndpoints = null;
        async function discoverMenuEndpoints() {
            if (_cachedMenuEndpoints) return _cachedMenuEndpoints;
            const found = { gradeEps: [], examEps: [] };
            try {
                const sources = [document.documentElement ? document.documentElement.innerHTML : ""];
                try {
                    const r = await fetchJwWithFallback("/xtgl/index_initMenu.html?jsdm=xs", { method: "GET", credentials: "include" });
                    if (r.ok) sources.push(await r.text());
                } catch (_) {}
                const re = /clickMenu\(\s*['"]([A-Za-z0-9_]+)['"]\s*,\s*['"]([^'"]+)['"]/g;
                for (const src of sources) {
                    let m;
                    while ((m = re.exec(src)) !== null) {
                        const code = m[1];
                        const path = m[2];
                        if (path.includes("/cjcx/")) {
                            const sep = path.includes("?") ? "&" : "?";
                            found.gradeEps.push(path + sep + "doType=query&gnmkdm=" + code);
                        }
                        if (path.includes("/kwgl/") && path.includes("kscx")) {
                            const sep = path.includes("?") ? "&" : "?";
                            found.examEps.push(path + sep + "doType=query&gnmkdm=" + code);
                        }
                    }
                }
            } catch (_) {}
            _cachedMenuEndpoints = found;
            return found;
        }

        function parseWeeks(weekStr) {
            if (!weekStr) return [];
            const groups = String(weekStr).replace(/，/g, ",").split(",");
            const weeks = [];
            for (const g of groups) {
                const item = g.trim();
                const rangeMatch = item.match(/第?(\d+)\s*[-~～]\s*(\d+)\s*周?/);
                const singleMatch = item.match(/第?(\d+)\s*周?/);
                let s = 0, e = 0, matched = false;
                if (rangeMatch) {
                    s = Number(rangeMatch[1]);
                    e = Number(rangeMatch[2]);
                    matched = true;
                } else if (singleMatch) {
                    s = e = Number(singleMatch[1]);
                    matched = true;
                }
                if (matched && s >= 1 && e >= s) {
                    const oddOnly = item.includes("(单)") || item.includes("（单）") || item.includes("单周");
                    const evenOnly = item.includes("(双)") || item.includes("（双）") || item.includes("双周");
                    for (let w = s; w <= e; w++) {
                        if (oddOnly && w % 2 === 0) continue;
                        if (evenOnly && w % 2 !== 0) continue;
                        weeks.push(w);
                    }
                }
            }
            return Array.from(new Set(weeks)).sort((a, b) => a - b);
        }

        function parseSectionRange(sectionStr) {
            const text = sectionStr == null ? "" : String(sectionStr);
            const m = text.match(/(\d+)\s*(?:[-~～]\s*(\d+))?/);
            if (!m) return null;
            const s = Number(m[1]);
            const e = Number(m[2] || m[1]);
            if (!Number.isInteger(s) || !Number.isInteger(e) || s < 1 || e < s) return null;
            return { startSection: s, endSection: e };
        }

        function normalizeCourseName(rawName) {
            const name = String(rawName || "").trim();
            const m = name.match(/^[（(]([^（）()]+)[）)]\s*/);
            if (!m) return name;
            const rest = name.slice(m[0].length);
            return rest ? (rest + "(" + m[1] + ")") : name;
        }

        function mergeAndDistinctCourses(courses) {
            if (!Array.isArray(courses) || courses.length <= 1) return courses;
            const list = courses.map(c => ({
                ...c,
                weeks: Array.from(new Set(c.weeks || [])).sort((a, b) => a - b)
            }));
            list.sort((a, b) =>
                a.name.localeCompare(b.name) ||
                a.teacher.localeCompare(b.teacher) ||
                a.location.localeCompare(b.location) ||
                (a.dayOfWeek - b.dayOfWeek) ||
                a.weeks.join(",").localeCompare(b.weeks.join(",")) ||
                (a.startSection - b.startSection)
            );
            const step1 = [];
            let cur = list[0];
            for (let i = 1; i < list.length; i++) {
                const nxt = list[i];
                const sameMeta =
                    cur.name === nxt.name &&
                    cur.teacher === nxt.teacher &&
                    cur.location === nxt.location &&
                    cur.dayOfWeek === nxt.dayOfWeek &&
                    cur.weeks.join(",") === nxt.weeks.join(",");
                if (sameMeta && cur.endSection + 1 === nxt.startSection) {
                    cur.endSection = nxt.endSection;
                } else if (sameMeta && cur.startSection === nxt.startSection && cur.endSection === nxt.endSection) {
                    continue;
                } else {
                    step1.push(cur);
                    cur = nxt;
                }
            }
            step1.push(cur);

            step1.sort((a, b) =>
                a.name.localeCompare(b.name) ||
                a.teacher.localeCompare(b.teacher) ||
                a.location.localeCompare(b.location) ||
                (a.dayOfWeek - b.dayOfWeek) ||
                (a.startSection - b.startSection) ||
                (a.endSection - b.endSection)
            );
            const step2 = [];
            cur = step1[0];
            for (let i = 1; i < step1.length; i++) {
                const nxt = step1[i];
                const sameSlot =
                    cur.name === nxt.name &&
                    cur.teacher === nxt.teacher &&
                    cur.location === nxt.location &&
                    cur.dayOfWeek === nxt.dayOfWeek &&
                    cur.startSection === nxt.startSection &&
                    cur.endSection === nxt.endSection;
                if (sameSlot) {
                    cur.weeks = Array.from(new Set([...cur.weeks, ...nxt.weeks])).sort((a, b) => a - b);
                } else {
                    step2.push(cur);
                    cur = nxt;
                }
            }
            step2.push(cur);
            return step2;
        }

        function normalizeDate(val) {
            const m = String(val || "").match(/(\d{4})[-\/.年](\d{1,2})[-\/.月](\d{1,2})/);
            if (!m) return null;
            return m[1] + "-" + m[2].padStart(2, "0") + "-" + m[3].padStart(2, "0");
        }

        function findSemesterStartDate(val) {
            if (val == null) return null;
            if (typeof val !== "object") return normalizeDate(val);
            if (Array.isArray(val)) {
                const w1 = val.find(x => x && typeof x === "object" && (String(x.zs) === "1" || String(x.zsmc) === "1")) || val[0];
                const d = findSemesterStartDate(w1);
                if (d) return d;
                for (const item of val) {
                    const dd = findSemesterStartDate(item);
                    if (dd) return dd;
                }
                return null;
            }
            for (const f of ["zrq", "zcrq", "rq", "ksrq"]) {
                const d = normalizeDate(val[f]);
                if (d) return d;
            }
            for (const v of Object.values(val)) {
                const d = findSemesterStartDate(v);
                if (d) return d;
            }
            return null;
        }

        async function fetchSemesterInfo(xnm, xqm) {
            try {
                const resp = await fetchJwJsonWithFallback("/kbcx/xskbcxZccx_cxZcByXnxq.html?gnmkdm=N2154", {
                    method: "POST",
                    headers: {
                        "accept": "application/json, text/javascript, */*; q=0.01",
                        "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
                        "x-requested-with": "XMLHttpRequest"
                    },
                    body: "xnm=" + encodeURIComponent(xnm) + "&xqm=" + encodeURIComponent(xqm),
                    credentials: "include"
                });
                if (!resp.ok) return null;
                const weeks = await resp.json();
                if (!Array.isArray(weeks) || weeks.length === 0) return null;
                return {
                    startDate: findSemesterStartDate(weeks),
                    totalWeeks: weeks.length
                };
            } catch (e) {
                return null;
            }
        }

        function detectCampusFromKbList(kbList) {
            const votes = { huguang: 0, haibin: 0, yangjiang: 0 };
            for (const raw of kbList) {
                if (!raw) continue;
                const xq = String(raw.xqmc || raw.cdxqmc || "").trim();
                const loc = String(raw.cdmc || "").trim();
                if (xq.includes("湖光")) votes.huguang += 3;
                else if (xq.includes("海滨")) votes.haibin += 3;
                else if (xq.includes("阳江")) votes.yangjiang += 3;

                if (/广学楼|明德楼|钟海楼|兴海楼|兴农楼|主楼|科技楼/.test(loc)) votes.huguang += 1;
                else if (/慎思楼|海纳楼|厚德楼|铭德楼/.test(loc)) votes.yangjiang += 1;
                else if (/^实\d|海滨/.test(loc)) votes.haibin += 1;
            }
            let best = "";
            let maxV = 0;
            for (const k of ["huguang", "haibin", "yangjiang"]) {
                if (votes[k] > maxV) { maxV = votes[k]; best = k; }
            }
            return best;
        }

        async function syncCoursesAndCalendar(xnm, xqm) {
            await ensureWfwJwSessionIfNeeded();
            const body = "xnm=" + encodeURIComponent(xnm) + "&xqm=" + encodeURIComponent(xqm) + "&kzlx=ck&xsdm=&kclbdm=";
            const [kbResp, semInfo] = await Promise.all([
                fetchJwJsonWithFallback("/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151", {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
                        "x-requested-with": "XMLHttpRequest"
                    },
                    body,
                    credentials: "include"
                }),
                fetchSemesterInfo(xnm, xqm)
            ]);
            if (!kbResp.ok) {
                return { ok: false, error: "HTTP " + kbResp.status + "，请确认已成功登录教务系统" };
            }
            const rawText = await kbResp.text();
            let data;
            try {
                data = JSON.parse(rawText);
            } catch (e) {
                return { ok: false, error: "登录态可能已过期，请刷新教务页面重新登录后再点同步" };
            }
            const kbList = Array.isArray(data && data.kbList) ? data.kbList : [];
            const rawCourses = [];
            for (const raw of kbList) {
                if (!raw || !raw.kcmc || raw.xqj == null || raw.jcs == null || raw.zcd == null) continue;
                const weeks = parseWeeks(raw.zcd);
                const sec = parseSectionRange(raw.jcs);
                const day = Number(raw.xqj);
                if (weeks.length === 0 || !sec || isNaN(day) || day < 1 || day > 7) continue;
                rawCourses.push({
                    name: normalizeCourseName(raw.kcmc),
                    teacher: String(raw.xm || "").trim(),
                    location: String(raw.cdmc || "").trim(),
                    dayOfWeek: day,
                    startSection: sec.startSection,
                    endSection: sec.endSection,
                    weeks,
                    credit: String(raw.xf || "").trim()
                });
            }
            const merged = mergeAndDistinctCourses(rawCourses);
            if (merged.length === 0) {
                return { ok: false, error: "该学年学期未查询到课程，请核对学年学期选择" };
            }
            const maxWeek = merged.reduce((m, c) => Math.max(m, ...c.weeks), 16);
            return {
                ok: true,
                courses: merged,
                startDate: semInfo ? semInfo.startDate : null,
                totalWeeks: Math.max(maxWeek, semInfo ? semInfo.totalWeeks : 20),
                detectedCampus: detectCampusFromKbList(kbList)
            };
        }

        async function syncGrades() {
            await ensureWfwJwSessionIfNeeded();
            // Pre-warm the Zhengfang v9 grade module page so server-side menu/session state is initialized
            // automatically without requiring the user to manually click into the grades page first!
            const pageDiscovered = [];
            try {
                const warmResp = await fetchJwWithFallback("/cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005&layout=default", {
                    method: "GET",
                    credentials: "include"
                });
                if (warmResp.ok) {
                    const html = await warmResp.text();
                    const epRe = /(\/cjcx\/cjcx_cx[A-Za-z0-9_]+\.html(?:\?[^'"\s)]*)?)/g;
                    let m;
                    while ((m = epRe.exec(html)) !== null) {
                        const rawEp = m[1];
                        const sep = rawEp.includes("?") ? "&" : "?";
                        if (!rawEp.includes("doType=query")) {
                            pageDiscovered.push(rawEp + sep + "doType=query&gnmkdm=N305005");
                        } else {
                            pageDiscovered.push(rawEp);
                        }
                    }
                }
            } catch (_) {}

            const discovered = await discoverMenuEndpoints();
            const endpoints = Array.from(new Set([
                ...discovered.gradeEps,
                ...pageDiscovered,
                "/cjcx/cjcx_cxDcListByXs.html?doType=query&gnmkdm=N305005",
                "/cjcx/cjcx_cxXsgrcj.html?doType=query&gnmkdm=N305005",
                "/cjcx/cjcx_cxDgXscj.html?doType=query&gnmkdm=N305005",
                "/cjcx/cjcx_cxDcListByXs.html?doType=query&gnmkdm=N305007",
                "/cjcx/cjcx_cxXsgrcj.html?doType=query&gnmkdm=N305007",
                "/cjcx/cjcx_cxDgXscj.html?doType=query&gnmkdm=N305007"
            ]));

            function mapGradeItems(items) {
                return items.map(it => {
                    const xnmText = String(it.xnmmc || (it.xnm ? (it.xnm + "-" + (Number(it.xnm) + 1)) : "")).trim();
                    const rawXq = String(it.xqmmc || it.xqm || "").trim();
                    const semText = rawXq === "1" || rawXq === "3" ? "第一学期"
                        : (rawXq === "2" || rawXq === "12" ? "第二学期" : (rawXq || "第一学期"));
                    const jd = parseFloat(it.jd);
                    const xf = parseFloat(it.xf);
                    return {
                        academicYear: xnmText || "未知学年",
                        semester: semText,
                        courseName: String(it.kcmc || "").trim(),
                        score: String(it.cj || it.bfzcj || "").trim(),
                        gradePoint: isNaN(jd) ? 0.0 : jd,
                        credit: isNaN(xf) ? 0.0 : xf,
                        courseType: String(it.kcxzmc || it.kclbmc || "").trim(),
                        examNature: String(it.ksxz || "正常考试").trim(),
                        teacher: String(it.jsxm || "").trim()
                    };
                }).filter(g => g.courseName.length > 0);
            }

            async function queryGradesOnce(ep, xnmVal, xqmVal) {
                const form = "xnm=" + encodeURIComponent(xnmVal) +
                    "&xqm=" + encodeURIComponent(xqmVal) +
                    "&kcbj=&_search=false&nd=" + Date.now() +
                    "&queryModel.showCount=200&queryModel.currentPage=1&queryModel.sortName=&queryModel.sortOrder=asc&time=0";
                const resp = await fetchJwJsonWithFallback(ep, {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
                        "x-requested-with": "XMLHttpRequest"
                    },
                    body: form,
                    credentials: "include"
                });
                if (!resp.ok) return null;
                const json = JSON.parse(await resp.text());
                return Array.isArray(json && json.items) ? json.items : (Array.isArray(json) ? json : []);
            }

            // Try ALL endpoints (NEVER break on first empty [] so cjcx_cxDcListByXs / cjcx_cxDgXscj all get tried!)
            const workingEps = [];
            for (const ep of endpoints) {
                try {
                    const items = await queryGradesOnce(ep, "", "");
                    if (items === null) continue;
                    workingEps.push(ep);
                    const mapped = mapGradeItems(items);
                    if (mapped.length > 0) {
                        return { ok: true, grades: mapped };
                    }
                } catch (e) {
                    // Try next endpoint
                }
            }

            // Fallback: query recent 4 academic years gently across working endpoints
            if (workingEps.length > 0) {
                const curY = Number(getDefaultAcademicYear());
                for (const ep of workingEps.slice(0, 3)) {
                    const collected = [];
                    const seen = new Set();
                    for (let offset = 0; offset < 4; offset++) {
                        const y = String(curY - offset);
                        try {
                            const items = await queryGradesOnce(ep, y, "");
                            if (items && items.length > 0) {
                                for (const g of mapGradeItems(items)) {
                                    const key = g.academicYear + "|" + g.semester + "|" + g.courseName + "|" + g.score;
                                    if (!seen.has(key)) {
                                        seen.add(key);
                                        collected.push(g);
                                    }
                                }
                            }
                        } catch (_) {}
                    }
                    if (collected.length > 0) {
                        return { ok: true, grades: collected };
                    }
                }
            }

            return { ok: false, error: "暂未获取到成绩（可能当前未出分或需先在教务页确认学业通知）" };
        }

        async function syncExams(xnm, xqm) {
            await ensureWfwJwSessionIfNeeded();
            // Pre-warm the Zhengfang v9 exam module page so off-campus one-tap sync NEVER requires manually visiting the exam page first!
            try {
                await fetchJwWithFallback("/kwgl/kscx_cxXsksxxIndex.html?gnmkdm=N358105&layout=default", {
                    method: "GET",
                    credentials: "include"
                });
            } catch (_) {}

            const discovered = await discoverMenuEndpoints();
            const endpoints = Array.from(new Set([
                ...discovered.examEps,
                "/kwgl/kscx_cxXsksxxIndex.html?doType=query&gnmkdm=N358105",
                "/kwgl/kscx_cxXsksxxIndex.html?doType=query&gnmkdm=N358110"
            ]));

            async function queryExamsOnce(ep, qXnm, qXqm) {
                const form = "xnm=" + encodeURIComponent(qXnm) + "&xqm=" + encodeURIComponent(qXqm) +
                    "&ksmcdmb_id=&kch=&kc=&ksrq=&kkbm_id=&_search=false&nd=" + Date.now() +
                    "&queryModel.showCount=200&queryModel.currentPage=1&queryModel.sortName=&queryModel.sortOrder=asc&time=0";
                const resp = await fetchJwJsonWithFallback(ep, {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
                        "x-requested-with": "XMLHttpRequest"
                    },
                    body: form,
                    credentials: "include"
                });
                if (!resp.ok) return null;
                const json = JSON.parse(await resp.text());
                const items = Array.isArray(json && json.items) ? json.items : (Array.isArray(json) ? json : []);
                return items.map(it => ({
                    academicYear: String(it.xnmc || qXnm || xnm).trim(),
                    semester: String(it.xqmmc || (qXqm === "3" ? "第一学期" : (qXqm === "12" ? "第二学期" : ""))).trim(),
                    courseName: String(it.kcmc || "").trim(),
                    examTimeRaw: String(it.kssj || "").trim(),
                    location: String(it.cdmc || it.ksmc || "").trim(),
                    seatNumber: String(it.zwh || "").trim(),
                    examType: String(it.ksmc || it.ksfs || "期末考试").trim(),
                    campus: String(it.cdxqmc || "").trim()
                })).filter(ex => ex.courseName.length > 0);
            }

            let workingEp = null;
            let lastHttpError = 0;
            let htmlInterceptHint = "";

            function classifyNonJsonHtml(rawText) {
                const t = String(rawText || "");
                if (/未通过|确认后方可|已阅读|教学评价|评教|问卷/.test(t)) {
                    return "需先在上方教务页确认通知或完成评教（也可切「校外免校园网」同步）";
                }
                if (/统一身份认证|cas\/login|请先登录|登录超时|会话超时/.test(t)) {
                    return "登录状态已过期，请刷新教务页重新登录";
                }
                if (/无操作权限|无权限|未开放|非查询时间/.test(t)) {
                    return "教务当前暂未开放考试查询入口";
                }
                return "教务返回了非数据页，建议切「校外免校园网」或先点进「查考场座位」后重试";
            }

            for (const ep of endpoints) {
                try {
                    const form = "xnm=" + encodeURIComponent(xnm) + "&xqm=" + encodeURIComponent(xqm) +
                        "&ksmcdmb_id=&kch=&kc=&ksrq=&kkbm_id=&_search=false&nd=" + Date.now() +
                        "&queryModel.showCount=200&queryModel.currentPage=1&queryModel.sortName=&queryModel.sortOrder=asc&time=0";
                    const resp = await fetchJwJsonWithFallback(ep, {
                        method: "POST",
                        headers: {
                            "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
                            "x-requested-with": "XMLHttpRequest"
                        },
                        body: form,
                        credentials: "include"
                    });
                    if (!resp.ok) {
                        if (resp.status >= 400) lastHttpError = resp.status;
                        continue;
                    }
                    const rawText = await resp.text();
                    let json;
                    try {
                        json = JSON.parse(rawText);
                    } catch (_) {
                        if (!htmlInterceptHint) htmlInterceptHint = classifyNonJsonHtml(rawText);
                        continue;
                    }
                    const rawItems = Array.isArray(json && json.items) ? json.items : (Array.isArray(json) ? json : []);
                    const exams = rawItems.map(it => ({
                        academicYear: String(it.xnmc || xnm).trim(),
                        semester: String(it.xqmmc || (xqm === "3" ? "第一学期" : (xqm === "12" ? "第二学期" : ""))).trim(),
                        courseName: String(it.kcmc || "").trim(),
                        examTimeRaw: String(it.kssj || "").trim(),
                        location: String(it.cdmc || it.ksmc || "").trim(),
                        seatNumber: String(it.zwh || "").trim(),
                        examType: String(it.ksmc || it.ksfs || "期末考试").trim(),
                        campus: String(it.cdxqmc || "").trim()
                    })).filter(ex => ex.courseName.length > 0);
                    if (!workingEp) workingEp = ep;
                    if (exams.length > 0) {
                        return { ok: true, exams };
                    }
                } catch (e) {
                    // Try next endpoint
                }
            }

            // Fallback 1: if term-scoped query returned 0, try whole-year query once
            if (workingEp && xqm) {
                try {
                    const yearExams = await queryExamsOnce(workingEp, xnm, "");
                    if (yearExams && yearExams.length > 0) {
                        return { ok: true, exams: yearExams };
                    }
                } catch (_) {}
            }

            // Fallback 2: if current year also returned 0, try unrestricted query once (catches cross-term / retake exams)
            if (workingEp) {
                try {
                    const allExams = await queryExamsOnce(workingEp, "", "");
                    if (allExams && allExams.length > 0) {
                        return { ok: true, exams: allExams };
                    }
                } catch (_) {}
                return { ok: false, error: "教务已联通，当前学期暂未公布考场安排" };
            }

            if (htmlInterceptHint) {
                return { ok: false, error: htmlInterceptHint };
            }

            return {
                ok: false,
                error: lastHttpError >= 400
                    ? ("考试接口请求失败(HTTP " + lastHttpError + ")，请刷新教务页后重试")
                    : "考试接口暂不可达（可能登录已过期或网络波动，请刷新教务页后重试）"
            };
        }
    """
}
