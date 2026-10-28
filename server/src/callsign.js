'use strict';

/**
 * 中国业余电台呼号：B + 电台种类字母 + 分区数字 + 1~4 位后缀（字母或字母数字组合）。
 * 依据工信部《业余无线电台呼号管理办法》，例如 BA4IGG、BG5XYZ、BI1ABC。
 * QOA~QUZ 以及 SOS / XXX / TTT 不作为后缀。
 */
const CALLSIGN_RE = /^B[A-Z]\d[A-Z0-9]{1,4}$/;
const RESERVED_SUFFIX_RE = /^Q[O-U][A-Z0-9]$/;
const FORBIDDEN_SUFFIX = new Set(['SOS', 'XXX', 'TTT', 'BPV', 'BPM']);

/** 呼号分区号 → 所在地区（工信部业余电台分区表）。 */
const REGION_BY_DIGIT = {
  0: '新疆、西藏',
  1: '北京',
  2: '黑龙江、吉林、辽宁',
  3: '天津、内蒙古、河北、山西',
  4: '上海、山东、江苏',
  5: '浙江、江西、福建',
  6: '河南、湖北、安徽',
  7: '湖南、广东、广西、海南',
  8: '四川、重庆、贵州、云南',
  9: '陕西、甘肃、宁夏、青海',
};

/** 规范化：去空格、转大写；返回 null 表示格式非法。 */
function normalizeCallsign(raw) {
  if (typeof raw !== 'string') return null;
  const value = raw.trim().toUpperCase().replace(/\s+/g, '');
  if (!CALLSIGN_RE.test(value)) return null;
  const suffix = value.slice(3);
  if (RESERVED_SUFFIX_RE.test(suffix) || FORBIDDEN_SUFFIX.has(suffix)) return null;
  return value;
}

/** 取呼号分区对应的地区名，非法呼号返回 null。 */
function callsignRegion(callsign) {
  const normalized = normalizeCallsign(callsign);
  if (!normalized) return null;
  return REGION_BY_DIGIT[Number(normalized.charAt(2))] || null;
}

module.exports = {
  CALLSIGN_RE,
  REGION_BY_DIGIT,
  normalizeCallsign,
  callsignRegion,
};
