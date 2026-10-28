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
