#!/usr/bin/env node
/**
 * Mobile render audit — measures the REAL rendered page in a REAL browser.
 *
 * Why this exists: every earlier audit either parsed CSS text or mounted the
 * app in jsdom. jsdom has no layout engine - getBoundingClientRect() returns
 * zeros - so it can tell you a rule exists but never that a button is actually
 * 34px on screen, that a row overflows, or that two controls overlap. Reading
 * the stylesheet also can't see what the cascade, flexbox and real font
 * metrics finally produce.
 *
 * This drives headless Chromium at real phone viewports and measures what a
 * person would actually touch:
 *
 *   1. horizontal overflow  — the page scrolls sideways (the classic mobile bug)
 *   2. tap targets          — interactive boxes smaller than 44x44 CSS px
 *   3. overlapping controls — two tappable things covering the same pixels
 *   4. offscreen content    — elements poking outside the viewport
 *   5. text legibility      — computed font-size below the floor
 *   6. content under chrome — content hidden behind the fixed bottom nav
 *   7. images               — broken or unsized (layout-shift) images
 *
 * Usage:
 *   npm run test:mobile                           # all devices, all routes
 *   node tests/mobile-render-audit.mjs --shots    # also write screenshots
 *
 * Requires a real Chromium binary plus the dev server and API running:
 *   npx vite preview --host 0.0.0.0 --port 5173 --strictPort
 *   CHROME_PATH=/path/to/chromium npm run test:mobile
 * On a stock Linux box `npx playwright install chromium` provides one. This is
 * deliberately NOT part of `test:all`, which must stay runnable anywhere with
 * nothing but node - a browser download is not something CI can assume.
 *
 * Two lessons are baked into the checks below, both learned the hard way:
 *   - Overlap detection must be scroll-container aware. An element inside its
 *     own scroller legitimately extends past that scroller; comparing its
 *     un-scrolled rect to something outside reports collisions nobody can see.
 *   - A green run is not a correct page. This suite reported zero findings
 *     while a screenshot plainly showed the verification panel sliced off the
 *     right edge, because the overflowing node's PARENT fit. Take screenshots
 *     and look at them.
 */
import { chromium } from 'playwright-core';
import { mkdirSync } from 'node:fs';

const BASE = process.env.WEB_BASE || 'http://127.0.0.1:5173';
const API = process.env.API_BASE || 'http://127.0.0.1:3001';
const CHROME = process.env.CHROME_PATH || '/tmp/chr/bin/chromium';
const SHOTS = process.argv.includes('--shots');
const SHOT_DIR = '/tmp/mobile-shots';

const G = (s) => `\x1b[32m${s}\x1b[0m`;
const R = (s) => `\x1b[31m${s}\x1b[0m`;
const Y = (s) => `\x1b[33m${s}\x1b[0m`;
const B = (s) => `\x1b[1m${s}\x1b[0m`;
const D = (s) => `\x1b[2m${s}\x1b[0m`;

// Real phones people in Uganda actually use, smallest first. The 280px Galaxy
// Fold cover screen is the hardest case and catches almost everything.
const DEVICES = [
  { name: 'Galaxy Fold (cover)', width: 280, height: 653, dpr: 3 },
  { name: 'iPhone SE / Android Go', width: 320, height: 568, dpr: 2 },
  { name: 'iPhone 12 mini', width: 360, height: 780, dpr: 3 },
  { name: 'Pixel / common Android', width: 393, height: 851, dpr: 2.75 },
  { name: 'iPhone 14 Pro Max', width: 430, height: 932, dpr: 3 },
];

const TAP_FLOOR = 44;
const TEXT_FLOOR = 11;

const PUBLIC_ROUTES = ['/', '/login', '/register', '/nearby', '/search', '/ai', '/cms/about'];
const BUYER_ROUTES = ['/buyer', '/buyer/orders', '/buyer/saved', '/buyer/addresses',
  '/buyer/payments', '/buyer/refunds', '/buyer/support', '/buyer/settings', '/buyer/ai',
  '/cart', '/messages', '/notifications'];
const SELLER_ROUTES = ['/seller', '/seller/inventory', '/seller/add-product',
  '/seller/bulk-import', '/seller/orders', '/seller/analytics', '/seller/ai',
  '/seller/store-settings'];
const ADMIN_ROUTES = ['/admin', '/admin/users', '/admin/products', '/admin/queue', '/admin/support'];

