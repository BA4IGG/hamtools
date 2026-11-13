# HAM工具合集 · 账号服务端

给 Android 客户端用的呼号账号服务：呼号 + 密码注册登录，密码 bcrypt 哈希入库，登录后签发 JWT。  
技术栈是 Node.js + Express + MySQL，任何能跑 Node 18+ 的机器都能部署。

> App 端已把服务器地址写死为 `https://api.ba4igg.cn`（`AppConfig.SERVER_BASE_URL`），
> 部署时的域名与证书要与之保持一致。

## 一、接口契约

所有接口前缀 `/api`，请求与响应均为 JSON。失败时返回 `{ "error": { "code": "...", "message": "..." } }`。

| 方法    | 路径                   | 鉴权     | 说明                                                         |
| ----- | -------------------- | ------ | ---------------------------------------------------------- |
| GET   | `/api/health`        | 否      | 健康检查，返回 `{ ok: true }`，客户端用它校验服务器地址                        |
| POST  | `/api/auth/register` | 否      | 注册：`{ callsign, password, nickname? }` → `{ token, user }` |
| POST  | `/api/auth/login`    | 否      | 登录：`{ callsign, password }` → `{ token, user }`            |
| GET   | `/api/auth/me`       | Bearer | 用令牌取回用户信息 → `{ user }`                                     |
| PATCH | `/api/auth/profile`  | Bearer | 改昵称：`{ nickname }` → `{ user }`                            |
| POST  | `/api/auth/password` | Bearer | 改密码：`{ oldPassword, newPassword }` → `{ ok: true }`        |
| GET   | `/api/qsl/ping`      | 否      | QSL 能力探测：App 靠它判断服务端是否已部署 QSL 接口                |

### QSL 卡片交换接口

全部挂在 `/api/qsl`，除 `ping` 外**一律要求 Bearer 令牌**。失败同样返回 `{ "error": { "code", "message" } }`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/qsl/me` | 我的名片（含本人邮寄地址）+ 我上传的卡片列表 |
| PUT | `/api/qsl/me` | 保存名片；`displayName / grid / qth / email / address / note / paperEnabled / eqslEnabled / discoverable` |
| DELETE | `/api/qsl/me` | 删除名片、卡片图片与全部交换记录（不删除登录账号） |
| POST | `/api/qsl/cards` | **二进制**上传 JPG / PNG / WebP（≤ 5 MB），模式走 `X-Qsl-Mode: paper \| eqsl` 头 |
| GET | `/api/qsl/cards/:id/image` | 鉴权下载卡片图片，仅本人及已建立交换的对方可见 |
| DELETE | `/api/qsl/cards/:id` | 删除我的一张卡片 |
| GET | `/api/qsl/directory?q=` | 已开放交换的台站名录，**不含地址与邮箱** |
| GET | `/api/qsl/exchanges` | 我的交换记录（即交换历史） |
| POST | `/api/qsl/exchanges` | 发起交换；对方未注册可用 `recordOnly: true` 只留档 |
| PATCH | `/api/qsl/exchanges/:id` | `accept / decline / sent / complete / cancel` 状态流转 |
| GET | `/api/qsl/exchanges/:id/contact` | 交换被接受后，双方才能取到对方邮寄信息 |
| GET | `/api/qsl/summary` | 统计与 `latestIncomingId`，供 App 角标和通知去重 |

状态机：纸质 `pending → accepted → sent → completed`，eQSL `pending → completed`（接受即互换电子卡片）。
`accept / decline` 只有接收方可做，`cancel` 只有发起方可做，`sent / complete` 双方均可。
客户端 `QslStateMachine` 与本文件实现必须保持一致，改动时请同时跑 `npm run qsl:check` 与 App 单测。

**地址可见性**：`address` / `email` 不出现在名录、交换列表等任何列表响应里，只有 `GET /me`（本人）
和交换被接受后的 `GET /exchanges/:id/contact`（参与双方）会返回。卡片图片不放在可匿名访问的静态目录，
需逐个鉴权下载。

`user` 结构：

```json
{ "callsign": "BA4IGG", "nickname": "", "region": "上海、山东、江苏", "createdAt": "2026-10-26T12:00:00.000Z" }
```

错误码：`INVALID_CALLSIGN` / `WEAK_PASSWORD` / `INVALID_NICKNAME` / `CALLSIGN_TAKEN` /  
`BAD_CREDENTIALS` / `NO_TOKEN` / `TOKEN_EXPIRED` / `NO_USER` / `NOT_FOUND` / `SERVER_ERROR`。

呼号按工信部《业余无线电台呼号管理办法》校验：`B` + 种类字母 + 分区数字 + 1~4 位后缀，  
分区数字会自动换算成地区名（1=北京，4=上海、山东、江苏，0=新疆、西藏）。

## 二、部署步骤

完整部署手册（二选一）：

- **[DEPLOY-BT.md](./DEPLOY-BT.md)** —— **宝塔面板**用户看这份：数据库面板建、反代/SSL 面板点
- **[DEPLOY.md](./DEPLOY.md)** —— 纯命令行：Linux 云服务器（Ubuntu），从装 MySQL 到 Nginx + HTTPS + 自动备份

下面是精简版。

1. 建库建表（任选其一）：
   - `mysql -u root -p < schema.sql`（会创建 `hamtools` 库和 `ham_users` 表，以及 QSL 的
     `qsl_profiles` / `qsl_cards` / `qsl_exchanges` 三张表）
   - 或者只建一个空库，服务端启动时会自己 `CREATE TABLE IF NOT EXISTS`（**升级老部署时靠这一步自动补表**）
2. 安装依赖：
   ```bash
   cd server
   npm install
   ```
3. 配置环境变量：
   ```bash
   cp .env.example .env
   # 编辑 .env：填 MySQL 账号密码，并生成一个 JWT_SECRET
   node -e "console.log(require('crypto').randomBytes(48).toString('hex'))"
   ```
4. 启动：
   ```bash
   npm start          # 前台运行
   pm2 start src/index.js --name ham-auth   # 推荐用 pm2 守护
   ```
5. 自检：
   ```bash
   curl http://127.0.0.1:3000/api/health
   # {"ok":true,"service":"ham-tools-auth","time":"..."}

   curl http://127.0.0.1:3000/api/qsl/ping
   # {"ok":true,"feature":"qsl","version":1,"requiresLogin":true}
   # 如果这里返回 404，说明跑的还是旧版服务端，App 会进入「本地演示」模式

   node smoke-test.js   # 不需要 MySQL，用内存假库跑一遍全部接口，18 项检查
   npm run qsl:check    # 不连数据库，检查 QSL 状态机、图片白名单与双端错误码一致性
   ```

   QSL 卡片图片默认写到 `server/uploads/qsl/`（可用 `.env` 的 `QSL_UPLOAD_DIR` 改到别处）。
   目录由服务自动创建，需保证运行 Node 的账号有写权限，**并且不要被 Nginx 配成可匿名访问的静态目录**。
   老部署升级后建议 `pm2 restart ham-auth` / `systemctl restart ham-auth` 触发补建表。
6. 对外开放：建议用 Nginx 反向代理并配 HTTPS 证书。App 端在「我的 → 服务器地址」里填  
   `https://你的域名`（不带 `/api`，客户端会自己拼）。如果用局域网 http 调试，Android 9+  
   默认禁止明文流量，App 已放开 `usesCleartextTraffic`，但请只在内网调试时使用。

