'use strict';

/**
 * 考试练习云同步的纯逻辑自检：不需要 MySQL、不需要 .env、不连网、不需要 Android 源码。
 * 用法：npm run exam:check
 *
 * 校验服务端字段白名单与边界，以及「路由挂载 / 建表 / 接口定义」是否一致。
 * 生产机通常只部署 server/ 这一个目录，所以自检只读取本目录内的文件；
 * 如果恰好同时存在 Android 工程源码，会附带核对 App 端接口路径（找不到就跳过，不算失败）。
 */

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { sanitize } = require('../src/routes/exam');

// 这里的 root 就是服务端的部署目录（本地是仓库的 server/，线上是 /www/wwwroot/xxx）
const root = path.resolve(__dirname, '..');
let passed = 0;

function check(name, fn) {
  fn();
  passed += 1;
  console.log(`  ✓ ${name}`);
}

check('规范化 App 端 v2 学习记录', () => {
  const payload = {
    v: 2,
    p: { 'MC1-0001': { s: 3, l: 100, n: 200, c: 5, w: 2, x: 1, e: 1 } },
    h: [{ lv: 'A', m: 'EXAM', s: 33, t: 40, p: 1, a: 1700000000000,
      id: 'rec-1', el: 120000, sc: 26, st: 32, mc: 7, mt: 8,
      wi: ['MC1-0001', 'MC2-0002'], to: 0 }],
    c: { A: 12, B: 3 },
  };
  const out = sanitize(payload);
  assert.equal(out.v, 2);
  assert.equal(out.p['MC1-0001'].s, 3);
  assert.equal(out.p['MC1-0001'].e, 1);
  assert.equal(out.h.length, 1);
  assert.equal(out.h[0].sc, 26);
  assert.equal(out.h[0].wi.length, 2);
  assert.deepEqual(out.c, { A: 12, B: 3 });
});

check('丢弃未知字段，不把任意内容写进数据库', () => {
  const out = sanitize({
    v: 99,
    p: { Q1: { s: 1, evil: 'DROP TABLE', profile: { callsign: 'BA4IGG' } } },
    h: [{ lv: 'C', m: 'EXAM', password: 'secret', wi: ['A', 42, null] }],
    c: { A: 5 },
    unexpected: { anything: true },
  });
  assert.equal(out.v, 2);
  assert.equal(out.p.Q1.evil, undefined);
  assert.equal(out.p.Q1.profile, undefined);
  assert.equal(out.h[0].password, undefined);
  assert.deepEqual(out.h[0].wi, ['A']);
  assert.equal(out.unexpected, undefined);
});

check('空记录、缺字段与旧版 v1 记录都能安全读取', () => {
  assert.deepEqual(sanitize({}), { v: 2, p: {}, h: [], c: {} });
  const legacy = sanitize({ p: { Q1: { s: 1, l: 10, n: 20, c: 1, w: 0, x: 0 } },
    h: [{ lv: 'A', m: 'EXAM', s: 30, t: 40, p: 1, a: 100 }] });
  assert.equal(legacy.h[0].id, '');
  assert.equal(legacy.h[0].el, 0);
  assert.equal(legacy.p.Q1.e, 0);
});

check('非法题号、非法类别与超量数据一律拒绝', () => {
  assert.equal(sanitize({ p: { 'bad id!': { s: 1 } } }), null);
  assert.equal(sanitize({ c: { Z: 1 } }), null);
  assert.equal(sanitize({ h: 'not-an-array' }), null);
  assert.equal(sanitize({ p: 'not-an-object' }), null);
  assert.equal(sanitize(null), null);
  const tooMany = {};
  for (let i = 0; i < 5001; i += 1) tooMany[`Q${i}`] = { s: 1 };
  assert.equal(sanitize({ p: tooMany }), null);
});

check('数值收敛：时间戳不为负、超大值被夹住', () => {
  const out = sanitize({
    p: { Q1: { s: -99999999999, l: -5, n: 1e30, c: 1.9 } },
    c: { A: -12 },
  });
  assert.equal(out.p.Q1.l, 0);
  assert.ok(out.p.Q1.n <= 9000000000000000);
  assert.equal(out.p.Q1.c, 1);
  assert.equal(out.c.A, -12);
});

check('路由挂载、建表与接口定义一致（仅依赖服务端目录）', () => {
  const indexJs = fs.readFileSync(path.join(root, 'src/index.js'), 'utf8');
  const dbJs = fs.readFileSync(path.join(root, 'src/db.js'), 'utf8');
  const examJs = fs.readFileSync(path.join(root, 'src/routes/exam.js'), 'utf8');
  const schema = fs.readFileSync(path.join(root, 'schema.sql'), 'utf8');
  assert.match(indexJs, /app\.use\('\/api\/exam', examRoutes\.router\)/);
  assert.match(indexJs, /express\.json\(\{ limit: '1mb' \}\)/);
  assert.match(dbJs, /CREATE TABLE IF NOT EXISTS exam_progress/);
  assert.match(schema, /CREATE TABLE IF NOT EXISTS `exam_progress`/);
  for (const route of [
    "router.get('/ping'", "router.get('/progress'",
    "router.put('/progress'", "router.delete('/progress'",
  ]) {
    assert.ok(examJs.includes(route), `exam.js 缺少 ${route}`);
  }
  assert.match(examJs, /feature: 'exam-sync', version: 2/);
  assert.match(examJs, /module\.exports = \{ router, sanitize \}/);
});

// 跨端 URL 核对只是开发期的额外保险：生产机没有 app/ 目录是正常情况，绝不能因此报错。
const appApiFile = path.join(
  root, '..', 'app/src/main/java/com/ham/tools/data/exam/ExamCloudApi.kt'
);
if (fs.existsSync(appApiFile)) {
  const api = fs.readFileSync(appApiFile, 'utf8');
  for (const endpoint of ['/api/exam/ping', '/api/exam/progress']) {
    assert.ok(api.includes(endpoint), `App 端缺少 ${endpoint}`);
  }
  console.log('  ✓ 附：检测到 Android 源码，跨端接口路径一致');
} else {
  console.log('  ⓘ 未检测到 Android 源码，跳过跨端 URL 核对（生产机只部署 server/，属正常）');
}

console.log(`\n考试练习云同步自检通过：${passed} 项`);
