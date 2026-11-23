# 安全架构、隐私承诺与合规声明（Security & Compliance）

**海屿课表**（包名 `io.github.rex25415.haiyu.schedule`）是由学生出于个人学习与技术交流目的开发的**非商业开源工具**（正方教务纯本地课表客户端，内置广东海洋大学三校区公开作息模板），与任何高校官方均无隶属、授权或合作关系。

本项目从第一行代码起即以 **“零中转服务器、零账号采集、零后台爬虫、零商业盈利、100% 本地可审计”** 为最高原则设计，欢迎高校网络信息中心、安全研究者与全体同学随时审计源码与安装包。

---

## 1. 为什么与常见“代登录中转类小程序”本质不同？

| 维度 | 传统中转后端类小程序 | 海屿课表（纯本地开源 App） |
| :--- | :--- | :--- |
| **后端服务器** | 需要自建服务器转发学号/密码/Cookie | **无任何自建后端服务器（Zero Backend）** |
| **账号密码去向** | 经开发者服务器模拟登录 | **完全在系统原生 WebView 内直连学校官方域名，代码不接触、不读取、不存储密码** |
| **数据存储位置** | 存在开发者云数据库 | **100% 仅存储在用户本机应用私有目录，且关闭系统云备份与跨设备迁移** |
| **教务访问行为** | 可能存在高频轮询、自动化抢课/刷课 | **严格只读、仅用户手动触发按需读取本人已登录页面数据，绝无后台定时轮询、抢课、选课或写入操作** |
| **第三方 SDK / 统计** | 常见埋点、广告、推送 SDK | **零广告 SDK、零统计 SDK、零推送 SDK**（仅使用 Google 官方 Jetpack / KotlinX 开源库） |

---

## 2. 技术安全加固与防二次打包篡改清单

1. **全链路严格 HTTPS（零明文、零绕过）**
   - `AndroidManifest.xml` 显式配置 `android:usesCleartextTraffic="false"` 与 `@xml/network_security_config`（全局禁用明文 HTTP，仅信任系统内置权威 CA 证书）；
   - `WebViewClient.onReceivedSslError` 严格执行 `handler.cancel()` 拦截任何证书异常，绝不静默放行中间人连接；
   - WebView 显式禁用 `allowFileAccess`、`allowContentAccess` 及混合内容（`MIXED_CONTENT_NEVER_ALLOW`）。
2. **原生 JS 桥接严格域名白名单**
   - `GdouJsBridge` 对每一次 `@JavascriptInterface` 调用执行严格的 HTTPS 主机名校验（仅放行 `gdou.edu.cn` 及其合法子域名，拒绝 `gdou.edu.cn.evil.com` 等伪造域名与非 HTTPS 来源）。
3. **本地数据隔离与防云端泄露**
   - 配置 `android:allowBackup="false"`、`@xml/data_extraction_rules` 与 `@xml/backup_rules`，阻止 Android 云备份与换机传输导出本地隐私数据；
   - 设置页随时支持「一键退出教务账号并清除 WebView Cookie/存储缓存」。
4. **应用内更新防篡改与运行时签名验真**
   - 更新包仅来源自公开 GitHub Releases（`rex25415/gdou-haiyu-schedule`）；
   - 任何经国内加速镜像下载的安装包**必须通过 SHA-256 完整性校验**（无摘要自动禁用镜像，校验不通过立即丢弃并切换下一源），同时 Android 系统在覆盖安装时强制校验证书签名一致性；
   - `v2.8.0+` 内置运行时签名验真器（`AppSignatureVerifier`）：在「设置 → 隐私安全与来源验真」中实时展示当前安装包的签名证书指纹；如遇第三方二次打包植入广告重签，App 会直接给出显著警示，避免篡改包冒充原作者发布。

---

## 3. Android 权限透明说明（零多余权限）

本应用仅申请以下 6 项必要权限，**绝不申请**定位、通讯录、短信、电话、相机、麦克风、相册读取或悬浮窗等任何敏感权限：