## 三、安全说明

- 密码只用 bcrypt 哈希存储，库里没有明文；登录失败时不区分「呼号不存在」和「密码错误」。
- 令牌默认 180 天有效，改密码后旧令牌仍然有效直到过期（如需立即失效，可在 `ham_users` 增加  
  `token_version` 字段并在 `requireAuth` 里比对）。
- 生产环境务必：改掉默认 `JWT_SECRET`、数据库不要用 root、只放行 3000 端口给 Nginx、定期备份  
  `ham_users` 表以及 QSL 三张表。
- QSL 涉及邮寄地址与邮箱这类敏感信息：所有 QSL 接口强制鉴权；名录与列表接口不返回地址/邮箱；
  图片只认真实文件头（JPG / PNG / WebP）且限制 5 MB；`uploads/` 已被 `.gitignore` 忽略，
  **备份与迁移时不要把用户卡片图片提交到公开仓库**。删除资料时会同步删数据库记录与磁盘文件。
- QSL 通知不是服务器推送：App 用前台轮询 + WorkManager 周期任务查询 `summary` 后在本机发通知，
  服务端不保存任何推送令牌或设备标识。

## 四、目录结构

```
server/
├── package.json
├── .env.example
├── .gitignore
├── DEPLOY.md           命令行部署手册
├── DEPLOY-BT.md        宝塔面板部署手册
├── schema.sql          建库建表（服务启动也会自建表）
├── smoke-test.js       无需 MySQL 的接口冒烟测试（18 项）
├── scripts/
│   ├── db-check.js        数据库连通性/权限自检：npm run db:check
│   ├── qsl-selfcheck.js   QSL 纯逻辑与双端契约自检：npm run qsl:check
│   └── backup-db.sh       mysqldump 备份：npm run backup
├── uploads/qsl/        用户上传的 QSL 卡片图片（自动创建，不入库、不公开）
├── deploy/
│   ├── ham-auth.service       systemd 单元
│   └── nginx-ham-auth.conf    Nginx 反代（只代理 /api）
└── src/
    ├── index.js        入口、健康检查、全局错误处理
    ├── db.js           MySQL 连接池与数据访问
    ├── callsign.js     呼号校验与分区换算
    └── routes/
        ├── auth.js     注册 / 登录 / me / 改昵称 / 改密码
        ├── ham.js      DX spots / 呼号在线数据聚合
        └── qsl.js      QSL 名片、卡片图片、交换状态机与统计
```
