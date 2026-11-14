# 宝塔面板部署步骤（BT Panel）

这份是给**宝塔面板**用的。数据库用面板创建、反代和证书在面板点、Node 服务用 systemd 或 PM2 管理器托管。

> 不走宝塔的纯命令行版本见 [DEPLOY.md](./DEPLOY.md)。

全篇约定：

| 约定值 | 说明 |
|---|---|
| 代码目录 | `/www/wwwroot/ham-auth`（把仓库 `server/` 的内容放进来） |
| 数据库 | 库名 `hamtools`，账号由面板生成 |
| Node 端口 | `3000`（只本机监听，对外只走 Nginx 443） |
| 域名 | `api.yourdomain.com`（换成你自己的） |

---

## 第 0 步：准备

- [ ] 域名已解析到服务器公网 IP（宝塔建站和签证书都要它）
- [ ] 云服务器安全组放行：**80 / 443 / 22 / 宝塔面板端口**（面板端口在 `bt` 命令里能看到）
- [ ] **3306 不要放行**，数据库只在服务器内部访问

---

## 第 1 步：软件商店装环境

面板左侧 **软件商店** → 已安装里确认有：

| 软件 | 版本要求 | 没有就装 |
|---|---|---|
| Nginx | 任意（1.20+） | 软件商店搜 Nginx 安装 |
| MySQL | 8.0 或 5.7；**已有的 MariaDB 10.6+ 也能用** | 搜 MySQL 安装（编译约 20–40 分钟，耐心等） |
| PM2管理器 | 可选 | 见第 7 步，不是必须 |

> 不用装 PHP。建站时 PHP 版本选「纯静态」。

---

## 第 2 步：创建数据库

面板左侧 **数据库** → **添加数据库**：

- 数据库名：`hamtools`
- 用户名：面板会自动填成和库名一样（`hamtools`），保持默认即可
- 密码：点「随机」生成一串强密码，**复制保存好**
- 字符集：**utf8mb4**

创建完在列表里能看到库名、用户名。点「管理」可进 phpMyAdmin（后面查数据用）。

---

## 第 3 步：确认 Node 能用 TCP 连上数据库（关键，别跳过）

宝塔建的数据库账号绑定的主机是 `localhost`。Node 的 mysql2 走的是 TCP（127.0.0.1），
多数情况下能匹配上，但一旦 MySQL 开了 `skip_name_resolve` 就会被拒。保险起见，直接补一个
明确允许 `127.0.0.1` 的账号。

面板左侧 **终端**（或 SSH）执行：

```bash
# 宝塔的 MySQL root 密码：面板 → 数据库 → 点「root密码」可查看/重置
mysql -u root -p
```

进入 MySQL 后执行（`密码换成第2步那个`）：

```sql
-- 如果面板已建过同名账号，这行会报已存在，忽略即可
CREATE USER IF NOT EXISTS 'hamtools'@'127.0.0.1' IDENTIFIED BY '第2步的密码';
CREATE USER IF NOT EXISTS 'hamtools'@'localhost' IDENTIFIED BY '第2步的密码';

GRANT SELECT, INSERT, UPDATE, DELETE ON hamtools.* TO 'hamtools'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE, DELETE ON hamtools.* TO 'hamtools'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

验证（换成你的密码）：

```bash
mysql -u hamtools -p -h 127.0.0.1 hamtools -e "SELECT 'ok' AS result;"
```

看到 `ok` 就说明账号没问题了。

> 顺手把服务器时区设成国内时间，避免注册时间记成 UTC：
> 面板 → 终端执行 `timedatectl set-timezone Asia/Shanghai`。

---

## 第 4 步：上传代码

面板左侧 **文件** → 进入 `/www/wwwroot/` → **新建目录** `ham-auth` → 进入该目录 →
**上传** 一个 zip（把仓库 `server/` 里的所有文件打包，含 `src/`、`scripts/`、`deploy/`、`schema.sql`、
`package.json`、`.env.example`）→ 右键 **解压** → 确认解压后 `package.json` 直接在
`/www/wwwroot/ham-auth/` 下，而不是多一层 `server/` 目录。

> 服务器上装了 git 的话也可以：终端 `cd /www/wwwroot && git clone <你的仓库> ham-auth`。

---

## 第 5 步：写 .env

同名目录下，右键 `.env.example` → 复制 → 重命名为 `.env`（或者终端执行）：

```bash
cd /www/wwwroot/ham-auth
cp .env.example .env
openssl rand -hex 48     # 生成 JWT 密钥，复制输出
```

点 `.env` → **编辑**，改成这样（`DB_PASSWORD` 用第 2 步面板生成的那个）：

```ini
PORT=3000

