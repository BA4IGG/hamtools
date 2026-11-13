'use strict';

/**
 * QSL 卡片交换 API。
 *
 * 设计约定：
 * - 所有接口都要求登录（复用 auth.js 的 requireAuth），未登录直接 401。
 * - 交换分 paper / eqsl 两种模式，共用一个状态机。
 * - 「邮寄地址」「邮箱」属于个人敏感信息：名录接口一律不下发，
 *   只有交换被接受之后，参与双方才能通过 /contact 单独取到。
 * - 卡片图片不放在可匿名访问的静态目录，必须经过鉴权下载；
 *   可见范围 = 本人 + 与该用户已完成/进行中交换的对方。
 * - 不引入 multer 等新依赖：图片以二进制请求体上传，校验文件头后落盘。
 */

const express = require('express');
const fs = require('node:fs/promises');
const path = require('node:path');
const crypto = require('node:crypto');
const db = require('../db');
const { requireAuth } = require('./auth');

const router = express.Router();

const MODES = new Set(['paper', 'eqsl']);
const ACTIVE_STATUSES = ['accepted', 'sent', 'completed'];
const CLOSED_STATUSES = ['declined', 'cancelled'];
const ALL_STATUSES = ['pending', ...ACTIVE_STATUSES, ...CLOSED_STATUSES, 'recorded'];
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const UPLOAD_DIR = process.env.QSL_UPLOAD_DIR
  ? path.resolve(process.env.QSL_UPLOAD_DIR)
  : path.resolve(__dirname, '../../uploads/qsl');

const MAX_TEXT = {
  displayName: 64,
  grid: 8,
  qth: 128,
  email: 128,
  address: 500,
  note: 255,
  qsoDate: 16,
  qsoBand: 16,
  qsoMode: 16,
  rst: 8,
  exchangeNote: 255,
};

/** 统一的失败响应，和 auth.js 保持一致。 */
function fail(res, status, code, message) {
  return res.status(status).json({ error: { code, message } });
}

function text(value, max) {
  if (value === undefined || value === null) return '';
  if (typeof value !== 'string') return null;
  return value.trim().slice(0, max);
}

function bool(value, fallback = true) {
  if (value === undefined || value === null) return fallback;
  return value === true || value === 1 || value === '1';
}

function toIso(value) {
  if (!value) return '';
  if (value instanceof Date) return value.toISOString();
  return String(value);
}

/**
 * 国际通联对方呼号的宽松校验（不同于注册用的中国呼号规则）。
 * 允许 W1AW / JA1ABC / 9M2PJU / G3ABC/P 这类形态，以及 /M、/MM、数字后缀。
 */
function normalizePeer(raw) {
  if (typeof raw !== 'string') return null;
  const value = raw.trim().toUpperCase().replace(/\s+/g, '');
  if (!value || value.length > 16) return null;
  if (!/^[A-Z0-9]{1,3}\d[A-Z0-9]{1,4}(?:\/[A-Z0-9]{1,3})?$/.test(value)) return null;
  return value;
}

async function myUser(req) {
  const [rows] = await db.pool.query(
    'SELECT id, callsign, nickname FROM ham_users WHERE id = ? LIMIT 1',
    [req.userId]
  );
  return rows[0] || null;
}

function emptyProfile(callsign, nickname) {
  return {
    callsign,
    displayName: nickname || '',
    grid: '',
    qth: '',
    email: '',
    note: '',
    paperEnabled: true,
    eqslEnabled: true,
    discoverable: true,
    cardId: null,
    hasAddress: false,
    configured: false,
    updatedAt: '',
  };
}

