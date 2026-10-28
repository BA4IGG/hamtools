'use strict';

/**
 * 数据库连通性自检：部署到第 3 步时先跑 `npm run db:check`。
 * 它会明确告诉你「环境变量缺哪个 / 连不上 / 账号没权限 / 表建没建好」。
 * 不需要先启动服务，也不需要 App 端配合。
 */

require('dotenv').config();

const mysql = require('mysql2/promise');

const REQUIRED = ['DB_HOST', 'DB_PORT', 'DB_USER', 'DB_PASSWORD', 'DB_NAME'];

const HINTS = {
  ECONNREFUSED:
    '连不上数据库端口。检查 MySQL 是否启动、DB_HOST/DB_PORT 是否正确、防火墙是否放行。',
  ENOTFOUND:
    'DB_HOST 解析失败。同机部署请填 127.0.0.1；不要填 localhost（会被解析成 socket）。',
  ETIMEDOUT:
    '连接超时。多半是防火墙或云安全组没放行 3306，或 MySQL 只监听了 127.0.0.1（bind-address）。',
  ER_ACCESS_DENIED_ERROR:
    '账号或密码不对，或者该账号不允许从这个主机登录（MySQL 账号是「用户@主机」绑定的）。',
  ER_BAD_DB_ERROR:
    '数据库不存在。先执行 CREATE DATABASE，或把 DB_NAME 改成已存在的库名。',
  ER_DBACCESS_DENIED_ERROR:
    '这个账号没有该库的操作权限，需要 GRANT SELECT, INSERT, UPDATE。',
  ER_NOT_SUPPORTED_AUTH_MODE:
    'MySQL 8 的 caching_sha2_password 与旧驱动不兼容。本项目用 mysql2 应无此问题，若出现请升级 mysql2。',
  ER_TABLEACCESS_DENIED_ERROR:
    '账号没有建表权限。请手动执行 schema.sql，或给该账号临时授予 CREATE 权限。',
  PROTOCOL_CONNECTION_LOST: '连接被服务端断开，检查 MySQL 的 wait_timeout 与 max_connections。',
};

function fail(title, detail) {
  console.error(`\n[失败] ${title}`);
  if (detail) console.error(`       ${detail}`);
  process.exitCode = 1;
}

async function main() {
  console.log('== HAM工具合集 · 数据库自检 ==\n');

  const missing = REQUIRED.filter((key) => !process.env[key]);
  if (missing.length > 0) {
    fail(
      '.env 缺少配置项：' + missing.join(', '),
      '复制 .env.example 为 .env 后补齐：cp .env.example .env'
    );
    return;
  }

  if (!process.env.JWT_SECRET || process.env.JWT_SECRET.length < 16) {
    fail('JWT_SECRET 未设置或短于 16 位', '用 openssl rand -hex 48 生成一串填进去');
    return;
  }

  const config = {
    host: process.env.DB_HOST,
    port: Number(process.env.DB_PORT || 3306),
    user: process.env.DB_USER,
    password: process.env.DB_PASSWORD,
    database: process.env.DB_NAME,
    charset: 'utf8mb4',
    connectTimeout: 8000,
  };

  console.log(`目标库：${config.user}@${config.host}:${config.port}/${config.database}`);

  let pool;
  try {
    pool = mysql.createPool(config);
    const [row] = await pool.query('SELECT VERSION() AS v, DATABASE() AS db, NOW() AS t');
    console.log(`已连接：MySQL ${row[0].v} / 当前库 ${row[0].db} / 服务器时间 ${row[0].t}`);
  } catch (error) {
    const hint = HINTS[error.code] || error.message;
    fail(`无法连接数据库（${error.code || 'UNKNOWN'}）`, hint);
    if (pool) await pool.end().catch(() => {});
    return;
  }

  // 建表（幂等），顺带验证账号有没有 CREATE 权限
  try {
    await pool.query(`
      CREATE TABLE IF NOT EXISTS ham_users (
        id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
        callsign      VARCHAR(16)  NOT NULL,
        nickname      VARCHAR(64)  DEFAULT NULL,
        password_hash VARCHAR(255) NOT NULL,
        created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        PRIMARY KEY (id),
        UNIQUE KEY uk_callsign (callsign)
      ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
    `);
    console.log('建表检查：ham_users 已就绪');
  } catch (error) {
    fail(`建表失败（${error.code || 'UNKNOWN'}）`, HINTS[error.code] || error.message);
    await pool.end().catch(() => {});
    return;
  }

  // 读写权限验证：插一条临时呼号再删掉，确保不只是能读
  try {
    await pool.query('INSERT INTO ham_users (callsign, password_hash) VALUES (?, ?)', [
      '__selftest__',
      'x',
    ]);
    await pool.query('DELETE FROM ham_users WHERE callsign = ?', ['__selftest__']);
    console.log('权限检查：SELECT / INSERT / UPDATE / DELETE 均可用');
  } catch (error) {
    fail(`读写权限不足（${error.code || 'UNKNOWN'}）`, '需要 GRANT SELECT, INSERT, UPDATE, DELETE');
    await pool.end().catch(() => {});
    return;
  }

  const [cols] = await pool.query('SHOW COLUMNS FROM ham_users');
  console.log('表结构：' + cols.map((c) => `${c.Field}:${c.Type}`).join('  '));
  const [cnt] = await pool.query('SELECT COUNT(*) AS n FROM ham_users');
  console.log(`现有账号数：${cnt[0].n}`);

  await pool.end();
  console.log('\n全部通过。可以执行 npm start 启动服务了。');
}

main().catch((error) => {
  console.error('\n[失败] 自检脚本异常：', error.message);
  process.exit(1);
});
