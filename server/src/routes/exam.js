'use strict';

/**
 * 考试练习「账号云同步」API（可选增强）。
 *
 * 设计约定：
 * - App 不登录也能完整练习；登录后才调用这里的接口，本地副本始终是工作副本。
 * - 服务端只保存题号进度、记忆曲线数字、顺序练习断点与成绩摘要，
 *   不保存题目正文、用户所选答案、位置或任何个人资料。
 * - 请求体是一份规范化后的 JSON（和 App 的 ExamProgressCodec v2 同构），
 *   服务端做字段白名单与数值收敛，避免把任意内容塞进数据库。
 * - GET /ping 是能力探测：线上仍是旧版服务端时返回 404，App 会如实标注
 *   「服务端尚未部署，当前仅保存在本机」，绝不把本机数据伪装成已同步。
 */

const express = require('express');
const db = require('../db');
const { requireAuth } = require('./auth');

const router = express.Router();

const MAX_PROGRESS_ENTRIES = 5000;
const MAX_HISTORY_ENTRIES = 200;
const MAX_WRONG_IDS = 200;
const MAX_BYTES = 512 * 1024;

function fail(res, status, code, message) {
  return res.status(status).json({ error: { code, message } });
}

function int(value, fallback = 0) {
  const n = Number(value);
  if (!Number.isFinite(n)) return fallback;
  return Math.max(-2_147_483_648, Math.min(2_147_483_647, Math.trunc(n)));
}

function long(value, fallback = 0) {
  const n = Number(value);
  if (!Number.isFinite(n)) return fallback;
  return Math.max(0, Math.min(9_000_000_000_000_000, Math.trunc(n)));
}

function str(value, max) {
  return typeof value === 'string' ? value.slice(0, max) : '';
}

/** 只保留 App 端 ExamProgressCodec 认识的字段，其余一律丢弃。 */
function sanitize(raw) {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return null;

  const p = {};
  if (raw.p !== undefined) {
    if (!raw.p || typeof raw.p !== 'object' || Array.isArray(raw.p)) return null;
    const entries = Object.entries(raw.p);
    if (entries.length > MAX_PROGRESS_ENTRIES) return null;
    for (const [id, item] of entries) {
      if (!/^[A-Za-z0-9_-]{1,32}$/.test(id)) return null;
      if (!item || typeof item !== 'object' || Array.isArray(item)) return null;
      p[id] = {
        s: int(item.s, -1),
        l: long(item.l),
        n: long(item.n),
        c: int(item.c),
        w: int(item.w),
        x: int(item.x),
        e: item.e ? 1 : 0,
      };
    }
  }

  const h = [];
  if (raw.h !== undefined) {
    if (!Array.isArray(raw.h)) return null;
    if (raw.h.length > MAX_HISTORY_ENTRIES) return null;
    for (const item of raw.h) {
      if (!item || typeof item !== 'object' || Array.isArray(item)) return null;
      const wrongIds = Array.isArray(item.wi)
        ? item.wi.filter((x) => typeof x === 'string').slice(0, MAX_WRONG_IDS).map((x) => x.slice(0, 32))
        : [];
      h.push({
        lv: str(item.lv, 8),
        m: str(item.m, 24),
        s: int(item.s),
        t: int(item.t),
        p: item.p ? 1 : 0,
        a: long(item.a),
        id: str(item.id, 80),
        el: long(item.el),
        sc: int(item.sc),
        st: int(item.st),
        mc: int(item.mc),
        mt: int(item.mt),
        wi: wrongIds,
        to: item.to ? 1 : 0,
      });
    }
  }

  const c = {};
  if (raw.c !== undefined) {
    if (!raw.c || typeof raw.c !== 'object' || Array.isArray(raw.c)) return null;
    for (const [level, index] of Object.entries(raw.c)) {
      if (!/^[ABC]$/.test(level)) return null;
      c[level] = int(index, 0);
    }
  }

  return { v: 2, p, h, c };
}

function toIso(value) {
  if (!value) return null;
  if (value instanceof Date) return value.toISOString();
  return String(value);
}

/** GET /api/exam/ping —— 无需登录的能力探测。 */
router.get('/ping', (_req, res) => {
  res.json({ ok: true, feature: 'exam-sync', version: 2, requiresLogin: true });
});

/** GET /api/exam/progress —— 取回该账号保存的学习记录。 */
router.get('/progress', requireAuth, async (req, res) => {
  try {
    const [rows] = await db.pool.query(
      'SELECT data, updated_at FROM exam_progress WHERE user_id = ? LIMIT 1',
      [req.userId]
    );
    if (!rows[0]) return res.json({ data: null, updatedAt: null });
    let data = null;
    try {
      data = JSON.parse(rows[0].data);
    } catch (_error) {
      data = null;
    }
    return res.json({ data, updatedAt: toIso(rows[0].updated_at) });
  } catch (error) {
    console.error('[exam] get progress failed:', error.message);
    return fail(res, 500, 'SERVER_ERROR', '服务器开小差了，请稍后重试');
  }
});

/** PUT /api/exam/progress —— 保存合并后的学习记录（覆盖式，合并由客户端完成）。 */
router.put('/progress', requireAuth, async (req, res) => {
  const data = sanitize(req.body && req.body.data);
  if (!data) return fail(res, 400, 'BAD_PAYLOAD', '学习记录格式不合法');
  const serialized = JSON.stringify(data);
  if (Buffer.byteLength(serialized, 'utf8') > MAX_BYTES) {
    return fail(res, 413, 'PAYLOAD_TOO_LARGE', '学习记录过大');
  }
  try {
    await db.pool.query(
      `INSERT INTO exam_progress (user_id, data) VALUES (?, ?)
       ON DUPLICATE KEY UPDATE data = VALUES(data), updated_at = CURRENT_TIMESTAMP`,
      [req.userId, serialized]
    );
    const [rows] = await db.pool.query(
      'SELECT updated_at FROM exam_progress WHERE user_id = ? LIMIT 1',
      [req.userId]
    );
    return res.json({ ok: true, updatedAt: toIso(rows[0] && rows[0].updated_at), bytes: Buffer.byteLength(serialized, 'utf8') });
  } catch (error) {
    console.error('[exam] save progress failed:', error.message);
    return fail(res, 500, 'SERVER_ERROR', '服务器开小差了，请稍后重试');
  }
});

/** DELETE /api/exam/progress —— 一键删除该账号的云端学习记录。 */
router.delete('/progress', requireAuth, async (req, res) => {
  try {
    await db.pool.query('DELETE FROM exam_progress WHERE user_id = ?', [req.userId]);
    return res.json({ ok: true });
  } catch (error) {
    console.error('[exam] delete progress failed:', error.message);
    return fail(res, 500, 'SERVER_ERROR', '服务器开小差了，请稍后重试');
  }
});

module.exports = { router, sanitize };