function rowToProfile(row, fallback) {
  return {
    callsign: row.callsign || fallback.callsign,
    displayName: row.display_name || '',
    grid: row.grid || '',
    qth: row.qth || '',
    email: row.email || '',
    note: row.note || '',
    paperEnabled: !!row.paper_enabled,
    eqslEnabled: !!row.eqsl_enabled,
    discoverable: !!row.discoverable,
    cardId: row.card_id || null,
    hasAddress: !!row.address,
    configured: true,
    updatedAt: toIso(row.updated_at),
    // 注意：这里刻意不返回 address。邮寄地址只在 GET /me（本人）里单独附加，
    // 避免以后新增接口时不小心把地址带进列表响应。
  };
}

async function loadProfile(userId, fallback) {
  const [rows] = await db.pool.query(
    'SELECT * FROM qsl_profiles WHERE user_id = ? LIMIT 1',
    [userId]
  );
  return rows[0] ? rowToProfile(rows[0], fallback) : emptyProfile(fallback.callsign, fallback.nickname);
}

function rowToExchange(row, myId) {
  const outgoing = Number(row.from_user_id) === Number(myId);
  return {
    id: Number(row.id),
    mode: row.mode,
    peerCallsign: row.peer_callsign,
    direction: outgoing ? 'outgoing' : 'incoming',
    qsoDate: row.qso_date || '',
    qsoBand: row.qso_band || '',
    qsoMode: row.qso_mode || '',
    rstSent: row.rst_sent || '',
    rstRcvd: row.rst_rcvd || '',
    note: row.note || '',
    status: row.status,
    registeredPeer: row.to_user_id !== null,
    fromCardId: row.from_card_id || null,
    toCardId: row.to_card_id || null,
    createdAt: toIso(row.created_at),
    updatedAt: toIso(row.updated_at),
  };
}

/** 图片文件头校验：只接受 JPEG / PNG / WebP，避免把任意文件写进磁盘。 */
function detectImage(buffer) {
  if (!Buffer.isBuffer(buffer) || buffer.length < 12) return null;
  if (buffer[0] === 0xff && buffer[1] === 0xd8 && buffer[2] === 0xff) {
    return { ext: 'jpg', mime: 'image/jpeg' };
  }
  if (buffer.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))) {
    return { ext: 'png', mime: 'image/png' };
  }
  if (buffer.subarray(0, 4).toString('ascii') === 'RIFF' &&
      buffer.subarray(8, 12).toString('ascii') === 'WEBP') {
    return { ext: 'webp', mime: 'image/webp' };
  }
  return null;
}

/** 用户是否能查看某张卡片：本人，或与该用户有进行中/已完成交换。 */
async function canSeeCard(requesterId, ownerId) {
  if (Number(requesterId) === Number(ownerId)) return true;
  const [rows] = await db.pool.query(
    `SELECT 1 FROM qsl_exchanges
     WHERE status IN ('accepted','sent','completed')
       AND ((from_user_id = ? AND to_user_id = ?) OR (from_user_id = ? AND to_user_id = ?))
     LIMIT 1`,
    [requesterId, ownerId, ownerId, requesterId]
  );
  return rows.length > 0;
}

async function currentCardId(userId) {
  const [rows] = await db.pool.query(
    'SELECT card_id FROM qsl_profiles WHERE user_id = ? LIMIT 1',
    [userId]
  );
  return rows[0] ? rows[0].card_id : null;
}

/** 对方当前卡片：只有资料里指定了 card_id 才返回。 */
async function peerCardId(userId) {
  const id = await currentCardId(userId);
  return id ? Number(id) : null;
}

// ------------------------------------------------------------------ 探针

/**
 * GET /api/qsl/ping —— 无需登录的能力探测。
 * 线上还是旧版服务端时这个路径会返回 404，App 据此判断「QSL 功能尚未部署」，
 * 从而进入明确标注的演示模式，而不是把网络失败误报成账号问题。
 */
router.get('/ping', (_req, res) => {
  res.json({ ok: true, feature: 'qsl', version: 1, requiresLogin: true });
});

// ------------------------------------------------------------------ 资料

