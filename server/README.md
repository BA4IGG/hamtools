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
   - `mysql -u root -p < schema.sql`（会创建 `hamtools` 库和 `ham_users` 表）
   - 或者只建一个空库，服务端启动时会自己 `CREATE TABLE IF NOT EXISTS`
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

   node smoke-test.js   # 不需要 MySQL，用内存假库跑一遍全部接口，18 项检查
   ```
6. 对外开放：建议用 Nginx 反向代理并配 HTTPS 证书。App 端在「我的 → 服务器地址」里填  
   `https://你的域名`（不带 `/api`，客户端会自己拼）。如果用局域网 http 调试，Android 9+  
   默认禁止明文流量，App 已放开 `usesCleartextTraffic`，但请只在内网调试时使用。

## 三、安全说明

- 密码只用 bcrypt 哈希存储，库里没有明文；登录失败时不区分「呼号不存在」和「密码错误」。
- 令牌默认 180 天有效，改密码后旧令牌仍然有效直到过期（如需立即失效，可在 `ham_users` 增加  
  `token_version` 字段并在 `requireAuth` 里比对）。
- 生产环境务必：改掉默认 `JWT_SECRET`、数据库不要用 root、只放行 3000 端口给 Nginx、定期备份  
  `ham_users` 表。

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
│   ├── db-check.js     数据库连通性/权限自检：npm run db:check
│   └── backup-db.sh    mysqldump 备份：npm run backup
├── deploy/
│   ├── ham-auth.service       systemd 单元
│   └── nginx-ham-auth.conf    Nginx 反代（只代理 /api）
└── src/
    ├── index.js        入口、健康检查、全局错误处理
    ├── db.js           MySQL 连接池与数据访问
    ├── callsign.js     呼号校验与分区换算
    └── routes/auth.js  注册 / 登录 / me / 改昵称 / 改密码
```
