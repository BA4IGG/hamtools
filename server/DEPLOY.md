# 账号数据库 + 服务端部署步骤（Linux 云服务器 · 纯命令行）

面向一台全新的 Linux 云服务器，从零到 App 能登录。示例系统 **Ubuntu 22.04 / 24.04**（CentOS 只需把 `apt` 换成 `dnf`/`yum`、服务名换成 `mysqld`，其余一致）。

> **如果你用宝塔面板，请改用 [DEPLOY-BT.md](./DEPLOY-BT.md)**——数据库用面板建、反代和证书在面板点，步骤更贴合。

全篇约定：

| 约定值  | 说明                           |
| ---- | ---------------------------- |
| 代码目录 | `/opt/hamtools/server`       |
| 数据库  | 库名 `hamtools`，账号 `hamtools`  |
| 服务端口 | `3000`（仅本机监听，对外只走 Nginx 443） |
| 域名   | `api.yourdomain.com`（换成你自己的） |
| 运行用户 | `hamtools`（不用 root 跑服务）      |

---

## 第 0 步：准备清单

- [ ] 一台云服务器（1 核 1G 就够，账号服务开销极小）
- [ ] 一个已备案（国内云）并解析到该服务器公网 IP 的域名，例如 `api.yourdomain.com` → A 记录指向服务器
- [ ] 云控制台安全组放行：**22 / 80 / 443**。数据库端口 **3306 不要对公网开放**

---

## 第 1 步：安装 Node.js 18+

```bash
sudo apt update
curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -
sudo apt install -y nodejs
node -v    # 需 >= 18，示例 v22.x
```

## 第 2 步：安装 MySQL（两条路线，选一条）

### 路线 A：服务器自建 MySQL 8（推荐，零额外成本）

```bash
sudo apt install -y mysql-server
sudo systemctl enable --now mysql
sudo systemctl status mysql --no-pager   # 看到 active (running) 才算好

# 安全初始化：一路 y，设置 root 密码，删除匿名用户、禁止 root 远程登录、删 test 库
sudo mysql_secure_installation
```

### 路线 B：用云厂商 RDS MySQL

1. 控制台买一个 MySQL 8.0 实例（最低配即可），字符集选 **utf8mb4 / utf8mb4_unicode_ci**
2. 白名单里加上你这台云服务器的 **内网 IP**
3. 记下内网连接地址、端口、账号密码，第 3 步的 `DB_HOST` 填那个内网地址，其余步骤一样

> 路线 B 更省心（自动备份、不用自己维护），但要花一点钱。路线 A 免费但需要自己备份（见第 11 步）。

顺手把服务器时区设成国内时间，避免 `created_at` 记录的是 UTC：

```bash
sudo timedatectl set-timezone Asia/Shanghai
```

## 第 3 步：建库、建专用账号、导表结构

**不要用 root 给 App 连库。** 用 root 登录 MySQL，建一个只有本库权限的账号：

```bash
# 先生成一个数据库密码，复制下来备用
openssl rand -base64 24 | tr -d '/+=' ; echo

sudo mysql -u root -p
```

进入 MySQL 提示符后执行（把 `'这里粘贴上一步生成的密码'` 换成真密码）：

```sql
CREATE DATABASE hamtools
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- 同机部署用 'hamtools'@'127.0.0.1'；如果把 MySQL 和 Node 分开部署，把 127.0.0.1 换成 Node 机器的 IP
CREATE USER 'hamtools'@'127.0.0.1' IDENTIFIED BY '这里粘贴上一步生成的密码';

-- 服务只做这些操作，按最小权限授予
GRANT SELECT, INSERT, UPDATE, DELETE ON hamtools.* TO 'hamtools'@'127.0.0.1';
FLUSH PRIVILEGES;
EXIT;
```

导入表结构：

```bash
cd /opt/hamtools/server
sudo mysql -u root -p hamtools < schema.sql
```

验证点（应看到 `ham_users` 一行）：

```bash
mysql -u hamtools -p hamtools -e "SHOW TABLES;"
```

> 其实跳过导表也行——服务启动时会自动 `CREATE TABLE IF NOT EXISTS`。但手动导一遍能在启动前就确认账号有建表/写权限，排错更快。

## 第 4 步：放代码 + 写 .env

```bash
sudo mkdir -p /opt/hamtools
sudo chown -R $USER:$USER /opt/hamtools
cd /opt/hamtools
# 任选一种方式把仓库的 server/ 目录放到 /opt/hamtools/server
#   git clone <你的仓库> hamtools
#   或本地：rsync -av ./server/ user@ip:/opt/hamtools/server/
```

