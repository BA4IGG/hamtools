# 📻 HAM 工具合集

面向业余无线电爱好者的 Android 原生工具箱。离线优先，Material Design 3 Expressive 深色主题，
配色严格遵循 M3 规范。

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-green)](./LICENSE)

> ✅ **当前为 1.2**。19 个工具入口**已全部实现**，不再有占位页面；底部导航另有独立的
> **QSL 卡片交换**板块（需登录，见[下文](#qsl-卡片交换独立板块底部-dock-第-4-页)）。
> 工具分为计算工具、查询手册、音频与硬件、联网与地图四组；所有联网能力都按
> 「离线优先 + 可选在线增强」实现，断网时仍可正常使用。欢迎提 Issue 或 PR。

## 功能现状

### 全部 19 个工具

**计算工具（6 个）**

| 工具 | 说明 |
|---|---|
| 频率波长 | 频率与波长互算，含频段归属提示 |
| 天线长度 | 偶极子、倒 V、垂直天线振子长度计算 |
| 驻波比回损 | VSWR、回波损耗、反射系数三者互算 |
| 馈线损耗 | 按线缆型号与长度估算衰减，采用厂商 7 频点双对数插值（非经验拟合） |
| 链路预算 | 弗里斯传输方程与接收电平推算 |
| APRS 验证码 | 按呼号算出 APRS-IS 登录 passcode；`0x73E2` 起逐对异或、取低 15 位，附计算过程与登录行示例，完全离线 |

**查询手册（5 个）**

| 工具 | 说明 |
|---|---|
| 业余频段划分 | 中国与 IARU 三个区的频段划分表，按区域切换与关键词检索 |
| 呼号前缀 | 数百个 DXCC 实体前缀检索，支持 `W1AW/KH6` 这类斜杠后缀解析 |
| Q 简语 | 常用 Q 简语（提问 / 陈述两面）+ ICAO 字母解释法 |
| 摩尔斯对照 | 文字与电码互转，可按键控速度真实发声（正弦波合成） |
| 卫星与中继 | 业余卫星参数与国内常用中继名录 |

**音频与硬件（4 个）**

| 工具 | 说明 |
|---|---|
| CW 电键 | 触屏手键与自动拍发，可调速度（WPM）与音调，正弦波合成带 5 ms 渐入渐出防爆音 |
| CW 听抄训练 | 科赫法 40 级渐进加字，随机码组可用固定种子复现，时长按 PARIS 标准换算 |
| DTMF | 12 键拨号盘生成 ITU-T Q.23 双音串，Goertzel 解码本机合成 PCM 做自检 |
| 音调发生器 | 单音持续输出（拖动变频不重建 AudioTrack）+ 线性扫频，4 种波形 |

**联网与地图（4 个）**

| 工具 | 说明 |
|---|---|
| 传播指数 | SFI / Kp / A 指数与 HF 六波段经验评估，可选 hamqsl.com 与 NOAA SWPC 实时源 |
| DX 集群 | spots 列表 + 频段/关键词筛选；在线拉 DXWatch 实时 DX cluster，失败保留内置 EXAMPLE 样例 |
| 网格与方位 | 梅登海德网格 2/4/6/8 字符互转、Haversine 大圆距离与双向初始方位、角度罗盘示意 |
| 呼号查询 | 离线前缀表解析 DXCC/后缀/中国分区，另有可选的 HamDB 在线执照资料查询 |

### 在线数据源与降级

相关页面已接入真实在线 API，但**一律是可选增强**：任何一路失败都退回内置数据，
界面如实标注来源，绝不把内置 EXAMPLE / DEMO 样例伪装成实时值。

| 工具 | 在线源 | 密钥 | 降级顺序 |
|---|---|---|---|
| DX 集群 | [DXWatch](https://dxwatch.com/dx) 实时 DX cluster（HTML 表格解析） | 无 | 服务端聚合 → DXWatch 直连 → 内置 EXAMPLE 样例 |
| 呼号查询 | [HamDB](https://hamdb.org/api) v1 REST JSON | 无 | HamDB 直连 → 服务端代理 → 内置离线前缀表 |
| 传播指数 | hamqsl.com solar XML / NOAA SWPC Kp JSON | 无 | 与本次改动无关，见既有实现 |

降级链的第一段是自部署服务端 `GET /api/ham/overview`（国内域名，顺带解决海外源连通性），
服务端用 `liveSpots` 标记 spots 是否为实时数据，失败即返回内置样例并如实标注。
DXWatch 没有公开 JSON API，只能解析 HTML 表格，页面结构变化时解析返回空列表并自动降级，不会把样例冒充实时数据。

> **地图合规**：「网格与方位」不接入任何第三方地图底图、瓦片或地图 SDK，
> 只绘制角度与距离极坐标示意，界面明确标注「不是地图底图、不表示真实地理边界」。
> 「网格与方位」完全离线可用，呼号查询的归属信息也始终来自内置离线前缀表。
> 在线呼号资料是**执照登记信息，不是电台的实时位置**，界面也如此标注。

### QSL 卡片交换（独立板块，底部 Dock 第 4 页）

不在 19 个工具里注册，而是底部导航的独立顶层页，**必须登录账号才能使用**。分「纸质卡片」与
「eQSL 电子卡片」两大板块，每块内再分「我的资料 / 发起交换 / 交换记录 / 台站名录」四个子页。

| 能力 | 说明 |
|---|---|
| 个人信息 | 显示名称、网格、QTH、联系邮箱、邮寄地址、留言，以及是否开放纸质 / eQSL、是否出现在名录 |
| 卡片图片 | 通过系统文件选择器上传 JPG / PNG / WebP（最大 5 MB），按文件头白名单校验，需带令牌鉴权下载 |
| 交换流程 | 申请 → 接受 →（纸质）标记寄出 → 确认完成；eQSL 接受即互换卡片图、无需邮寄 |
| 交换历史 | 进行中 / 待我处理 / 全部历史三种视图，含通联日期、波段、模式、RST 与状态流转 |
| 台站名录 | 按呼号搜索已开放交换的台站，结果已脱敏，不含地址与邮箱 |
| 新申请通知 | Android 13+ 首次进入时说明用途后申请 `POST_NOTIFICATIONS`；前台 30 秒轮询 + WorkManager 15 分钟周期检查，在**本机**发通知 |
| 数据管理 | 应用内可一键删除服务端 QSL 名片、卡片图片与全部交换记录 |

设计约束（与本项目一贯的「数据诚实」一致）：

- **地址最小可见**：邮寄地址与邮箱不进任何列表接口，只有交换被接受后，双方才能按单条记录取到。
- **离线 / 未部署降级**：`GET /api/qsl/ping` 返回 404 说明线上还是旧版服务端，App 进入**本地演示模式**，
  界面醒目标注「EXAMPLE 演示数据、不会发送给任何用户」，数据只存在内存、退出即清空。
  网络故障 / 5xx **不会**误判成演示模式，而是如实报错。
- **不集成任何推送 SDK**：通知是轮询发现新申请后在本机生成的，演示模式不发交换通知，测试通知也明确标注。
- **无地图**：本板块不接入任何地图底图或地图 SDK，不涉及位置轨迹。

### 已完成的基础设施

- **主题系统**：内置 Google 官方 HCT 色彩算法源码，支持调色盘任意取色、9 种方案变体、
  明暗双套与 Android 12+ 动态取色
- **账号体系**：呼号 + 密码注册登录，密码 bcrypt 哈希、JWT 鉴权，配套可自部署的后端
- **刷新率优化**：突破部分 ROM 对第三方应用的 90Hz 封顶（见[下文](#关于-90hz--120hz)）
- **音频引擎**：基于 `AudioTrack` 的双模式播放（短音一次性写入 / 持续流实时改参），统一 PCM 合成层
- **在线数据层**：`HamDataClient` 统一多源请求（HamDB / DXWatch / 服务端聚合），8 秒超时、失败静默降级；
  解析逻辑为纯 JVM 函数并配单元测试，界面始终标注数据来源与实时性
- **QSL 数据层**：`QslApi` / `QslRepository` / `QslViewModel` 三层，JSON 解析与交换状态机是纯 JVM 代码，
  与服务端 `nextStatus` / `ACTION_ACTOR` 保持双端一致；演示与真实模式共用同一套流转规则
- **启动动画 / 圆形品牌图标 / 导航节流 / 输入防抖**

## 技术栈

| | |
|---|---|
| 语言 | Kotlin 2.2.10 |
| UI | Jetpack Compose（BOM 2026.02.01）+ Material 3 Expressive |
| 架构 | 单 Activity + Navigation Compose 2.10.2 |
| 存储 | DataStore Preferences 1.3.0 |
| 网络 | OkHttp 4.12.0 |
| 构建 | AGP 9.4.0 / Gradle 9.6.0 / JDK 22 |
| 兼容 | minSdk 24（Android 7.0）～ targetSdk 37 |

- 单 Activity 架构，工具页之间用 Navigation Compose 跳转；底部 Dock 用 `HorizontalPager` 左右滑动切换
- 算法层（`HamMath`、`AprsPasscode`、`AudioSignal`/`DtmfCodec`、`GridMath`、`CwLesson`、`SolarModel`）为无 Android
  依赖的纯函数与纯数据，配套单元测试按手算标杆值校验
- **126 个单元测试**：算法标杆值、M3 配色回归、WCAG AA 对比度、呼号校验，以及 v1.2 新增的
  PCM 合成、DTMF/Goertzel 编解码、梅登海德网格与大圆路径、科赫课程、内置数据表一致性、
  APRS-IS 验证码公开标杆值与逐对计算过程，和在线数据源的 HamDB JSON / DXWatch HTML 解析与降级回归

## 编译

环境要求：JDK 22、Android SDK（compileSdk 37）。

```bash
./gradlew :app:assembleDebug      # 调试包
./gradlew :app:assembleRelease    # 正式包（需签名配置）
./gradlew :app:testDebugUnitTest  # 单元测试
```

### 签名

正式包必须签名才能安装。仓库不含密钥库，请自行生成：

```bash
keytool -genkeypair -v -keystore keystore/hamtools-release.p12 \
  -storetype PKCS12 -alias hamtools -keyalg RSA -keysize 2048 -validity 10000
```

然后在项目根目录建 `keystore.properties`：

```properties
storeFile=keystore/hamtools-release.p12
storePassword=你的密钥库密码
keyAlias=hamtools
keyPassword=你的密钥密码
```

`keystore/` 与 `keystore.properties` 已在 `.gitignore` 中排除。
**密钥库是这个 App 的唯一身份——丢了它，你将永远无法发布它的升级版**，请务必异地备份。

## 账号服务

App 内置呼号 + 密码的账号体系，服务端源码在 `server/`：

- Node 18+ / Express / MySQL，bcryptjs 哈希 + JWT 鉴权
- 注册、登录、me、改昵称、改密码接口
- 呼号按工信部《业余无线电台呼号管理办法》校验，并自动换算所属分区
  （例如 `BA4IGG` → 上海、山东、江苏）
- 附带建表 SQL、数据库自检脚本、备份脚本、systemd 单元与 Nginx 反代配置
- `GET /api/ham/overview`：聚合 DXWatch 实时 spots，用 `liveSpots` 标注实时性，上游失败即返回内置样例
- `GET /api/ham/callsign?q=W1AW`：代理 HamDB 呼号资料，供国内网络下备用
- **`GET /api/qsl/ping`** 及其余 QSL 接口：QSL 卡片交换的鉴权 API，见 [`server/README.md`](./server/README.md)。
  数据表 `qsl_profiles` / `qsl_cards` / `qsl_exchanges` 由服务启动时自建；卡片图片落到不可匿名访问的
  `uploads/qsl/`（可用 `QSL_UPLOAD_DIR` 覆盖），Nginx 不要把它配成静态目录。
- `npm run qsl:check`：不连数据库的 QSL 自检，覆盖呼号校验、图片文件头、状态机、错误码双端一致性。
- 可选配置见 [`server/.env.example`](./server/.env.example)：
  `DXWATCH_URL` / `HAMDB_URL` / `QSL_UPLOAD_DIR`。服务端不需要任何 API 密钥。

> **升级已有部署**：老版本服务端没有 `/api/qsl`，App 会自动进入明确标注的「本地演示」模式。
> 要让 QSL 真正可用，需要把 `server/` 更新到本版本并重启服务（启动时自动补建三张表），
> 然后确认 `curl https://api.ba4igg.cn/api/qsl/ping` 返回 `{"ok":true,...}`。
> 未升级不影响其余 19 个工具。

App 端地址写死在 `AppConfig.SERVER_BASE_URL`，**换服务器改这一行即可**，界面不提供修改入口。

部署文档：

- [`server/DEPLOY.md`](./server/DEPLOY.md) — Linux 云服务器命令行部署
- [`server/DEPLOY-BT.md`](./server/DEPLOY-BT.md) — 宝塔面板部署

## 关于 90Hz → 120Hz

不少 ROM 会把第三方应用锁在 90Hz。本项目的做法是两层叠加：

1. **系统策略层**（`RefreshPolicy`）：申请 `WRITE_SETTINGS` 权限后改写
   `Settings.System.min_refresh_rate` / `peak_refresh_rate`，抬升系统全局允许的上限。
   这是破解 90Hz 的关键——窗口级请求只能落在系统给定的区间内，上限被压到 90 时，
   应用请求 120 是无效的。附带 ContentObserver 防回落守护，ROM 改回去会自动补写。
2. **窗口应用层**（`FrameRateController`）：通过公开的
   `preferredDisplayModeId` / `preferredRefreshRate` 声明本窗口的期望帧率。

设备保护：只请求 `min(120, 屏幕硬件上限)`，60Hz 屏幕直接放弃请求，不会为了帧率烤热低端机。

> ⚠️ **首次使用需要手动授权**：`WRITE_SETTINGS` 属于特殊权限，应用无法弹窗申请，
> 必须到「系统设置 → 应用 → HAM 工具合集 → 修改系统设置」手动打开，刷新率优化才会生效。
>
> ⚠️ **省电模式是 AOSP 写死的 60Hz 硬策略，任何应用都无法覆盖**，需要高刷请先关闭省电模式。
>
> 注：早期版本曾用反射写隐藏字段 `preferredMin/MaxDisplayRefreshRate` 作为第三层兜底，
> 但 targetSdk 37 起该私有 API 已被封禁（lint `BlockedPrivateApi` 判为 fatal），
> 且系统策略层已能达成同样效果，现已移除。

## 官网静态站

`site/` 是纯静态官网（首页 / 用户协议 / 隐私政策），直接把目录内容上传到站点根目录即可，无需构建、无后端依赖。

首页引用的 3 分钟宣传片 `site/assets/media/HAM-Tools-promo-3min.mp4`（约 12 MB）**不纳入 Git 仓库**：二进制视频体积大，且会随版本重做。部署时二选一：

- 用 `promo/render_3min.py` 自行生成（依赖 Pillow / numpy / imageio-ffmpeg）后放到 `site/assets/media/`；
- 或使用随版本分发的官网压缩包 `hamtools-site-v1.2.zip`（内含完整 `assets/media/`，通过 Release 或本地交付，不入库）。

缺少该视频时首页其余内容正常，仅宣传片区无法播放。宣传片生成脚本 `promo/*.py` 已随仓库提供。

## 第三方代码

`app/src/main/java/com/ham/tools/materialcolor/utilities/` 下的 Java 类取自 Google
[material-components-android](https://github.com/material-components/material-components-android)
1.14.0 的 `com.google.android.material.color.utilities`（即 material-color-utilities），
依 **Apache License 2.0** 引入，各文件头部保留原始版权与许可声明。
改动仅限包名与移除 Android 注解，详见
[`PROVENANCE.txt`](./app/src/main/java/com/ham/tools/materialcolor/PROVENANCE.txt)。

## 作者

**BA4IGG** — 业余无线电爱好者，本工具的作者与维护者。

## 许可证

本项目主体采用 **MIT** 许可证，详见 [`LICENSE`](./LICENSE)。

例外：`materialcolor/utilities/` 目录沿用上游的 **Apache License 2.0**，
该部分的使用受其条款约束（需保留原始版权与许可声明）。
