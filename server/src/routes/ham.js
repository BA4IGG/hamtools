'use strict';

/**
 * 联网板块的数据聚合（可选部署）。
 *
 * App 的 DX 集群与呼号查询都有内置离线数据，这个路由只是「在线刷新」的数据来源：
 * 未部署、网络不可达或上游源失败时，App 会保留内置数据并在界面上如实标注降级状态。
 *
 * 数据源：
 * - DX spots：DXWatch 公开实时 DX cluster 页面（HTML 表格，无需密钥）
 * - 呼号资料：HamDB 免费公开 API（无需密钥），由 /callsign 代理
 *
 * 服务端不需要任何 API 密钥。请求只在终端用户主动刷新时发生，并做 60 秒缓存。
 */

// 幂等加载：即使自检脚本直接 require 本模块（不经过 index.js），也能读到 .env。
require('dotenv').config();

const express = require('express');
const router = express.Router();

const APP_UA = 'HAM-Tools/1.2 (+https://github.com/BA4IGG/hamtools)';
const DXWATCH_URL = process.env.DXWATCH_URL || 'https://www.dxwatch.com/dx';
const HAMDB_URL = process.env.HAMDB_URL || 'https://api.hamdb.org/v1/';
const TIMEOUT_MS = 8000;
const CACHE_MS = 60 * 1000;

/**
 * 可选代理支持：Node 原生 fetch 默认不读 HTTPS_PROXY。
 * 仅当项目里安装了 undici 且配置了代理时才启用；没有依赖就保持直连。
 */
let fetchImpl = globalThis.fetch;
let fetchDispatcher;
let proxyNote = '未启用（直连）';
try {
  const undici = require('undici');
  const proxy = process.env.HTTPS_PROXY || process.env.https_proxy ||
    process.env.HTTP_PROXY || process.env.http_proxy;
  if (proxy) {
    fetchDispatcher = new undici.ProxyAgent(proxy);
    fetchImpl = undici.fetch;
    proxyNote = '已启用（undici ProxyAgent）';
  } else {
    proxyNote = '已安装 undici，但未配置代理变量（直连）';
  }
} catch (_) {
  proxyNote = '未安装 undici，忽略代理变量（直连）';
}

let cache = { at: 0, payload: null };

/** 清洗上游错误文本：绝不让 URL 进入响应 / 日志。 */
function sanitize(message) {
  return String(message || '')
    .replace(/https?:\/\/\S+/gi, '(url)')
    .replace(/\s+/g, ' ')
    .slice(0, 160);
}

const SAMPLE_SPOTS = [
  { time: '08:12', callsign: 'EXAMPLE-1', freqKHz: 14085, spotter: 'DEMO-2', comment: 'FT8 · 服务端样例' },
  { time: '08:24', callsign: 'EXAMPLE-3', freqKHz: 21074, spotter: 'DEMO-4', comment: 'FT8 · 服务端样例' },
  { time: '08:47', callsign: 'EXAMPLE-5', freqKHz: 7032, spotter: 'DEMO-6', comment: 'CW · 服务端样例' },
  { time: '09:05', callsign: 'EXAMPLE-7', freqKHz: 28495, spotter: 'DEMO-8', comment: 'SSB · 服务端样例' },
  { time: '09:31', callsign: 'EXAMPLE-9', freqKHz: 14205, spotter: 'DEMO-1', comment: 'SSB · 服务端样例' },
  { time: '09:58', callsign: 'EXAMPLE-2', freqKHz: 18130, spotter: 'DEMO-3', comment: 'CW · 服务端样例' }
];

