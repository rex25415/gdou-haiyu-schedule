# 🌊 海屿课表 · 广海全校区纯净开源课表客户端

[![Release](https://img.shields.io/github/v/release/rex25415/gdou-haiyu-schedule?label=%E6%9C%80%E6%96%B0%E7%89%88%E6%9C%AC&color=0C7D72)](https://github.com/rex25415/gdou-haiyu-schedule/releases/latest)
[![Android CI](https://github.com/rex25415/gdou-haiyu-schedule/actions/workflows/android-ci.yml/badge.svg)](https://github.com/rex25415/gdou-haiyu-schedule/actions/workflows/android-ci.yml)
[![Android](https://img.shields.io/badge/Android-8.0%20~%2017%20(targetSdk%2036)-3DDC84?logo=android&logoColor=white)](https://rex25415.github.io/gdou-haiyu-schedule/)
[![License: PolyForm Noncommercial 1.0.0](https://img.shields.io/badge/License-PolyForm%20Noncommercial%201.0.0-0C7D72)](LICENSE)

> **非校方官方 & 非商业开源声明**：本项目是由学生出于个人学习与开源技术交流开发的独立纯本地正方教务课表客户端（内置广东海洋大学三校区公开作息模板），与任何高校官方无隶属或授权关系。应用 **零自建后端、零账号密码中转、零广告统计、零后台自动爬虫/选课/抢课**，采用 [PolyForm Noncommercial 1.0.0](LICENSE) 开源协议（禁止商用、禁止二次打包植入广告与冒充原作者分发）。详见 [安全与合规白皮书 (SECURITY.md)](SECURITY.md) 与 [NOTICE](NOTICE)。

专为 **广东海洋大学三校区（湖光主校区 · 海滨校区 · 阳江校区）** 适配的纯原生 **Kotlin + Jetpack Compose + Material 3** 安卓课表与学业助手。

---

## 📥 极速下载安装（推荐发同学用中文下载主页）

- 🌐 **中文一键下载主页（推荐 · 内置国内多线加速与微信/QQ 跳转指引）**：https://rex25415.github.io/gdou-haiyu-schedule/
- 📦 **GitHub Releases 发布页**：https://github.com/rex25415/gdou-haiyu-schedule/releases/latest

- **支持系统**：Android 8.0 ～ Android 17（`minSdk 26` / `targetSdk 36` Android 16），同时兼容 **华为鸿蒙 4.x 及以下**（包体仅约 **1.7 MB**）；
- **内置一键更新与验真**：内置 4 层免梯更新检查、国内多线加速下载与 **SHA-256 强制校验**，并配备 **运行时签名指纹验真**，防第三方篡改套壳；
- **包名说明（`v2.8.0+`）**：自 `v2.8.0` 起已全面启用中性个人开源包名 `io.github.rex25415.haiyu.schedule`（如此前装过 `v2.7.0` 及以下旧包名版本，可在旧版复制「同班课表分享码」1 秒导入新版后再卸载旧图标，之后所有升级均可直接无缝覆盖）。

---

## ✨ 界面预览

| 湖光主校区 · 清爽日间 | 深海蔚蓝 · 护眼深色 | 一键生成 7 天高清相册海报 |
| :---: | :---: | :---: |
| <img src="docs/screenshots/light_schedule.png" width="240" alt="日间主课表" /> | <img src="docs/screenshots/dark_ocean.png" width="240" alt="深色主课表" /> | <img src="docs/screenshots/poster_7day.png" width="240" alt="高清课表长图" /> |

---

## 🚀 核心功能一览

### 1. 免校园网一键同步广海正方教务（智能双通道 · 纯只读）
- **校外免校园网**：通过学校官方校外访问门户，4G/5G 流量与校外 Wi-Fi 均可直连登录，无需额外开 VPN；
- **校园网直连**：连接校园 Wi-Fi 时自动探测并支持一键切换直连，全链路强制 HTTPS 升级；
- 自动适配多因子二次认证页面（防白屏、防重定向死循环）；
- 一键离线读取：**个人课表 + 校历开学日期与总周数 + 全部历史成绩与加权 GPA + 考试时间/考场/座位号**；
- 课表页右上角支持 **一键静默刷新课表**（同步临时调课并保留个人手动加课/调课），教务菜单内置 **一键查空教室（自习推荐）**。

### 2. 广海三校区自动识别 & 精准错峰作息
同步课表时自动读取校区与教学楼特征，匹配对应作息与错峰时间表（支持扩展 11–12 节晚课）：
- **湖光主校区**：常规 3–4 节 `10:15-11:50`、7–8 节 `16:30-18:05`；自动识别 `广学楼` / `明德楼` 错峰（3–4 节 `10:05-11:35`、7–8 节 `16:25-17:50`）；
- **海滨校区**：常规同上；自动识别 `实验楼`、`实+房号` 简写（如 `实506-1`）、球场及方位场地错峰（3–4 节 `10:05-11:35`、7–8 节 `16:25-17:50`）；
- **阳江校区**：常规 7–8 节 `16:20-17:55`、`慎思楼` 3–4 节 `10:25-12:00`，自动识别非慎思楼 3–4 节错峰 `10:10-11:40`。

### 3. 旗舰级课表体验 & 重课/调课/假期全适配
- **稳定 7 天周视图 + 明日早课预告**：每周固定显示周一至周日，晚间下课后顶部卡片与桌面小组件自动预告**明日早课**；寒暑假/开学前自动识别假期状态，零误报；
- **本学期课程检索 & 学分汇总**：支持按课程名、教师或教室秒查全部上课周次并统计总学分；
- **重课（时段冲突）全链路适配**：手机窄屏堆叠显示 + `重×N` 徽标，点击重课卡片或顶部冲突一览均可直达单门课详情进行「仅调第 X 周」或编辑；平板大屏自动并排分列显示；
- **单周临时调课（「仅调第 X 周」）+ 节假日一键调休**。

### 4. 学业中心与离线实用工具箱
- **作业 / 实验 DDL 清单**：支持可视化日历选截止日期、编辑修改、临近截止本地提醒，且在课表点击课程详情即可直接查看该课程关联待办；
- **成绩查询 & GPA 模拟器**：按学期筛选、关键词搜索、支持手动加模拟课测算目标绩点；
- **考试倒计时**：自动排序最近考试、支持日历选日期与编辑、清晰显示倒计时天数、考场与座位号；
- **一键生成 1080×1920 高清课表长图**：直存相册（`Pictures/海屿课表`），设锁屏壁纸超清晰；
- **同班课表分享码 + 浅/深色自适应桌面小组件 + 课前精准提醒 + `.ics` 系统日历导出（含课前 15 分钟闹钟）**。

---

## 🔒 隐私与安全承诺

1. **100% 纯本地运行**：无任何自建后端服务器、无广告 SDK、无统计上报；
2. **账号零留存**：教务登录完全在系统原生 WebView 内严格 HTTPS 直连 `*.gdou.edu.cn`，应用代码不接触、不存储、不上传任何学号与密码；
3. **关闭系统云备份 + 签名验真**：显式配置 `android:allowBackup="false"`，设置页内置运行时安装包签名指纹验真与一键清除登录缓存。

---

## 🛠️ 本地从源码编译

### 环境要求
- JDK 17
- Android SDK（Platform 36 / Build-Tools 36.0.0）
- Android Studio Meerkat+ 或直接使用仓库自带的 Gradle Wrapper

### 命令行构建
```bash
# 运行全部单元测试
./gradlew testDebugUnitTest

# 编译 Debug 安装包
./gradlew assembleDebug

# 编译 Release 安装包（如需自定义签名，参考 keystore.properties.example 创建 keystore.properties）
./gradlew assembleRelease
```

---

## 📄 开源非商用许可协议

本项目源码基于 [PolyForm Noncommercial License 1.0.0](LICENSE) 开源发布，请同时遵守 [NOTICE](NOTICE) 中的来源与非商用声明：
- ✅ 允许个人学习、免费自用、代码安全审计及非商业公益交流；
- ❌ 严禁任何商业用途、收费分发、二次打包植入广告或冒充原作者发布。