const findings = [];
let checks = 0;
let remoteUnreachable = 0;
function report(device, route, kind, detail) {
  findings.push({ device, route, kind, detail });
}

async function login(email, password) {
  const r = await fetch(`${API}/api/v1/auth/login`, {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  return r.ok ? r.json() : null;
}

async function makeBuyer() {
  const email = `render_${Date.now()}@scottstechx.test`;
  const r = await fetch(`${API}/api/v1/auth/register`, {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ email, password: 'Test123!', displayName: 'Render Buyer', role: 'buyer' }),
  });
  if (r.status !== 201) return null;
  const reg = await r.json();
  const code = reg?.verification?.devCode;
  if (!code) return null;
  const c = await fetch(`${API}/api/v1/auth/verify/confirm`, {
    method: 'POST',
    headers: { 'content-type': 'application/json', authorization: `Bearer ${reg.token}` },
    body: JSON.stringify({ code }),
  });
  if (!c.ok) return null;
  const conf = await c.json();
  return { token: reg.token, user: { ...reg.user, ...(conf?.user || {}), emailVerified: true } };
}

/**
 * Everything below runs INSIDE the page, where real geometry is available.
 * Returns plain data so it can cross the bridge.
 */
function measureInPage([TAP_FLOOR, TEXT_FLOOR]) {
  // NOTE: page.evaluate passes ONE argument, so the thresholds arrive as a
  // single array and must be destructured. Taking them as two parameters made
  // TAP_FLOOR the array itself: every comparison became NaN, so every control
  // was reported as too small and TEXT_FLOOR was undefined, silently disabling
  // the text check entirely.

  const vw = document.documentElement.clientWidth;
  const vh = document.documentElement.clientHeight;
  const out = {
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: vw,
    overflowers: [],
    smallTargets: [],
    overlaps: [],
    tinyText: [],
    hiddenUnderNav: [],
    badImages: [],
  };

  const label = (el) => {
    const id = el.id ? `#${el.id}` : '';
    const cls = typeof el.className === 'string' && el.className
      ? `.${el.className.trim().split(/\s+/).slice(0, 3).join('.')}` : '';
    const txt = (el.textContent || '').trim().slice(0, 24);
    return `${el.tagName.toLowerCase()}${id}${cls}${txt ? ` "${txt}"` : ''}`;
  };

  const visible = (el) => {
    const s = getComputedStyle(el);
    if (s.display === 'none' || s.visibility === 'hidden' || Number(s.opacity) === 0) return false;
    const r = el.getBoundingClientRect();
    return r.width > 0 && r.height > 0;
  };

  // 1. What is making the page wider than the screen?
  if (document.documentElement.scrollWidth > vw + 1) {
    for (const el of document.querySelectorAll('body *')) {
      if (!visible(el)) continue;
      const r = el.getBoundingClientRect();
      // Only blame the element if its own box exceeds the viewport, and its
      // parent's does not - that finds the culprit, not every ancestor.
      if (r.right > vw + 1 || r.left < -1) {
        const p = el.parentElement;
        const pr = p ? p.getBoundingClientRect() : null;
        if (!pr || (pr.right <= vw + 1 && pr.left >= -1)) {
          out.overflowers.push({
            el: label(el),
            right: Math.round(r.right), left: Math.round(r.left), width: Math.round(r.width),
          });
        }
      }
    }
  }

  // 2. Tap targets. Only things a person actually taps, and only if visible.
  const tappable = [...document.querySelectorAll(
    'button, a[href], input:not([type=hidden]), select, textarea, [role=button], [onclick], summary'
  )];
  const seen = new Set();
  for (const el of tappable) {
    if (!visible(el)) continue;
    const r = el.getBoundingClientRect();
    // Skip things scrolled far out of view; measure what is on this screen.
    if (r.bottom < -200 || r.top > vh + 2000) continue;
    // Half-a-pixel tolerance: a 44px control routinely measures 43.99 because
    // of fractional layout, and reporting that as a failure is noise.
    if (r.width >= TAP_FLOOR - 0.5 && r.height >= TAP_FLOOR - 0.5) continue;
    // An inline link inside a paragraph is not a "button" - judging body text
    // links by the 44px rule is noise, so only flag standalone controls.
    const isInlineLink = el.tagName === 'A'
      && getComputedStyle(el).display.startsWith('inline')
      && el.parentElement
      && /^(P|SPAN|LI|TD|SMALL|LABEL|DIV)$/.test(el.parentElement.tagName)
      && (el.parentElement.textContent || '').trim().length > (el.textContent || '').trim().length + 8;
    if (isInlineLink) continue;
    // A bare <input> inside a padded wrapper (.searchbar) is not the target -
    // the wrapper is, and tapping anywhere in it focuses the field. Judge the
    // wrapper's size instead of the input's own text box.
    const wrap = el.closest('.searchbar, .input-wrap, label');
    if (wrap && wrap !== el) {
      const wr = wrap.getBoundingClientRect();
      if (wr.width >= TAP_FLOOR - 0.5 && wr.height >= TAP_FLOOR - 0.5) continue;
    }
    const key = label(el) + Math.round(r.width) + Math.round(r.height);
    if (seen.has(key)) continue;
    seen.add(key);
    out.smallTargets.push({
      el: label(el), w: Math.round(r.width * 10) / 10, h: Math.round(r.height * 10) / 10,
    });
  }

  // 3. Do two tappable things cover the same pixels? That is a mis-tap waiting
  //    to happen and usually means a layout collapsed.
  const isFixed = (el) => {
    for (let n = el; n && n !== document.body; n = n.parentElement) {
      const pos = getComputedStyle(n).position;
      if (pos === 'fixed' || pos === 'sticky') return true;
    }
    return false;
  };
  // Content inside its OWN scroll container legitimately extends past that
  // container - that is what scrolling means. Comparing such an element's
  // un-scrolled position against something outside the container reports a
  // collision that a user can never see, which is what flagged the AI starter
  // prompts against the message box.
  const scrollParent = (el) => {
    for (let n = el.parentElement; n && n !== document.body; n = n.parentElement) {
      const s = getComputedStyle(n);
      if (/auto|scroll/.test(s.overflowY) && n.scrollHeight > n.clientHeight + 1) return n;
    }
    return null;
  };
  const boxes = tappable.filter(visible)
    // A fixed bar covering content as it scrolls past is by design, not a bug.
    // Only in-flow controls colliding with each other indicate broken layout.
    .filter((el) => !isFixed(el))
    .map((el) => ({ el, r: el.getBoundingClientRect(), sp: scrollParent(el) }))
    .filter(({ r }) => r.top > -100 && r.top < vh + 400);
  for (let i = 0; i < boxes.length; i += 1) {
    for (let j = i + 1; j < boxes.length; j += 1) {
      const a = boxes[i]; const b = boxes[j];
      if (a.el.contains(b.el) || b.el.contains(a.el)) continue;
      // Different scroll containers (or one inside a scroller, one outside):
      // their relative positions change as the user scrolls, so a momentary
      // overlap of un-scrolled boxes is not a layout bug.
      if (a.sp !== b.sp) continue;
      // Clipped out of view inside its own scroller? Then it is not visible
      // where it appears to be.
      if (a.sp) {
        const cr = a.sp.getBoundingClientRect();
        if (a.r.top > cr.bottom || a.r.bottom < cr.top
            || b.r.top > cr.bottom || b.r.bottom < cr.top) continue;
      }
      const ox = Math.min(a.r.right, b.r.right) - Math.max(a.r.left, b.r.left);
      const oy = Math.min(a.r.bottom, b.r.bottom) - Math.max(a.r.top, b.r.top);
      if (ox > 4 && oy > 4) {
        const area = Math.min(a.r.width * a.r.height, b.r.width * b.r.height);
        if ((ox * oy) / Math.max(area, 1) > 0.3) {
          out.overlaps.push({ a: label(a.el), b: label(b.el), overlap: `${Math.round(ox)}x${Math.round(oy)}` });
        }
      }
    }
  }

  // 4. Computed text size, which is what the eye sees after the cascade.
  for (const el of document.querySelectorAll('body *')) {
    if (!visible(el)) continue;
    const direct = [...el.childNodes].some((n) => n.nodeType === 3 && n.textContent.trim().length > 1);
    if (!direct) continue;
    const fs = parseFloat(getComputedStyle(el).fontSize);
    if (fs && fs < TEXT_FLOOR) out.tinyText.push({ el: label(el), fs });
  }

  // 5. Is the last of the content reachable, or does the fixed bottom bar sit
  //    on top of it?
  const bars = [...document.querySelectorAll('*')].filter((el) => {
    const s = getComputedStyle(el);
    if (s.position !== 'fixed') return false;
    const r = el.getBoundingClientRect();
    if (!(r.bottom >= vh - 2 && r.height > 24 && r.width > vw * 0.6)) return false;
    // The closed navigation drawer is also fixed and full-height, so it looked
    // like a bar whose top edge was 0 - making every page appear to be hidden
    // "behind" it. A bottom bar is short relative to the screen; a drawer is
    // not. Also skip anything translated off-canvas.
    if (r.height > vh * 0.5) return false;
    if (/translate/.test(s.transform || '') && r.left < -1) return false;
    return true;
  });
  if (bars.length) {
    const barTop = Math.min(...bars.map((b) => b.getBoundingClientRect().top));
    const main = document.querySelector('main, .content, .public-content') || document.body;
    const last = [...main.querySelectorAll('button, a[href], input, p, h1, h2, h3, li')]
      .filter(visible).pop();
    if (last) {
      // Scroll to the very bottom of the document, as a person would, then ask
      // whether the bar covers the last control.
      //
      // Do NOT use scrollIntoView({block:'end'}) here: it aligns the element
      // flush with the viewport bottom, which puts it underneath a fixed bar
      // BY CONSTRUCTION, so every page failed this check even when the content
      // was perfectly reachable. Scrolling to the end of the document is the
      // real-world condition.
      window.scrollTo(0, document.documentElement.scrollHeight);
      const r = last.getBoundingClientRect();
      if (r.bottom > barTop + 2 && r.top < vh) {
        out.hiddenUnderNav.push({
          el: label(last), bottom: Math.round(r.bottom), barTop: Math.round(barTop),
        });
      }
    }
  }

  // 6. Images: broken, or no intrinsic size (causes layout shift on a phone).
  for (const img of document.querySelectorAll('img')) {
    if (!visible(img)) continue;
    if (img.complete && img.naturalWidth === 0) {
      const src = img.currentSrc || img.src || '';
      // Remote hosts may simply be unreachable from wherever this runs (the
      // sandbox blocks images.unsplash.com), which is a network fact, not an
      // app bug. Report those separately from images the app itself fails to
      // produce - a broken LOCAL asset is a real defect.
      const remote = /^https?:\/\//i.test(src) && !src.includes(location.host);
      out.badImages.push({ el: label(img), why: remote ? 'remote-unreachable' : 'broken', src: src.slice(-60) });
    }
  }
  return out;
}