const TR_RE = /<tr[^>]*>([\s\S]*?)<\/tr>/gi;
const TD_RE = /<td[^>]*>([\s\S]*?)<\/td>/gi;
const QRZ_RE = /\/qrz\/[^"'/?\s]+/i;
const FREQ_RE = /^\d{3,6}(?:\.\d+)?$/;
const TIME_RE = /\d{4}z\s+\d{1,2}\s+[a-z]{3}/i;

async function fetchText(url) {
  const init = {
    signal: AbortSignal.timeout(TIMEOUT_MS),
    headers: {
      'User-Agent': APP_UA,
      Accept: 'application/json, text/html;q=0.9, */*;q=0.8'
    }
  };
  if (fetchDispatcher) init.dispatcher = fetchDispatcher;
  const res = await fetchImpl(url, init);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.text();
}

function stripTags(raw) {
  return String(raw)
    .replace(/<[^>]*>/g, ' ')
    .replace(/&nbsp;/gi, ' ')
    .replace(/&amp;/gi, '&')
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/&quot;/gi, '"')
    .replace(/&#39;/g, "'")
    .replace(/\s+/g, ' ')
    .trim();
}

function formatTime(raw) {
  const m = /(\d{4})z/i.exec(raw);
  if (!m) return raw.slice(0, 16);
  return `${m[1].slice(0, 2)}:${m[1].slice(2)}`;
}

/** DXWatch 没有公开 JSON API，只解析它的 spots 表格；结构变化时返回空数组由上层降级。 */
function parseDxWatch(html) {
  if (!html) return [];
  const out = [];
  for (const rowMatch of html.matchAll(TR_RE)) {
    if (out.length >= 60) break;
    const tds = [...rowMatch[1].matchAll(TD_RE)].map((m) => m[1]);
    if (tds.length < 4) continue;
    const cells = tds.map(stripTags);
    const linkIdx = [];
    tds.forEach((td, i) => {
      if (QRZ_RE.test(td)) linkIdx.push(i);
    });
    if (linkIdx.length < 2) continue;
    const spotter = cells[linkIdx[0]];
    const dx = cells[linkIdx[1]];
    const freqIdx = cells.findIndex((c) => FREQ_RE.test(c));
    const timeIdx = cells.findIndex((c) => TIME_RE.test(c));
    if (!dx || freqIdx < 0 || timeIdx < 0) continue;
    const freq = Number(cells[freqIdx]);
    if (!(freq > 0)) continue;
    const comment = timeIdx > freqIdx + 1
      ? cells.slice(freqIdx + 1, timeIdx).join(' ').trim()
      : '';
    out.push({
      time: formatTime(cells[timeIdx]),
      callsign: dx,
      freqKHz: freq,
      spotter,
      comment
    });
  }
  return out;
}

async function buildOverview(force) {
  if (!force && cache.payload && Date.now() - cache.at < CACHE_MS) {
    return cache.payload;
  }

  let dxSpots = [];
  let spotsError = null;
  try {
    dxSpots = parseDxWatch(await fetchText(DXWATCH_URL));
  } catch (error) {
    spotsError = sanitize(error.message);
  }
  const liveSpots = dxSpots.length > 0;
  const spotsSource = liveSpots
    ? 'DXWatch 实时 DX cluster（dxwatch.com）'
    : '服务端内置样例（DXWatch 不可用）';

  const payload = {
    ok: true,
    updatedAt: new Date().toISOString(),
    liveSpots,
    spotsSource,
    source: spotsSource,
    spots: liveSpots ? dxSpots : SAMPLE_SPOTS,
    diagnostics: { proxy: proxyNote, spotsError }
  };

  cache = { at: Date.now(), payload };
  return payload;
}

router.get('/overview', async (req, res) => {
  try {
    res.json(await buildOverview(req.query.refresh === '1'));
  } catch (error) {
    console.warn('[ham] overview 聚合失败，返回样例数据：', sanitize(error.message));
    res.json({
      ok: false,
      updatedAt: new Date().toISOString(),
      liveSpots: false,
      spotsSource: '服务端内置样例（聚合失败）',
      source: '服务端内置样例（在线源不可用）',
      spots: SAMPLE_SPOTS,
      diagnostics: { proxy: proxyNote, spotsError: '聚合异常' }
    });
  }
});

/** 呼号资料代理：HamDB 免费公开 API。失败时返回 NOT_FOUND 让 App 降级。 */
router.get('/callsign', async (req, res) => {
  const q = String(req.query.q || '')
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, '')
    .slice(0, 12);
  if (q.length < 3) {
    return res.json({ hamdb: { messages: { status: 'NOT_FOUND' } } });
  }
  try {
    const body = await fetchText(`${HAMDB_URL}${q}/json/hamtools-ba4igg`);
    res.type('application/json').send(body);
  } catch (error) {
    console.warn('[ham] HamDB 代理失败：', sanitize(error.message));
    res.json({ hamdb: { messages: { status: 'NOT_FOUND' } } });
  }
});

// 导出纯函数与聚合入口，便于本地自检脚本验证解析与降级逻辑
module.exports = { router, parseDxWatch, formatTime, stripTags, buildOverview, APP_UA, proxyNote };
