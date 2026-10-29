# 📻 HAM 工具合集

面向业余无线电爱好者的 Android 原生工具箱。离线优先，Material Design 3 Expressive 深色主题，
配色严格遵循 M3 规范。

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-green)](./LICENSE)

> ⚠️ **当前为 beta 1.1**。19 个工具入口中 **10 个已完整实现**
> （5 个计算工具 + 5 个查询手册工具），其余 9 个是占位页面，
> 点进去会显示"敬请期待"。请按需取用，也欢迎提 Issue 或 PR。

## 功能现状

### 已实现（10 个）

**计算工具（5 个）**

| 工具 | 说明 |
|---|---|
| 频率波长 | 频率与波长互算，含频段归属提示 |
| 天线长度 | 偶极子、倒 V、垂直天线振子长度计算 |
| 驻波比回损 | VSWR、回波损耗、反射系数三者互算 |
| 馈线损耗 | 按线缆型号与长度估算衰减，采用厂商 7 频点双对数插值（非经验拟合） |
| 链路预算 | 弗里斯传输方程与接收电平推算 |

**查询手册（5 个）**

| 工具 | 说明 |
|---|---|
| 业余频段划分 | 中国与 IARU 三个区的频段划分表，按区域切换与关键词检索 |
| 呼号前缀 | 数百个 DXCC 实体前缀检索，支持 `W1AW/KH6` 这类斜杠后缀解析 |
| Q 简语 | 常用 Q 简语（提问 / 陈述两面）+ ICAO 字母解释法 |
| 摩尔斯对照 | 文字与电码互转，可按键控速度真实发声（正弦波合成） |
| 卫星与中继 | 业余卫星参数与国内常用中继名录 |

### 计划中（9 个占位入口）

- **音频**：CW 电键、CW 听抄训练、DTMF、音调发生器
- **联网**：传播指数、DX 集群、网格与方位、APRS 地图、呼号查询

### 已完成的基础设施

- **主题系统**：内置 Google 官方 HCT 色彩算法源码，支持调色盘任意取色、9 种方案变体、
  明暗双套与 Android 12+ 动态取色
- **账号体系**：呼号 + 密码注册登录，密码 bcrypt 哈希、JWT 鉴权，配套可自部署的后端
- **刷新率优化**：突破部分 ROM 对第三方应用的 90Hz 封顶（见[下文](#关于-90hz--120hz)）
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
- 算法层 `HamMath` 是无 Android 依赖的纯函数，配套单元测试按手算标杆值校验
- **48 个单元测试**：算法标杆值、M3 配色回归、WCAG AA 对比度、呼号校验

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
