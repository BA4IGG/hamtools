'use strict';

/**
 * QSL 接口自检：不连数据库、不启动服务，只检查纯逻辑与跨端契约。
 *
 * 用法：npm run qsl:check
 *
 * 检查项：
 * 1. 呼号规范化、图片文件头白名单、交换状态机；
 * 2. 服务端错误码是否都能被 App 端 QslErrorCode 识别（避免提示退化成「服务器开小差」）；
 * 3. schema.sql / db.js / index.js 里 QSL 的建表与挂载是否齐全。
 *
 * 生产机一般只部署 server/ 这一个目录，没有 Android 源码；此时第 2 项的跨端
 * 枚举比对会跳过并如实提示，不算失败。自检绝不依赖仓库根目录下的其它文件。
 */

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

// 这几个模块顶层不会连接数据库，但需要给 auth.js 一个合法的 JWT_SECRET 占位
process.env.JWT_SECRET = process.env.JWT_SECRET || 'qsl-selfcheck-placeholder-secret';

const qsl = require('../src/routes/qsl');
// 部署目录：本地是仓库的 server/，线上是 /www/wwwroot/xxx（目录里直接就是 src/、schema.sql）
const serverRoot = path.resolve(__dirname, '..');

let passed = 0;
const failures = [];

function check(name, fn) {
  try {
    fn();
    passed += 1;
    console.log(`  ✓ ${name}`);
  } catch (error) {
    failures.push(`${name}: ${error.message}`);
    console.log(`  ✗ ${name}: ${error.message}`);
  }
}

console.log('QSL 服务端自检（无需数据库）');

check('normalizePeer 接受国际呼号与斜杠后缀', () => {
  assert.equal(qsl.normalizePeer(' ja1abc '), 'JA1ABC');
  assert.equal(qsl.normalizePeer('w1aw/p'), 'W1AW/P');
  assert.equal(qsl.normalizePeer('9m2pju'), '9M2PJU');
  assert.equal(qsl.normalizePeer('ba4igg-7'), null);
  assert.equal(qsl.normalizePeer(''), null);
  assert.equal(qsl.normalizePeer('DROP TABLE'), null);
  assert.equal(qsl.normalizePeer(123), null);
});

check('detectImage 只认 JPG/PNG/WebP 文件头', () => {
  const jpeg = Buffer.alloc(16);
  jpeg[0] = 0xff; jpeg[1] = 0xd8; jpeg[2] = 0xff;
  assert.equal(qsl.detectImage(jpeg).mime, 'image/jpeg');

  const png = Buffer.alloc(16);
  Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]).copy(png);
  assert.equal(qsl.detectImage(png).ext, 'png');

  const webp = Buffer.alloc(16);
  Buffer.from('RIFF').copy(webp, 0);
  Buffer.from('WEBP').copy(webp, 8);
  assert.equal(qsl.detectImage(webp).mime, 'image/webp');

  assert.equal(qsl.detectImage(Buffer.alloc(16)), null);
  assert.equal(qsl.detectImage(Buffer.alloc(4)), null);
  assert.equal(qsl.detectImage('not a buffer'), null);
});

check('状态机：纸质需要邮寄环节，eQSL 接受即完成', () => {
  const n = qsl.nextStatus;
  assert.equal(n('accept', 'paper', 'pending'), 'accepted');
  assert.equal(n('accept', 'eqsl', 'pending'), 'completed');
  assert.equal(n('sent', 'paper', 'accepted'), 'sent');
  assert.equal(n('sent', 'eqsl', 'accepted'), null);
  assert.equal(n('sent', 'paper', 'pending'), null);
  assert.equal(n('complete', 'paper', 'sent'), 'completed');
  assert.equal(n('complete', 'paper', 'pending'), null);
  assert.equal(n('decline', 'paper', 'pending'), 'declined');
  assert.equal(n('cancel', 'eqsl', 'accepted'), 'cancelled');
  assert.equal(n('cancel', 'paper', 'completed'), null);
  assert.equal(n('unknown', 'paper', 'pending'), null);
});

check('状态集合完整', () => {
  for (const status of ['pending', 'accepted', 'sent', 'completed', 'declined', 'cancelled', 'recorded']) {
    assert.ok(qsl.ALL_STATUSES.includes(status), `缺少 ${status}`);
  }
});

