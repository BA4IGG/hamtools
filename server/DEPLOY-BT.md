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

---

## 日常运维（宝塔）

- 改配置：文件管理器改 `.env` → 终端 `systemctl restart ham-auth`（或 PM2 里重启）
- 看日志：终端 `journalctl -u ham-auth -f`；或 PM2 管理器点「日志」
- 更新代码：覆盖 `src/` → 重启服务
- 查账号：面板 → 数据库 → 管理（phpMyAdmin）→ `ham_users` 表
