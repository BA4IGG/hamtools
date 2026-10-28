'use strict';

const express = require('express');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');

const db = require('../db');
const { normalizeCallsign, callsignRegion } = require('../callsign');

const router = express.Router();

const MIN_PASSWORD = 6;
const MAX_PASSWORD = 64;
const BCRYPT_ROUNDS = Number(process.env.BCRYPT_ROUNDS || 10);

function jwtSecret() {
  const secret = process.env.JWT_SECRET;
  if (!secret || secret.length < 16) {
    throw new Error('JWT_SECRET 未配置或过短，请在 .env 中设置至少 16 位的随机字符串');
  }
  return secret;
}

function publicUser(user) {
  return {
    callsign: user.callsign,
    nickname: user.nickname || '',
    region: callsignRegion(user.callsign),
    createdAt: user.created_at instanceof Date
      ? user.created_at.toISOString()
      : String(user.created_at || ''),
  };
}

function signToken(user) {
  return jwt.sign({ uid: user.id, callsign: user.callsign }, jwtSecret(), {
    expiresIn: process.env.JWT_EXPIRES_IN || '180d',
  });
}

/** 统一的失败响应：{ error: { code, message } } */
function fail(res, status, code, message) {
  return res.status(status).json({ error: { code, message } });
}

function checkPassword(raw) {
  return typeof raw === 'string' && raw.length >= MIN_PASSWORD && raw.length <= MAX_PASSWORD;
}

function checkNickname(raw) {
  if (raw === undefined || raw === null || raw === '') return true;
  return typeof raw === 'string' && raw.trim().length <= 32;
}

/** POST /api/auth/register —— 呼号注册 */
router.post('/register', async (req, res) => {
  const { callsign, password, nickname } = req.body || {};
  const normalized = normalizeCallsign(callsign);
  if (!normalized) return fail(res, 400, 'INVALID_CALLSIGN', '呼号格式不正确，应形如 BA4IGG');
  if (!checkPassword(password)) {
    return fail(res, 400, 'WEAK_PASSWORD', `密码长度需为 ${MIN_PASSWORD}~${MAX_PASSWORD} 位`);
  }
  if (!checkNickname(nickname)) return fail(res, 400, 'INVALID_NICKNAME', '昵称过长');

  try {
    const exists = await db.findUserByCallsign(normalized);
    if (exists) return fail(res, 409, 'CALLSIGN_TAKEN', '该呼号已注册，请直接登录');

    const hash = await bcrypt.hash(password, BCRYPT_ROUNDS);
    const user = await db.insertUser(normalized, hash, nickname ? nickname.trim() : '');
    return res.status(201).json({ token: signToken(user), user: publicUser(user) });
  } catch (error) {
    if (error && error.code === 'ER_DUP_ENTRY') {
      return fail(res, 409, 'CALLSIGN_TAKEN', '该呼号已注册，请直接登录');
    }
    console.error('[auth] register failed:', error.message);
    return fail(res, 500, 'SERVER_ERROR', '服务器开小差了，请稍后重试');
  }
});

/** POST /api/auth/login —— 呼号登录 */
router.post('/login', async (req, res) => {
  const { callsign, password } = req.body || {};
  const normalized = normalizeCallsign(callsign);
  if (!normalized) return fail(res, 400, 'INVALID_CALLSIGN', '呼号格式不正确，应形如 BA4IGG');
  if (!checkPassword(password)) return fail(res, 400, 'WEAK_PASSWORD', '密码长度不正确');

  try {
    const user = await db.findUserByCallsign(normalized);
    // 呼号不存在与密码错误返回同一提示，避免暴露账号是否存在
    if (!user) return fail(res, 401, 'BAD_CREDENTIALS', '呼号或密码不正确');
    const ok = await bcrypt.compare(password, user.password_hash);
    if (!ok) return fail(res, 401, 'BAD_CREDENTIALS', '呼号或密码不正确');
    return res.json({ token: signToken(user), user: publicUser(user) });
  } catch (error) {
    console.error('[auth] login failed:', error.message);
    return fail(res, 500, 'SERVER_ERROR', '服务器开小差了，请稍后重试');
  }
});

/** 需要登录的接口统一走这里 */
function requireAuth(req, res, next) {
  const header = req.headers.authorization || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : '';
  if (!token) return fail(res, 401, 'NO_TOKEN', '请先登录');
  try {
    const payload = jwt.verify(token, jwtSecret());
    req.userId = payload.uid;
    return next();
  } catch (error) {
    return fail(res, 401, 'TOKEN_EXPIRED', '登录状态已失效，请重新登录');
  }
}

/** GET /api/auth/me —— 用令牌换回用户信息（App 冷启动时校验登录态） */
router.get('/me', requireAuth, async (req, res) => {
  try {
    const user = await db.findUserById(req.userId);
    if (!user) return fail(res, 401, 'NO_USER', '账号不存在，请重新登录');
    return res.json({ user: publicUser(user) });
  } catch (error) {
    console.error('[auth] me failed:', error.message);
    return fail(res, 500, 'SERVER_ERROR', '服务器开小差了，请稍后重试');
  }
});

/** PATCH /api/auth/profile —— 修改昵称 */
router.patch('/profile', requireAuth, async (req, res) => {
  const { nickname } = req.body || {};
  if (!checkNickname(nickname)) return fail(res, 400, 'INVALID_NICKNAME', '昵称过长');
  try {
    const user = await db.updateNickname(req.userId, nickname ? nickname.trim() : '');
    return res.json({ user: publicUser(user) });
  } catch (error) {
    console.error('[auth] profile failed:', error.message);
    return fail(res, 500, 'SERVER_ERROR', '服务器开小差了，请稍后重试');
  }
});

/** POST /api/auth/password —— 登录后改密码 */
router.post('/password', requireAuth, async (req, res) => {
  const { oldPassword, newPassword } = req.body || {};
  if (!checkPassword(newPassword)) {
    return fail(res, 400, 'WEAK_PASSWORD', `新密码长度需为 ${MIN_PASSWORD}~${MAX_PASSWORD} 位`);
  }
  try {
    const user = await db.findUserById(req.userId);
    if (!user) return fail(res, 401, 'NO_USER', '账号不存在，请重新登录');
    const full = await db.findUserByCallsign(user.callsign);
    const ok = await bcrypt.compare(String(oldPassword || ''), full.password_hash);
    if (!ok) return fail(res, 401, 'BAD_CREDENTIALS', '原密码不正确');
    const hash = await bcrypt.hash(newPassword, BCRYPT_ROUNDS);
    await db.updatePasswordHash(user.id, hash);
    return res.json({ ok: true });
  } catch (error) {
    console.error('[auth] password failed:', error.message);
    return fail(res, 500, 'SERVER_ERROR', '服务器开小差了，请稍后重试');
  }
});

module.exports = { router, requireAuth };
