# 更新日志（Changelog）

本文件记录「海屿空间（原名海屿课表）」各个公开版本的主要功能与维护变更；包名自 `v2.8.0` 起统一为 `io.github.rex25415.haiyu.schedule`。

## [v3.5.1] - 2026-11-27（校外兼容通道协议打通 · 根治 WebVPN 拒登与老会话失效）
- **从底层彻底解决「校外 WebVPN 提示『未在本地用户库注册』」与「备用通道重启失忆」**：
  - 校外认证升级为直连广海公网第一方统一身份认证（`authserver.gdou.edu.cn`），免下 WebVPN 374KB 代理脚本、彻底告别“您正在使用VPN”浮窗，且一次登录即可持有全通道通用的第一方 `CASTGC`（支持校园网 ↔ 5G 切网静默续期）；
  - 重构原生引擎（[GdouNativeSyncEngine.kt](app/src/main/java/io/github/rex25415/haiyu/schedule/sync/GdouNativeSyncEngine.kt) + [EphemeralGdouWebSheet.kt](app/src/main/java/io/github/rex25415/haiyu/schedule/ui/components/EphemeralGdouWebSheet.kt)）实现直连 `authserver` 静默签票并透传 `wfw` 正方网关（完整保留 `Path=/sso` 会话并自动重写校内重定向），彻底打通以前浏览器下无法登录的 `wfw` 兼容通道；
  - 账号如遇新版 WebVPN 拒登（`/wengine-vpn/denied`），自动利用已有第一方 `CASTGC` **零密码无缝切至「校外兼容通道 (wfw)」**，并通过持久化配置（`preferLegacyWfw`）永久记忆网关偏好，重启手机或次日再点「刷新课表」绝不再弹回 WebVPN 撞红屏。
- **加固老会话过期与边缘场景稳定性**：
  - WebVPN 会话过期重登前自动清理脏/半过期 `wengine_vpn_ticket`，补齐认证页 `service` 丢失落入个人中心及官网全屏浏览跳过 WebVPN 门户列表的自动接管；
  - 教务中心右上角菜单新增「校外改用兼容通道(wfw) / WebVPN」手动切换，且未登录态常驻「清除登录缓存」，并在网络探活中补齐 `wfw` 回退探测。

## [v3.5.0] - 2026-11-25（纯原生教务同步引擎重构 · 根治登录卡顿与过期误报）
- **耗时多轮深挖根治「校外登录后误报过期、WebView 加载卡顿、切页掉帧」顽疾**：
  - 彻底告别常驻内嵌 WebView 同步方案，重写纯 Kotlin 原生 HTTPS 并发同步引擎（[GdouNativeSyncEngine.kt](app/src/main/java/io/github/rex25415/haiyu/schedule/sync/GdouNativeSyncEngine.kt) + [GdouCookieStore.kt](app/src/main/java/io/github/rex25415/haiyu/schedule/sync/GdouCookieStore.kt)），平时零常驻 `WebView`（`WebViews: 0`），彻底绕开广海 WebVPN 374KB 代理重写脚本、3.7MB 无用登录装饰背景图与旧式正方网页渲染阻塞，切页丝滑、校外 5G 全量同步缩短至约 2 秒、刷课表 1 秒内完成；
  - 针对 WebVPN 服务端托管票据、`/login?cas_login=true` 浮窗复现、纯 4G/5G 流量撞内网超时及跨网切线做了全链路优化：新增纯蜂窝 0ms 秒判校外（[NetworkChannelProbe.kt](app/src/main/java/io/github/rex25415/haiyu/schedule/sync/NetworkChannelProbe.kt)）、空 Cookie `0-RTT` 瞬时判别、`CASTGC → WebVPN` 跨通道无头静默换票、HTTP Keep-Alive 连接池复用、学年学期/空教室上下文 10 分钟内存缓存与后台预热、成绩与等级考试并行拉取；
  - 一次性全屏官方认证与官网浮层（[EphemeralGdouWebSheet.kt](app/src/main/java/io/github/rex25415/haiyu/schedule/ui/components/EphemeralGdouWebSheet.kt)）提升至应用根级覆盖底栏，彻底修复软键盘弹出穿透背后页面问题，认证通过拿到票据的瞬间立即销毁 `WebView` 释放内存。