| 权限 | 用途说明 |
| :--- | :--- |
| `INTERNET` | 直连学校官方教务域名（`*.gdou.edu.cn`）与可选的开源版本检查/下载 |
| `ACCESS_NETWORK_STATE` | 区分 Wi-Fi / 移动网络状态，辅助判断校园网直连与校外通用通道 |
| `POST_NOTIFICATIONS` | 仅当用户主动开启「课前提醒」时发送本地上课/DDL 通知（Android 13+ 需用户明确授权） |
| `RECEIVE_BOOT_COMPLETED` | 手机重启后重新注册用户已开启的本地课前提醒闹钟并刷新桌面组件 |
| `SCHEDULE_EXACT_ALARM` | 保证本地课前提醒按错峰作息准点触发 |
| `REQUEST_INSTALL_PACKAGES` | 仅当用户主动在应用内点击下载新版并确认安装时，调起系统安装器完成覆盖升级 |

---

## 4. 全部网络通信目的地清单（与实际抓包 100% 一致）

本应用所有出站 HTTPS 请求均限定于以下两类公开透明的目的地，**绝不携带任何学号、姓名、设备号（IMEI/OAID/Android ID）或统计标识**：

1. **广东海洋大学官方教务域名**（仅在用户打开「教务」页并手动操作时由系统 WebView 访问）：
   - `https://wfw.gdou.edu.cn/`（校外通用官方入口）
   - `https://jw.gdou.edu.cn/`（校园网直连教务入口）
   - `https://authserver.gdou.edu.cn/`（学校统一身份认证与多因子认证页面）
2. **可选的开源版本检查与安装包下载**（可在「设置 → 应用更新」中一键关闭自动检查）：
   - `https://api.github.com/repos/rex25415/gdou-haiyu-schedule/releases/latest`（GitHub Releases 官方公开接口）
   - `https://github.com/rex25415/gdou-haiyu-schedule/...`（GitHub 官方发布源与直链）
   - `https://rex25415.github.io/gdou-haiyu-schedule/latest.json`（GitHub Pages 托管的同源静态版本清单，用于国内 5G 免限流检查）
   - `https://gh-proxy.com/`、`https://ghfast.top/`、`https://ghproxy.net/`（仅用于国内网络加速拉取上述公开静态清单与 Releases APK，**所有镜像下载的 APK 均强制执行 SHA-256 哈希核验**）

---

## 5. 独立审计、开源非商用许可与签名核验方法

1. **开源非商用协议**：本项目采用 [PolyForm Noncommercial License 1.0.0](LICENSE) 并附带 [NOTICE](NOTICE) 声明，允许个人学习、校园公益交流、代码审计与非商业用途免费使用，**严格禁止任何商业使用、付费分发、植入广告或冒充原作者重打包**。
2. **云端 CI 透明度**：任何个人或机构均可查看本仓库的 [GitHub Actions CI 构建记录](https://github.com/rex25415/gdou-haiyu-schedule/actions)，确认源码在纯净开源云环境中自动通过单元测试与 R8 构建。
3. **验证本项目统一发布签名证书指纹**（应完全一致）：
   - 应用包名：`io.github.rex25415.haiyu.schedule`
   - 证书 DN：`CN=HaiyuSchedule, OU=GDOU Community, O=Haiyu, L=Zhanjiang, ST=Guangdong, C=CN`
   - 证书 SHA-256：`9d7c62e6a8b6099970964d7ff8cf6918c82422d2b37985523f40517898ec26ce`
4. 对比每个 GitHub Release 页面及 [latest.json](docs/latest.json) 公布的 APK 文件 SHA-256 哈希值。

---

## 6. 问题反馈与校方友好沟通渠道

如发现任何安全漏洞、合规建议，或学校网络信息中心/相关老师对接口频次、文案表述或任何细节有改进建议，欢迎直接在本仓库提交 [Issue](https://github.com/rex25415/gdou-haiyu-schedule/issues)，维护者将第一时间核实并积极配合调整。