/** GET /api/qsl/me —— 我的 QSL 资料（含邮寄地址，仅本人可见）。 */
router.get('/me', requireAuth, async (req, res) => {
  try {
    const user = await myUser(req);
    if (!user) return fail(res, 401, 'NO_USER', '账号不存在，请重新登录');
    const profile = await loadProfile(user.id, user);
    if (profile.configured) {
      const [rows] = await db.pool.query(
        'SELECT address, callsign FROM qsl_profiles WHERE user_id = ? LIMIT 1',
        [user.id]
      );
      profile.address = rows[0]?.address || '';
    }
    const [cards] = await db.pool.query(
      'SELECT id, mode, mime, size_bytes, created_at FROM qsl_cards WHERE user_id = ? ORDER BY id DESC',
      [user.id]
    );
    return res.json({
      profile,
      cards: cards.map((c) => ({
        id: Number(c.id),
        mode: c.mode,
        mime: c.mime,
        sizeBytes: Number(c.size_bytes),
        createdAt: toIso(c.created_at),
      })),
    });
  } catch (error) {
    console.error('[qsl] me failed:', error.message);
    return fail(res, 500, 'SERVER_ERROR', '服务器开小差了，请稍后重试');
  }
});

/** PUT /api/qsl/me —— 保存资料。address 单独存敏感字段，不出现在任何列表接口。 */
router.put('/me', requireAuth, async (req, res) => {
  const body = req.body || {};
  const fields = {};
  for (const [key, max] of Object.entries(MAX_TEXT)) {
    if (key === 'exchangeNote') continue;
    const camel = key;
    const value = text(body[camel], max);
    if (value === null) return fail(res, 400, 'INVALID_FIELD', `${camel} 格式不正确`);
    fields[camel] = value;
  }
  if (fields.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(fields.email)) {
    return fail(res, 400, 'INVALID_EMAIL', '邮箱格式不正确');
  }
  if (fields.grid && !/^[A-R]{2}\d{2}(?:[A-X]{2})?(?:\d{2})?$/i.test(fields.grid)) {
    return fail(res, 400, 'INVALID_GRID', '网格格式不正确，应形如 OM89 或 OM89ab');
  }

  try {
    const user = await myUser(req);
    if (!user) return fail(res, 401, 'NO_USER', '账号不存在，请重新登录');
    await db.pool.query(
      `INSERT INTO qsl_profiles
         (user_id, callsign, display_name, grid, qth, email, address, note,
          paper_enabled, eqsl_enabled, discoverable)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
       ON DUPLICATE KEY UPDATE
         callsign = VALUES(callsign), display_name = VALUES(display_name),
         grid = VALUES(grid), qth = VALUES(qth), email = VALUES(email),
         address = VALUES(address), note = VALUES(note),
         paper_enabled = VALUES(paper_enabled), eqsl_enabled = VALUES(eqsl_enabled),
         discoverable = VALUES(discoverable)`,
      [
        user.id, user.callsign,
        fields.displayName, fields.grid, fields.qth, fields.email,
        fields.address || null, fields.note,
        bool(body.paperEnabled) ? 1 : 0,
        bool(body.eqslEnabled) ? 1 : 0,
        bool(body.discoverable) ? 1 : 0,
      ]
    );
    // 与 GET /me 保持一致：邮寄地址只对本人回显，列表/名录接口永远不带。
    // 若这里不回显，App 保存后短暂把地址框清空，随后刷新又填回来，看起来像「地址丢了」。
    const profile = await loadProfile(user.id, user);
    profile.address = fields.address || '';
    return res.json({ profile });
  } catch (error) {
    console.error('[qsl] save profile failed:', error.message);
    return fail(res, 500, 'SERVER_ERROR', '服务器开小差了，请稍后重试');
  }
});

