# 📻 HAM 工具合集

面向业余无线电爱好者的 Android 原生工具箱。离线优先，Material Design 3 Expressive 深色主题，
配色严格遵循 M3 规范。

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-green)](./LICENSE)

> ✅ **当前正式版为 2.0（versionCode 4）**，是「正式版通道」的首发版本。
> **21 个工具**（计算 6 / 查询 5 / 音频与硬件 6 / 联网与地图 4）全部实现，另有底部导航的
> **考试练习**（正中央，离线可用）与 **QSL 卡片交换**（需登录）两个独立板块，
> 以及「关于」页的 **检测更新**（正式 / 测试双通道）。
> GitHub 上的 `v1.2`、`v1.1-beta`、`v1.0-beta` 已全部归入**测试版通道（Pre-release）**，
> 不再作为正式版推荐；`v2.0` 是当前唯一的正式版 Release。
> 工具分为计算工具、查询手册、音频与硬件、联网与地图四组；所有联网能力都按
> 「离线优先 + 可选在线增强」实现，断网时仍可正常使用。欢迎提 Issue 或 PR。
>
> 底部 Dock 从左到右为 **主页 / QSL 交换 / 考试练习 / 我的 / 关于**，考试练习固定在正中央。
> Dock 用短标签保证窄屏与大字体下也不截断（完整名称用于页面标题与无障碍朗读），
> 五个顶层页共用同一条 64dp 顶栏（标题 + 副标题），左右滑动时顶部不会跳动。

## 功能现状

### 工具清单（21 个，v2.0 正式版全部包含）

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

**音频与硬件（6 个）**

| 工具 | 说明 |
|---|---|
| CW 电键 | 触屏手键与自动拍发，可调速度（WPM）与音调，正弦波合成带 5 ms 渐入渐出防爆音 |
| CW 听抄训练 | 科赫法 40 级渐进加字，随机码组可用固定种子复现，时长按 PARIS 标准换算 |
| DTMF | 12 键拨号盘生成 ITU-T Q.23 双音串，Goertzel 解码本机合成 PCM 做自检（不采集外部声音） |
| 音调发生器 | 单音持续输出（拖动变频不重建 AudioTrack）+ 线性扫频，4 种波形 |
| SSTV 编解码 🆕 | 图片 ↔ 慢扫描电视基带音频互转，支持 Robot36 / Martin M1 / Scottie S1；可播放、导出 WAV、导入 WAV 解码、导出 PNG，可经麦克风实时解码 |
| FT8 编解码 🆕 | FT8 消息生成、播放、导出 WAV 与轻量化解码；完整实现 LDPC(174,91) + CRC14 + Costas 同步的编解码，可经麦克风监听 15 秒时隙解码 |

**联网与地图（4 个）**

| 工具 | 说明 |
|---|---|
| 传播指数 | SFI / Kp / A 指数与 HF 六波段经验评估，可选 hamqsl.com 与 NOAA SWPC 实时源 |
| DX 集群 | spots 列表 + 频段/关键词筛选；在线拉 DXWatch 实时 DX cluster，失败保留内置 EXAMPLE 样例 |
| 网格与方位 | 梅登海德网格 2/4/6/8 字符互转、Haversine 大圆距离与双向初始方位、角度罗盘示意 |
| 呼号查询 | 离线前缀表解析 DXCC/后缀/中国分区，另有可选的 HamDB 在线执照资料查询 |

### SSTV / FT8 编解码：权限、隐私与能力边界 🆕

两个工具都生成与解析**音频基带信号，不是射频发射**。实际通联需要电台、合法执照、
按合规频率工作，并把音频接到电台的音频输入；界面也如此标注。