- **重塑第 2 个大界面「教务中心」& 新增「刷新课表 · 检查调课」**：
  - [SyncPortalScreen.kt](app/src/main/java/io/github/rex25415/haiyu/schedule/ui/screens/SyncPortalScreen.kt) 全面对齐「课表 / 学业中心 / 设置与工作台」视觉规范（统一顶栏大标题、标准分段控件、`20dp` 无框洁白/深色卡片与宽屏适配），未登录态为专注纯净的连接引导页，认证后原子切换 2 步建连过渡态（0 帧按钮闪现、零弹窗与 Toast 叠层）；
  - 去除与其他页面重复的入口，下半区精简为唯一不重复的「访问广海正方教务官网」直连卡片（用于教学评价、自主选课、培养方案等官网事务）；新增纯本地调课变动比对，刷完课表直接提示「暂无调课变动」或精准播报变动课程并保留手动调课；打通「查空教室」未登录认证后的自动回跳闭环。

## [v3.4.3] - 2026-11-25（校外免校园网全链路深修与跨网自动恢复）
- **根治「校外免校园网（WebVPN）」同步误报与跨网刷课表卡顿**：
  - 修复 [GdouSyncScript.kt](app/src/main/java/io/github/rex25415/haiyu/schedule/sync/GdouSyncScript.kt) 中 WebVPN 分支回退裸根路径触发 `302 /login` 导致成绩/考试同步误判 `登录态已过期` 的故障；补全 WebVPN 登录页 HTML 拦截识别（`WEB VPN` / `wengine-vpn` / `cas_login` / `统一认证`），同步修复 `expectJson=false` 误放行门户页面，并将网络异常 `redirect: "manual"` 探测超时收敛为 `3.5s`；
  - 新增课表页后台刷新与网络故障前的快速双通道探测（[SyncPortalScreen.kt](app/src/main/java/io/github/rex25415/haiyu/schedule/ui/screens/SyncPortalScreen.kt) `pendingQuickProbeNonce`），校外或离校断直连后 ≤ 2.8s 自动切换「校外免校园网」，不再在不可达的 `jw.gdou.edu.cn` 上干等 18 秒，且清零残留报错以保证重刷生效；
- **修复切页/回退体验细节与旧版教务 JS 兼容**：
  - 从「查空教室」切回课表再触发需要登录时，自动切回 `教务登录与同步` 面板不再卡在空教室子页；历史后退造成当前网址与通道芯片不一致时支持再次点击当前通道纠偏重进；
  - 消除 `EMPTY_ROOM_CONTEXT_HELPER` 与 `findSemesterStartDate` 中残留的 `Array.prototype.find()` 调用，彻底兼容正方旧版 Prototype.js 注入页面。

## [v3.4.2] - 2026-11-25（切页竞态与学校节点抖动热修复）
- **修复「课表切教务 / 点刷新后白屏重刷、误报已过期又自动进入」竞态**：
  - 修复 [SyncPortalScreen.kt](app/src/main/java/io/github/rex25415/haiyu/schedule/ui/screens/SyncPortalScreen.kt) 中已在教务已登录域时因 `!isPageSettled` 仍强行 `stopLoading + loadUrl` 重刷的逻辑，并在确实需要重置入口时先同步重置完成态并等待新导航真实 `onPageStarted -> onPageFinished`，杜绝提前 150ms 向空白帧注入脚本导致 `教务尚未登录或登录态已过期` 的误报；
  - 紧缩 [GdouSyncScript.kt](app/src/main/java/io/github/rex25415/haiyu/schedule/sync/GdouSyncScript.kt) 根入口 URL 过渡帧的登录态识别（`isBareJwEntryUrl`），修复 WebVPN `/login` 隐藏密码框引发的过门误判，放宽无感续期超时至 `18s` 并自动复位二次进门引导标志；
