/* HAM 工具合集官网 · 交互层
   主题 / 导航 / 状态层 / 滚动显现 / 目录高亮 */
(function () {
  'use strict';

  var root = document.documentElement;
  var KEY = 'ham-theme';
  var mq = window.matchMedia('(prefers-color-scheme: dark)');

  function stored() { try { return localStorage.getItem(KEY); } catch (e) { return null; } }
  function save(v) { try { localStorage.setItem(KEY, v); } catch (e) { /* 隐私模式下忽略 */ } }
  function current() { return root.getAttribute('data-theme') === 'dark' ? 'dark' : 'light'; }

  function paint(v) {
    root.setAttribute('data-theme', v);
    document.querySelectorAll('[data-theme-toggle]').forEach(function (b) {
      var use = b.querySelector('use');
      if (use) use.setAttribute('href', v === 'dark' ? '#i-sun' : '#i-moon');
      var label = v === 'dark' ? '切换到浅色主题' : '切换到深色主题';
      b.setAttribute('aria-label', label);
      b.setAttribute('title', label);
    });
  }

  paint(current()); // 与 <head> 里的防闪烁脚本保持一致

  document.querySelectorAll('[data-theme-toggle]').forEach(function (b) {
    b.addEventListener('click', function () {
      var next = current() === 'dark' ? 'light' : 'dark';
      save(next);
      paint(next);
    });
  });

  // 用户没有手动选过主题时，跟随系统切换
  function onSchemeChange(e) { if (!stored()) paint(e.matches ? 'dark' : 'light'); }
  if (mq.addEventListener) mq.addEventListener('change', onSchemeChange);
  else if (mq.addListener) mq.addListener(onSchemeChange);

  // ---- 移动端导航 ----------------------------------------------------
  var toggle = document.querySelector('[data-nav-toggle]');
  var nav = document.getElementById('primary-nav');
  function setNav(open) {
    if (!nav || !toggle) return;
    nav.classList.toggle('is-open', open);
    toggle.setAttribute('aria-expanded', String(open));
    var use = toggle.querySelector('use');
    if (use) use.setAttribute('href', open ? '#i-close' : '#i-menu');
  }
  if (toggle) toggle.addEventListener('click', function () { setNav(!nav.classList.contains('is-open')); });
  if (nav) nav.addEventListener('click', function (e) { if (e.target.closest('a')) setNav(false); });
  document.addEventListener('keydown', function (e) { if (e.key === 'Escape') setNav(false); });

  // ---- 顶栏阴影 / 返回顶部 --------------------------------------------
  var bar = document.querySelector('.app-bar');
  var backTop = document.querySelector('.back-top');
  function onScroll() {
    var y = window.scrollY || window.pageYOffset || 0;
    if (bar) bar.classList.toggle('is-scrolled', y > 8);
    if (backTop) backTop.classList.toggle('is-visible', y > 600);
  }
  window.addEventListener('scroll', onScroll, { passive: true });
  onScroll();
  if (backTop) backTop.addEventListener('click', function () {
    window.scrollTo({ top: 0, behavior: 'smooth' });
  });

  // ---- Material 状态层涟漪 --------------------------------------------
  document.addEventListener('pointerdown', function (e) {
    var el = e.target && e.target.closest ? e.target.closest('.btn, .icon-btn') : null;
    if (!el) return;
    var r = el.getBoundingClientRect();
    var size = Math.max(r.width, r.height) * 2;
    var s = document.createElement('span');
    s.className = 'ripple';
    s.style.width = size + 'px';
    s.style.height = size + 'px';
    s.style.left = (e.clientX - r.left - size / 2) + 'px';
    s.style.top = (e.clientY - r.top - size / 2) + 'px';
    el.appendChild(s);
    window.setTimeout(function () { s.remove(); }, 660);
  });

  // ---- 进入视口时显现 --------------------------------------------------
  var reveals = document.querySelectorAll('.reveal');
  if ('IntersectionObserver' in window && reveals.length) {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (en) {
        if (en.isIntersecting) {
          en.target.classList.add('is-visible');
          io.unobserve(en.target);
        }
      });
    }, { rootMargin: '0px 0px -8% 0px', threshold: 0.06 });
    reveals.forEach(function (el) { io.observe(el); });
  } else {
    reveals.forEach(function (el) { el.classList.add('is-visible'); });
  }

  // 兜底：某些嵌入式预览环境不派发 IntersectionObserver 回调，
  // 1.5 秒后把首屏内仍未显现的元素直接显示，避免内容被隐藏。
  window.setTimeout(function () {
    document.querySelectorAll('.reveal:not(.is-visible)').forEach(function (el) {
      if (el.getBoundingClientRect().top < window.innerHeight) el.classList.add('is-visible');
    });
  }, 1500);

  // ---- 法律页目录跟随高亮 ----------------------------------------------
  var links = Array.prototype.slice.call(document.querySelectorAll('.toc a[href^="#"]'));
  if (links.length && 'IntersectionObserver' in window) {
    var pairs = links.map(function (a) {
      return { link: a, target: document.querySelector(a.getAttribute('href')) };
    }).filter(function (o) { return o.target; });
    var spy = new IntersectionObserver(function (entries) {
      entries.forEach(function (en) {
        if (!en.isIntersecting) return;
        links.forEach(function (a) { a.classList.remove('is-active'); });
        var hit = pairs.filter(function (o) { return o.target === en.target; })[0];
        if (hit) hit.link.classList.add('is-active');
      });
    }, { rootMargin: '-100px 0px -70% 0px' });
    pairs.forEach(function (o) { spy.observe(o.target); });
  }

  // ---- 网站备案信息 ----------------------------------------------------
  // 号码同时写在下方 FILINGS 与各页 HTML 的 [data-filing-text] 中，两处需保持一致：
  // FILINGS 负责自动生成公安备案查询链接，HTML 兜底保证禁用 JS 时也能显示。
  var FILINGS = {
    icp: '鲁ICP备2025143500号',
    police: '鲁公网安备37142402000186号'
  };
  var ICP_HOME = 'https://beian.miit.gov.cn/';
  var POLICE_HOME = 'https://www.beian.gov.cn/';
  function renderFiling(key, label, value, home, detail) {
    var el = document.querySelector('[data-filing="' + key + '"]');
    if (!el || !value) return;
    var text = el.querySelector('[data-filing-text]');
    if (text) text.textContent = label + value;
    el.setAttribute('href', detail ? detail(value, home) : home);
    el.removeAttribute('data-filing-placeholder');
    el.setAttribute('aria-label', label + value + '（在新窗口打开备案查询）');
  }
  renderFiling('icp', 'ICP 备案号：', FILINGS.icp, ICP_HOME);
  renderFiling('police', '公安备案号：', FILINGS.police, POLICE_HOME, function (v, home) {
    var digits = v.match(/\d{10,}/);
    return digits ? 'https://www.beian.gov.cn/portal/registerSystemInfo?recordcode=' + digits[0] : home;
  });

  // ---- 杂项 ------------------------------------------------------------
  document.querySelectorAll('[data-year]').forEach(function (el) {
    el.textContent = new Date().getFullYear();
  });
  var page = document.body.getAttribute('data-page');
  if (page) {
    document.querySelectorAll('.nav a[data-nav="' + page + '"]').forEach(function (a) {
      a.classList.add('is-active');
    });
  }
})();