console.log(B('\nMobile render audit — real browser, real geometry\n'));
console.log(D(`  base ${BASE}   chromium ${CHROME}\n`));

if (SHOTS) mkdirSync(SHOT_DIR, { recursive: true });

const sessions = {
  buyer: await makeBuyer(),
  seller: await login('techhub@scottstechx.ug', 'Seller123!'),
  admin: await login('admin@scottstechx.ug', 'Admin123!'),
};

const browser = await chromium.launch({
  executablePath: CHROME,
  args: ['--no-sandbox','--disable-setuid-sandbox','--disable-dev-shm-usage','--disable-gpu',
    '--use-gl=swiftshader','--hide-scrollbars',
    '--no-first-run','--disable-background-networking','--mute-audio'],
  timeout: 60000,
});

const groups = [
  ['public', null, PUBLIC_ROUTES],
  ['buyer', sessions.buyer, BUYER_ROUTES],
  ['seller', sessions.seller, SELLER_ROUTES],
  ['admin', sessions.admin, ADMIN_ROUTES],
];

for (const device of DEVICES) {
  console.log(B(`${device.name}  ${device.width}x${device.height}`));
  const ctx = await browser.newContext({
    viewport: { width: device.width, height: device.height },
    deviceScaleFactor: device.dpr,
    isMobile: true,
    hasTouch: true,
    userAgent: 'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36',
  });
  ctx.setDefaultTimeout(20000);

  for (const [role, session, routes] of groups) {
    if (role !== 'public' && !session) continue;
    // Seed this group's session into every page the context opens.
    await ctx.clearCookies();
    await ctx.addInitScript(([t, u]) => {
      if (t) { localStorage.setItem('stx_token', t); localStorage.setItem('stx_user', u); }
      else { localStorage.removeItem('stx_token'); localStorage.removeItem('stx_user'); }
    }, [session ? session.token : null, session ? JSON.stringify(session.user) : null]);
    for (const route of routes) {
      const page = await ctx.newPage();
      let m = null;
      try {
        await page.goto(`${BASE}${route}`, { waitUntil: 'domcontentloaded', timeout: 20000 });
        await page.waitForTimeout(1200);
        // Measure at the top, then scrolled to the bottom, so fixed chrome and
        // the end of the content are both judged fairly.
        m = await page.evaluate(measureInPage, [TAP_FLOOR, TEXT_FLOOR]);
        await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));
        await page.waitForTimeout(400);
        const bottom = await page.evaluate(measureInPage, [TAP_FLOOR, TEXT_FLOOR]);
        m.hiddenUnderNav = bottom.hiddenUnderNav;
        if (SHOTS) {
          const safe = route.replace(/\W+/g, '_') || '_root';
          await page.screenshot({ path: `${SHOT_DIR}/${device.width}${safe}.png`, fullPage: false });
        }
      } catch (e) {
        report(device.name, route, 'page failed to load', e.message.slice(0, 120));
        await page.close().catch(() => {});
        console.log(`  ${R('✗')} ${route} ${D('(load failed)')}`);
        continue;
      }
      checks += 1;

      if (m.scrollWidth > m.clientWidth + 1) {
        const who = m.overflowers.slice(0, 3).map((o) => `${o.el} → right:${o.right}px`).join(' | ');
        report(device.name, route, `page scrolls sideways (${m.scrollWidth}px in a ${m.clientWidth}px screen)`, who);
      }
      if (m.smallTargets.length) {
        report(device.name, route, `${m.smallTargets.length} tap target(s) under ${TAP_FLOOR}px`,
          m.smallTargets.slice(0, 4).map((t) => `${t.el} ${t.w}x${t.h}`).join(' | '));
      }
      if (m.overlaps.length) {
        report(device.name, route, `${m.overlaps.length} overlapping control(s)`,
          m.overlaps.slice(0, 3).map((o) => `${o.a} ⨯ ${o.b} (${o.overlap})`).join(' | '));
      }
      if (m.tinyText.length) {
        report(device.name, route, `${m.tinyText.length} element(s) with text under ${TEXT_FLOOR}px`,
          m.tinyText.slice(0, 4).map((t) => `${t.el} ${t.fs}px`).join(' | '));
      }
      if (m.hiddenUnderNav.length) {
        report(device.name, route, 'content hidden behind the fixed bottom bar',
          m.hiddenUnderNav.map((h) => `${h.el} bottom:${h.bottom} > bar:${h.barTop}`).join(' | '));
      }
      const localBroken = m.badImages.filter((i) => i.why === 'broken');
      const remoteBroken = m.badImages.filter((i) => i.why === 'remote-unreachable');
      if (localBroken.length) {
        report(device.name, route, `${localBroken.length} broken image(s)`,
          localBroken.slice(0, 3).map((i) => `${i.el} ${i.src}`).join(' | '));
      }
      if (remoteBroken.length) remoteUnreachable += remoteBroken.length;

      const here = findings.filter((f) => f.device === device.name && f.route === route).length;
      console.log(here === 0 ? `  ${G('✓')} ${route}` : `  ${R('✗')} ${route}  ${D(`${here} issue(s)`)}`);
      await page.close().catch(() => {});
    }
  }
  await ctx.close();
  console.log('');
}

