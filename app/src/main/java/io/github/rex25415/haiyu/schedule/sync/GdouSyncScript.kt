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
                        const pList = courseResult.practiceCourses || [];
                        HaiyuBridge.onCoursesSynced(JSON.stringify({
                            courses: courseResult.courses,
                            practiceCourses: pList,
                            semesterStartDate: courseResult.startDate || "",
                            totalWeeks: courseResult.totalWeeks || 20,
                            termLabel: TERM_LABEL,
                            academicYear: XNM,
                            semesterCode: XQM,
                            detectedCampus: courseResult.detectedCampus || ""
                        }));
                        summary.push("课表 " + courseResult.courses.length + " 条" + (pList.length > 0 ? ("(含实践 " + pList.length + " 门)") : ""));
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
                        const lvList = gradeResult.levelExams || [];
                        HaiyuBridge.onGradesSynced(JSON.stringify({
                            grades: gradeResult.grades,
                            levelExams: lvList
                        }));
                        summary.push("成绩 " + gradeResult.grades.length + " 门" + (lvList.length > 0 ? (" · 等级考 " + lvList.length + " 项") : ""));
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
                    const pList = courseResult.practiceCourses || [];
                    HaiyuBridge.onCoursesSynced(JSON.stringify({
                        courses: courseResult.courses,
                        practiceCourses: pList,
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

    /**
     * 一键从当前正方网页已展示的表格（DOM #tabGrid）直接提取成绩或考试数据（100% 绕过接口拦截兜底）
     */
    fun buildScrapeCurrentPageDomScript(): String = """
        (function() {
            $COMMON_HELPERS
            try {
                if (!checkOnJwDomain()) {
                    HaiyuBridge.onSyncError("请先登录并进入正方教务成绩或考试查询页");
                    return;
                }
                const domExams = scrapeExamsFromCurrentDom();
                if (domExams.length > 0) {
                    HaiyuBridge.onExamsSynced(JSON.stringify({ exams: domExams }));
                    HaiyuBridge.onSyncComplete("已从当前网页表格直接提取考试 " + domExams.length + " 场");
                    return;
                }
                const domGrades = scrapeGradesFromCurrentDom();
                if (domGrades.length > 0) {
                    HaiyuBridge.onGradesSynced(JSON.stringify({ grades: domGrades, levelExams: [] }));
                    HaiyuBridge.onSyncComplete("已从当前网页表格直接提取成绩 " + domGrades.length + " 门");
                    return;
                }
                HaiyuBridge.onSyncError("当前网页未检测到已加载的考试或成绩表格（请先在网页中点击「查询」展开表格后再试）");
            } catch (err) {
                HaiyuBridge.onSyncError("网页表格提取异常：" + (err && err.message ? err.message : String(err)));
            }
        })();
    """.trimIndent()

    private const val EMPTY_ROOM_CONTEXT_HELPER = """
        async function resolveEmptyRoomQueryContext(safeCampus, safeYear, safeSem) {
            let xnm = safeYear || getDefaultAcademicYear();
            let xqm = safeSem || ((new Date().getMonth() >= 7 || new Date().getMonth() === 0) ? "3" : "12");
            const knownCampusMap = {
                "湖光": "1",
                "海滨": "2",
                "阳江": "BBF8A51ACEA2144EE053768926D2788B",
                "霞山": "3"
            };
            let xqhId = "";
            for (const [k, v] of Object.entries(knownCampusMap)) {
                if (safeCampus.includes(k)) {
                    xqhId = v;
                    break;
                }
            }
            try {
                const pageResp = await fetchJwWithFallback("/cdjy/cdjy_cxKxcdlb.html?gnmkdm=N2155&layout=default", {
                    method: "GET",
                    credentials: "include"
                });
                if (pageResp.ok) {
                    const html = await pageResp.text();
                    const doc = new DOMParser().parseFromString(html, "text/html");
                    const dmCxEl = doc.querySelector("#dm_cx, select[name='dm_cx']");
                    if (dmCxEl && dmCxEl.value && String(dmCxEl.value).includes("-")) {
                        const parts = String(dmCxEl.value).trim().split("-");
                        if (parts[0]) xnm = parts[0].trim();
                        if (parts[1]) xqm = parts[1].trim();
                    }
                    const xnmEl = doc.querySelector("#xnm, select[name='xnm'], input[name='xnm']");
                    const xqmEl = doc.querySelector("#xqm, select[name='xqm'], input[name='xqm']");
                    if (xnmEl && xnmEl.value) xnm = String(xnmEl.value).trim();
                    if (xqmEl && xqmEl.value) xqm = String(xqmEl.value).trim();

                    const xqSelect = doc.querySelector("#xqh_id, select[name='xqh_id']");
                    if (xqSelect) {
                        const opts = Array.from(xqSelect.querySelectorAll("option"));
                        const matched = opts.find(o => o.value && String(o.textContent || "").includes(safeCampus));
                        const firstValid = opts.find(o => o.value && String(o.value).trim().length > 0);
                        if (matched) xqhId = String(matched.value).trim();
                        else if (!xqhId && firstValid) xqhId = String(firstValid.value).trim();
                    }
                }
            } catch (_) {}
            return { xnm, xqm, xqhId };
        }

        async function fetchEmptyRoomBlock(ctx, zcdMask, dow, secStart, secEnd, roomKeyword) {
            let jcdMask = 0;
            for (let sec = secStart; sec <= secEnd; sec++) {
                jcdMask += Math.pow(2, sec - 1);
            }
            function buildForm(pageNo) {
                return "fwzt=cx&xqh_id=" + encodeURIComponent(ctx.xqhId) +
                    "&xnm=" + encodeURIComponent(ctx.xnm) +
                    "&xqm=" + encodeURIComponent(ctx.xqm) +
                    "&cdlb_id=&cdejlb_id=&qszws=&jszws=&cdmc=" + encodeURIComponent(roomKeyword || "") +
                    "&lh=&jyfs=0&cdjylx=&zcd=" + encodeURIComponent(String(zcdMask)) +
                    "&xqj=" + encodeURIComponent(String(dow)) +
                    "&jcd=" + encodeURIComponent(String(jcdMask)) +
                    "&_search=false&nd=" + (Date.now() + secStart) +
                    "&queryModel.showCount=1000&queryModel.currentPage=" + pageNo + "&queryModel.sortName=cdbh&queryModel.sortOrder=asc&time=0";
            }
            const resp = await fetchJwJsonWithFallback("/cdjy/cdjy_cxKxcdlb.html?doType=query&gnmkdm=N2155", {
                method: "POST",
                headers: {
                    "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
                    "x-requested-with": "XMLHttpRequest"
                },
                body: buildForm(1),
                credentials: "include"
            });
            if (!resp.ok) return null;
            const json = JSON.parse(await resp.text());
            let rawItems = Array.isArray(json && json.items) ? json.items : (Array.isArray(json) ? json : []);
            const totalPage = Number((json && json.totalPage) || 1);
            const totalResult = Number((json && json.totalResult) || rawItems.length);
            if (totalPage > 1 && rawItems.length < totalResult) {
                try {
                    const resp2 = await fetchJwJsonWithFallback("/cdjy/cdjy_cxKxcdlb.html?doType=query&gnmkdm=N2155", {
                        method: "POST",
                        headers: {
                            "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
                            "x-requested-with": "XMLHttpRequest"
                        },
                        body: buildForm(2),
                        credentials: "include"
                    });
                    if (resp2.ok) {
                        const json2 = JSON.parse(await resp2.text());
                        const more = Array.isArray(json2 && json2.items) ? json2.items : [];
                        rawItems = rawItems.concat(more);
                    }
                } catch (_) {}
            }
            return rawItems;
        }
    """

    /**
     * 原生级自习空教室只读查询（直接在当前登录态内调用正方场地查询接口，全量拉取当前校区全部教学楼空教室，
     * 对跨双节如 1-4节/5-8节 并行拆块严格求交集，自动剔除线上/球场/基地等非自习场地）
     */
    fun buildQueryEmptyClassroomsScript(
        campusKeyword: String,
        weekNumber: Int,
        dayOfWeek: Int,
        startSection: Int,
        endSection: Int,
        buildingKeyword: String,
        fallbackAcademicYear: String = "",
        fallbackSemesterCode: String = ""
    ): String {
        val safeCampus = campusKeyword.replace("\"", "")
        val safeBuilding = buildingKeyword.replace("\"", "")
        val safeYear = fallbackAcademicYear.replace("\"", "").trim()
        val safeSem = fallbackSemesterCode.replace("\"", "").trim()
        val w = weekNumber.coerceIn(1, 25)
        val d = dayOfWeek.coerceIn(1, 7)
        val s1 = startSection.coerceIn(1, 12)
        val s2 = endSection.coerceIn(s1, 12)
        return """
        (async function() {
            $COMMON_HELPERS
            $EMPTY_ROOM_CONTEXT_HELPER
            try {
                if (!checkOnJwDomain()) {
                    HaiyuBridge.onSyncError("请先完成广海教务系统登录，再查询空教室");
                    return;
                }
                HaiyuBridge.onSyncProgress("正在连接正方场地系统全量拉取「$safeCampus」空教室...");
                await ensureWfwJwSessionIfNeeded();

                const ctx = await resolveEmptyRoomQueryContext("$safeCampus", "$safeYear", "$safeSem");
                const zcdMask = Math.pow(2, $w - 1);

                // Split multi-block ranges (e.g. 1-4 = [1..2, 3..4]) cleanly and intersect for 100% authentic consecutive free guarantee
                const blocks = [];
                if ($s2 - $s1 >= 2) {
                    for (let cur = $s1; cur <= $s2; cur += 2) {
                        blocks.push([cur, Math.min(cur + 1, $s2)]);
                    }
                } else {
                    blocks.push([$s1, $s2]);
                }

                // Sequential per-block fetch avoids Struts2/WebVPN session race when intersecting multi-block ranges (1-4 / 5-8)
                const mySeq = (window.__haiyuRoomQuerySeq = (window.__haiyuRoomQuerySeq || 0) + 1);
                const blockResults = [];
                for (const pair of blocks) {
                    if (window.__haiyuRoomQuerySeq !== mySeq) return;
                    const res = await fetchEmptyRoomBlock(ctx, zcdMask, $d, pair[0], pair[1], "");
                    blockResults.push(res);
                }
                if (window.__haiyuRoomQuerySeq !== mySeq) return;

                let hasNullBlock = false;
                for (const r of blockResults) {
                    if (r === null) {
                        hasNullBlock = true;
                        break;
                    }
                }
                if (hasNullBlock) {
                    HaiyuBridge.onSyncError("空教室接口暂未返回数据（可在教务页点「原网页」查看）");
                    return;
                }

                const nonStudyBuildingRe = /线上教学|无楼号|体育课场地|田径运动场|体育场|体育馆|动物科学基地|动物医院|林学后山基地|林果楼|^工厂$/;
                const nonStudyTypeRe = /网络教室|运动场|操场|录像/;
                const nonStudyRoomRe = /网络教学平台|腾讯会议|钉钉直播|微信直播|超星平台|腾讯课堂|线上教学|^校外$|足球场|篮球场|排球场|网球场|羽毛球|游泳池|校道|田径运动场|台球室|乒乓球室|健身室|牛舍|鸡舍|羊舍|动物房|室外基地|后山基地|气象观测场|大厅前空地/;

                function normalizeRooms(rawItems) {
                    const map = new Map();
                    for (const it of rawItems) {
                        if (!it) continue;
                        const roomName = String(it.cdmc || it.cdbh || "").trim();
                        if (!roomName) continue;
                        let building = String(it.jxlmc || it.lh || "").trim();
                        const roomType = String(it.cdlbmc || "普通教室").trim();
                        if (nonStudyBuildingRe.test(building) || nonStudyTypeRe.test(roomType) || nonStudyRoomRe.test(roomName)) {
                            continue;
                        }
                        if (!building) {
                            const bm = roomName.match(/^(广学楼|明德楼|钟海楼|博文楼|兴教楼|兴海楼|兴农楼|望海楼|图海楼|艺海楼[ABC]?|海科楼|海博楼|教学楼|实验楼|电教楼|济海楼|慎思楼|厚为楼|博学楼|明辨楼|海纳楼|海行楼|济航楼)/);
                            building = bm ? bm[1] : "综合教学区";
                        }
                        const key = building + "|" + roomName;
                        if (!map.has(key)) {
                            map.set(key, {
                                roomName,
                                building,
                                roomType,
                                seats: String(it.kwzws || it.zws || "").trim(),
                                campus: String(it.xqmc || "$safeCampus").trim()
                            });
                        }
                    }
                    return map;
                }

                const firstMap = normalizeRooms(blockResults[0]);
                const otherMaps = blockResults.slice(1).map(normalizeRooms);
                const bKw = "$safeBuilding".trim();
                const rooms = [];
                for (const [key, item] of firstMap.entries()) {
                    // Explicitly loop instead of Array.prototype.every because legacy Zhengfang pages override it
                    let inAllBlocks = true;
                    for (const m of otherMaps) {
                        if (!m.has(key)) {
                            inAllBlocks = false;
                            break;
                        }
                    }
                    if (!inAllBlocks) continue;
                    if (bKw && !item.roomName.includes(bKw) && !item.building.includes(bKw)) continue;
                    rooms.push(item);
                }

                const primeCount = rooms.filter(r =>
                    !/实验室|实训|机房|语音|画室|琴房|多功能厅|咖啡|活动中心/.test(r.roomName) &&
                    !/语音室|计算机房|多功能计算机房|设计教室|画室|绘图教室|艺术类|声乐|键盘|琴房|舞蹈|实验剧场/.test(r.roomType) &&
                    !/工程训练中心|轮机实验楼|水产科技楼|第二实验综合楼|智能制造/.test(r.building)
                ).length;
                const buildingCount = new Set(rooms.map(r => r.building)).size;

                HaiyuBridge.onEmptyClassroomsLoaded(JSON.stringify({
                    rooms,
                    summary: "$safeCampus" + "校区 · 共 " + primeCount + " 间自习教室（全校区共 " + rooms.length + " 间 · 覆盖 " + buildingCount + " 栋教学楼）"
                }));
            } catch (err) {
                HaiyuBridge.onSyncError("查询空教室失败：" + (err && err.message ? err.message : String(err)));
            }
        })();
        """.trimIndent()
    }

    /**
     * 单间教室今日全天（1-10 节共 5 大节）空闲时间轴并发只读检测，帮助判断“现在进去后下节课会不会被赶走”。
     */
    fun buildInspectRoomDayScheduleScript(
        campusKeyword: String,
        weekNumber: Int,
        dayOfWeek: Int,
        buildingName: String,
        roomName: String,
        fallbackAcademicYear: String = "",
        fallbackSemesterCode: String = ""
    ): String {
        val safeCampus = campusKeyword.replace("\"", "").trim()
        val safeBuilding = buildingName.replace("\"", "").trim()
        val safeRoom = roomName.replace("\"", "").trim()
        val safeYear = fallbackAcademicYear.replace("\"", "").trim()
        val safeSem = fallbackSemesterCode.replace("\"", "").trim()
        val w = weekNumber.coerceIn(1, 25)
        val d = dayOfWeek.coerceIn(1, 7)
        val noTag = safeRoom
            .replace(Regex("[（(][^）)]*[）)]$"), "")
            .trim()
        val shortNoBld = if (safeBuilding.isNotEmpty() && noTag.startsWith(safeBuilding)) {
            noTag.removePrefix(safeBuilding).trimStart('-', '—', ' ')
        } else noTag
        val searchKey = if (shortNoBld.length >= 2) shortNoBld else noTag
        return """
        (async function() {
            $COMMON_HELPERS
            $EMPTY_ROOM_CONTEXT_HELPER
            try {
                if (!checkOnJwDomain()) {
                    HaiyuBridge.onSyncError("请先完成广海教务系统登录");
                    return;
                }
                await ensureWfwJwSessionIfNeeded();
                const ctx = await resolveEmptyRoomQueryContext("$safeCampus", "$safeYear", "$safeSem");
                const zcdMask = Math.pow(2, $w - 1);
                const dayBlocks = [[1,2],[3,4],[5,6],[7,8],[9,10]];
                const targetRaw = "$safeRoom";
                const targetCore = targetRaw.replace(/[（(][^）)]*[）)]${'$'}/, "").trim();
                const targetBld = "$safeBuilding";
                const qKw = "$searchKey" || targetCore || targetRaw;

                const mySeq = (window.__haiyuRoomInspectSeq = (window.__haiyuRoomInspectSeq || 0) + 1);
                const results = [];
                let activeKw = qKw;
                let switchedToFull = false;
                for (let idx = 0; idx < dayBlocks.length; idx++) {
                    if (window.__haiyuRoomInspectSeq !== mySeq) return;
                    const pair = dayBlocks[idx];
                    let items = await fetchEmptyRoomBlock(ctx, zcdMask, $d, pair[0], pair[1], activeKw);
                    if (items === null && activeKw) {
                        activeKw = "";
                        switchedToFull = true;
                        items = await fetchEmptyRoomBlock(ctx, zcdMask, $d, pair[0], pair[1], "");
                    }
                    results.push(items);
                }
                if (window.__haiyuRoomInspectSeq !== mySeq) return;
                // Explicitly avoid Array.prototype.every/.find due to Zhengfang legacy Prototype/jQuery overrides
                let allEmptyArr = results.length > 0;
                for (const r of results) {
                    if (!Array.isArray(r) || r.length > 0) {
                        allEmptyArr = false;
                        break;
                    }
                }
                if (activeKw && !switchedToFull && allEmptyArr) {
                    for (let idx = 0; idx < dayBlocks.length; idx++) {
                        if (window.__haiyuRoomInspectSeq !== mySeq) return;
                        const pair = dayBlocks[idx];
                        results[idx] = await fetchEmptyRoomBlock(ctx, zcdMask, $d, pair[0], pair[1], "");
                    }
                }
                if (window.__haiyuRoomInspectSeq !== mySeq) return;
                let allNull = true;
                for (const r of results) {
                    if (r !== null) {
                        allNull = false;
                        break;
                    }
                }
                if (allNull) {
                    HaiyuBridge.onSyncError("未能查询该教室今日全天排课状况");
                    return;
                }

                let resolvedName = targetRaw;
                let resolvedBuilding = targetBld;
                let resolvedSeats = "";
                let resolvedType = "";
                const bCore = targetBld.replace(/楼${'$'}/, "");

                const slots = dayBlocks.map((pair, idx) => {
                    const items = results[idx] || [];
                    let matched = null;
                    for (const it of items) {
                        const mc = String(it.cdmc || "").trim();
                        const b = String(it.jxlmc || it.lh || "").trim();
                        const bOk = !targetBld || (b ? (b === targetBld || b.includes(bCore)) : (mc.includes(bCore) || mc === targetRaw));
                        const rOk = mc === targetRaw || mc === targetCore ||
                            mc.replace(/[（(][^）)]*[）)]${'$'}/, "").trim() === targetCore ||
                            (items.length === 1 && mc.includes(qKw));
                        if (bOk && rOk) {
                            matched = it;
                            break;
                        }
                    }
                    if (!matched && !targetBld && items.length === 1) {
                        matched = items[0];
                    }
                    if (matched) {
                        resolvedName = String(matched.cdmc || targetRaw).trim();
                        if (!resolvedBuilding) resolvedBuilding = String(matched.jxlmc || matched.lh || "").trim();
                        if (!resolvedSeats) resolvedSeats = String(matched.kwzws || matched.zws || "").trim();
                        if (!resolvedType) resolvedType = String(matched.cdlbmc || "").trim();
                    }
                    return {
                        startSection: pair[0],
                        endSection: pair[1],
                        isFree: !!matched,
                        matchedRoomName: matched ? String(matched.cdmc || "").trim() : "",
                        seats: matched ? String(matched.kwzws || matched.zws || "").trim() : "",
                        roomType: matched ? String(matched.cdlbmc || "").trim() : ""
                    };
                });

                HaiyuBridge.onClassroomDayScheduleLoaded(JSON.stringify({
                    campusName: "$safeCampus",
                    building: resolvedBuilding,
                    roomName: resolvedName,
                    weekNumber: $w,
                    dayOfWeek: $d,
                    seats: resolvedSeats,
                    roomType: resolvedType,
                    slots
                }));
            } catch (err) {
                HaiyuBridge.onSyncError("查询教室全天时间轴失败：" + (err && err.message ? err.message : String(err)));
            }
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

    private const val HAS_LOGGED_IN_PORTAL_DOM_JS = """
        function hasLoggedInPortalDom() {
            try {
                if (typeof document === "undefined" || !document) return false;
                if (document.querySelector('input[type="password"], #mm, #yhm, input[name="mm"]')) {
                    return false;
                }
                const title = String(document.title || "").toLowerCase();
                if (
                    title.includes("统一身份认证") ||
                    title.includes("多因子") ||
                    title.includes("正在跳转") ||
                    title.includes("无法打开") ||
                    title.includes("出错") ||
                    title.includes("error") ||
                    title.includes("登录") ||
                    title.includes("login")
                ) {
                    return false;
                }
                const bodyText = String((document.body && (document.body.innerText || document.body.textContent)) || "");
                const headText = bodyText.slice(0, 600);
                if (/统一身份认证|登录超时|会话超时|请先登录|用户登录/.test(headText)) {
                    return false;
                }
                if (document.querySelector('#cdNav, #yhxx, .nav_menu, #top_menu, .top_menu')) {
                    return true;
                }
                return /个人课表查询|学生成绩查询|空教室查询|考试查询|选课学分/.test(bodyText);
            } catch (_) {
                return false;
            }
        }
    """

    private const val COMMON_HELPERS = """
        $BASE_PREFIX_HELPER
        $HAS_LOGGED_IN_PORTAL_DOM_JS

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
                href.includes("reauthcheck") ||
                href.includes("reauthloginview") ||
                href.includes("ismultifactor") ||
                title.includes("统一身份认证") ||
                title.includes("多因子") ||
                title.includes("正在跳转") ||
                title.includes("登录")
            ) {
                return false;
            }
            // Server-side forward after login may keep /xtgl/login_slogin.html or login_tickitLogin.html URL
            if (href.includes("login_slogin") || href.includes("login_tickit") || href.includes("/login")) {
                return hasLoggedInPortalDom();
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

                if (/广学楼|明德楼|钟海楼|博文楼|兴海楼|兴教楼|兴农楼|望海楼|图海楼|艺海楼|海科楼|海博楼|水产科技楼|主楼|科技楼/.test(loc)) votes.huguang += 1;
                else if (/慎思楼|海纳楼|厚为楼|博学楼|明辨楼|海科楼一|海行楼|济航楼|海阳馆|海驰楼|海润楼|厚德楼|铭德楼/.test(loc)) votes.yangjiang += 1;
                else if (/^实\d|海滨|电教楼|济海楼/.test(loc)) votes.haibin += 1;
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
            const sjkList = Array.isArray(data && data.sjkList) ? data.sjkList : [];
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
                    credit: String(raw.xf || "").trim(),
                    courseType: String(raw.kcxzmc || "").trim(),
                    examMethod: String(raw.khfsmc || "").trim(),
                    classGroup: String(raw.jxbmc || "").trim(),
                    totalHours: String(raw.zxs || "").trim()
                });
            }
            const practiceCourses = [];
            for (const raw of sjkList) {
                if (!raw) continue;
                const pName = normalizeCourseName(raw.kcmc || raw.sjkcgs || "");
                if (!pName) continue;
                const wTxt = String(raw.qsjsz || raw.zcd || "").trim();
                practiceCourses.push({
                    name: pName,
                    teacher: String(raw.jsxm || raw.xm || "").trim(),
                    weeksText: wTxt,
                    weeks: parseWeeks(wTxt),
                    credit: String(raw.xf || "").trim(),
                    courseType: String(raw.kcxzmc || "实践环节").trim(),
                    note: String(raw.sjkcgs && raw.kcmc ? raw.sjkcgs : "").trim()
                });
            }
            const merged = mergeAndDistinctCourses(rawCourses);
            if (merged.length === 0 && practiceCourses.length === 0) {
                return { ok: false, error: "该学年学期未查询到课程，请核对学年学期选择" };
            }
            const maxWeek = merged.reduce((m, c) => Math.max(m, ...c.weeks), 16);
            return {
                ok: true,
                courses: merged,
                practiceCourses,
                startDate: semInfo ? semInfo.startDate : null,
                totalWeeks: Math.max(maxWeek, semInfo ? semInfo.totalWeeks : 20),
                detectedCampus: detectCampusFromKbList(kbList)
            };
        }

        function readCellByCol(row, colSuffixes) {
            for (const sfx of colSuffixes) {
                const cell = row.querySelector("td[aria-describedby$='_" + sfx + "'], td[aria-describedby*='" + sfx + "']");
                if (cell) {
                    const txt = String(cell.textContent || "").replace(/\u00a0/g, " ").trim();
                    if (txt) return txt;
                }
            }
            return "";
        }

        function scrapeGradesFromCurrentDom() {
            const rows = Array.from(document.querySelectorAll("#tabGrid tr.jqgrow, table.ui-jqgrid-btable tr.jqgrow"));
            const out = [];
            for (const row of rows) {
                const courseName = readCellByCol(row, ["kcmc"]);
                if (!courseName) continue;
                const xnmText = readCellByCol(row, ["xnmmc", "xnm"]) || "未知学年";
                const rawXq = readCellByCol(row, ["xqmmc", "xqm"]);
                const semText = (rawXq === "1" || rawXq === "3") ? "第一学期"
                    : ((rawXq === "2" || rawXq === "12") ? "第二学期" : (rawXq || "第一学期"));
                const score = readCellByCol(row, ["cj", "bfzcj"]);
                const jd = parseFloat(readCellByCol(row, ["jd"]));
                const xf = parseFloat(readCellByCol(row, ["xf"]));
                const courseType = readCellByCol(row, ["kcxzmc", "kclbmc"]);
                const examNature = readCellByCol(row, ["ksxz"]) || "正常考试";
                const teacher = readCellByCol(row, ["jsxm"]);
                out.push({
                    academicYear: xnmText,
                    semester: semText,
                    courseName,
                    score,
                    gradePoint: isNaN(jd) ? 0.0 : jd,
                    credit: isNaN(xf) ? 0.0 : xf,
                    courseType,
                    examNature,
                    teacher,
                    breakdown: ""
                });
            }
            return out;
        }

        function scrapeExamsFromCurrentDom() {
            const rows = Array.from(document.querySelectorAll("#tabGrid tr.jqgrow, table.ui-jqgrid-btable tr.jqgrow"));
            const out = [];
            for (const row of rows) {
                const courseName = readCellByCol(row, ["kcmc"]);
                const examTimeRaw = readCellByCol(row, ["kssj"]);
                if (!courseName || !examTimeRaw) continue;
                const academicYear = readCellByCol(row, ["xnmc", "xnmmc", "xnm"]);
                const semester = readCellByCol(row, ["xqmmc", "xqm"]);
                const location = readCellByCol(row, ["cdmc", "ksmc"]);
                const seatNumber = readCellByCol(row, ["zwh"]);
                const ksmc = readCellByCol(row, ["ksmc"]);
                const ksfs = readCellByCol(row, ["ksfs", "khfs"]);
                const examType = [ksmc, ksfs].filter(Boolean).join(" · ") || "期末考试";
                const campus = readCellByCol(row, ["cdxqmc"]);
                out.push({
                    academicYear,
                    semester,
                    courseName,
                    examTimeRaw,
                    location,
                    seatNumber,
                    examType,
                    campus
                });
            }
            return out;
        }

        async function syncLevelExamsGentle() {
            const eps = [
                "/cjcx/djkscx_cxXsDjksxxIndex.html?doType=query&gnmkdm=N305010",
                "/cjcx/djkscx_cxXsDjksxxIndex.html?doType=query&gnmkdm=N358115"
            ];
            const form = "_search=false&nd=" + Date.now() + "&queryModel.showCount=100&queryModel.currentPage=1&queryModel.sortName=&queryModel.sortOrder=asc&time=0";
            for (const ep of eps) {
                try {
                    const resp = await fetchJwJsonWithFallback(ep, {
                        method: "POST",
                        headers: {
                            "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
                            "x-requested-with": "XMLHttpRequest"
                        },
                        body: form,
                        credentials: "include"
                    });
                    if (!resp.ok) continue;
                    const json = JSON.parse(await resp.text());
                    const items = Array.isArray(json && json.items) ? json.items : (Array.isArray(json) ? json : []);
                    const mapped = items.map(it => {
                        const examName = String(it.djksmc || it.ksmc || "").trim();
                        const score = String(it.cj || it.zcj || it.dj || "").trim();
                        const examDate = String(it.ksrq || it.xnmc || "").trim();
                        const parts = [];
                        if (it.tlcj) parts.push("听力:" + it.tlcj);
                        if (it.ydcj) parts.push("阅读:" + it.ydcj);
                        if (it.xzcj) parts.push("写作:" + it.xzcj);
                        if (it.zhcj) parts.push("综合:" + it.zhcj);
                        return { examName, score, examDate, detail: parts.join(" · ") };
                    }).filter(x => x.examName.length > 0 && x.score.length > 0);
                    if (mapped.length > 0) return mapped;
                } catch (_) {}
            }
            return [];
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
                    const subParts = [];
                    if (it.pscj) subParts.push("平时:" + it.pscj);
                    if (it.qzcj) subParts.push("期中:" + it.qzcj);
                    if (it.sycj) subParts.push("实验:" + it.sycj);
                    if (it.qmcj) subParts.push("期末:" + it.qmcj);
                    return {
                        academicYear: xnmText || "未知学年",
                        semester: semText,
                        courseName: String(it.kcmc || "").trim(),
                        score: String(it.cj || it.bfzcj || "").trim(),
                        gradePoint: isNaN(jd) ? 0.0 : jd,
                        credit: isNaN(xf) ? 0.0 : xf,
                        courseType: String(it.kcxzmc || it.kclbmc || "").trim(),
                        examNature: String(it.ksxz || "正常考试").trim(),
                        teacher: String(it.jsxm || "").trim(),
                        breakdown: subParts.join(" · ")
                    };
                }).filter(g => g.courseName.length > 0);
            }

            const levelExams = await syncLevelExamsGentle();

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
                        return { ok: true, grades: mapped, levelExams };
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
                        return { ok: true, grades: collected, levelExams };
                    }
                }
            }

            // DOM Table Fallback: if user already opened the grade query page in WebView, scrape #tabGrid directly!
            const domGrades = scrapeGradesFromCurrentDom();
            if (domGrades.length > 0) {
                return { ok: true, grades: domGrades, levelExams };
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

            function formatExamTypeLabel(it) {
                const name = String(it.ksmc || "").trim();
                const mode = String(it.ksfs || it.khfs || "").trim();
                if (name && mode && !name.includes(mode)) return name + " · " + mode;
                return name || mode || "期末考试";
            }

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
                    examType: formatExamTypeLabel(it),
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
                        examType: formatExamTypeLabel(it),
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
            }

            // Fallback 3: DOM Table Fallback — if user is already viewing the exam table in WebView, scrape #tabGrid directly!
            const domExams = scrapeExamsFromCurrentDom();
            if (domExams.length > 0) {
                return { ok: true, exams: domExams };
            }

            if (workingEp) {
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

    /** 探测服务端内部转发后 URL 仍停留在 login_* 路径的页面是否已真实渲染教务首页 DOM。 */
    val DOM_PORTAL_PROBE_SCRIPT: String = """
        (function() {
            $HAS_LOGGED_IN_PORTAL_DOM_JS
            return hasLoggedInPortalDom() ? "LOGGED_IN" : "NOT_READY";
        })();
    """.trimIndent()

    /** 判断当前 URL 是否处于统一认证多因子二次验证页。 */
    fun isMfaUrl(url: String?): Boolean {
        val lower = url.orEmpty().lowercase(java.util.Locale.US)
        return lower.contains("reauthcheck") ||
            lower.contains("reauthloginview") ||
            lower.contains("ismultifactor")
    }

    /** 判断 URL 是否仍停留在教务登录/票据转发路径上。 */
    fun isUrlOnPortalLoginPath(url: String?): Boolean {
        val lower = url.orEmpty().lowercase(java.util.Locale.US)
        return lower.contains("login_slogin") ||
            lower.contains("login_tickit") ||
            lower.contains("/login")
    }

    /** 安全提取请求 URL 的小写主机名，供纯 JVM 单元测试与 WebView 错误拦截复用。 */
    fun extractHostLower(url: String?): String {
        val raw = url?.trim().orEmpty()
        if (raw.isEmpty()) return ""
        return try {
            java.net.URI(raw).host?.lowercase(java.util.Locale.US).orEmpty()
        } catch (_: Exception) {
            raw.substringAfter("://", raw)
                .substringBefore("/")
                .substringBefore("?")
                .substringBefore("#")
                .substringBefore(":")
                .lowercase(java.util.Locale.US)
        }
    }

    /** 切回目标通道后，忽略上一条被中断通道延迟送达的主框架网络错误。 */
    fun isStaleChannelError(useOffCampus: Boolean, requestUrl: String?): Boolean {
        val host = extractHostLower(requestUrl)
        if (host.isEmpty()) return false
        return if (useOffCampus) {
            host == "jw.gdou.edu.cn"
        } else {
            host == "wfw.gdou.edu.cn"
        }
    }

    /** 综合 URL、页面标题、错误状态与 DOM 探测结果，准确判定当前教务页是否已登录。 */
    fun resolveJwLoginState(
        url: String?,
        pageTitle: String?,
        pageErrorMsg: String?,
        domConfirmedLoggedIn: Boolean
    ): Boolean {
        if (!pageErrorMsg.isNullOrBlank()) return false
        val lower = url.orEmpty().lowercase(java.util.Locale.US)
        val titleLower = pageTitle.orEmpty().lowercase(java.util.Locale.US)
        val onJwOrWfw = lower.contains("jw.gdou.edu.cn") || lower.contains("wfw.gdou.edu.cn")
        if (!onJwOrWfw) return false
        if (
            isMfaUrl(lower) ||
            lower.contains("auth_error") ||
            lower.contains("authserver.") ||
            lower.contains("/authserver/") ||
            lower.contains("sso.") ||
            lower.contains("/cas/login")
        ) {
            return false
        }
        if (
            titleLower.contains("无法打开") ||
            titleLower.contains("出错") ||
            titleLower.contains("error") ||
            titleLower.contains("统一身份认证") ||
            titleLower.contains("多因子") ||
            titleLower.contains("正在跳转") ||
            titleLower.contains("登录") ||
            titleLower.contains("login")
        ) {
            return false
        }
        if (isUrlOnPortalLoginPath(lower)) {
            return domConfirmedLoggedIn
        }
        return true
    }
}