check('rowToExchange 方向、注册标记与时间格式', () => {
  const row = {
    id: 42, mode: 'eqsl', peer_callsign: 'JA1ABC',
    from_user_id: 7, to_user_id: 9,
    qso_date: '2026-11-10', qso_band: '40m', qso_mode: 'CW',
    rst_sent: '599', rst_rcvd: '579', note: 'TNX', status: 'pending',
    from_card_id: null, to_card_id: 5,
    created_at: new Date('2026-11-10T00:00:00Z'), updated_at: null,
  };
  const mine = qsl.rowToExchange(row, 7);
  assert.equal(mine.direction, 'outgoing');
  assert.equal(mine.registeredPeer, true);
  assert.equal(mine.toCardId, 5);
  assert.equal(mine.note, 'TNX');
  assert.equal(mine.createdAt, '2026-11-10T00:00:00.000Z');
  assert.equal(qsl.rowToExchange(row, 9).direction, 'incoming');
  const unregistered = qsl.rowToExchange({ ...row, to_user_id: null }, 7);
  assert.equal(unregistered.registeredPeer, false);
});

check('QSL 接口错误码全部被 App 端枚举覆盖（生产机可跳过跨端比对）', () => {
  const qslSource = fs.readFileSync(path.join(serverRoot, 'src/routes/qsl.js'), 'utf8');
  const used = new Set();
  for (const match of qslSource.matchAll(/fail\(res, \d+, '([A-Z_]+)'/g)) used.add(match[1]);
  // requireAuth 复用了 auth.js 的令牌校验，它的这几个码也会从 QSL 接口透出
  for (const code of ['NO_TOKEN', 'TOKEN_EXPIRED', 'NO_USER', 'SERVER_ERROR']) used.add(code);
  assert.ok(used.size >= 4, '未能从 qsl.js 提取到服务端错误码');
  for (const code of used) assert.match(code, /^[A-Z_]+$/, `错误码格式异常：${code}`);

  const kotlinPath = path.join(
    serverRoot, '..', 'app/src/main/java/com/ham/tools/data/qsl/QslModels.kt'
  );
  if (!fs.existsSync(kotlinPath)) {
    console.log('    ⓘ 未检测到 Android 源码，跳过 App 端枚举比对（生产机只部署 server/，属正常）');
    return;
  }
  const kotlin = fs.readFileSync(kotlinPath, 'utf8');
  // 截到枚举末尾的分号为止，不要把后面的 companion object 也扫进来
  const enumBlock = kotlin.match(/enum class QslErrorCode \{([\s\S]*?);/);
  assert.ok(enumBlock, '未找到 QslErrorCode 枚举');
  const known = new Set(
    enumBlock[1].split(',')
      .map((line) => line.trim())
      .filter((name) => /^[A-Z_]+$/.test(name))
  );
  const missing = [...used].filter((code) => !known.has(code));
  assert.deepEqual(missing, [], `App 端缺少错误码：${missing.join(', ')}`);
});

check('schema.sql / db.js / index.js 的 QSL 接线齐全', () => {
  const schema = fs.readFileSync(path.join(serverRoot, 'schema.sql'), 'utf8');
  const dbSource = fs.readFileSync(path.join(__dirname, '../src/db.js'), 'utf8');
  const indexSource = fs.readFileSync(path.join(__dirname, '../src/index.js'), 'utf8');
  for (const table of ['qsl_profiles', 'qsl_cards', 'qsl_exchanges']) {
    assert.ok(schema.includes(`CREATE TABLE IF NOT EXISTS \`${table}\``), `schema.sql 缺 ${table}`);
    assert.ok(dbSource.includes(table), `db.js ensureSchema 缺 ${table}`);
  }
  assert.ok(indexSource.includes("app.use('/api/qsl'"), 'index.js 未挂载 /api/qsl');
});

console.log('');
if (failures.length > 0) {
  console.error(`自检失败：${passed} 项通过，${failures.length} 项失败`);
  failures.forEach((f) => console.error(`  - ${f}`));
  process.exit(1);
}
console.log(`全部通过：${passed} 项检查`);