await browser.close();

console.log(B('Summary'));
console.log(`  ${checks} page renders measured`);
if (remoteUnreachable) {
  console.log(`  ${Y('note')} ${remoteUnreachable} remote image(s) could not load from this network `
    + `${D('(third-party CDN blocked here — not an app defect)')}`);
}
if (!findings.length) {
  console.log(`\n${G(B('Every page fits and works at every phone size'))}\n`);
  process.exit(0);
}

// Group identical problems so one bug is reported once, not 30 times.
const byKind = new Map();
for (const f of findings) {
  const key = `${f.kind}||${f.detail}`;
  if (!byKind.has(key)) byKind.set(key, { ...f, routes: new Set(), devices: new Set() });
  byKind.get(key).routes.add(f.route);
  byKind.get(key).devices.add(f.device);
}
console.log(`  ${R(`${findings.length} finding(s)`)} — ${byKind.size} distinct\n`);
for (const f of byKind.values()) {
  const routes = [...f.routes];
  const devices = [...f.devices];
  console.log(`  ${R('✗')} ${B(f.kind)}`);
  console.log(`     ${D(`${routes.length} route(s): ${routes.slice(0, 5).join(', ')}${routes.length > 5 ? ` +${routes.length - 5}` : ''}`)}`);
  console.log(`     ${D(`${devices.length} device(s): ${devices.join(', ')}`)}`);
  if (f.detail) console.log(`     ${D(f.detail)}`);
}
console.log('');
process.exit(1);
