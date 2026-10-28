'use strict';

require('dotenv').config();

const express = require('express');
const cors = require('cors');

const db = require('./db');
const authRoutes = require('./routes/auth');

const app = express();
const PORT = Number(process.env.PORT || 3000);

app.use(cors());
app.use(express.json({ limit: '64kb' }));

// 健康检查：App 的「服务器设置」就是靠它判断地址是否可用
app.get('/api/health', (_req, res) => {
  res.json({ ok: true, service: 'ham-tools-auth', time: new Date().toISOString() });
});

app.use('/api/auth', authRoutes.router);

app.use((_req, res) => {
  res.status(404).json({ error: { code: 'NOT_FOUND', message: '接口不存在' } });
});

// eslint-disable-next-line no-unused-vars
app.use((error, _req, res, _next) => {
  console.error('[server] unhandled error:', error.message);
  res.status(500).json({ error: { code: 'SERVER_ERROR', message: '服务器内部错误' } });
});

async function main() {
  // 启动时自检配置，避免“跑起来了但一登录就 500”
  if (!process.env.JWT_SECRET || process.env.JWT_SECRET.length < 16) {
    console.error('启动失败：请在 .env 中设置至少 16 位的 JWT_SECRET');
    process.exit(1);
  }
  await db.ensureSchema();
  await db.pool.query('SELECT 1');
  console.log(`数据库就绪：${process.env.DB_NAME || 'hamtools'}@${process.env.DB_HOST || '127.0.0.1'}`);

  app.listen(PORT, () => {
    console.log(`HAM工具合集账号服务已启动：http://0.0.0.0:${PORT}`);
    console.log(`自检地址：http://127.0.0.1:${PORT}/api/health`);
  });
}

main().catch((error) => {
  console.error('启动失败：', error.message);
  process.exit(1);
});