DB_HOST=127.0.0.1
DB_PORT=3306
DB_USER=hamtools
DB_PASSWORD=第2步的密码
DB_NAME=hamtools

JWT_SECRET=上面 openssl 生成的那串
JWT_EXPIRES_IN=180d
BCRYPT_ROUNDS=10
```

保存后把权限收紧（密码明文在里面）：

```bash
chmod 600 /www/wwwroot/ham-auth/.env
```

---

## 第 6 步：装依赖 + 数据库自检

面板 → **终端**：

```bash
cd /www/wwwroot/ham-auth
npm install --omit=dev
npm run db:check
```

全通过应看到：

```
目标库：hamtools@127.0.0.1:3306/hamtools
已连接：MySQL 8.0.xx / 当前库 hamtools / 服务器时间 ...
建表检查：ham_users 已就绪
权限检查：SELECT / INSERT / UPDATE / DELETE 均可用
表结构：id:bigint unsigned  callsign:varchar(16)  nickname:varchar(64)  password_hash:varchar(255) ...
现有账号数：0

全部通过。可以执行 npm start 启动服务了。
```

脚本会自动建表，所以不用手动导 `schema.sql`。任何一项失败，它会直接给出中文原因和修法。

---

## 第 7 步：启动 Node 服务（两种方式，选一个）

### 方式 A：systemd（推荐，不依赖面板插件）

终端执行：

```bash
useradd -r -s /usr/sbin/nologin hamtools
chown -R hamtools:hamtools /www/wwwroot/ham-auth

# 复用仓库里的 unit，把路径换成宝塔目录
sed 's#/opt/hamtools/server#/www/wwwroot/ham-auth#g' \
  /www/wwwroot/ham-auth/deploy/ham-auth.service > /etc/systemd/system/ham-auth.service

systemctl daemon-reload
systemctl enable --now ham-auth
systemctl status ham-auth --no-pager
```

看到 `Active: active (running)` 且日志里有 `HAM工具合集账号服务已启动` 即成功。

- 看日志：`journalctl -u ham-auth -f`
- 改完 `.env` 重启：`systemctl restart ham-auth`

### 方式 B：PM2管理器（可视化）

软件商店装 **PM2管理器** → 打开 → **添加项目**：

- 项目名称：`ham-auth`
- 运行目录：`/www/wwwroot/ham-auth`
- 启动文件：`src/index.js`
- 项目端口：`3000`
- 其余默认 → 提交

提交后它会自动 `npm install` 并启动。注意：PM2 管理器不会去读 `.env`，如果它提供了
「环境变量」/「Env」输入框，就把第 5 步那几项逐个填进去（我们的代码里 `require('dotenv').config()`
会读 `.env` 文件，所以**只要 `.env` 在目录下通常就够了**，填了也不冲突）。

确认端口在听：

```bash
ss -lntp | grep 3000
curl -s http://127.0.0.1:3000/api/health ; echo
```

---

## 第 8 步：建站点 + 反向代理

**网站** → **添加站点**：

- 域名：`api.yourdomain.com`
- 数据库：**不创建**
- PHP版本：**纯静态**
- 根目录：`/www/wwwroot/ham-auth`（随便，反代用不到）

建好后点该站点的 **设置** → **反向代理** → **添加反向代理**：

- 代理名称：`ham-auth`
- 目标 URL：`http://127.0.0.1:3000`
- 发送域名：`$host`
- **代理目录：`/api`** ← 关键，填 `/` 会把整站暴露给 Node
- 提交

确认生成的配置正确（终端执行）：

```bash
cat /www/server/panel/vhost/nginx/proxy/*.conf | grep -n "proxy_pass\|location"
```

应看到类似 `location ^~ /api {` 和 `proxy_pass http://127.0.0.1:3000;`（**末尾不要带 `/`**，
否则 `/api/health` 会被砍成 `/health`）。带上了就手动改掉再重载 Nginx。

---

## 第 9 步：申请 SSL

站点 **设置** → **SSL** → **Let's Encrypt** → 选你的域名 → **申请**。
申请完打开 **强制 HTTPS**。

验证：

```bash
curl -i https://api.yourdomain.com/api/health
# HTTP/2 200 + {"ok":true,"service":"ham-tools-auth","time":"..."}
```

---

## 第 10 步：端到端验证

终端执行：