/** DELETE /api/qsl/me —— 删除我的全部 QSL 资料、卡片与交换记录。 */
router.delete('/me', requireAuth, async (req, res) => {
  try {
    const [cards] = await db.pool.query('SELECT file_name FROM qsl_cards WHERE user_id = ?', [req.userId]);
    await db.pool.query('DELETE FROM qsl_exchanges WHERE from_user_id = ? OR to_user_id = ?', [req.userId, req.userId]);
    await db.pool.query('DELETE FROM qsl_cards WHERE user_id = ?', [req.userId]);
    await db.pool.query('DELETE FROM qsl_profiles WHERE user_id = ?', [req.userId]);
    await fs.mkdir(UPLOAD_DIR, { recursive: true }).catch(() => {});
    for (const card of cards) {
      await fs.unlink(path.join(UPLOAD_DIR, card.file_name)).catch(() => {});
    }
    return res.json({ ok: true });
  } catch (error) {
    console.error('[qsl] delete profile failed:', error.message);
    return fail(res, 500, 'SERVER_ERROR', '服务器开小差了，请稍后重试');
  }
});

// ------------------------------------------------------------------ 卡片图片

/** POST /api/qsl/cards —— 二进制图片上传；模式走 X-Qsl-Mode 头。 */
router.post(
  '/cards',
  requireAuth,
  // 放宽一层，让 handler 自己给出中文的 413 提示，而不是被 body-parser 直接抛给全局 500
  express.raw({ type: '*/*', limit: '8mb' }),
  async (req, res) => {
    const mode = String(req.headers['x-qsl-mode'] || '').toLowerCase();
    if (!MODES.has(mode)) return fail(res, 400, 'INVALID_MODE', '卡片模式需为 paper 或 eqsl');
    const buffer = req.body;
    const kind = detectImage(buffer);
    if (!kind) return fail(res, 400, 'INVALID_IMAGE', '仅支持 JPG / PNG / WebP 图片');
    if (buffer.length > MAX_IMAGE_BYTES) {
      return fail(res, 413, 'IMAGE_TOO_LARGE', '图片不能超过 5 MB');
    }

    const fileName = `${crypto.randomBytes(16).toString('hex')}.${kind.ext}`;
    try {
      await fs.mkdir(UPLOAD_DIR, { recursive: true });
      await fs.writeFile(path.join(UPLOAD_DIR, fileName), buffer);
      const [result] = await db.pool.query(
        'INSERT INTO qsl_cards (user_id, mode, file_name, mime, size_bytes) VALUES (?, ?, ?, ?, ?)',
        [req.userId, mode, fileName, kind.mime, buffer.length]
      );
      const cardId = result.insertId;
      // 上传后自动设为当前使用的卡片
      await db.pool.query(
        `INSERT INTO qsl_profiles (user_id, callsign, card_id) VALUES (?, ?, ?)
         ON DUPLICATE KEY UPDATE card_id = VALUES(card_id)`,
        [req.userId, (await myUser(req))?.callsign || '', cardId]
      );
      return res.status(201).json({
        card: {
          id: Number(cardId),
          mode,
          mime: kind.mime,
          sizeBytes: buffer.length,
          url: `/api/qsl/cards/${cardId}/image`,
        },
      });
    } catch (error) {
      await fs.unlink(path.join(UPLOAD_DIR, fileName)).catch(() => {});
      console.error('[qsl] card upload failed:', error.message);
      return fail(res, 500, 'SERVER_ERROR', '服务器开小差了，请稍后重试');
    }
  }
);

