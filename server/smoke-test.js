'use strict';

/**
 * 本地冒烟测试：用内存里的假数据库跑一遍注册 / 登录 / me / 改昵称 / 改密码。
 * 不需要 MySQL，只验证接口逻辑。运行：node smoke-test.js
 */

process.env.JWT_SECRET = 'smoke_test_secret_please_ignore_123456';
process.env.BCRYPT_ROUNDS = '6';

const dbPath = require.resolve('./src/db');
const users = [];
let nextId = 1;

const fakeDb = {
  pool: { query: async () => {} },
  ensureSchema: async () => {},
  findUserByCallsign: async (c) => users.find((u) => u.callsign === c) || null,
  findUserById: async (id) => users.find((u) => u.id === id) || null,
  insertUser: async (callsign, passwordHash, nickname) => {
    const user = { id: nextId++, callsign, nickname, password_hash: passwordHash, created_at: new Date() };
    users.push(user);
    return user;
  },
  updateNickname: async (id, nickname) => {
    const user = users.find((u) => u.id === id);
    user.nickname = nickname;
    return user;
  },
  updatePasswordHash: async (id, passwordHash) => {
    users.find((u) => u.id === id).password_hash = passwordHash;
  },
};

require.cache[dbPath] = {
  id: dbPath,
  filename: dbPath,
  loaded: true,
  exports: fakeDb,
  children: [],
  paths: [],
};

const express = require('express');
const { router } = require('./src/routes/auth');

const app = express();
app.use(express.json());
app.get('/api/health', (_req, res) => res.json({ ok: true }));
app.use('/api/auth', router);

let passed = 0;
let failed = 0;

function check(name, condition, detail) {
  if (condition) {
    passed += 1;
    console.log(`  ok   ${name}`);
  } else {
    failed += 1;
    console.log(`  FAIL ${name} -> ${detail}`);
  }
}

const PORT = 0;

const server = app.listen(PORT, async () => {
  try {
    await run(server.address().port);
  } catch (error) {
    console.error(error);
    failed += 1;
  } finally {
    server.close();
    console.log(`\n通过 ${passed} 项，失败 ${failed} 项`);
    process.exit(failed === 0 ? 0 : 1);
  }
});

async function run(port) {
  const call = (method, path, body, token) =>
    fetch(`http://127.0.0.1:${port}${path}`, {
      method,
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: body ? JSON.stringify(body) : undefined,
    }).then(async (r) => ({ status: r.status, json: await r.json() }));

  const health = await call('GET', '/api/health');
  check('健康检查 ok', health.status === 200 && health.json.ok === true, JSON.stringify(health.json));

  const registered = await call('POST', '/api/auth/register', {
    callsign: 'ba4igg',
    password: 'secret123',
    nickname: '作者',
  });
  check('注册成功', registered.status === 201 && !!registered.json.token, JSON.stringify(registered.json));
  check('注册返回大写呼号', registered.json.user?.callsign === 'BA4IGG', JSON.stringify(registered.json.user));
  check('注册返回分区', registered.json.user?.region === '上海、山东、江苏', JSON.stringify(registered.json.user));

  const duplicate = await call('POST', '/api/auth/register', { callsign: 'BA4IGG', password: 'secret123' });
  check('重复呼号被拒', duplicate.status === 409 && duplicate.json.error.code === 'CALLSIGN_TAKEN', JSON.stringify(duplicate.json));

  const badCallsign = await call('POST', '/api/auth/register', { callsign: 'X1ABC', password: 'secret123' });
  check('非法呼号被拒', badCallsign.status === 400 && badCallsign.json.error.code === 'INVALID_CALLSIGN', JSON.stringify(badCallsign.json));

  const weak = await call('POST', '/api/auth/register', { callsign: 'BG5XYZ', password: '123' });
  check('弱密码被拒', weak.status === 400 && weak.json.error.code === 'WEAK_PASSWORD', JSON.stringify(weak.json));

  const wrongLogin = await call('POST', '/api/auth/login', { callsign: 'BA4IGG', password: 'nope12345' });
  check('错误密码被拒', wrongLogin.status === 401 && wrongLogin.json.error.code === 'BAD_CREDENTIALS', JSON.stringify(wrongLogin.json));

  const unknownLogin = await call('POST', '/api/auth/login', { callsign: 'BI1ABC', password: 'secret123' });
  check('不存在呼号同样返回凭证错误', unknownLogin.status === 401 && unknownLogin.json.error.code === 'BAD_CREDENTIALS', JSON.stringify(unknownLogin.json));

  const login = await call('POST', '/api/auth/login', { callsign: 'BA4IGG', password: 'secret123' });
  check('登录成功', login.status === 200 && !!login.json.token, JSON.stringify(login.json));
  const token = login.json.token;

  const noToken = await call('GET', '/api/auth/me');
  check('无令牌访问 me 被拒', noToken.status === 401 && noToken.json.error.code === 'NO_TOKEN', JSON.stringify(noToken.json));

  const badToken = await call('GET', '/api/auth/me', null, 'not.a.token');
  check('无效令牌被拒', badToken.status === 401 && badToken.json.error.code === 'TOKEN_EXPIRED', JSON.stringify(badToken.json));

  const me = await call('GET', '/api/auth/me', null, token);
  check('me 返回资料', me.status === 200 && me.json.user?.callsign === 'BA4IGG', JSON.stringify(me.json));

  const renamed = await call('PATCH', '/api/auth/profile', { nickname: '老张' }, token);
  check('改昵称成功', renamed.status === 200 && renamed.json.user?.nickname === '老张', JSON.stringify(renamed.json));

  const badOld = await call('POST', '/api/auth/password', { oldPassword: 'wrong123', newPassword: 'newpass123' }, token);
  check('原密码错误被拒', badOld.status === 401 && badOld.json.error.code === 'BAD_CREDENTIALS', JSON.stringify(badOld.json));

  const changed = await call('POST', '/api/auth/password', { oldPassword: 'secret123', newPassword: 'newpass123' }, token);
  check('改密码成功', changed.status === 200 && changed.json.ok === true, JSON.stringify(changed.json));

  const relogin = await call('POST', '/api/auth/login', { callsign: 'BA4IGG', password: 'newpass123' });
  check('新密码可登录', relogin.status === 200 && !!relogin.json.token, JSON.stringify(relogin.json));

  const oldLogin = await call('POST', '/api/auth/login', { callsign: 'BA4IGG', password: 'secret123' });
  check('旧密码失效', oldLogin.status === 401, JSON.stringify(oldLogin.json));
}