- **适配 `jw.gdou.edu.cn` 负载均衡故障节点与正方 `HTTP 901`**：
  - 在 [NetworkChannelProbe.kt](app/src/main/java/io/github/rex25415/haiyu/schedule/sync/NetworkChannelProbe.kt) 中区分 TCP 建连超时（真校外）与建连后读超时/断连（校内撞中故障节点），携带 `Connection: close` 自动换连接复测并保护加载中的 SSO 跳转流不被打断；
  - 补充识别正方接口返回自定义状态码 **`HTTP 901`** 为会话过期状态。

## [v3.4.1] - 2026-11-25（🚨 紧急热修复）
- **紧急修复校外无法登录教务问题（切至海大官方新版 WebVPN）**：
  - 针对近期学校旧校外入口 `wfw.gdou.edu.cn` 在移动端流量下统一认证参数与会话校验异常导致反复弹回密码页无法进入的紧急故障，全面将校外通用通道升级为官方新版 **WebVPN（`webvpn.gdou.edu.cn`）**，支持 `/login → CAS 认证 → 教务` 全链路自动直达与 `/https/<hex>` 路径解析，校外流量即可顺畅登录与同步；
- **修复直连校园网 / 切换网络误报「登录态已过期」与冷启动刷新卡死**：
  - 在 [GdouSyncScript.kt](app/src/main/java/io/github/rex25415/haiyu/schedule/sync/GdouSyncScript.kt) 中重构请求容错与过期判定：去除把断连 `status = 0` 转为假 `HTTP 500` 的逻辑，引入 `redirect: "manual"` 探测区分真实 `302` 认证失效与网络波动，并加入 `12s` 单次请求 `AbortController` 超时；
  - 首页冷启动点击一键刷新新增 **后台静默 SSO 校验与无感续期**，严格等待 `onPageFinished`（`isPageSettled`）后注入同步脚本、保持后台刷新期间 WebView 活跃并增设 `35s` 同步看门狗，根治冷启动转圈卡死问题；
  - 临时自动降级不再永久覆写「校园网直连」偏好，重新连回校园网且未登录时自动恢复直连，并为 `shouldOverrideUrlLoading` 增加 `isForMainFrame` 主框架拦截校验。

## [v3.4.0] - 2026-11-24
- **广海官方服务枢纽、校内官网直达与深色/竖屏全量精修**：
  - 新增「广海官方服务与教务公告」（`CampusOfficialService` + `CampusServicesBottomSheet`）：直连 `jwc.gdou.edu.cn` 拉取教务公告分类流与最新官方校历原表，支持公告一键转入作业/考试 DDL 并联动「课表百宝箱」与「设置 → 官网校历与公告」；
  - 教务直达新增动态发现正方菜单（教学评价、自主选课、学业情况/培养方案）及评教待办拦截引导横幅；重构校内外双通道智能探测（并发轻量 `GET` 探测 `jw` / `authserver` / `wfw` 并过滤未认证 Wi-Fi 劫持；校外丢包超时约 `1.8s` 快切、校内冷连接抖动单次容错复测、`wfw` 登录跳转等待缩至 `300ms`、`/sso/jziotlogin` 状态纠偏与慢加载一键切线条）；
  - 按 `1080×2400` 真实手机态与深浅双色模式逐页微调十余处细节：修复长课名挤裁教室定位、消除官方服务/成绩主卡/设置工作台副标题截断、考试座号与学期去重孤字折行、空教室占位符/邻近卡片省略号与深色 Snackbar 过亮反差，统一全部菜单图标风格。
- **面对面零摩擦分享与下载闭环（零商业推销 · 纯开源免费）**：
  - 在「分享海屿空间给同学」弹窗新增**纯离线 QR 码编码器（`QrCodeEncoder`）**，支持手机相机/浏览器扫码直达下载页，并上线**「直接传安装包（.apk）给同学」**（读取本机已装只读安装包生成标准命名文件，避开 Android 11+ 包可见性拦阻直拉系统 Sharesheet，适配 QQ/互传/微信转发文件）；
  - 同班课表分享码新增默认勾选的「附带下载页提示」开关，导出课表长图页脚安静保留开源主页来源，优化分享文案并全线修正微信拦截 `github.io` 时的真实引导（提醒勿用微信扫码、复制网址进手机浏览器或直接发 `.apk`）；
  - 升级 GitHub Pages 落地页：调整最快国内主线 `ghfast.top` 为首选、加入桌面端纯离线 `<svg>` 扫码框与下载点击引导浮层。