/** GET /api/qsl/cards/:id/image —— 鉴权下载，不暴露匿名可访问的静态目录。 */
router.get('/cards/:id/image', requireAuth, async (req, res) => {
  const id = Number(req.params.id);
  if (!Number.isInteger(id) || id <= 0) return fail(res, 400, 'INVALID_ID', '卡片编号不正确');
  try {
    const [rows] = await db.pool.query('SELECT * FROM qsl_cards WHERE id = ? LIMIT 1', [id]);
    const card = rows[0];
    if (!card) return fail(res, 404, 'CARD_NOT_FOUND', '卡片不存在');
    if (!(await canSeeCard(req.userId, card.user_id))) {
      return fail(res, 403, 'FORBIDDEN', '交换完成后才能查看对方卡片');
    }
    // 文件名是服务端生成的十六进制串，再做一次白名单校验防止路径穿越
    if (!/^[a-f0-9]{32}\.(jpg|png|webp)$/.test(card.file_name)) {
      return fail(res, 500, 'SERVER_ERROR', '卡片文件异常');
    }
    const buffer = await fs.readFile(path.join(UPLOAD_DIR, card.file_name));
    res.set('Cache-Control', 'private, max-age=600');
    return res.type(card.mime).send(buffer);
  } catch (error) {
    if (error && error.code === 'ENOENT') {
      return fail(res, 404, 'CARD_NOT_FOUND', '卡片文件已丢失');
    }
    console.error('[qsl] card download failed:', error.message);
    return fail(res, 500, 'SERVER_ERROR', '服务器开小差了，请稍后重试');
  }
});

/** DELETE /api/qsl/cards/:id —— 删除我上传的卡片。 */
router.delete('/cards/:id', requireAuth, async (req, res) => {
  const id = Number(req.params.id);
  if (!Number.isInteger(id) || id <= 0) return fail(res, 400, 'INVALID_ID', '卡片编号不正确');
  try {
    const [rows] = await db.pool.query(
      'SELECT file_name FROM qsl_cards WHERE id = ? AND user_id = ? LIMIT 1',
      [id, req.userId]
    );
    if (!rows[0]) return fail(res, 404, 'CARD_NOT_FOUND', '卡片不存在');
    await db.pool.query('DELETE FROM qsl_cards WHERE id = ?', [id]);
    await db.pool.query(
      'UPDATE qsl_profiles SET card_id = NULL WHERE user_id = ? AND card_id = ?',
      [req.userId, id]
    );
    await fs.unlink(path.join(UPLOAD_DIR, rows[0].file_name)).catch(() => {});
    return res.json({ ok: true });
  } catch (error) {
    console.error('[qsl] card delete failed:', error.message);
    return fail(res, 500, 'SERVER_ERROR', '服务器开小差了，请稍后重试');
  }
});

// ------------------------------------------------------------------ 名录

/**
 * GET /api/qsl/directory?q= —— 愿意交换的用户名录。
 * 只返回脱敏字段：绝不下发 address / email / user_id。
 */
router.get('/directory', requireAuth, async (req, res) => {
  const q = String(req.query.q || '').trim().toUpperCase().slice(0, 16);
  try {
    const [rows] = await db.pool.query(
      `SELECT p.callsign, p.display_name, p.grid, p.qth, p.note,
              p.paper_enabled, p.eqsl_enabled, p.card_id, u.nickname
         FROM qsl_profiles p
         JOIN ham_users u ON u.id = p.user_id
        WHERE p.discoverable = 1 AND p.user_id <> ?
          AND (p.paper_enabled = 1 OR p.eqsl_enabled = 1)
          AND (? = '' OR p.callsign LIKE ?)
        ORDER BY p.callsign ASC LIMIT 50`,
      [req.userId, q, `%${q}%`]
    );
    return res.json({
      users: rows.map((r) => ({
        callsign: r.callsign,
        displayName: r.display_name || r.nickname || '',
        grid: r.grid || '',
        qth: r.qth || '',
        note: r.note || '',
        paperEnabled: !!r.paper_enabled,
        eqslEnabled: !!r.eqsl_enabled,
        hasCard: !!r.card_id,
      })),
    });
  } catch (error) {
    console.error('[qsl] directory failed:', error.message);
    return fail(res, 500, 'SERVER_ERROR', '服务器开小差了，请稍后重试');
  }
});

// ------------------------------------------------------------------ 交换