配置：

```bash
cd /opt/hamtools/server
cp .env.example .env

# 生成 JWT 密钥
openssl rand -hex 48
nano .env
```

`.env` 里必须改的四项：

```ini
DB_HOST=127.0.0.1
DB_PORT=3306
DB_USER=hamtools
DB_PASSWORD=第3步生成的密码
DB_NAME=hamtools
JWT_SECRET=上一步 openssl rand -hex 48 的输出
```

其余保持默认：`PORT=3000`、`JWT_EXPIRES_IN=180d`、`BCRYPT_ROUNDS=10`（CPU 很弱的机器可降到 8）。

权限收紧（.env 里有密码）：

```bash
chmod 600 .env
```

## 第 5 步：装依赖 + 数据库自检

```bash
npm install --omit=dev
npm run db:check
```

`db:check` 会逐项打印结果，全通过应看到：

```
目标库：hamtools@127.0.0.1:3306/hamtools
已连接：MySQL 8.0.xx / 当前库 hamtools / 服务器时间 ...
建表检查：ham_users 已就绪
权限检查：SELECT / INSERT / UPDATE / DELETE 均可用
表结构：id:bigint unsigned  callsign:varchar(16)  nickname:varchar(64)  password_hash:varchar(255) ...
现有账号数：0

全部通过。可以执行 npm start 启动服务了。
```

任何一项失败，它会直接给出原因和修法（缺环境变量 / 连不上 / 密码错 / 没权限），照着改完重跑即可。

## 第 6 步：用 systemd 托管服务

建运行用户并授权：

```bash
sudo useradd -r -s /usr/sbin/nologin hamtools
sudo chown -R hamtools:hamtools /opt/hamtools
```

写服务单元（仓库里已备好，直接复制）：

```bash
sudo cp /opt/hamtools/server/deploy/ham-auth.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now ham-auth
sudo systemctl status ham-auth --no-pager
```

看到 `Active: active (running)` 且日志里有 `HAM工具合集账号服务已启动` 即成功。

常用命令：

```bash
sudo journalctl -u ham-auth -f        # 实时看日志
sudo systemctl restart ham-auth       # 改完 .env 后重启
```

> 想用 pm2 也行：`sudo npm i -g pm2 && pm2 start src/index.js --name ham-auth && pm2 save && pm2 startup`。  
> 但 systemd 不需要额外装东西，云机上更省事。

## 第 7 步：Nginx 反代 + Let's Encrypt 证书

```bash
sudo apt install -y nginx
sudo cp /opt/hamtools/server/deploy/nginx-ham-auth.conf /etc/nginx/sites-available/ham-auth
sudo sed -i 's/api.yourdomain.com/你的真实域名/g' /etc/nginx/sites-available/ham-auth
sudo ln -s /etc/nginx/sites-available/ham-auth /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

签发证书（会自动改写配置加上 HTTPS 与自动续期）：

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d api.yourdomain.com
sudo certbot renew --dry-run    # 确认自动续期可用
```

验证点：

```bash
curl -i https://api.yourdomain.com/api/health
# HTTP/2 200 + {"ok":true,"service":"ham-tools-auth","time":"..."}
```

## 第 8 步：防火墙

```bash
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw --force enable
sudo ufw status
```

同时确认云控制台安全组里 **3306 没有对 0.0.0.0/0 放行**。数据库只允许本机或内网访问。

## 第 9 步：端到端验证（真的注册一个号）

```bash
# 1) 本机直连
curl -s http://127.0.0.1:3000/api/health ; echo

# 2) 走域名注册一个测试账号
curl -s -X POST https://api.yourdomain.com/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"callsign":"BA4IGG","password":"Ham@12345","nickname":"测试"}'
# 返回 {"token":"eyJ...","user":{"callsign":"BA4IGG",...}}

# 3) 用这个 token 取回用户信息（把 TOKEN 换成上一步返回值）
TOKEN=eyJ...
curl -s https://api.yourdomain.com/api/auth/me -H "Authorization: Bearer $TOKEN"

# 4) 进库确认存的是 bcrypt 哈希，不是明文
mysql -u hamtools -p hamtools -e \
  "SELECT callsign, nickname, LEFT(password_hash,7) AS algo, created_at FROM ham_users;"
# algo 应为 $2a$10$ 或 $2b$10$

# 5) 再注册同一个呼号，应报 CALLSIGN_TAKEN
curl -s -X POST https://api.yourdomain.com/api/auth/register \
  -H 'Content-Type: application/json' -d '{"callsign":"BA4IGG","password":"Ham@12345"}'
```