```bash
# 1) 走域名注册一个测试账号
curl -s -X POST https://api.yourdomain.com/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"callsign":"BA4IGG","password":"Ham@12345","nickname":"测试"}'
# 返回 {"token":"eyJ...","user":{...}}

# 2) 带令牌取用户信息
TOKEN=上一步的token
curl -s https://api.yourdomain.com/api/auth/me -H "Authorization: Bearer $TOKEN"

# 3) 进库确认存的是 bcrypt 哈希，不是明文
mysql -u hamtools -p -h 127.0.0.1 hamtools -e \
  "SELECT callsign, nickname, LEFT(password_hash,7) AS algo, created_at FROM ham_users;"
# algo 必须是 $2a$10$ 或 $2b$10$

# 4) 重复注册应报 CALLSIGN_TAKEN
curl -s -X POST https://api.yourdomain.com/api/auth/register \
  -H 'Content-Type: application/json' -d '{"callsign":"BA4IGG","password":"Ham@12345"}'
```

第 3 条出现 `$2a$10$` / `$2b$10$` 开头，说明密码哈希入库正确。测完可
`DELETE FROM ham_users WHERE callsign='BA4IGG';` 清掉。

App 端已经把账号服务器地址**写死**为 `https://api.ba4igg.cn`，位置在
`app/src/main/java/com/ham/tools/data/AppConfig.kt` 的 `SERVER_BASE_URL` 常量，
「我的」页不再提供修改入口。所以：

- 面板里建的站点域名就是 **`api.ba4igg.cn`**（把第 0 步和第 8 步里的占位域名换成它并解析到本机）
- 证书也要签这个域名
- 要换别的域名/路径，改 `AppConfig.SERVER_BASE_URL` 后重新编译 App；地址不带结尾斜杠、不带 `/api`

一切就绪后，打开 App →「我的」→ 登录/注册即可连通，App 端不需要再做任何设置。

---

## 第 11 步：数据库自动备份

面板左侧 **计划任务** → **添加任务**：

- 任务类型：**备份数据库**
- 执行周期：每天（推荐凌晨 3 点）
- 备份数据库：选 `hamtools`
- 保留份数：`14`
- 备份到：默认本机磁盘；有 OSS/COS 插件的话建议选云端

保存后点「执行」跑一次，确认生成了 `.sql.gz` 文件。

> 也可以用仓库脚本：`cd /www/wwwroot/ham-auth && BACKUP_DIR=/www/backup bash scripts/backup-db.sh`
> （计划任务类型选 shell 脚本，保留 `KEEP_DAYS=14` 自动清理）。

---

## 宝塔专属排错表

| 现象 | 原因 | 处理 |
|---|---|---|
| `npm run db:check` 报 ER_ACCESS_DENIED_ERROR | 面板建的账号是 `user@localhost`，TCP 连不上 | 执行第 3 步，补 `'hamtools'@'127.0.0.1'` |
| 报 ER_BAD_DB_ERROR | 库名写错 | 面板 → 数据库 里确认真实库名，填进 `DB_NAME` |
| 面板里没找到 MySQL root 密码 | 宝塔隐藏了 | 数据库 → 点「root密码」查看/重置 |
| 访问域名 502 Bad Gateway | Node 没起来或端口不对 | `ss -lntp \| grep 3000`；`systemctl status ham-auth` |
| `/api/health` 返回 404 且响应是宝塔默认页 | 反代没生效，代理目录填错 | 站点 → 反向代理 检查是 `/api`；看 `/www/server/panel/vhost/nginx/proxy/*.conf` |
| `proxy_pass http://127.0.0.1:3000/;` 多了尾斜杠 | 宝塔自动生成时可能带上 | 手动删掉尾斜杠 → `nginx -t` → 重载 |
| 面板提示「站点已存在」 | 域名被别的站点占了 | 先删旧站点或换个子域名 |
| SSL 申请失败 | 域名没解析到本机，或 80 端口被墙 | `ping 域名` 确认解析；安全组放行 80 |
| 开了「强制HTTPS」后 App 连不上 | App 里填的是 http | App 端改成 `https://` |
| 装了「Nginx防火墙/网站监控报表」插件后 POST 被拦 | WAF 规则误杀 JSON | 防火墙插件 → 规则里临时关闭 URL 过滤验证一下 |
| 注册一直转圈 | 小机器 bcrypt 轮数太高 | `.env` 改 `BCRYPT_ROUNDS=8` 后重启服务 |
| App 的 QSL 板块显示「本地演示模式」 | 线上还是旧版服务端，`/api/qsl/ping` 返回 404 | 按上节覆盖 `src/` 并重启，确认 ping 返回 `ok:true` |
| QSL 卡片上传失败 | 二进制 POST 被防火墙插件拦截，或 `uploads/qsl/` 不可写 | 暂时关闭 URL 过滤验证；检查目录属主与 `client_max_body_size` |
| 考试练习显示「服务端尚未部署学习记录接口」 | 线上没有 `/api/exam` 路由 | 覆盖 `src/routes/exam.js`、`src/db.js`、`src/index.js` 后重启；App 仍可纯本机使用 |
| 学习记录同步报 413 | Nginx `client_max_body_size` 太小（进度 JSON 可达几百 KB） | 站点配置里调到 `1m` 以上；服务端自身已放宽到 1 MB |

