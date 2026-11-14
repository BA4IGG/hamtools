'use strict';

const mysql = require('mysql2/promise');

const pool = mysql.createPool({
  host: process.env.DB_HOST || '127.0.0.1',
  port: Number(process.env.DB_PORT || 3306),
  user: process.env.DB_USER || 'root',
  password: process.env.DB_PASSWORD || '',
  database: process.env.DB_NAME || 'hamtools',
  waitForConnections: true,
  connectionLimit: 10,
  queueLimit: 0,
  charset: 'utf8mb4',
});

/**
 * 建表：首次启动时执行，已存在则跳过。
 * 这样部署时不必手动导入 schema.sql。
 */
async function ensureSchema() {
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

  // QSL 交换：资料、卡片图片、交换记录。三张表都通过 user_id 关联账号，
  // 删除账号资料时会一并清理，不留存孤立的个人信息。
  await pool.query(`
    CREATE TABLE IF NOT EXISTS qsl_profiles (
      user_id       BIGINT UNSIGNED NOT NULL,
      callsign      VARCHAR(16)  NOT NULL,
      display_name  VARCHAR(64)  NOT NULL DEFAULT '',
      grid          VARCHAR(8)   NOT NULL DEFAULT '',
      qth           VARCHAR(128) NOT NULL DEFAULT '',
      email         VARCHAR(128) NOT NULL DEFAULT '',
      address       TEXT,
      note          VARCHAR(255) NOT NULL DEFAULT '',
      paper_enabled TINYINT(1)   NOT NULL DEFAULT 1,
      eqsl_enabled  TINYINT(1)   NOT NULL DEFAULT 1,
      discoverable  TINYINT(1)   NOT NULL DEFAULT 1,
      card_id       BIGINT UNSIGNED DEFAULT NULL,
      created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      PRIMARY KEY (user_id),
      UNIQUE KEY uk_qsl_callsign (callsign)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  `);

  await pool.query(`
    CREATE TABLE IF NOT EXISTS qsl_cards (
      id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
      user_id     BIGINT UNSIGNED NOT NULL,
      mode        VARCHAR(8)   NOT NULL,
      file_name   VARCHAR(128) NOT NULL,
      mime        VARCHAR(32)  NOT NULL,
      size_bytes  INT UNSIGNED NOT NULL,
      created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY (id),
      INDEX idx_qsl_cards_user (user_id)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  `);

  await pool.query(`
    CREATE TABLE IF NOT EXISTS qsl_exchanges (
      id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
      from_user_id  BIGINT UNSIGNED NOT NULL,
      to_user_id    BIGINT UNSIGNED DEFAULT NULL,
      peer_callsign VARCHAR(16)  NOT NULL,
      mode          VARCHAR(8)   NOT NULL,
      qso_date      VARCHAR(16)  NOT NULL DEFAULT '',
      qso_band      VARCHAR(16)  NOT NULL DEFAULT '',
      qso_mode      VARCHAR(16)  NOT NULL DEFAULT '',
      rst_sent      VARCHAR(8)   NOT NULL DEFAULT '',
      rst_rcvd      VARCHAR(8)   NOT NULL DEFAULT '',
      note          VARCHAR(255) NOT NULL DEFAULT '',
      status        VARCHAR(16)  NOT NULL DEFAULT 'pending',
      from_card_id  BIGINT UNSIGNED DEFAULT NULL,
      to_card_id    BIGINT UNSIGNED DEFAULT NULL,
      created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      PRIMARY KEY (id),
      INDEX idx_qsl_ex_from (from_user_id),
      INDEX idx_qsl_ex_to (to_user_id),
      INDEX idx_qsl_ex_peer (peer_callsign)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  `);

  // 考试练习云同步：一个账号一条记录，只存题号进度、复习数字与成绩摘要的 JSON。
  // App 不登录也能完整使用，因此这张表是可选增强；字段白名单在 routes/exam.js。
  await pool.query(`
    CREATE TABLE IF NOT EXISTS exam_progress (
      user_id     BIGINT UNSIGNED NOT NULL,
      data        MEDIUMTEXT   NOT NULL,
      updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      PRIMARY KEY (user_id)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  `);
}

async function findUserByCallsign(callsign) {
  const [rows] = await pool.query(
    'SELECT id, callsign, nickname, password_hash, created_at FROM ham_users WHERE callsign = ? LIMIT 1',
    [callsign]
  );
  return rows[0] || null;
}

async function findUserById(id) {
  const [rows] = await pool.query(
    'SELECT id, callsign, nickname, created_at FROM ham_users WHERE id = ? LIMIT 1',
    [id]
  );
  return rows[0] || null;
}

async function insertUser(callsign, passwordHash, nickname) {
  const [result] = await pool.query(
    'INSERT INTO ham_users (callsign, nickname, password_hash) VALUES (?, ?, ?)',
    [callsign, nickname || null, passwordHash]
  );
  return findUserById(result.insertId);
}

async function updateNickname(id, nickname) {
  await pool.query('UPDATE ham_users SET nickname = ? WHERE id = ?', [nickname || null, id]);
  return findUserById(id);
}

async function updatePasswordHash(id, passwordHash) {
  await pool.query('UPDATE ham_users SET password_hash = ? WHERE id = ?', [passwordHash, id]);
}

module.exports = {
  pool,
  ensureSchema,
  findUserByCallsign,
  findUserById,
  insertUser,
  updateNickname,
  updatePasswordHash,
};