/** GET /api/qsl/exchanges —— 与我相关的全部交换（即交换历史的数据源）。 */
router.get('/exchanges', requireAuth, async (req, res) => {
  try {
    const [rows] = await db.pool.query(
      `SELECT * FROM qsl_exchanges
        WHERE from_user_id = ? OR to_user_id = ?
        ORDER BY created_at DESC, id DESC LIMIT 300`,
      [req.userId, req.userId]
    );
    return res.json({ exchanges: rows.map((r) => rowToExchange(r, req.userId)) });
  } catch (error) {
    console.error('[qsl] exchanges failed:', error.message);
    return fail(res, 500, 'SERVER_ERROR', '服务器开小差了，请稍后重试');
  }
});

/** POST /api/qsl/exchanges —— 发起交换申请，或仅记录一次线下已完成的交换。 */
router.post('/exchanges', requireAuth, async (req, res) => {
  const body = req.body || {};
  const mode = String(body.mode || '').toLowerCase();
  if (!MODES.has(mode)) return fail(res, 400, 'INVALID_MODE', '交换方式需为 paper 或 eqsl');
  const peer = normalizePeer(body.peerCallsign);
  if (!peer) return fail(res, 400, 'INVALID_CALLSIGN', '对方呼号格式不正确');
  const recordOnly = body.recordOnly === true || body.recordOnly === 1;

  const values = {};
  for (const [key, max] of Object.entries({
    qsoDate: MAX_TEXT.qsoDate,
    qsoBand: MAX_TEXT.qsoBand,
    qsoMode: MAX_TEXT.qsoMode,
    rstSent: MAX_TEXT.rst,
    rstRcvd: MAX_TEXT.rst,
    note: MAX_TEXT.exchangeNote,
  })) {
    const v = text(body[key], max);
    if (v === null) return fail(res, 400, 'INVALID_FIELD', `${key} 格式不正确`);
    values[key] = v;
  }

  try {
    const user = await myUser(req);
    if (!user) return fail(res, 401, 'NO_USER', '账号不存在，请重新登录');
    if (peer === user.callsign) {
      return fail(res, 400, 'SELF_EXCHANGE', '不能和自己交换卡片');
    }

    // 同一 QSO 重复申请没有意义，拒绝的/取消的除外
    const [dup] = await db.pool.query(
      `SELECT id FROM qsl_exchanges
        WHERE from_user_id = ? AND peer_callsign = ? AND mode = ? AND qso_date = ?
          AND status NOT IN ('declined','cancelled')
        LIMIT 1`,
      [user.id, peer, mode, values.qsoDate]
    );
    if (dup[0]) return fail(res, 409, 'DUPLICATE_EXCHANGE', '这次通联已有一条交换记录');

    let toUserId = null;
    let status = 'recorded';
    if (!recordOnly) {
      const [peers] = await db.pool.query(
        'SELECT id FROM ham_users WHERE callsign = ? LIMIT 1',
        [peer]
      );
      if (!peers[0]) {
        return fail(res, 409, 'PEER_NOT_REGISTERED', '对方尚未注册本 App，可选择仅记录到历史');
      }
      const [profiles] = await db.pool.query(
        'SELECT paper_enabled, eqsl_enabled FROM qsl_profiles WHERE user_id = ? LIMIT 1',
        [peers[0].id]
      );
      const profile = profiles[0];
      const enabled = profile && (mode === 'paper' ? profile.paper_enabled : profile.eqsl_enabled);
      if (!enabled) {
        return fail(res, 409, 'PEER_NOT_AVAILABLE', '对方未开放该交换方式，可选择仅记录到历史');
      }
      toUserId = peers[0].id;
      status = 'pending';
    }

    const fromCardId = await peerCardId(user.id);
    const [result] = await db.pool.query(
      `INSERT INTO qsl_exchanges
         (from_user_id, to_user_id, peer_callsign, mode, qso_date, qso_band, qso_mode,
          rst_sent, rst_rcvd, note, status, from_card_id)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        user.id, toUserId, peer, mode, values.qsoDate, values.qsoBand, values.qsoMode,
        values.rstSent, values.rstRcvd, values.note, status, fromCardId,
      ]
    );
    const [rows] = await db.pool.query('SELECT * FROM qsl_exchanges WHERE id = ?', [result.insertId]);
    return res.status(201).json({ exchange: rowToExchange(rows[0], user.id) });
  } catch (error) {
    console.error('[qsl] create exchange failed:', error.message);
    return fail(res, 500, 'SERVER_ERROR', '服务器开小差了，请稍后重试');
  }
});

/** 纯状态机：便于单测，也避免路由里散落 if。 */
function nextStatus(action, mode, status) {
  switch (action) {
    case 'accept':
      if (status !== 'pending') return null;
      return mode === 'eqsl' ? 'completed' : 'accepted';
    case 'decline':
      return status === 'pending' ? 'declined' : null;
    case 'sent':
      return mode === 'paper' && status === 'accepted' ? 'sent' : null;
    case 'complete':
      return (status === 'accepted' || status === 'sent') ? 'completed' : null;
    case 'cancel':
      return ['pending', 'accepted', 'sent'].includes(status) ? 'cancelled' : null;
    default:
      return null;
  }
}

const ACTION_ACTOR = {
  accept: 'receiver',
  decline: 'receiver',
  sent: 'both',
  complete: 'both',
  cancel: 'sender',
};

/** PATCH /api/qsl/exchanges/:id —— 接受 / 拒绝 / 标记寄出 / 完成 / 取消。 */
router.patch('/exchanges/:id', requireAuth, async (req, res) => {
  const id = Number(req.params.id);
  const action = String((req.body || {}).action || '').toLowerCase();
  if (!Number.isInteger(id) || id <= 0) return fail(res, 400, 'INVALID_ID', '交换编号不正确');
  if (!Object.prototype.hasOwnProperty.call(ACTION_ACTOR, action)) {
    return fail(res, 400, 'INVALID_ACTION', '不支持的操作');
  }

  try {
    const [rows] = await db.pool.query('SELECT * FROM qsl_exchanges WHERE id = ? LIMIT 1', [id]);
    const ex = rows[0];
    if (!ex) return fail(res, 404, 'EXCHANGE_NOT_FOUND', '交换记录不存在');

    const isSender = Number(ex.from_user_id) === Number(req.userId);
    const isReceiver = Number(ex.to_user_id) === Number(req.userId);
    if (!isSender && !isReceiver) return fail(res, 403, 'FORBIDDEN', '无权操作这条交换');
    const actor = ACTION_ACTOR[action];
    if (actor === 'receiver' && !isReceiver) return fail(res, 403, 'FORBIDDEN', '只有接收方能处理申请');
    if (actor === 'sender' && !isSender) return fail(res, 403, 'FORBIDDEN', '只有发起方能取消申请');

    const next = nextStatus(action, ex.mode, ex.status);
    if (!next) return fail(res, 409, 'INVALID_TRANSITION', '当前状态不支持该操作');

    let toCardId = ex.to_card_id;
    let fromCardId = ex.from_card_id;
    // eQSL 一旦接受即互换电子卡片，不需要邮寄环节
    if (next === 'completed') {
      fromCardId = fromCardId || await peerCardId(ex.from_user_id);
      if (ex.to_user_id) toCardId = toCardId || await peerCardId(ex.to_user_id);
    }

    await db.pool.query(
      'UPDATE qsl_exchanges SET status = ?, from_card_id = ?, to_card_id = ? WHERE id = ?',
      [next, fromCardId, toCardId, id]
    );
    const [updated] = await db.pool.query('SELECT * FROM qsl_exchanges WHERE id = ?', [id]);
    return res.json({ exchange: rowToExchange(updated[0], req.userId) });
  } catch (error) {
    console.error('[qsl] update exchange failed:', error.message);
    return fail(res, 500, 'SERVER_ERROR', '服务器开小差了，请稍后重试');
  }
});

/** GET /api/qsl/exchanges/:id/contact —— 交换被接受后，双方才能拿到邮寄信息。 */
router.get('/exchanges/:id/contact', requireAuth, async (req, res) => {
  const id = Number(req.params.id);
  if (!Number.isInteger(id) || id <= 0) return fail(res, 400, 'INVALID_ID', '交换编号不正确');
  try {
    const [rows] = await db.pool.query('SELECT * FROM qsl_exchanges WHERE id = ? LIMIT 1', [id]);
    const ex = rows[0];
    if (!ex) return fail(res, 404, 'EXCHANGE_NOT_FOUND', '交换记录不存在');
    const isSender = Number(ex.from_user_id) === Number(req.userId);
    const isReceiver = Number(ex.to_user_id) === Number(req.userId);
    if (!isSender && !isReceiver) return fail(res, 403, 'FORBIDDEN', '无权查看');
    if (!ACTIVE_STATUSES.includes(ex.status)) {
      return fail(res, 409, 'NOT_ACCEPTED', '对方接受交换后才能查看邮寄信息');
    }
    if (!ex.to_user_id) {
      return fail(res, 409, 'UNREGISTERED_PEER', '对方未注册，没有可查询的邮寄信息');
    }
    const peerId = isSender ? ex.to_user_id : ex.from_user_id;
    const [profiles] = await db.pool.query('SELECT * FROM qsl_profiles WHERE user_id = ? LIMIT 1', [peerId]);
    const [users] = await db.pool.query('SELECT callsign FROM ham_users WHERE id = ? LIMIT 1', [peerId]);
    const p = profiles[0];
    return res.json({
      contact: {
        callsign: users[0]?.callsign || ex.peer_callsign,
        displayName: p?.display_name || '',
        address: p?.address || '',
        email: p?.email || '',
        qth: p?.qth || '',
        grid: p?.grid || '',
        hasCard: !!p?.card_id,
      },
    });
  } catch (error) {
    console.error('[qsl] contact failed:', error.message);
    return fail(res, 500, 'SERVER_ERROR', '服务器开小差了，请稍后重试');
  }
});

/** GET /api/qsl/summary —— 统计与待处理申请，供 App 角标和通知轮询使用。 */
router.get('/summary', requireAuth, async (req, res) => {
  try {
    const [rows] = await db.pool.query(
      `SELECT status, mode, from_user_id, to_user_id, id FROM qsl_exchanges
        WHERE from_user_id = ? OR to_user_id = ?`,
      [req.userId, req.userId]
    );
    let incoming = 0;
    let outgoing = 0;
    let completed = 0;
    let paper = 0;
    let eqsl = 0;
    let latestIncomingId = 0;
    for (const r of rows) {
      const isSender = Number(r.from_user_id) === Number(req.userId);
      if (r.status === 'pending') {
        if (isSender) outgoing += 1;
        else {
          incoming += 1;
          latestIncomingId = Math.max(latestIncomingId, Number(r.id));
        }
      }
      if (r.status === 'completed' || r.status === 'recorded') {
        completed += 1;
        if (r.mode === 'paper') paper += 1;
        else eqsl += 1;
      }
    }
    return res.json({
      incoming,
      outgoing,
      completed,
      paper,
      eqsl,
      latestIncomingId,
      serverTime: new Date().toISOString(),
    });
  } catch (error) {
    console.error('[qsl] summary failed:', error.message);
    return fail(res, 500, 'SERVER_ERROR', '服务器开小差了，请稍后重试');
  }
});

module.exports = {
  router,
  // 纯函数导出，便于本地自检脚本与回归
  normalizePeer,
  detectImage,
  nextStatus,
  rowToExchange,
  ALL_STATUSES,
};