| 项目 | 说明 |
|---|---|
| 麦克风权限 | `RECORD_AUDIO` 是运行时危险权限，**只在用户主动点「申请权限」后**才申请：先弹用途说明，确认后才调系统对话框。不自动弹、不反复弹 |
| 权限影响范围 | 编码、播放、保存 WAV、导入 WAV 解码、本机自测都不需要麦克风；拒绝权限只是「实时监听」不可用 |
| 音频处理 | `MicCapture` 用 `AudioRecord` 以 48000 Hz 单声道 PCM16 采集（优先 `UNPROCESSED` 音源，避免 AGC/降噪破坏信号），滚动缓冲上限 125 秒 |
| 隐私 | PCM **只在内存**里交给本机编解码器：不写录音文件、不上传、不联网，也不用于其它用途；停止或离开页面即释放 `AudioRecord` |
| 文件读写 | 导出 WAV / PNG 与选图、导入 WAV 全走系统文件选择器（SAF），**不申请外部存储读取权限** |
| 解码能力 | FT8 为轻量化实现：完整 LDPC(174,91) + CRC14 + Costas 同步与 77bit 解包，但不含 OSD 深搜、多信号消除与高斯成形，调制为简化连续相位 FSK；SSTV 只支持三种 VIS 模式，用 I/Q 正交鉴频 + 行同步，不做斜率/倾斜校正与自动增益。弱信号灵敏度低于 WSJT-X / QSSTV 等专业软件，界面明确标注 |
| 许可 | 参考了 [FT8CN](https://github.com/N0BOY/FT8CN)（MIT）的设计取舍，以及公开协议文档；FT8 的 `generator.dat` / `parity.dat` 依 QEX 论文 §9 属公有领域。**算法由本项目用 Kotlin 独立实现，未复制任何第三方源码** |

> 注意：Martin M1 整幅图约 108 秒音频、240 行 × 320 像素，解码在后台线程做数字信号处理，
> 低配设备上耗时可能较长，属正常现象。

### 检测更新（GitHub Releases）🆕

| 项目 | 说明 |
|---|---|
| 数据源 | 只读 `GET` GitHub 官方 `releases` 列表（无需密钥、无需登录），按 `prerelease` 字段区分通道；被限流或不可达时自动改用同域名下的 `releases.atom` 备用源（无 REST 限流，但**不含 APK 附件信息、也不含 `prerelease` 字段**，界面会如实标注来源差异与「通道未确认」） |
| 发布通道 | 「关于」页可切换 **正式通道（默认）** 与 **测试通道**，偏好写在本机。正式通道只取非预发布；测试通道包含 Pre-release 并在弹窗标注「预发布」。切换后立即按新通道重新检测，不沿用旧通道查到的日志 |
| 触发方式 | 启动动画结束后自动检测一次，12 小时节流（失败也记时间，避免连不上时反复重试）；「关于」页可随时手动检测，不受节流与「跳过此版本」限制 |
| 有更新时 | 全局弹窗列出当前/最新版本、发布日期、**数据真实来源**、APK 附件体积与 release body 里的完整更新日志（标题 / 列表排版，可滚动）；可「前往下载」「稍后」或「跳过此版本」。即使本机已是最新版，关于页也有「查看 vX.Y 发布说明」入口用同一弹窗查阅日志，标题会明确说明**这不是升级提醒** |
| 下载行为 | 「前往下载」只用浏览器打开 GitHub 发布页，**不自动下载、不自动安装**，提示用户自行核对签名与来源；模拟考试锁定期间不弹窗遮挡试卷 |
| 失败归因 | 网络不通 / GitHub 限流（403、429）/ 当前通道暂无 Release / 上游错误（5xx）/ 响应无法解析，五类分别提示；解析失败不猜版本号，自动检测失败静默只在关于页显示 |
| 隐私 | 只发一次匿名 GET，**不附带账号、设备标识或工具输入**；不申请任何新权限。本地只存上次检测时间、跳过的 tag、上次看到的最新版本号与通道选择，卸载即删除 |

> 版本比较按 tag 里的数字段逐段进行（`v2.0` → `2.0`，`1.10 > 1.9`）。正式通道不提示预发布；
> 测试通道允许预发布。备用源无法确认通道时弹窗会提示自行核对，不冒充已确认的正式版。
> 解析、通道筛选、版本比较与更新日志排版都是无 Android 依赖的纯 JVM 代码
> （`data/update/UpdateChecker.kt`），配 18 项单测覆盖真实响应、双通道筛选、脏数据与 Atom 备用源。
> **当前唯一正式 Release 是 v2.0，与本机一致，因此正式通道会显示「已是最新版本」；
> 可在关于页点「查看 2.0 发布说明」查阅完整更新日志。**

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

### QSL 卡片交换（独立板块，底部 Dock 第 2 页）

不在工具集里注册，而是底部导航的独立顶层页，**必须登录账号才能使用**。分「纸质卡片」与
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

### 业余无线电考试练习（独立板块，底部 Dock 正中央）

> 已随 **v2.0 正式版** 发布。题库、附图与学习记录默认全部在设备本地：不联网、不上传、
> 不需要登录账号；登录后可选地把错题进度同步到账号（需服务端已部署考试云同步补丁）。

题库来自《业余电台操作证书考试题库（v20250728）》的 A / B / C 三类 PDF 与
「总题库附图标记.pdf」。官方格式中 `[I]` 是全局题号、`[Q]` 题干、`[A]`–`[D]` 选项、
`[T]` 答案（多个字母即多选题）；`[J]` / `[P]` 只用于溯源与章节标记。

| 项目 | 实现 |
|---|---|
| 题号去重 | 三类 PDF 按 `[I]` 全局题号合并，共 **1375 题**；A/B 共有题只保留一份（例如 `ABC` 590 题、`BC` 497 题），不会重复抽到 |
| 多选判定 | 以 `[T]` 是否含多个字母为准；漏选、少选、多选一律判错，必须与答案集合完全一致 |
| 附图 | 附图 PDF 共 3 页 / 53 张图，按 `[J]` 编号关联；JSON 存 PDF points 裁切框，App 按 dpi 换算裁切并自动去白边 |
| 组卷规则 | A：32 单 + 8 多 / 40 分钟 / 答对 30 题合格；B：45 单 + 15 多 / 60 分钟 / 45 题；C：70 单 + 20 多 / 90 分钟 / 70 题 |
| 由易到难 | 静态难度由题号顺序、题型、篇幅、数值单位与附图估算；单选/多选各自按难度分层抽题，整卷再按难度升序排列 |
| 艾宾浩斯 | 答对按 5 分钟 → 30 分钟 → 12 小时 → 1 / 2 / 4 / 7 / 15 / 30 天逐级拉长间隔；答错立即回到 5 分钟档重新巩固 |
| 四种模式 | 模拟考试（倒计时、到点自动交卷）、顺序练习、到期复习、错题重练；练习类即时判题并更新记忆曲线 |
| 记录与隐私 | 学习进度、错题本、最近 60 次成绩摘要写入应用私有目录，卸载即删除；界面提供一键清空 |

题库资源由脚本重建（依赖 `workbuddy-pdf` CLI 读取 PDF）：

```bash
sh tools/exam/prepare_exam_sources.sh "<题库PDF所在目录>"
python tools/exam/build_exam_assets.py --dpi 180
```

产出 `app/src/main/assets/exam/questions.json`（约 530 KB）与 `figures/page-1..3.png`。
C 类 PDF 每页换页处会夹带一行独立页码数字，脚本会先删除，否则会混进答案与选项尾部；
附图标记中的 `LK060` 是 `LK0603` 的截断，脚本已做别名映射。
组卷规则、记忆曲线、判分与进度编解码均为纯 JVM 代码并配有单元测试。

> 难度分是按篇幅与章节顺序的**估算值**，只用于「由易到难」排序，不代表官方难度分级。
> 题库内容版权归原发布机构所有，本项目仅作离线练习使用，不提供任何考试通过承诺。

### 已完成的基础设施

- **主题系统**：内置 Google 官方 HCT 色彩算法源码，支持调色盘任意取色、9 种方案变体、
  明暗双套与 Android 12+ 动态取色
- **账号体系**：呼号 + 密码注册登录，密码 bcrypt 哈希、JWT 鉴权，配套可自部署的后端
- **刷新率优化**：突破部分 ROM 对第三方应用的 90Hz 封顶（见[下文](#关于-90hz--120hz)）
- **音频引擎**：基于 `AudioTrack` 的双模式播放（短音一次性写入 / 持续流实时改参），统一 PCM 合成层；
  解码侧另有 `AudioRecord` 采集层（`MicCapture`），只在用户授权并主动开始监听时打开
- **编解码层**：`Dsp` / `WavIo` / `Ft8Codec` / `SstvCodec` 为无 Android 依赖的纯 JVM 代码，
  配 JVM 单测验证 LDPC 校验矩阵、CRC14、帧结构、编码音频闭环解码与 SSTV 整幅图还原
- **更新检测层**：`data/update/` 的 GitHub Release 解析、版本比较与更新日志排版是纯 JVM 代码，
  网络层双源 + 五类失败归因，全局单例 `UpdateController` 负责节流与「跳过此版本」
- **在线数据层**：`HamDataClient` 统一多源请求（HamDB / DXWatch / 服务端聚合），8 秒超时、失败静默降级；
  解析逻辑为纯 JVM 函数并配单元测试，界面始终标注数据来源与实时性
- **QSL 数据层**：`QslApi` / `QslRepository` / `QslViewModel` 三层，JSON 解析与交换状态机是纯 JVM 代码，
  与服务端 `nextStatus` / `ACTION_ACTOR` 保持双端一致；演示与真实模式共用同一套流转规则
- **考试练习数据层**：`data/exam/` 的题库模型、艾宾浩斯调度、分层组卷与判分为纯 JVM 代码，
  assets 题库与附图可离线加载；学习进度写入应用私有目录，不经过服务端
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
- 算法层（`HamMath`、`AprsPasscode`、`AudioSignal`/`DtmfCodec`、`GridMath`、`CwLesson`、
  `SolarModel`、`Ft8Codec`、`SstvCodec`、`WavIo`、`Dsp`、`UpdateChecker`）为无 Android
  依赖的纯函数与纯数据，配套单元测试按手算标杆值校验
- **197 个单元测试**：算法标杆值、M3 配色回归、WCAG AA 对比度、呼号校验、PCM 合成、
  DTMF/Goertzel 编解码、梅登海德网格与大圆路径、科赫课程、内置数据表一致性、
  APRS-IS 验证码公开标杆值、在线数据源的 HamDB JSON / DXWatch HTML 解析与降级、
  考试题库与组卷、QSL 状态机、FT8 LDPC/CRC/帧结构/闭环解码与 SSTV VIS/整幅图还原，
  以及 GitHub Release / Atom 响应解析、正式 / 测试双通道筛选、版本比较与更新日志排版

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
> 未升级不影响工具集中的任何计算、查询与编解码工具。

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
- 或使用随版本本地交付的官网压缩包 `hamtools-site-v2.0.zip`（内含完整 `assets/media/`，不入库）。

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
