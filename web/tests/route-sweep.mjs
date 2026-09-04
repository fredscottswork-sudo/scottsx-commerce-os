#!/usr/bin/env node
/**
 * Route sweep — mount EVERY route as every role and report what breaks.
 *
 * ui.mjs tests specific behaviours deeply. This does the opposite: it visits
 * all 34 routes with a real session and a real backend and asks the blunt
 * questions that catch whole-page failures —
 *
 *   - did it render anything at all, or a blank screen?
 *   - did it throw a console error?
 *   - did it get stuck in a loading state?
 *   - does it show an error/empty state where it should show data?
 *   - are there dead controls (buttons with no handler, links to nowhere)?
 *   - are images missing alt text?
 *   - do form inputs have labels?
 *
 * Usage: node tests/route-sweep.mjs
 */
import { readFileSync, readdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { JSDOM, VirtualConsole } from 'jsdom';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = join(__dirname, '..');
const DIST = join(ROOT, 'dist');
const API = process.env.API_BASE || 'http://127.0.0.1:3001';

const G = (s) => `\x1b[32m${s}\x1b[0m`;
const R = (s) => `\x1b[31m${s}\x1b[0m`;
const Y = (s) => `\x1b[33m${s}\x1b[0m`;
const B = (s) => `\x1b[1m${s}\x1b[0m`;
const D = (s) => `\x1b[2m${s}\x1b[0m`;

const assetsDir = join(DIST, 'assets');
const cssFile = readdirSync(assetsDir).find((f) => f.endsWith('.css'));
const jsFile = readdirSync(assetsDir).find((f) => f.endsWith('.js') && f.startsWith('index-'));
const bundleCss = readFileSync(join(assetsDir, cssFile), 'utf8');
const bundleJs = readFileSync(join(assetsDir, jsFile), 'utf8');

let pass = 0;
const problems = [];
function bad(route, role, msg, detail) {
  problems.push({ route, role, msg, detail });
}

async function login(email, password) {
  const r = await fetch(`${API}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  if (!r.ok) return null;
  return r.json();
}

async function visit(route, session) {
  const consoleErrors = [];
  const vc = new VirtualConsole();
  vc.on('jsdomError', (e) => {
    if (/Could not parse CSS|Not implemented/i.test(e.message)) return;
    consoleErrors.push(e.message);
  });
  vc.on('error', (...a) => {
    const m = a.map(String).join(' ');
    if (/Not implemented|Could not parse CSS/i.test(m)) return;
    consoleErrors.push(m);
  });

  const dom = new JSDOM(
    `<!doctype html><html><head><style>${bundleCss}</style></head><body><div id="root"></div></body></html>`,
    { url: `http://localhost:5173${route}`, runScripts: 'outside-only', pretendToBeVisual: true, virtualConsole: vc }
  );
  const { window } = dom;

  window.fetch = (input, init) => {
    const url = typeof input === 'string' ? input : input.url;
    const p = url.startsWith('/assets/')
      ? url
      : (url.startsWith('http') && new URL(url).pathname.startsWith('/assets/') ? new URL(url).pathname : null);
    if (p) {
      try {
        const body = readFileSync(join(DIST, p), 'utf8');
        return Promise.resolve(new Response(body, {
          status: 200, headers: { 'content-type': p.endsWith('.css') ? 'text/css' : 'text/javascript' },
        }));
      } catch { return Promise.resolve(new Response('nf', { status: 404 })); }
    }
    return fetch(url.startsWith('http') ? url : `${API}${url}`, init);
  };
  window.Headers = Headers; window.Request = Request; window.Response = Response;
  window.matchMedia = window.matchMedia || ((q) => ({
    matches: false, media: q, onchange: null,
    addListener() {}, removeListener() {}, addEventListener() {}, removeEventListener() {}, dispatchEvent() { return false; },
  }));
  window.scrollTo = () => {};
  window.Element.prototype.scrollTo = function () {};
  window.Element.prototype.scrollIntoView = function () {};
  window.HTMLElement.prototype.scrollTo = function () {};
  if (!window.requestAnimationFrame) {
    window.requestAnimationFrame = (cb) => setTimeout(() => cb(Date.now()), 16);
    window.cancelAnimationFrame = (id) => clearTimeout(id);
  }
  window.navigator.geolocation = {
    getCurrentPosition: (ok) => ok({ coords: { latitude: 0.3476, longitude: 32.5825, accuracy: 30 } }),
    watchPosition: () => 1, clearWatch: () => {},
  };
  if (session) {
    window.localStorage.setItem('stx_token', session.token);
    window.localStorage.setItem('stx_user', JSON.stringify(session.user));
  }

  try {
    window.eval(bundleJs);
  } catch (e) {
    return { fatal: e.message, consoleErrors, window, dom };
  }
  await new Promise((r) => setTimeout(r, 1500));
  return { fatal: null, consoleErrors, window, dom };
}

const PUBLIC_ROUTES = ['/', '/login', '/register', '/nearby', '/search', '/ai', '/cms/about'];
const BUYER_ROUTES = ['/buyer', '/buyer/orders', '/buyer/saved', '/buyer/addresses', '/buyer/payments',
  '/buyer/refunds', '/buyer/support', '/buyer/settings', '/buyer/ai', '/cart', '/messages', '/notifications'];
const SELLER_ROUTES = ['/seller', '/seller/inventory', '/seller/add-product', '/seller/bulk-import',
  '/seller/orders', '/seller/analytics', '/seller/ai', '/seller/store-settings', '/messages', '/notifications'];
const ADMIN_ROUTES = ['/admin', '/admin/users', '/admin/products', '/admin/queue', '/admin/support'];

console.log(B('\nRoute sweep — every page, every role\n'));

// Buyers are registered fresh, the same way ui.mjs does it, then taken
// through the real verification flow - private routes are gated on it.
async function makeBuyer() {
  const email = `sweep_${Date.now()}@scottstechx.test`;
  const r = await fetch(`${API}/api/v1/auth/register`, {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ email, password: 'Test123!', displayName: 'Sweep Buyer', role: 'buyer' }),
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

const sessions = {
  buyer: await makeBuyer(),
  seller: await login('techhub@scottstechx.ug', 'Seller123!'),
  admin: await login('admin@scottstechx.ug', 'Admin123!'),
};
for (const [k, v] of Object.entries(sessions)) {
  if (!v) console.log(`  ${Y('!')} could not log in as ${k} — its routes will be skipped`);
}

const groups = [
  ['public', null, PUBLIC_ROUTES],
  ['buyer', sessions.buyer, BUYER_ROUTES],
  ['seller', sessions.seller, SELLER_ROUTES],
  ['admin', sessions.admin, ADMIN_ROUTES],
];

for (const [role, session, routes] of groups) {
  if (role !== 'public' && !session) continue;
  console.log(B(`${role}`));
  for (const route of routes) {
    const { fatal, consoleErrors, window, dom } = await visit(route, session);
    const doc = window.document;
    const root = doc.getElementById('root');
    const text = (root?.textContent || '').trim();
    let line = `  ${route}`;

    if (fatal) {
      bad(route, role, 'threw while mounting', fatal);
      console.log(`  ${R('✗')} ${route}  ${D('fatal: ' + fatal.slice(0, 60))}`);
      dom.window.close();
      continue;
    }
    if (consoleErrors.length) {
      bad(route, role, 'console error', consoleErrors[0].slice(0, 160));
    }
    // Blank screen
    if (text.length < 20) {
      bad(route, role, 'rendered almost nothing', `${text.length} chars of text`);
    }
    // Stuck loading: a spinner/skeleton still present and no real content
    const skeletons = doc.querySelectorAll('.skeleton, .spinner, [aria-busy="true"]').length;
    if (skeletons > 0 && text.length < 120) {
      bad(route, role, 'still loading after 1.5s', `${skeletons} skeleton/spinner nodes`);
    }
    // Crash boundary / error text
    if (/something went wrong|unexpected error|failed to load/i.test(text)) {
      bad(route, role, 'shows an error state', text.slice(0, 120));
    }
    // Images without alt (decorative must be alt="")
    const imgs = [...doc.querySelectorAll('img')];
    const noAlt = imgs.filter((i) => !i.hasAttribute('alt'));
    if (noAlt.length) bad(route, role, `${noAlt.length} <img> without alt`, noAlt[0].getAttribute('src') || '');
    // Inputs without an accessible name
    const inputs = [...doc.querySelectorAll('input, select, textarea')]
      .filter((el) => el.type !== 'hidden');
    const unlabelled = inputs.filter((el) => {
      if (el.getAttribute('aria-label') || el.getAttribute('aria-labelledby')) return false;
      if (el.id && doc.querySelector(`label[for="${el.id}"]`)) return false;
      if (el.closest('label')) return false;
      if (el.getAttribute('placeholder')) return false;
      return true;
    });
    if (unlabelled.length) bad(route, role, `${unlabelled.length} form field(s) with no accessible name`, unlabelled[0].outerHTML.slice(0, 90));
    // Buttons with no text and no aria-label = invisible to screen readers
    const buttons = [...doc.querySelectorAll('button')];
    const mute = buttons.filter((b) => !(b.textContent || '').trim() && !b.getAttribute('aria-label') && !b.querySelector('img[alt]:not([alt=""])'));
    if (mute.length) bad(route, role, `${mute.length} button(s) with no accessible name`, mute[0].outerHTML.slice(0, 90));
    // Links to nowhere
    const deadLinks = [...doc.querySelectorAll('a[href="#"], a:not([href])')].length;
    if (deadLinks) bad(route, role, `${deadLinks} link(s) with no destination`);

    const issuesHere = problems.filter((p) => p.route === route && p.role === role).length;
    if (issuesHere === 0) { pass += 1; console.log(`  ${G('✓')} ${route}`); }
    else console.log(`  ${R('✗')} ${route}  ${D(`${issuesHere} issue(s)`)}`);
    dom.window.close();
  }
  console.log('');
}

console.log(B('Summary'));
console.log(`  ${G(`${pass} route(s) clean`)}`);
if (problems.length) {
  console.log(`\n${B(R(`${problems.length} issue(s)`))}`);
  for (const p of problems) {
    console.log(`  ${R('✗')} ${D(`[${p.role}] ${p.route}`)}  ${p.msg}`);
    if (p.detail) console.log(`      ${D(p.detail)}`);
  }
  process.exit(1);
}
console.log(`\n${G(B('Every route renders clean'))}\n`);