第 4 条出现 `$2a$10$` / `$2b$10$` 开头，说明密码哈希入库正确。测试完可以 `DELETE FROM ham_users WHERE callsign='BA4IGG';` 清掉。

## 第 10 步：域名必须匹配 App 里写死的地址

App 端已经把账号服务器地址**写死**了：`https://api.ba4igg.cn`，位置在
`app/src/main/java/com/ham/tools/data/AppConfig.kt` 的 `SERVER_BASE_URL` 常量，
「我的」页不再提供修改入口。所以：

- 这台机器上的站点域名（以及证书）**必须用 `api.ba4igg.cn`**，并把它解析到本机公网 IP
- 换域名或换端口都要同步改 `AppConfig.SERVER_BASE_URL` 再重新编译 App
- 地址不带结尾斜杠、不带 `/api`，请求路径由 App 内部拼接

网络没问题的话，打开 App →「我的」→ 登录/注册即可连通，不需要再做任何配置。

## 第 11 步：每日自动备份

仓库已备好脚本，加到 crontab：

```bash
chmod +x /opt/hamtools/server/scripts/backup-db.sh
sudo mkdir -p /data/backup && sudo chown hamtools:hamtools /data/backup
sudo crontab -u hamtools -e
```

写入一行（每天凌晨 3 点备份，保留 14 天）：

```
0 3 * * * cd /opt/hamtools/server && BACKUP_DIR=/data/backup /usr/bin/bash scripts/backup-db.sh >> /var/log/hamtools-backup.log 2>&1
```

手动跑一次确认：

```bash
sudo -u hamtools BACKUP_DIR=/data/backup bash /opt/hamtools/server/scripts/backup-db.sh
ls -lh /data/backup
```

恢复：

```bash
gunzip < /data/backup/hamtools-20261026-030000.sql.gz | mysql -u hamtools -p hamtools
```

---

## 排错对照表

| 现象                                     | 原因                             | 处理                                                                     |
| -------------------------------------- | ------------------------------ | ---------------------------------------------------------------------- |
| `npm run db:check` 报 ECONNREFUSED      | MySQL 没起或端口不对                  | `sudo systemctl status mysql`，确认 `DB_PORT=3306`                        |
| 报 ER_ACCESS_DENIED_ERROR               | 密码错，或账号主机不匹配                   | MySQL 账号是「用户@主机」绑定的，同机必须用 `'hamtools'@'127.0.0.1'`；若用 RDS 要在白名单加服务器 IP |
| 报 ER_BAD_DB_ERROR                      | 库不存在                           | 执行 `CREATE DATABASE hamtools ...`                                      |
| 报 ER_DBACCESS_DENIED_ERROR             | 没授权                            | 重跑第 3 步的 `GRANT` + `FLUSH PRIVILEGES`                                  |
| 启动即退出：`请在 .env 中设置至少 16 位的 JWT_SECRET` | JWT_SECRET 太短或没填               | `openssl rand -hex 48` 重新生成                                            |
| 服务起来了但 App 连不上                         | 安全组/防火墙没开 443，或 Nginx 没 reload | `sudo ufw status`、`curl -i https://域名/api/health`                      |
| App 提示「无法连接服务器」但浏览器能打开                 | 域名证书链不全，或填了 http               | 用 https；`curl -vI` 看证书                                                 |
| 注册一直转圈                                 | bcrypt 轮数太高，CPU 打满             | `.env` 里 `BCRYPT_ROUNDS=8` 后重启                                         |
| 日志 `unhandled error: ER_NO_SUCH_TABLE` | 账号没建表权限                        | 手动 `mysql -u root -p hamtools < schema.sql`                            |
| 手机端登录状态掉了                              | 服务器时间漂移导致 JWT 判定过期             | `sudo timedatectl set-ntp on`                                          |

---

## 日常运维

- **改配置**：`sudo nano /opt/hamtools/server/.env` → `sudo systemctl restart ham-auth`
- **更新代码**：覆盖 `server/src/` → `sudo systemctl restart ham-auth`
- **看日志**：`sudo journalctl -u ham-auth -f`
- **数据库迁移**：当前没有迁移机制，`ham_users` 结构变了就手写 `ALTER TABLE`；`db.js` 用的是 `CREATE TABLE IF NOT EXISTS`，加字段要自己补 SQL
- **让旧令牌立即失效**：给 `ham_users` 加 `token_version INT DEFAULT 0`，在 `routes/auth.js` 的 `requireAuth` 里比对，改密码时 +1（默认改密码后旧令牌在 180 天内仍有效）