---

## 升级到含 QSL 交换的版本

App 靠 `GET /api/qsl/ping` 判断服务端版本。老服务端没这个路由，App 会进入明确标注的
「本地演示」模式，交换记录不会真正入库。升级步骤：

1. 文件管理器把仓库里新的 `server/src/`（尤其是 `routes/qsl.js`、`db.js`、`index.js`）
   覆盖到 `/www/wwwroot/ham-auth/src/`，保留原 `.env`。
2. 终端执行 `systemctl restart ham-auth`，或在 PM2 管理器里点「重启」。
   服务启动时会自动 `CREATE TABLE IF NOT EXISTS` 补建 `qsl_profiles` / `qsl_cards` / `qsl_exchanges`。
3. 浏览器或终端验证：
   ```bash
   curl https://api.ba4igg.cn/api/qsl/ping
   # {"ok":true,"feature":"qsl","version":1,"requiresLogin":true}
   # 若返回 404 或宝塔默认页：检查反代是否只代理了 /api，以及跑的是不是新代码
   ```
4. 检查 `/www/wwwroot/ham-auth/uploads/qsl/` 已自动创建且 Node 运行账号可写（PM2 管理器里看运行用户）。

> ⚠️ 卡片图片属于个人信息：站点设置里**不要**把 `uploads/` 配成静态可访问目录，
> 图片只能走带令牌的 `GET /api/qsl/cards/:id/image`。若开了 Nginx 防火墙，
> 确认二进制 POST 上传（≤ 5 MB）没被 WAF 的 URL / 上传规则误拦，必要时把站点
> `client_max_body_size` 调到 8m 以上。

---

## 升级到含「考试练习云同步」的版本（可选增强）

App 的考试练习**不登录也能完整使用**：题库、附图、组卷、复习进度、错题本与成绩历史
默认都存本机。登录账号后才会调用新接口，把这些摘要同步到账号；服务端没部署时
界面会如实显示「仅本机」，不会把本地数据伪装成已上云。

1. 覆盖这三个文件（保留原 `.env`）：
   - `src/routes/exam.js`（新增）
   - `src/db.js`（新增 `exam_progress` 建表）
   - `src/index.js`（挂载 `/api/exam`，单独放宽 JSON body 到 1 MB）
   另外把 `schema.sql`、`scripts/exam-selfcheck.js`、`package.json` 一并更新，便于手工部署与自检。
2. 终端执行 `systemctl restart ham-auth`（或 PM2 里点「重启」）。
   启动时的 `db.ensureSchema()` 会自动补建 `exam_progress` 表，**不需要手工导入 SQL**。
3. 验证（把路径换成你实际的项目目录；本文其它章节用 `ham-auth` 作示例，
   你当前这台机器是 `/www/wwwroot/hamtools_auth`）：
   ```bash
   curl https://api.ba4igg.cn/api/exam/ping
   # {"ok":true,"feature":"exam-sync","version":2,"requiresLogin":true}
   cd /www/wwwroot/hamtools_auth && npm run exam:check
   # 考试练习云同步自检通过：6 项
   cd /www/wwwroot/hamtools_auth && npm run qsl:check
   # 全部通过：7 项检查
   ```

> 两个自检**只读取服务端目录内的文件**，不需要 Android 工程源码，也不需要连数据库。
> 生产机上会打印一行「未检测到 Android 源码，跳过跨端核对」，这是正常提示，不是失败。
> 若旧版自检报 `ENOENT ... app/src/main/java/...`，只需覆盖
> `scripts/exam-selfcheck.js` 与 `scripts/qsl-selfcheck.js` 两个文件，**不用重启服务**。

4. App 端进入考试练习 → 「账号同步」卡片点「立即同步」，状态变为「已同步」即生效。

> 该表只存题号进度、复习数字、顺序练习断点与成绩摘要，不含题目正文、
> 用户所选答案或任何个人资料；接口全部 `requireAuth`。若站点开了 Nginx 防火墙，
> 确认 `PUT /api/exam/progress` 的 JSON 请求没有被规则误拦。

---

## 日常运维（宝塔）

- 改配置：文件管理器改 `.env` → 终端 `systemctl restart ham-auth`（或 PM2 里重启）
- 看日志：终端 `journalctl -u ham-auth -f`；或 PM2 管理器点「日志」
- 更新代码：覆盖 `src/` → 重启服务
- 查账号：面板 → 数据库 → 管理（phpMyAdmin）→ `ham_users` 表