- **同步完整性、模块化架构与云端运维基建**：
  - 全链路补齐正方教务多页分页拉取（空教室、成绩、考试、等级考试），拆分 `levelExamEps` 与 `gradeEps` 菜单发现并修复 HTTP 200 非 JSON 诊断；修复短学期 `16` / `xqmmc="3"`、重修辅修判定、五年制学年跨度与实践课识别；
  - 拆分 `ScheduleWeekGrid`、`AcademicGradesPane`、`AcademicExamsPane`、`EmptyClassroomSheet` 与 `PracticeCoursesBottomSheet`，隔离 30 秒倒计时重绘范围，加入 `.ics` 日历导出、挂科/实践快捷入口、Beta 预览通道与 1.3× 大字适配；
  - 上线云端签名发版流水线（`.github/workflows/release.yml`）、镜像巡检与 65 项核心单元测试覆盖（AGP `8.13.2`）。

## [v3.3.0] - 2026-11-24
- **查空教室 2.0**：支持搜索教室号查看今日全天 5 大节逐节空课时间轴、湖光/海滨/阳江三校区全楼栋与楼层识别、附近自习楼推理，以及常去楼栋/教室收藏置顶。
- **成绩与 GPA 科学去重**：合并正方「初修 + 补考/重修」同课重复记录，支持「有效去重 / 仅已通过 / 原始全计」三档切换并增强实验/实践课识别。
- **桌面小组件扩展（#2）**：新增第 4 款「海屿极简方卡（`2×2`）」微件，设置工作台支持一键固定到桌面。
- **壁纸导入兼容性重构（#3）**：落地零权限双引擎解码（适配 Android 8–17 照片选择器、HEIC/EXIF 旋转与透明图，支持系统文件选择器兜底）。
- **教务通道切回与登录态探测**：隔离切通道残留错误回调并加强重定向门户 DOM 已登录探测。

## [v3.2.0] - 2026-11-24
- **全站交互架构重构**：首页新增「课表百宝箱」2×4 视觉工具抽屉与「微缩单行预告条」；学业中心支持智能默认落页与「学业深度分析」统一抽屉；设置页重构为「三大工作台」。
- **实时查空教室上线**：教务页升级双模式，可复用登录态直连查询实时空闲教室。
- **深海蔚蓝深色视觉精修**：全面优化深色模式成绩卡片与分段徽章对比度。

## [v3.1.0] - 2026-11-23
- 正式更名为「海屿空间」（保持包名与发布签名不变，覆盖安装零数据丢失）。
- 新增目标 GPA 反推规划器、课程详情考核标签与考勤/加分计次、万能校园倒计时置顶、「迷你下节课程（4×1）」小组件及应用分享/排障中心。

## [v3.0.0] - 2026-11-23
- 清爽移动课表视觉打磨、期末考试周投影至课表与考前提醒、双人离线空课交集对比、GPA 趋势折线图与桌面倒计时微件。

## [v2.9.0] - 2026-11-23
- 修复校外免校园网一键全同步成绩/考试问题，新增重课冲突置顶、自定义壁纸与全量换机备份。

## [v2.8.0] - 2026-11-23
- 启用独立开源包名 `io.github.rex25415.haiyu.schedule` 与 PolyForm Noncommercial 1.0.0 协议，新增运行时签名指纹验真与深色微件优化。

## [v2.7.0] - 2026-11-23
- 修复周视图顶栏重叠、重构通道开关、接入国内免 VPN `docs/latest.json` 更新容灾与自动清理示例数据。

## [v2.6.0] - 2026-11-23
- 强化 TLS / WebView / 禁止云备份合规配置，新增双通道自动探测与多线路自更新下载站（`docs/`）。

## [v2.5.0] - 2026-11-23
- 上线内置 GitHub Releases 自动检查更新、SHA-256 强制校验与课表时间线精修。

## [v2.4.0] - 2026-11-23
- 广东海洋大学三校区（湖光 · 海滨 · 阳江）首个纯本地开源正式版本发布。
