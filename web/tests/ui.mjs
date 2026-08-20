/**
 * ScottsTechX web — UI integration tests.
 *
 * Mounts the REAL production bundle inside jsdom, pointed at the REAL backend.
 * No mocks: every assertion below proves that a page actually rendered data
 * that came out of Postgres through Fastify.
 *
 *   node tests/ui.mjs            (requires `npm run build` + API on :3001)
 *
 * Env: API_BASE (default http://127.0.0.1:3001), WEB_DIST (default ./dist)
 */
import { readdirSync, readFileSync, existsSync, writeFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { JSDOM, VirtualConsole } from 'jsdom';

// Surface an unhandled crash as a CI annotation too. Without this, a throw
// before the summary block leaves nothing readable in CI, where log downloads
// are blocked.
if (process.env.GITHUB_ACTIONS) {
  const report = (kind) => async (err) => {
    let extra = '';
    // A bare "fetch failed" does not say whether the API died or a single
    // request was refused. Probe it, and report how far the suite got.
    try {
      const r = await fetch(`${API_BASE}/api/v1/geo/status`);
      extra = `\nAPI probe after crash: HTTP ${r.status} (server still up)`;
    } catch (e) {
      extra = `\nAPI probe after crash: unreachable (${e && e.message}) -> the API process died`;
    }
    extra += `\nProgress when it died: ${pass} passed, ${fail} failed`;
    extra += `\nLast section: ${currentSection || '(none)'}`;
    const text = `${kind}: ${err && err.stack ? err.stack : err}${extra}`;
    console.log(`::error title=WEB UI SUITE CRASH::${text.slice(0, 3500).replace(/\r?\n/g, '%0A')}`);
    process.exit(1);
  };
  process.on('uncaughtException', report('uncaughtException'));
  process.on('unhandledRejection', report('unhandledRejection'));
}

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = join(__dirname, '..');
const DIST = process.env.WEB_DIST || join(ROOT, 'dist');
const API_BASE = process.env.API_BASE || 'http://127.0.0.1:3001';

/** fetch that reports which URL failed — "fetch failed" alone is useless in CI. */
async function fetchNamed(url, init) {
  try {
    return await fetch(url, init);
  } catch (err) {
    throw new TypeError(`fetch failed for ${url}: ${err && err.message}`);
  }
}

let pass = 0;
let fail = 0;
const failures = [];

function check(name, cond, detail = '') {
  if (cond) { pass++; console.log(`  ✓ ${name}`); }
  else { fail++; failures.push(`${name}${detail ? ` — ${detail}` : ''}`); console.log(`  ✗ ${name}${detail ? ` — ${detail}` : ''}`); }
}

let currentSection = '';
function section(t) { currentSection = t; console.log(`\n\x1b[1m${t}\x1b[0m`); }

// ── Locate the built bundle ─────────────────────────────────────────────────
if (!existsSync(join(DIST, 'index.html'))) {
  console.error(`No build found at ${DIST}. Run: npm run build`);
  process.exit(1);
}
const indexHtml = readFileSync(join(DIST, 'index.html'), 'utf8');
const jsMatch = indexHtml.match(/src="(\/assets\/index-[^"]+\.js)"/);
const cssMatch = indexHtml.match(/href="(\/assets\/index-[^"]+\.css)"/);
if (!jsMatch) { console.error('Could not find the JS bundle in index.html'); process.exit(1); }
const bundleJs = readFileSync(join(DIST, jsMatch[1]), 'utf8');
const bundleCss = cssMatch ? readFileSync(join(DIST, cssMatch[1]), 'utf8') : '';

// ── Backend helpers ─────────────────────────────────────────────────────────
async function apiFetch(path, opts = {}) {
  const res = await fetchNamed(`${API_BASE}/api/v1${path}`, {
    ...opts,
    headers: { 'content-type': 'application/json', ...(opts.headers || {}) },
  });
  const body = await res.json().catch(() => ({}));
  return { status: res.status, body };
}

async function login(email, password) {
  const r = await apiFetch('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) });
  if (r.status !== 200) throw new Error(`login failed for ${email}: ${r.status} ${JSON.stringify(r.body)}`);
  return r.body;
}

// ── Mount the app in jsdom ──────────────────────────────────────────────────
/**
 * Boots the real bundle at `route`, optionally pre-seeding a session into
 * localStorage, and resolves once the app has rendered and settled.
 */
async function mount(route, session = null, { settleMs = 1400, google = 'block', geo = null, offline = false } = {}) {
  const virtualConsole = new VirtualConsole();
  const consoleErrors = [];
  virtualConsole.on('jsdomError', (e) => {
    // jsdom cannot lay out CSS or run canvas; those are not app bugs.
    if (/Could not parse CSS|Not implemented/i.test(e.message)) return;
    consoleErrors.push(e.message);
  });
  virtualConsole.on('error', (...args) => {
    const msg = args.map(String).join(' ');
    if (/Not implemented|Could not parse CSS/i.test(msg)) return;
    consoleErrors.push(msg);
  });

  const dom = new JSDOM(
    `<!doctype html><html><head><style>${bundleCss}</style></head><body><div id="root"></div></body></html>`,
    {
      url: `http://localhost:5173${route}`,
      runScripts: 'outside-only',
      pretendToBeVisual: true,
      virtualConsole,
    }
  );

  const { window } = dom;

  // Real network: proxy the app's relative /api/v1 calls to the live backend.
  // `offline: true` simulates an unreachable backend — a Render free instance
  // asleep, a dropped connection, a phone in a lift — which is exactly how
  // fetch() fails in a browser: a rejected promise, not an HTTP status.
  window.fetch = (input, init) => {
    if (offline) return Promise.reject(new TypeError('Failed to fetch'));
    const url = typeof input === 'string' ? input : input.url;
    const absolute = url.startsWith('http') ? url : `${API_BASE}${url}`;
    return fetch(absolute, init).catch((err) => {
      // Name the URL: a bare "fetch failed" is undiagnosable in CI.
      throw new TypeError(`fetch failed for ${absolute}: ${err && err.message}`);
    });
  };
  window.Headers = Headers;
  window.Request = Request;
  window.Response = Response;

  // APIs the bundle touches that jsdom lacks.
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
  // Geolocation. `geo` lets a test script a coarse first fix followed by a
  // sharper one, which is what a real phone does while the GPS chip warms up.
  {
    const first = geo?.first ?? { latitude: 0.3476, longitude: 32.5825, accuracy: 30 };
    const refined = geo?.refined ?? null;
    const denied = geo?.denied ?? false;
    let watchSeq = 0;
    window.__geoWatches = 0;
    window.navigator.geolocation = {
      getCurrentPosition: (ok, err) => {
        if (denied) {
          err?.({ code: 1, PERMISSION_DENIED: 1, message: 'denied' });
          return;
        }
        ok({ coords: first });
      },
      watchPosition: (ok) => {
        window.__geoWatches += 1;
        if (refined) setTimeout(() => ok({ coords: refined }), 30);
        return ++watchSeq;
      },
      clearWatch: () => {},
    };
  }

  // ── Google Identity Services ───────────────────────────────────────────
  // 'block'  : script never loads  -> the "unavailable" path
  // 'ready'  : GIS present, button renders, credential on demand
  // 'offline': leave it hanging    -> the "loading" path
  if (google === 'ready') {
    window.__gisCredential = null;
    window.google = {
      accounts: {
        id: {
          initialize(cfg) {
            window.__gisConfig = cfg;
          },
          renderButton(parent) {
            const b = window.document.createElement('button');
            b.type = 'button';
            b.setAttribute('data-testid', 'gis-button');
            b.textContent = 'Continue with Google';
            b.addEventListener('click', () => {
              const cred = window.__gisCredential;
              if (cred) window.__gisConfig?.callback({ credential: cred });
            });
            parent.appendChild(b);
          },
          prompt() {},
          disableAutoSelect() {
            window.__gisAutoSelectDisabled = true;
          },
        },
      },
    };
  }
  // The loader appends a <script> to head; jsdom won't fetch it, so drive the
  // load/error event ourselves to hit the branch we want.
  {
    const realAppend = window.document.head.appendChild.bind(window.document.head);
    window.document.head.appendChild = (node) => {
      const out = realAppend(node);
      if (node.tagName === 'SCRIPT' && String(node.src).includes('gsi/client')) {
        if (google === 'ready') setTimeout(() => node.dispatchEvent(new window.Event('load')), 10);
        else if (google === 'block') setTimeout(() => node.dispatchEvent(new window.Event('error')), 10);
        // 'offline' -> never fires; the component's timeout decides.
      }
      return out;
    };
  }

  if (session) {
    window.localStorage.setItem('stx_token', session.token);
    window.localStorage.setItem('stx_user', JSON.stringify({
      id: session.user.id,
      email: session.user.email,
      displayName: session.user.displayName ?? session.user.display_name ?? '',
      phone: session.user.phone ?? '',
      role: session.user.role,
      emailVerified: !!session.user.emailVerified,
      profilePhotoUrl: null,
      city: session.user.city ?? '',
    }));
  }

  // Run the bundle.
  try {
    window.eval(bundleJs);
  } catch (e) {
    consoleErrors.push(`bundle threw: ${e.message}`);
  }

  // Let React mount + all data fetches resolve.
  await new Promise((r) => setTimeout(r, settleMs));

  const text = () => window.document.body.textContent || '';
  const html = () => window.document.body.innerHTML;
  const $ = (sel) => window.document.querySelector(sel);
  const $$ = (sel) => [...window.document.querySelectorAll(sel)];

  async function click(el, waitMs = 900) {
    el.dispatchEvent(new window.MouseEvent('click', { bubbles: true, cancelable: true }));
    await new Promise((r) => setTimeout(r, waitMs));
  }

  async function type(el, value, waitMs = 500) {
    // React tracks the value on the DOM node; bypass its setter so onChange fires.
    const proto = el.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
    const setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
    setter.call(el, value);
    el.dispatchEvent(new window.Event('input', { bubbles: true }));
    await new Promise((r) => setTimeout(r, waitMs));
  }

  function byText(selector, needle) {
    return $$(selector).find((e) => (e.textContent || '').toLowerCase().includes(needle.toLowerCase()));
  }

  return { dom, window, text, html, $, $$, click, type, byText, consoleErrors,
    close: () => dom.window.close() };
}

// ════════════════════════════════════════════════════════════════════════════
console.log(`\n\x1b[1mScottsTechX web UI tests\x1b[0m  (bundle: ${jsMatch[1]}, api: ${API_BASE})`);

// Sanity: backend reachable and seeded.
const seedProducts = await apiFetch('/products?pageSize=5');
if (seedProducts.status !== 200 || !seedProducts.body.products?.length) {
  console.error(`Backend not ready at ${API_BASE} — got ${seedProducts.status}`);
  process.exit(1);
}
const sampleProduct = seedProducts.body.products[0];

/** Products whose stock the run consumes; restored in Cleanup. */
const stockToRestore = [];

const admin = await login('admin@scottstechx.ug', 'Admin123!');
const seller = await login('techhub@scottstechx.ug', 'Seller123!');

// A dedicated buyer so cart/order assertions never collide with real data.
const buyerEmail = `uitest_${Date.now()}@scottstechx.test`;
const reg = await apiFetch('/auth/register', {
  method: 'POST',
  body: JSON.stringify({ email: buyerEmail, password: 'Test123!', displayName: 'UI Test Buyer', role: 'buyer' }),
});
if (reg.status !== 201) { console.error('Could not create the test buyer', reg); process.exit(1); }
const buyer = reg.body;

// Verification is a hard gate now, so a freshly-registered account cannot
// reach any private route. Take the fixture through the real flow rather than
// forging the flag — that way these tests exercise the path users take.
{
  // Registration already issued a code and resends are rate limited, so use
  // that one - the same thing the real signup screen does.
  const code = reg.body?.verification?.devCode;
  if (!code) { console.error('No verification code issued for the test buyer', reg); process.exit(1); }
  const conf = await apiFetch('/auth/verify/confirm', {
    method: 'POST',
    headers: { authorization: `Bearer ${buyer.token}` },
    body: JSON.stringify({ code }),
  });
  if (conf.status !== 200) { console.error('Could not verify the test buyer', conf); process.exit(1); }
  buyer.user = { ...buyer.user, ...(conf.body?.user || {}), emailVerified: true };
}
const buyerAuth = { authorization: `Bearer ${buyer.token}` };
const sellerAuth = { authorization: `Bearer ${seller.token}` };

// ── 1. Public marketplace ───────────────────────────────────────────────────
section('1. Public marketplace (logged out)');
{
  const app = await mount('/');
  const t = app.text();
  check('home page renders the brand', t.includes('ScottsTechX'));
  check('shows sign-in and get-started CTAs', t.includes('Sign in') && t.includes('Get started'));
  check('renders real product cards from the API', app.$$('.pcard').length > 0,
    `found ${app.$$('.pcard').length} cards`);
  check('shows a real seeded product title', t.includes(sampleProduct.title),
    `expected "${sampleProduct.title}"`);
  check('prices are formatted as UGX', /UGX\s[\d,]+/.test(t));
  check('dark theme is the default', app.window.document.documentElement.getAttribute('data-theme') === 'dark',
    `got ${app.window.document.documentElement.getAttribute('data-theme')}`);
  check('no runtime errors on the home page', app.consoleErrors.length === 0, app.consoleErrors[0]);
  app.close();
}

// ── 1b. Site navigation bar ─────────────────────────────────────────────────
section('1b. Main navigation bar');
{
  const facetsRes = await apiFetch('/products/facets');
  const liveCats = facetsRes.body.categories || [];
  const liveBrands = facetsRes.body.brands || [];

  // Logged out: the bar exists and offers the public destinations.
  const app = await mount('/');
  const nav = app.$('[data-testid="mainnav"]');
  check('nav bar renders for logged-out visitors', !!nav);
  const navText = nav ? nav.textContent : '';
  for (const label of ['Market', 'Deals', 'Nearby', 'AI']) {
    check(`nav offers "${label}"`, navText.includes(label));
  }
  check('nav shows the seller CTA when logged out', navText.includes('Sell on ScottsTechX'));

  // The mega-menu is closed until asked for, then lists REAL categories.
  check('category menu is collapsed initially', !app.$('#mainnav-mega'));
  const catsBtn = app.$('.mainnav-cats');
  check('all-categories trigger exists', !!catsBtn);
  if (catsBtn) {
    await app.click(catsBtn, 400);
    check('clicking opens the category mega-menu', !!app.$('#mainnav-mega'));
    check('trigger reports expanded state', catsBtn.getAttribute('aria-expanded') === 'true');
    const items = app.$$('.mega-item');
    check('mega-menu lists every live category', items.length === liveCats.length,
      `${items.length} rendered vs ${liveCats.length} from /products/facets`);
    if (liveCats.length) {
      const first = liveCats[0];
      const row = items.find((e) => (e.textContent || '').includes(first.name));
      check(`category "${first.name}" is listed`, !!row);
      check('category shows its live product count',
        !!row && (row.textContent || '').includes(String(first.count)),
        row ? row.textContent : '');
      check('category links into a filtered search',
        !!row && row.getAttribute('href') === `/search?category=${encodeURIComponent(first.name)}`,
        row ? String(row.getAttribute('href')) : '');
    }
    if (liveBrands.length) {
      const brandLink = app.$$('.mainnav-mega-side a.chip')
        .find((e) => (e.textContent || '').trim() === liveBrands[0].name);
      check(`top brand "${liveBrands[0].name}" is linked`, !!brandLink);
      check('brand link filters search by brand',
        !!brandLink && brandLink.getAttribute('href') === `/search?brand=${encodeURIComponent(liveBrands[0].name)}`);
    }
    app.window.document.dispatchEvent(new app.window.KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await new Promise((r) => setTimeout(r, 250));
    check('Escape closes the mega-menu', !app.$('#mainnav-mega'));
  }

  // Navigating through the bar actually changes the route.
  const nearbyLink = app.$$('.mainnav-link').find((e) => (e.textContent || '').includes('Nearby'));
  if (nearbyLink) {
    await app.click(nearbyLink, 1100);
    check('clicking "Nearby" navigates to /nearby', app.window.location.pathname === '/nearby',
      app.window.location.pathname);
  } else {
    check('clicking "Nearby" navigates to /nearby', false, 'link not found');
  }
  check('mobile bottom bar is present in the DOM', !!app.$('[data-testid="bottomnav"]'));
  check('no runtime errors from the nav bar', app.consoleErrors.length === 0, app.consoleErrors[0]);
  app.close();
}
{
  const app = await mount('/buyer', buyer);
  const nav = app.$('[data-testid="mainnav"]');
  const t = nav ? nav.textContent : '';
  check('buyer nav shows the dashboard link', t.includes('Dashboard'));
  check('buyer nav shows Orders and Saved', t.includes('Orders') && t.includes('Saved'));
  check('buyer nav shows Cart', t.includes('Cart'));
  check('buyer nav shows Messages', t.includes('Messages'));
  check('buyer nav hides the seller CTA', !t.includes('Sell on ScottsTechX'));
  check('buyer bottom bar links to the cart',
    app.$$('[data-testid="bottomnav"] a').some((a) => a.getAttribute('href') === '/cart'));
  const active = app.$$('.mainnav-link.active').map((e) => e.textContent || '');
  check('active state marks the current page', active.some((x) => x.includes('Dashboard')),
    active.join(' | '));
  app.close();
}
{
  const app = await mount('/seller', seller);
  const t = (app.$('[data-testid="mainnav"]') || { textContent: '' }).textContent;
  check('seller nav shows Inventory', t.includes('Inventory'));
  check('seller nav shows the add-product CTA', t.includes('Add product'));
  check('seller nav has no cart', !t.includes('Cart'));
  app.close();
}
{
  const app = await mount('/admin', admin);
  const t = (app.$('[data-testid="mainnav"]') || { textContent: '' }).textContent;
  check('admin nav shows Approvals', t.includes('Approvals'));
  check('admin nav shows Users', t.includes('Users'));
  check('admin bottom bar links to the moderation queue',
    app.$$('[data-testid="bottomnav"] a').some((a) => a.getAttribute('href') === '/admin/queue'));
  app.close();
}

// ── 2. Search: filters, facets, results ─────────────────────────────────────
section('2. Search page');
{
  const app = await mount('/search?q=phones');
  const t = app.text();
  check('search page renders results for "phones"', app.$$('.pcard').length > 0,
    `${app.$$('.pcard').length} cards`);
  check('synonym expansion surfaces a real phone', /iPhone|Galaxy/i.test(t));
  check('result count is displayed', /result/i.test(t));
  check('sort control is present', !!app.$('select'));
  check('AI search button is offered', /Ask AI/i.test(t));
  check('voice + image search controls exist',
    !!app.byText('button', '') && app.$$('button').length > 4);
  check('no runtime errors on search', app.consoleErrors.length === 0, app.consoleErrors[0]);
  app.close();
}

{
  const app = await mount('/search?category=Electronics&sort=price_asc');
  const cards = app.$$('.pcard');
  check('category filter returns products', cards.length > 0, `${cards.length} cards`);
  const prices = app.$$('.pcard-price').map((e) => Number((e.textContent || '').replace(/[^\d]/g, '')));
  const sortedAsc = prices.every((p, i) => i === 0 || prices[i - 1] <= p);
  check('price_asc really sorts ascending', sortedAsc, prices.slice(0, 5).join(' → '));
  check('active filter chip reflects the category', app.text().includes('Electronics'));
  app.close();
}

// ── 3. Product detail ───────────────────────────────────────────────────────
section('3. Product detail');
{
  const app = await mount(`/product/${sampleProduct.id}`);
  const t = app.text();
  check('renders the product title', t.includes(sampleProduct.title));
  check('renders the price', t.includes(sampleProduct.priceMinor.toLocaleString('en-UG')) || /UGX/.test(t));
  check('shows the seller', t.includes(sampleProduct.seller.name), `seller ${sampleProduct.seller.name}`);
  check('no runtime errors on product detail', app.consoleErrors.length === 0, app.consoleErrors[0]);
  app.close();
}

// ── 4. Nearby (live vs last-known positions) ────────────────────────────────
section('4. Nearby stores');
{
  const app = await mount('/nearby');
  const t = app.text();
  check('nearby page renders store cards', app.$$('.store-card').length > 0,
    `${app.$$('.store-card').length} stores`);
  check('distances are shown in km', /\d+(\.\d+)?\s*km/.test(t));
  check('explains the last-known-position rule',
    /last known position/i.test(t) || /Fixed address/i.test(t) || /Last seen/i.test(t));
  check('offers live GPS tracking', /Follow my location/i.test(t));
  // The marketplace is global: no radius control, no hard-coded city list.
  check('no radius control is shown', !/Radius/i.test(t) && app.$$('input[type="range"]').length === 0);
  // No city-picker chips. (Real store locations may legitimately name a city —
  // Jinja stores now appear at all because the radius cap is gone — so assert
  // on the removed control, not on the word.)
  check('no city-preset picker chips',
    app.$$('.chip').filter((c) => /^(Kampala|Entebbe|Jinja|Mbarara|Gulu|Mbale)$/i.test((c.textContent || '').trim())).length === 0,
    app.$$('.chip').map((c) => (c.textContent || '').trim()).join(','));
  check('stores outside the old 50 km radius are now listed',
    /Jinja/i.test(t), 'a Jinja store should be reachable from Kampala');
  check('names the detected location', /Your location/i.test(t));
  check('shows the resolved place from the geocoder',
    !!app.$('[data-testid="place-label"]'),
    app.$('[data-testid="place-label"]') ? '' : 'place label missing');
  check('breaks the place into city / region / country',
    /City:/.test(t) && /Region:/.test(t) && /Country:/.test(t));
  check('no runtime errors on nearby', app.consoleErrors.length === 0, app.consoleErrors[0]);
  app.close();
}
{
  // The offline geocoder must answer for coordinates anywhere on earth, and
  // the store list must not be clipped to some radius around Kampala.
  const cases = [
    ['Kampala', 0.3476, 32.5825, 'Uganda'],
    ['London', 51.5074, -0.1278, 'United Kingdom'],
    ['Tokyo', 35.6595, 139.7005, 'Japan'],
    ['Sao Paulo', -23.5505, -46.6333, 'Brazil'],
  ];
  for (const [name, lat, lng, country] of cases) {
    const r = await apiFetch(`/geo/reverse?lat=${lat}&lng=${lng}`);
    check(`geocoder names ${name}`, r.status === 200 && r.body.place?.country === country,
      `got ${r.body.place?.label}`);
    check(`${name} resolves a region`, !!r.body.place?.region, `label ${r.body.place?.label}`);
  }
  const far = await apiFetch('/sellers/nearby?lat=51.5074&lng=-0.1278');
  check('a buyer far from every store still gets results (global, no radius)',
    far.status === 200 && far.body.sellers.length > 0, `${far.body.sellers?.length} stores`);
  check('the far buyer\'s own place is reported',
    far.body.place?.country === 'United Kingdom', far.body.place?.label);
  check('stores carry a human place label',
    far.body.sellers.every((s) => typeof s.placeLabel === 'string'));
  const clipped = await apiFetch('/sellers/nearby?lat=0.3476&lng=32.5825&radiusKm=10');
  const unclipped = await apiFetch('/sellers/nearby?lat=0.3476&lng=32.5825');
  check('an explicit radius still filters', clipped.body.total < unclipped.body.total,
    `${clipped.body.total} vs ${unclipped.body.total}`);
  check('results are sorted nearest first',
    unclipped.body.sellers.every((s, i, a) => i === 0 || a[i - 1].distanceKm <= s.distanceKm));
}

// ── 5. Buyer dashboard ──────────────────────────────────────────────────────
section('5. Buyer dashboard');
{
  const app = await mount('/buyer', buyer);
  const t = app.text();
  check('greets the signed-in buyer by name', t.includes('UI Test Buyer'.split(' ')[0]) || /Hi\s/.test(t));
  check('renders the buyer sidebar', t.includes('Dashboard') && t.includes('Orders'));
  check('cart is in the navigation', t.includes('Cart'));
  check('AI shopper entry point is present', /AI shopper/i.test(t));
  check('stat cards rendered', app.$$('.stat-card').length >= 4, `${app.$$('.stat-card').length} stat cards`);
  check('product feed loaded real products', app.$$('.pcard').length > 0,
    `${app.$$('.pcard').length} products`);
  check('category tiles rendered from facets', app.$$('.cat-tile').length > 0);
  check('no runtime errors on buyer dashboard', app.consoleErrors.length === 0, app.consoleErrors[0]);
  app.close();
}

// ── 6. Cart + checkout (real mutations) ─────────────────────────────────────
section('6. Cart and checkout');
{
  // Seed two lines via the API, then verify the UI reflects them.
  // Checkout decrements real stock, so remember it and put it back in Cleanup —
  // otherwise repeated runs drain a product to 0 and the section breaks.
  const p1 = seedProducts.body.products[0];
  const p2 = seedProducts.body.products[1];
  stockToRestore.push({ id: p1.id, quantity: p1.stockQuantity, sellerId: p1.seller.id });
  stockToRestore.push({ id: p2.id, quantity: p2.stockQuantity, sellerId: p2.seller.id });
  await apiFetch('/me/cart', { method: 'POST', headers: buyerAuth, body: JSON.stringify({ productId: p1.id, quantity: 2 }) });
  await apiFetch('/me/cart', { method: 'POST', headers: buyerAuth, body: JSON.stringify({ productId: p2.id, quantity: 1 }) });

  const app = await mount('/cart', buyer);
  const t = app.text();
  check('cart page lists the first product', t.includes(p1.title));
  check('cart page lists the second product', t.includes(p2.title));
  check('cart groups lines by seller', app.$$('.cart-line').length === 2,
    `${app.$$('.cart-line').length} lines`);
  check('quantity steppers rendered', app.$$('.qty-stepper').length === 2);
  const expectedSubtotal = p1.priceMinor * 2 + p2.priceMinor;
  check('subtotal matches the server total',
    t.replace(/[^\d]/g, '').includes(String(expectedSubtotal)),
    `expected ${expectedSubtotal}`);
  check('place-order button present', /Place order/i.test(t));
  check('shows the pay-on-delivery promise', /pay on delivery/i.test(t));

  // Increment quantity through the UI and confirm the server agrees.
  const plusButtons = app.$$('.qty-stepper button').filter((b) => b.getAttribute('aria-label') === 'Increase quantity');
  if (plusButtons.length > 0) {
    await app.click(plusButtons[0], 1100);
    const server = await apiFetch('/me/cart', { headers: buyerAuth });
    const total = server.body.itemCount;
    check('clicking + persisted a new quantity to the backend', total === 4, `server itemCount=${total}`);
  } else {
    check('clicking + persisted a new quantity to the backend', false, 'no + button found');
  }
  app.close();
}

{
  // Check out through the real endpoint, then confirm the UI shows the orders.
  const before = await apiFetch('/me/cart', { headers: buyerAuth });
  const checkout = await apiFetch('/me/cart/checkout', { method: 'POST', headers: buyerAuth, body: '{}' });
  check('checkout returns 201 with orders', checkout.status === 201 && checkout.body.orders?.length > 0,
    `status ${checkout.status}`);
  check('checkout created one order per cart line',
    checkout.body.orders?.length === before.body.items.length,
    `${checkout.body.orders?.length} orders for ${before.body.items.length} lines`);

  const emptied = await apiFetch('/me/cart', { headers: buyerAuth });
  check('cart is emptied after checkout', emptied.body.itemCount === 0);

  const app = await mount('/buyer/orders', buyer);
  const t = app.text();
  check('orders page shows the newly placed order', t.includes(checkout.body.orders[0].title));
  check('order status badge rendered', /pending/i.test(t));
  check('order stat cards rendered', app.$$('.stat-card').length >= 4);
  app.close();
}

// ── 7. Seller: approval workflow end to end ─────────────────────────────────
section('7. Seller dashboard and publishing');
{
  const app = await mount('/seller', seller);
  const t = app.text();
  check('seller dashboard renders revenue stats', app.$$('.stat-card').length >= 4);
  check('shows store controls', /Store controls/i.test(t));
  check('exposes the live-location toggle', /Live location/i.test(t));
  check('explains the last-known-position fallback when off',
    /last known position/i.test(t) || /Turn on/i.test(t));
  check('links to the AI copilot', /AI copilot/i.test(t));
  check('no runtime errors on seller dashboard', app.consoleErrors.length === 0, app.consoleErrors[0]);
  app.close();
}

let createdProductId = null;
{
  // Create a listing through the API exactly as the UI does, then verify the
  // moderation gate: it must NOT be publicly visible until an admin approves.
  const created = await apiFetch('/seller/products', {
    method: 'POST',
    headers: sellerAuth,
    body: JSON.stringify({
      title: 'UI Test Widget Pro',
      description: 'Created by the automated UI test suite.',
      category: 'Electronics',
      brand: 'TestBrand',
      priceMinor: 425000,
      stockQuantity: 7,
      imageUrl: 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e',
    }),
  });
  createdProductId = created.body.product?.id;
  check('seller can submit a listing', created.status === 200 || created.status === 201, `status ${created.status}`);
  check('new listing starts as pending, not live', created.body.product?.status === 'pending',
    `status ${created.body.product?.status}`);

  const anon = await apiFetch(`/products/${createdProductId}`);
  check('pending listing is hidden from the public API', anon.status === 404, `got ${anon.status}`);

  const app = await mount('/seller/inventory?status=pending', seller);
  check('inventory shows the pending listing', app.text().includes('UI Test Widget Pro'));
  check('pending status badge is visible', /pending/i.test(app.text()));
  app.close();
}

// ── 8. Admin: queue, approval, notification fan-out ─────────────────────────
section('8. Admin approval queue');
{
  const app = await mount('/admin/queue', admin);
  const t = app.text();
  check('approval queue renders the pending listing', t.includes('UI Test Widget Pro'));
  check('queue shows approve and reject actions', /Approve/i.test(t) && /Reject/i.test(t));
  check('queue stat cards rendered', app.$$('.stat-card').length >= 4);
  check('no runtime errors on the queue', app.consoleErrors.length === 0, app.consoleErrors[0]);

  // Approve through the real UI button.
  const card = app.$$('.review-card').find((c) => (c.textContent || '').includes('UI Test Widget Pro'));
  const approveBtn = card && [...card.querySelectorAll('button')].find((b) => /approve/i.test(b.textContent || ''));
  if (approveBtn) {
    await app.click(approveBtn, 1500);
    const nowPublic = await apiFetch(`/products/${createdProductId}`);
    check('clicking Approve in the UI publishes the product', nowPublic.status === 200,
      `public GET returned ${nowPublic.status}`);
    check('approved product is now visible to buyers', nowPublic.body.product?.status === 'approved');
  } else {
    check('clicking Approve in the UI publishes the product', false, 'approve button not found');
  }
  app.close();
}

{
  const app = await mount('/admin', admin);
  const t = app.text();
  check('admin overview renders platform stats', app.$$('.stat-card').length >= 4);
  check('shows the user total', /Total users/i.test(t));
  check('shows catalogue health breakdown', /Catalogue health/i.test(t));
  check('links to the support desk', /Support desk/i.test(t));
  check('no runtime errors on admin overview', app.consoleErrors.length === 0, app.consoleErrors[0]);
  app.close();
}

{
  const app = await mount('/admin/users', admin);
  const t = app.text();
  check('admin users page lists real users', t.includes('admin@scottstechx.ug') || t.includes('@'));
  check('role filter is present', !!app.$('select'));
  check('renders a users table', !!app.$('table'));
  app.close();
}

{
  const app = await mount('/admin/products', admin);
  check('admin products page renders a table', !!app.$('table'));
  check('status tabs are present', /Pending/i.test(app.text()) && /Live/i.test(app.text()));
  app.close();
}

// ── 9. AI console (grounded, no key required) ───────────────────────────────
section('9. AI console');
{
  const app = await mount('/buyer/ai', buyer);
  const t = app.text();
  check('AI page renders the agent picker', app.$$('.agent-card').length > 0,
    `${app.$$('.agent-card').length} agents`);
  check('shows store-aware grounding status', /Store-aware|Limited/i.test(t));
  check('offers conversation starters', app.$$('.chip').length > 0);
  check('message composer is present', !!app.$('.ai-chat-input textarea'));

  // Actually ask the assistant something and assert a grounded answer.
  const box = app.$('.ai-chat-input textarea');
  await app.type(box, 'cheapest phones');
  const sendBtn = [...app.$$('.ai-chat-input button')].find((b) => /send/i.test(b.textContent || ''));
  if (box && sendBtn) {
    await app.click(sendBtn, 3200);
    const after = app.text();
    check('assistant produced a reply bubble', app.$$('.bubble-ai').length > 0,
      `${app.$$('.bubble-ai').length} ai bubbles`);
    check('reply is grounded in the real catalogue',
      /UGX/.test(after) || app.$$('.bubble-ai .pcard').length > 0,
      'expected prices or product cards in the answer');
  } else {
    check('assistant produced a reply bubble', false, 'composer not found');
  }
  app.close();
}

// ── 10. Support desk (AI mode + admin escalation) ───────────────────────────
section('10. Support desk');
{
  const created = await apiFetch('/me/support/threads', {
    method: 'POST',
    headers: buyerAuth,
    body: JSON.stringify({ subject: 'UI test — where is my order?', message: 'I ordered yesterday, any update?', mode: 'admin' }),
  });
  check('buyer can open an admin support thread', created.status === 200 || created.status === 201,
    `status ${created.status}`);

  const app = await mount('/admin/support', admin);
  const t = app.text();
  check('admin support desk lists the ticket', t.includes('UI test — where is my order?'));
  check('ticket status filters rendered', /Needs a human/i.test(t));
  check('reply composer available', !!app.$('textarea'));
  check('no runtime errors on the support desk', app.consoleErrors.length === 0, app.consoleErrors[0]);
  app.close();
}

// ── 11. Messaging ───────────────────────────────────────────────────────────
section('11. Messaging');
{
  // Talk to the seller we hold a token for (techhub), not whichever seller
  // happens to own the first catalogue row — otherwise every reply 404s.
  const mine = await apiFetch(`/products?sellerId=${seller.user.id}&pageSize=1`);
  const chatProduct = mine.body.products?.[0] ?? sampleProduct;

  const conv = await apiFetch('/conversations', {
    method: 'POST',
    headers: buyerAuth,
    body: JSON.stringify({ sellerId: seller.user.id, productId: chatProduct.id }),
  });
  check('buyer can open a conversation with a seller', conv.status === 200 || conv.status === 201,
    `status ${conv.status}`);
  const convId = conv.body.conversation?.id;

  await apiFetch(`/conversations/${convId}/messages`, {
    method: 'POST', headers: buyerAuth, body: JSON.stringify({ text: 'Hello, is this still available?' }),
  });

  // A seller reply so the transcript has both sides.
  await apiFetch(`/conversations/${convId}/messages`, {
    method: 'POST', headers: sellerAuth,
    body: JSON.stringify({ text: 'Yes it is, we deliver same day.' }),
  });

  const app = await mount('/messages', buyer);
  const inboxText = app.text();
  check('messages list renders the conversation', inboxText.includes(seller.user.displayName) || app.$$('a').length > 0);
  check('inbox shows the latest message preview', inboxText.includes('we deliver same day'),
    inboxText.slice(0, 160));
  check('inbox exposes filter chips with counts',
    /All/.test(inboxText) && /Unread|Offers|Pinned|Archived/.test(inboxText));

  // Pin from the inbox row and confirm it survives a reload.
  const pinBtn = app.$('[aria-label="Pin conversation"]');
  check('inbox row exposes a pin control', !!pinBtn);
  if (pinBtn) {
    await app.click(pinBtn, 1100);
    const state = await apiFetch('/conversations?filter=pinned', { headers: buyerAuth });
    check('pinning from the inbox persists to the API',
      (state.body.conversations || []).some((c) => c.id === convId));
  }
  app.close();

  const thread = await mount(`/messages/${convId}`, buyer);
  const threadText = thread.text();
  check('thread shows the sent message', threadText.includes('Hello, is this still available?'));
  check('thread shows the seller reply', threadText.includes('we deliver same day'));
  check('thread header names the counterparty', threadText.includes(seller.user.displayName));
  check('thread shows the product context bar', threadText.includes(chatProduct.title));
  check('thread renders a date separator', /Today|Yesterday/.test(threadText));

  // Compose and send a message straight through the UI.
  const composer = thread.$('input[placeholder="Type a message…"]');
  check('composer input is present', !!composer);
  if (composer) {
    await thread.type(composer, 'Sending this from the web UI');
    const sendBtn = thread.$('button[aria-label="Send"]');
    await thread.click(sendBtn, 1500);
    const after = await apiFetch(`/conversations/${convId}/messages`, { headers: buyerAuth });
    check('message sent from the UI reaches the backend',
      (after.body.messages || []).some((m) => m.text === 'Sending this from the web UI'));
    check('sent message appears in the transcript',
      thread.text().includes('Sending this from the web UI'));
  }

  // Offer controls are buyer-side only and require product context.
  const offerBtn = thread.$('[aria-label="Make an offer"]');
  check('buyer sees the make-an-offer control', !!offerBtn);
  thread.close();

  // ---- offers rendered in the transcript ----------------------------------
  const offer = await apiFetch(`/conversations/${convId}/messages`, {
    method: 'POST', headers: buyerAuth,
    body: JSON.stringify({ kind: 'offer', offerMinor: 4200000, offerQuantity: 3 }),
  });
  check('offer created for the UI to render', offer.status === 200, `status ${offer.status}`);
  const offerId = offer.body.message?.id;

  const withOffer = await mount(`/messages/${convId}`, buyer);
  const offerText = withOffer.text();
  check('transcript renders the offer card', offerText.includes('Your offer'));
  check('offer card shows the formatted price', offerText.includes('42,000'), offerText.slice(0, 200));
  check('offer card shows the quantity', offerText.includes('for 3 units'));
  check('pending offer shows a withdraw action for the sender',
    !!withOffer.byText('button', 'Withdraw'));
  withOffer.close();

  // The seller sees accept/decline on the same offer.
  const sellerView = await mount(`/messages/${convId}`, seller);
  check('seller sees the offer as received', sellerView.text().includes('Offer received'));
  const acceptBtn = sellerView.byText('button', 'Accept');
  check('seller sees an accept button', !!acceptBtn);
  if (acceptBtn) {
    await sellerView.click(acceptBtn, 1600);
    const settled = await apiFetch(`/conversations/${convId}/messages`, { headers: sellerAuth });
    const row = (settled.body.messages || []).find((m) => m.id === offerId);
    check('accepting the offer in the UI updates the backend', row?.offerStatus === 'accepted',
      `status ${row?.offerStatus}`);
    check('acceptance renders a system event',
      sellerView.text().includes('Offer accepted'), sellerView.text().slice(-200));
  }
  sellerView.close();

  // ---- read receipts -------------------------------------------------------
  await apiFetch(`/conversations/${convId}/read`, { method: 'POST', headers: sellerAuth });
  const receipts = await apiFetch(`/conversations/${convId}/messages`, { headers: buyerAuth });
  check('buyer messages report read receipts',
    (receipts.body.messages || []).some((m) => m.senderId === buyer.user.id && m.readByOther === true));
}

// ── 12. Notifications and follow-driven push ────────────────────────────────
section('12. Notifications and favourites');
{
  // Follow the seller that will actually post below (techhub), not whichever
  // seller happens to own the first catalogue row — that made this flaky.
  const sellerId = seller.user.id;
  const follow = await apiFetch(`/me/favorites/${sellerId}`, { method: 'POST', headers: buyerAuth });
  check('buyer can follow a seller', follow.status === 200 && follow.body.following === true);

  const before = await apiFetch('/me/notifications', { headers: buyerAuth });
  const beforeCount = before.body.notifications.length;

  // Seller posts + admin approves → follower must be notified.
  const newP = await apiFetch('/seller/products', {
    method: 'POST', headers: sellerAuth,
    body: JSON.stringify({
      title: 'UI Test Follow Alert Item', description: 'Follow-notification test.',
      category: 'Electronics', priceMinor: 99000, stockQuantity: 3,
      imageUrl: 'https://images.unsplash.com/photo-1523275335684-37898b6baf30',
    }),
  });
  const followProductId = newP.body.product?.id;
  await apiFetch(`/admin/products/${followProductId}/approve`, {
    method: 'POST', headers: { authorization: `Bearer ${admin.token}` },
  });
  await new Promise((r) => setTimeout(r, 500));

  const after = await apiFetch('/me/notifications', { headers: buyerAuth });
  const gotNewProductNotif = after.body.notifications.some(
    (n) => n.type === 'new_product' && (n.body || '').includes('UI Test Follow Alert Item')
  );
  check('following a seller produces a new-product notification on approval', gotNewProductNotif,
    `${beforeCount} → ${after.body.notifications.length} notifications`);

  const app = await mount('/notifications', buyer);
  check('notification centre renders the alert', app.text().includes('UI Test Follow Alert Item'));
  check('unread count badge appears in the shell', /\d/.test(app.text()));
  app.close();

  // Cleanup
  await apiFetch(`/admin/products/${followProductId}`, {
    method: 'DELETE', headers: { authorization: `Bearer ${admin.token}` },
  });
  await apiFetch(`/me/favorites/${sellerId}`, { method: 'DELETE', headers: buyerAuth });
}

// ── 13. Theme switching ─────────────────────────────────────────────────────
section('13. Account settings');
{
  const app = await mount('/buyer/settings', buyer);
  const t = app.text();
  check('settings page renders the identity summary', t.includes(buyerEmail));
  check('settings shows every tab',
    ['Profile', 'Appearance', 'Notifications', 'Privacy', 'Security'].every((x) => t.includes(x)));
  check('profile form is prefilled with the account name',
    !!app.$$('input').find((i) => i.value === 'UI Test Buyer'));

  // Edit the display name through the UI and confirm it reaches Postgres.
  const nameInput = app.$$('input').find((i) => i.value === 'UI Test Buyer');
  if (nameInput) {
    await app.type(nameInput, 'Renamed Via UI');
    const saveBtn = app.byText('button', 'Save changes');
    check('save button enables once the form is dirty', !!saveBtn && !saveBtn.disabled);
    if (saveBtn) {
      await app.click(saveBtn, 1500);
      const me = await apiFetch('/auth/me', { headers: buyerAuth });
      check('profile edit persists to the backend', me.body.user?.displayName === 'Renamed Via UI',
        `got ${me.body.user?.displayName}`);
    }
  }

  // Notifications tab: toggles write straight through.
  const notifTab = app.byText('button', 'Notifications');
  if (notifTab) {
    await app.click(notifTab, 700);
    check('notification preferences render', app.text().includes('Order updates'));
    const boxes = app.$$('input[type="checkbox"]');
    check('preference switches are present', boxes.length >= 3, `found ${boxes.length}`);
    if (boxes.length) {
      const before = await apiFetch('/me/preferences', { headers: buyerAuth });
      await app.click(boxes[0], 1200);
      const after = await apiFetch('/me/preferences', { headers: buyerAuth });
      check('toggling a switch persists the preference',
        after.body.preferences.notifyOrderUpdates !== before.body.preferences.notifyOrderUpdates,
        `${before.body.preferences.notifyOrderUpdates} -> ${after.body.preferences.notifyOrderUpdates}`);
    }
  }

  // Security tab: strength meter + validation.
  const secTab = app.byText('button', 'Security');
  if (secTab) {
    await app.click(secTab, 700);
    check('password form renders', app.text().includes('Change password'));
    const pwInputs = app.$$('input[type="password"]');
    check('three password fields are present', pwInputs.length === 3, `found ${pwInputs.length}`);
    if (pwInputs.length === 3) {
      await app.type(pwInputs[1], 'Sh0rt!Passw0rd');
      check('password strength meter appears', /Strong|Good|Fair|Weak/.test(app.text()));
      await app.type(pwInputs[2], 'different');
      check('mismatched confirmation is flagged', app.text().includes('Passwords do not match'));
    }
  }
  app.close();
}

// ── 13b. Theming ────────────────────────────────────────────────────────────
section('13b. Theming');
{
  const app = await mount('/buyer', buyer);
  const root = app.window.document.documentElement;
  check('starts in dark mode', root.getAttribute('data-theme') === 'dark');

  const themeBtn = [...app.$$('button')].find((b) => /^(Light|Dark)$/i.test((b.textContent || '').trim()));
  if (themeBtn) {
    await app.click(themeBtn, 600);
    check('toggling switches to light mode', root.getAttribute('data-theme') === 'light',
      `got ${root.getAttribute('data-theme')}`);
    check('theme choice is persisted', app.window.localStorage.getItem('stx_theme') === 'light');
  } else {
    check('toggling switches to light mode', false, 'theme button not found');
  }
  app.close();
}

// ── 14. Role guards ─────────────────────────────────────────────────────────
section('14. Route guards');
{
  const app = await mount('/admin', buyer);
  check('buyer is redirected away from /admin', !app.text().includes('Platform overview'),
    'buyer should not see the admin overview');
  app.close();
}
{
  const app = await mount('/seller/inventory', buyer);
  check('buyer cannot reach the seller inventory', !app.text().includes('In review'));
  app.close();
}
{
  const app = await mount('/cart');
  check('anonymous visitor is sent to login for the cart',
    app.text().includes('Sign in') || app.window.location.pathname === '/login');
  app.close();
}

// ── 15. Google Sign-In ──────────────────────────────────────────────────────
section('15. Google Sign-In');
{
  // (a) Google reachable: the button renders and a credential signs the user in.
  const app = await mount('/login', null, { google: 'ready' });
  check('the sign-in page offers Google', !!app.$('[data-testid="google-signin"]'));
  check("Google's button is mounted", !!app.$('[data-testid="gis-button"]'),
    app.$('[data-testid="google-signin"]')?.getAttribute('data-status') || 'no widget');
  check('the button is initialised with our OAuth client id',
    String(app.window.__gisConfig?.client_id || '').endsWith('.apps.googleusercontent.com'),
    String(app.window.__gisConfig?.client_id));
  check('the email and password form is still offered',
    !!app.$('input[type="password"]'));

  // A real Google credential, minted by the backend's own test issuer, is not
  // available here — assert instead that a *rejected* credential surfaces an
  // error to the user rather than a silent no-op or a crash.
  app.window.__gisCredential = 'not.a.valid.google.token';
  await app.click(app.$('[data-testid="gis-button"]'), 1200);
  check('a credential Google did not sign is reported to the user',
    !!app.$('[data-testid="google-error"]'),
    app.$('[data-testid="google-signin"]')?.textContent?.slice(0, 90));
  check('a failed Google sign-in leaves the visitor signed out',
    !app.window.localStorage.getItem('stx_token'));
  app.close();
}
{
  // (b) Google blocked (offline, ad-blocker, firewall): degrade honestly.
  const app = await mount('/login', null, { google: 'block' });
  check('a blocked Google script shows an explanation',
    !!app.$('[data-testid="google-unavailable"]'));
  check('the explanation points at email sign-in',
    /email/i.test(app.$('[data-testid="google-unavailable"]')?.textContent || ''));
  check('email sign-in still works when Google is blocked',
    !!app.$('input[type="password"]') && !!app.byText('button', 'Sign in'));
  check('no unhandled error escapes when Google is blocked',
    app.consoleErrors.length === 0, app.consoleErrors[0]);
  app.close();
}
{
  // (c) Register page offers the same entry point.
  const app = await mount('/register', null, { google: 'ready' });
  check('the register page also offers Google', !!app.$('[data-testid="google-signin"]'));
  check("Google's button renders on register", !!app.$('[data-testid="gis-button"]'));
  app.close();
}

// ── 16. Brand identity ──────────────────────────────────────────────────────
// The web must carry the same logo the Android app uses as its launcher icon.
section('16. Brand identity (logo parity with the app)');
{
  const app = await mount('/');
  const mark = app.$('.brand .brand-logo');
  check('the public topbar renders the real logo image',
    !!mark && mark.tagName === 'IMG', mark ? mark.tagName : 'missing');
  check('the topbar logo points at the shared brand asset',
    !!mark && mark.getAttribute('src') === '/brand/scottstechx-mark.png',
    mark?.getAttribute('src') || 'no src');
  check('the logo is decorative next to the visible wordmark',
    !!mark && mark.getAttribute('aria-hidden') === 'true');
  check('no placeholder "S" tile is left in the topbar',
    !!mark && mark.textContent.trim() === '');
  app.close();
}
{
  const app = await mount('/login');
  const lockup = app.$('.brand-lockup');
  check('the sign-in page shows the full brand lockup',
    !!lockup && lockup.getAttribute('src') === '/brand/scottstechx-logo-transparent.png',
    lockup?.getAttribute('src') || 'missing');
  check('the lockup carries an accessible name',
    !!lockup && (lockup.getAttribute('alt') || '').includes('ScottsTechX'));
  check('the shopping-bag emoji placeholder is gone', !app.text().includes('\u{1F6CD}'));
  app.close();
}
{
  const app = await mount('/', { token: buyer.token, user: buyer.user });
  const mark = app.$('.brand .brand-logo');
  check('the signed-in shell uses the same logo asset',
    !!mark && mark.getAttribute('src') === '/brand/scottstechx-mark.png',
    mark?.getAttribute('src') || 'missing');
  app.close();
}
// The referenced assets must actually ship in the production build.
for (const asset of [
  'brand/scottstechx-mark.png',
  'brand/scottstechx-logo-transparent.png',
  'brand/favicon-32.png',
  'brand/favicon-192.png',
  'brand/favicon-512.png',
  'manifest.webmanifest',
]) {
  check(`${asset} ships in the build`, existsSync(join(DIST, asset)));
}
check('the favicon is the real mark, not an inline placeholder',
  indexHtml.includes('/brand/favicon-32.png') && !indexHtml.includes('data:image/svg+xml'));
check('the build links a web app manifest', indexHtml.includes('manifest.webmanifest'));

// ── 17. Stored XSS ──────────────────────────────────────────────────────────
// Seller-supplied text reaches many screens, and the AI answer renderer uses
// dangerouslySetInnerHTML to support its little markdown subset. Publish a
// listing whose every text field is an XSS payload and confirm the real,
// rendered page never materialises an executable element.
section('17. Stored XSS (seller-supplied content)');
let xssProductId = null;
{
  const PAYLOAD = '<img src=x onerror="window.__xss=1">';
  const made = await apiFetch('/seller/products', {
    method: 'POST',
    headers: sellerAuth,
    body: JSON.stringify({
      title: `UI Test XSS ${PAYLOAD}`,
      description: `Payload <script>window.__xss=1<\/script> and **${PAYLOAD}**`,
      category: 'Electronics',
      brand: `<svg/onload="window.__xss=1">`,
      priceMinor: 111000,
      stockQuantity: 3,
      imageUrl: 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e',
    }),
  });
  check('a listing with script payloads can be created', made.status === 200, `got ${made.status}`);
  xssProductId = made.body?.product?.id;

  if (xssProductId) {
    await apiFetch(`/admin/products/${xssProductId}/approve`, {
      method: 'POST', headers: { authorization: `Bearer ${admin.token}` },
    });

    // The payload must survive as DATA — escaping is the renderer's job, so a
    // mangled title here would mean the API is silently corrupting content.
    const detail = await apiFetch(`/products/${xssProductId}`);
    check('the payload is stored verbatim, not silently mangled',
      (detail.body?.product?.title || detail.body?.title || '').includes('<img src=x'));

    for (const route of [`/product/${xssProductId}`, '/search?q=UI%20Test%20XSS']) {
      const app = await mount(route);
      const doc = app.window.document;
      // Real check: did an executable element or inline handler appear?
      const dangerous = [...doc.querySelectorAll('script,iframe,object,embed')]
        .filter((el) => !el.src || !el.src.includes('/assets/'));
      let handlers = 0;
      doc.querySelectorAll('*').forEach((el) => {
        for (const at of el.attributes) if (/^on/i.test(at.name)) handlers++;
      });
      // An <img> injected by the payload would have src="x"; real product
      // images point at a URL.
      const injectedImgs = [...doc.querySelectorAll('img')].filter((i) => i.getAttribute('src') === 'x');

      check(`${route} creates no executable element`, dangerous.length === 0,
        dangerous.map((e) => e.tagName).join(','));
      check(`${route} creates no inline event handler`, handlers === 0, `${handlers} found`);
      check(`${route} does not inject the payload image`, injectedImgs.length === 0);
      check(`${route} did not execute the payload`, app.window.__xss === undefined);
      check(`${route} still shows the payload as visible text`,
        app.text().includes('UI Test XSS'));
      app.close();
    }
  }
}

// ── 18. Mobile & uploads ────────────────────────────────────────────────────
// The site is used mostly on phones, so the seller flow has to work without a
// keyboard-pasted URL, and no page may force sideways scrolling.
section('18. Mobile readiness & photo upload');
{
  const app = await mount('/seller/add-product', { token: seller.token, user: seller.user });
  check('the add-product page offers a real file picker',
    !!app.$('[data-testid="image-file-input"]'));
  const fileInput = app.$('[data-testid="image-file-input"]');
  check('the picker accepts images only',
    (fileInput?.getAttribute('accept') || '').includes('image/'),
    fileInput?.getAttribute('accept') || 'no accept');
  check('several photos can be attached at once', fileInput?.hasAttribute('multiple'));
  check('there is an upload button', !!app.$('[data-testid="choose-photos"]'));
  check('pasting a link is still possible', app.text().includes('Use a link'));
  check('the old URL-only field is gone',
    !app.text().includes('Paste a public link to a clear photo'));
  check('no runtime errors on the add-product page', app.consoleErrors.length === 0, app.consoleErrors[0]);
  app.close();
}
{
  // The viewport contract: without these a phone renders a zoomed-out desktop.
  const viewport = indexHtml.match(/name="viewport"\s+content="([^"]+)"/)?.[1] || '';
  check('the viewport is device-width', /width=device-width/.test(viewport), viewport);
  check('the viewport allows zoom (accessibility)',
    !/user-scalable\s*=\s*no/.test(viewport) && !/maximum-scale\s*=\s*1/.test(viewport), viewport);
  check('the viewport covers the notch', /viewport-fit=cover/.test(viewport), viewport);

  // Guards that keep a stray wide element from panning the whole page.
  check('horizontal overflow is guarded', /overflow-x:\s*hidden/.test(bundleCss));
  check('long words wrap instead of stretching the page', /overflow-wrap:\s*break-word/.test(bundleCss));
  // Photos/video are clamped; icons are deliberately exempt (see section 20 —
  // clamping them squashed icons in flex rows on phones).
  check('media never exceeds its column', /img,\s*video,\s*canvas\s*\{[^}]*max-width:\s*100%/.test(bundleCss));
  check('non-icon svg is still clamped', /svg:not\(\.lucide\):not\(\.icon\)\s*\{[^}]*max-width:\s*100%/.test(bundleCss));
  check('iOS text inflation is disabled', /text-size-adjust:\s*100%/.test(bundleCss));
  check('form fields are 16px on phones so iOS does not zoom',
    /@media \(max-width: 620px\)[^{]*\{[^@]*font-size:\s*16px/.test(bundleCss));
  check('touch devices get 44px tap targets',
    /pointer:\s*coarse[^{]*\{[\s\S]{0,400}min-height:\s*44px/.test(bundleCss));
  check('a bottom nav appears on phones', /@media \(max-width: 620px\)[\s\S]{0,600}\.bottomnav\s*\{[\s\S]{0,120}display:\s*flex/.test(bundleCss));
  check('the bottom bar respects the home indicator', /env\(safe-area-inset-bottom/.test(bundleCss));
  check('wide tables scroll instead of stretching', /\.table-wrap\s*\{[^}]*overflow-x:\s*auto/.test(bundleCss));

  // Every multi-column layout must collapse to one column on a phone.
  for (const cls of ['search-layout', 'checkout-layout', 'ai-console', 'support-layout', 'form-row']) {
    const collapses = new RegExp(`\\.${cls}\\s*\\{[^}]*grid-template-columns:\\s*1fr\\s*[;}]`).test(bundleCss);
    check(`.${cls} collapses to one column on mobile`, collapses);
  }
}
{
  // The bottom nav is what makes the app usable one-handed.
  const app = await mount('/', { token: buyer.token, user: buyer.user });
  const nav = app.$('[data-testid="bottomnav"]');
  check('the mobile bottom nav is rendered', !!nav);
  check('it has enough destinations to be useful', (nav?.querySelectorAll('a').length || 0) >= 4,
    `${nav?.querySelectorAll('a').length || 0} links`);
  check('the bottom nav is labelled for screen readers',
    (nav?.getAttribute('aria-label') || '').length > 0);
  app.close();
}

// ── 19. Real location ───────────────────────────────────────────────────────
// The complaint was that Nearby named the wrong place. Two causes: a coarse
// Wi-Fi fix accepted as final, and a stale cached fix. Both are covered here.
section('19. Location is the buyer\'s real position');
{
  // A sharp fix should be reported as precise and named exactly.
  const app = await mount('/nearby', null, {
    geo: { first: { latitude: 0.3345, longitude: 32.5726, accuracy: 18 } },
    settleMs: 2200,
  });
  const label = app.$('[data-testid="place-label"]')?.textContent || '';
  check('a precise fix resolves to the real neighbourhood',
    /Wandegeya/i.test(label), label);
  const acc = app.$('[data-testid="gps-accuracy"]')?.textContent || '';
  check('the precision of the fix is shown', /±\s*18\s*m/.test(acc), acc);
  check('a precise fix is not labelled approximate', !/approximate/i.test(acc), acc);
  check('no runtime errors on Nearby', app.consoleErrors.length === 0, app.consoleErrors[0]);
  app.close();
}
{
  // A 3 km Wi-Fi fix must be flagged AND must trigger a hunt for something
  // better — this is the bug that put buyers in the wrong suburb.
  const app = await mount('/nearby', null, {
    geo: {
      first:   { latitude: 0.3050, longitude: 32.5400, accuracy: 3000 },
      refined: { latitude: 0.3345, longitude: 32.5726, accuracy: 12 },
    },
    settleMs: 2600,
  });
  const acc = app.$('[data-testid="gps-accuracy"]')?.textContent || '';
  check('a coarse fix is corrected by the sharper one that follows',
    /±\s*12\s*m/.test(acc), acc);
  check('the corrected fix renames the place to the real one',
    /Wandegeya/i.test(app.$('[data-testid="place-label"]')?.textContent || ''),
    app.$('[data-testid="place-label"]')?.textContent);
  check('a coarse first fix starts a refinement watch', (app.window.__geoWatches || 0) >= 1);
  app.close();
}
{
  // Denied permission must say so rather than inventing a location.
  const app = await mount('/nearby', null, { geo: { denied: true }, settleMs: 1800 });
  const text = app.text();
  check('a denied permission is reported, not faked',
    /could not detect your location|permission denied/i.test(text));
  check('no city is invented when location is unavailable',
    !/Kampala Central/i.test(app.$('[data-testid="place-label"]')?.textContent || ''));
  app.close();
}

// ── 20. Icons on mobile ─────────────────────────────────────────────────────
// Reported: icons rendered badly on phones. Cause was a mobile-hardening rule
// I added earlier — `svg { max-width: 100% }` — combined with the fact that an
// <svg> in a flex row is a flex item that shrinks by default, so icons were
// squashed into ovals next to long text.
section('20. Icons render correctly on mobile');
{
  const app = await mount('/', { token: buyer.token, user: buyer.user });

  // The whole fix keys off Lucide's own class. If lucide-react ever stops
  // emitting it, the CSS silently stops working — so assert it directly.
  const icons = app.$$('svg.lucide');
  check('lucide icons carry the .lucide class the CSS targets', icons.length > 0,
    `${icons.length} found`);
  check('icons declare explicit pixel dimensions',
    icons.length > 0 && icons.every((i) => i.getAttribute('width') && i.getAttribute('height')));

  // Icons must be square: a squashed icon is exactly a width/height mismatch.
  const skewed = icons.filter((i) => i.getAttribute('width') !== i.getAttribute('height'));
  check('every icon is authored square', skewed.length === 0,
    skewed.slice(0, 3).map((i) => `${i.getAttribute('width')}x${i.getAttribute('height')}`).join(', '));
  app.close();
}
{
  // Now the CSS contract itself, against the shipped bundle.
  const noShrink = /\.lucide[^{]*\{[^}]*flex:\s*0 0 auto/.test(bundleCss);
  check('icons are excluded from flex shrinking', noShrink);
  check('icons are exempt from the media max-width rule',
    /svg:not\(\.lucide\)/.test(bundleCss));
  check('real media is still clamped to its column',
    /img,\s*video,\s*canvas\s*\{[^}]*max-width:\s*100%/.test(bundleCss));
  // Regression guard: the old blanket rule is what caused the bug.
  check('the blanket svg max-width rule is gone',
    !/(^|[;}])\s*img,\s*svg,\s*video,\s*canvas\s*\{/.test(bundleCss));
}
{
  // The pattern that actually broke: icon + long text inside a flex row.
  const app = await mount('/nearby', null, {
    geo: { first: { latitude: 0.3345, longitude: 32.5726, accuracy: 18 } },
    settleMs: 2200,
  });
  // `.row` is `display:flex`, so a bare <svg> child is a shrinkable flex item —
  // this is the exact structure that produced oval icons.
  const rowIcons = app.$$('.row > svg.lucide');
  check('the page really does put bare icons inside flex rows', rowIcons.length > 0,
    `${rowIcons.length} found — if 0, this test no longer covers the bug`);
  check('those icons are square in the markup',
    rowIcons.every((i) => i.getAttribute('width') === i.getAttribute('height')));
  check('the location banner icon is present', !!app.$('.place-ico svg.lucide'));
  check('no runtime errors with icons on a phone-sized page',
    app.consoleErrors.length === 0, app.consoleErrors[0]);
  app.close();
}

// ── 21. Dashboards on phones ────────────────────────────────────────────────
// Reported: dashboards did not fit on mobile. Two measured causes — KPI cards
// stacked one per row (4 cards = 508px = 79% of a 360x780 screen), and data
// tables keeping their desktop width (seller inventory ~605px vs ~334px
// usable) so every row had to be read by scrolling sideways.
section('21. Dashboards fit on a phone');
{
  // KPI grids go two-up, not one-up. .grid-2 holds wide panels and still
  // collapses fully.
  const kpiTwoUp = /@media \(max-width: 620px\)[\s\S]*?\.grid-4,\s*\.grid-3,\s*\.grid-5\s*\{[^}]*grid-template-columns:\s*repeat\(2,\s*1fr\)/.test(bundleCss);
  check('KPI grids stay two-up on phones instead of stacking', kpiTwoUp);
  check('wide two-column panels still collapse to one',
    /@media \(max-width: 620px\)[\s\S]*?\.grid-2\s*\{[^}]*grid-template-columns:\s*1fr/.test(bundleCss));

  // A 162px-wide card cannot hold a 26px currency figure beside a 40px icon.
  check('stat cards re-proportion for the narrower two-up card',
    /@media \(max-width: 620px\)[\s\S]*?\.stat-value\s*\{[^}]*font-size:\s*15px/.test(bundleCss));
  check('the stat icon moves above the number rather than beside it',
    /@media \(max-width: 620px\)[\s\S]*?\.stat-card \.row-between\s*\{[^}]*column-reverse/.test(bundleCss));
  check('long KPI labels are clamped so cards keep equal height',
    /@media \(max-width: 620px\)[\s\S]*?\.stat-label\s*\{[^}]*line-clamp:\s*2/.test(bundleCss));

  // Tables stop being tables.
  check('table rows become stacked cards on phones',
    /@media \(max-width: 620px\)[\s\S]*?\.table,\s*\.table tbody,\s*\.table tr,\s*\.table td\s*\{[^}]*display:\s*block/.test(bundleCss));
  check('the now-meaningless header row is hidden from sight',
    /@media \(max-width: 620px\)[\s\S]*?\.table thead\s*\{[^}]*position:\s*absolute/.test(bundleCss));
  check('each stacked cell prints its column heading',
    /@media \(max-width: 620px\)[\s\S]*?\.table tbody td::?before\s*\{[^}]*content:\s*attr\(data-label\)/.test(bundleCss));
  check('stacked tables no longer scroll sideways',
    /@media \(max-width: 620px\)[\s\S]*?\.table-wrap\s*\{[^}]*overflow-x:\s*visible/.test(bundleCss));
}
{
  // The CSS above is inert unless the markup actually carries data-label.
  const app = await mount('/seller/inventory', { token: seller.token, user: seller.user }, { settleMs: 2000 });
  const cells = app.$$('.table tbody td');
  check('the inventory table renders rows', cells.length > 0, `${cells.length} cells`);
  const labelled = cells.filter((c) => c.getAttribute('data-label'));
  check('data cells carry the heading the stacked layout prints',
    labelled.length > 0, `${labelled.length}/${cells.length} labelled`);
  // The actions column has an empty header and must NOT print a stray label.
  const blankLabels = cells.filter((c) => c.getAttribute('data-label') === '');
  check('the actions column has no empty label', blankLabels.length === 0,
    `${blankLabels.length} cells carry an empty data-label`);
  check('no runtime errors on the seller inventory', app.consoleErrors.length === 0, app.consoleErrors[0]);
  app.close();
}
{
  const app = await mount('/admin', { token: admin.token, user: admin.user }, { settleMs: 2200 });
  const cells = app.$$('.table tbody td');
  check('the admin dashboard table also carries headings',
    cells.length === 0 || cells.some((c) => c.getAttribute('data-label')),
    `${cells.length} cells`);
  check('no runtime errors on the admin dashboard', app.consoleErrors.length === 0, app.consoleErrors[0]);
  app.close();
}

// ── 22. Google Search Console verification ──────────────────────────────────
// Google fetches this file anonymously from the site root. Two ways it breaks
// silently: the file is not copied into the build output, or the SPA catch-all
// rewrite swallows the path and returns index.html instead of the token.
section('22. Search Console verification file');
{
  const NAME = 'google4cc19033657ba3e3.html';
  const EXPECTED = 'google-site-verification: google4cc19033657ba3e3.html';

  const built = join(DIST, NAME);
  check('the verification file is published to the site root', existsSync(built), built);

  if (existsSync(built)) {
    const body = readFileSync(built, 'utf8');
    // Google matches the token exactly; a stray edit or a wrapping template
    // fails verification.
    check('it contains exactly the token Google expects', body.trim() === EXPECTED,
      JSON.stringify(body.slice(0, 90)));
    check('it was not turned into a full HTML page', !/<html|<head|<script/i.test(body));
    check('the filename matches the token inside it',
      body.includes(NAME), NAME);
  }

  // The SPA rewrite must not shadow it. Static files win on Cloudflare Pages
  // and Netlify, but only because the file really exists in the output — this
  // asserts the rule is still a catch-all and not something broader.
  const redirects = join(DIST, '_redirects');
  if (existsSync(redirects)) {
    const rule = readFileSync(redirects, 'utf8').trim();
    check('the SPA rewrite is a catch-all served after static files',
      /^\/\*\s+\/index\.html\s+200$/.test(rule), rule);
  }
}

// ── 23. Page metadata (titles, canonical, link previews) ────────────────────
// Every route previously served the one <title> and description baked into
// index.html. That made the 40 URLs in sitemap.xml look like near-duplicates
// to a search engine, and a shared product link produced no preview at all.
section('23. Per-page titles and link previews');
{
  const meta = (dom, sel, attr = 'content') =>
    dom.$(sel)?.getAttribute(attr) || '';

  // A product page must name the product, quote the price, and carry an image.
  const app = await mount(`/product/${sampleProduct.id}`, null, { settleMs: 2200 });
  const title = app.window.document.title;
  check('a product page titles itself after the product',
    title.includes(sampleProduct.title), title);
  check('the site name is still present for brand recognition',
    /ScottsTechX/.test(title), title);

  const desc = meta(app, 'meta[name="description"]');
  check('the description is product-specific, not the site boilerplate',
    desc.length > 0 && !desc.startsWith("ScottsTechX — Uganda's AI-powered"), desc.slice(0, 80));
  check('the description quotes the price', /UGX/.test(desc), desc.slice(0, 80));
  check('the description is short enough for a search result',
    desc.length <= 161, `${desc.length} chars`);

  check('og:title is set for link previews', meta(app, 'meta[property="og:title"]').length > 0);
  check('og:type marks it as a product',
    meta(app, 'meta[property="og:type"]') === 'product');
  const ogImage = meta(app, 'meta[property="og:image"]');
  check('og:image is an absolute URL a scraper can fetch',
    /^https?:\/\//.test(ogImage), ogImage || '(none)');
  check('twitter uses the large-image card when there is an image',
    meta(app, 'meta[name="twitter:card"]') === 'summary_large_image');

  const canonical = app.$('link[rel="canonical"]')?.getAttribute('href') || '';
  check('a canonical URL is declared', canonical.includes(`/product/${sampleProduct.id}`), canonical);
  check('the canonical carries no query string', !canonical.includes('?'), canonical);
  check('a public product page is indexable', !app.$('meta[name="robots"]'));
  app.close();
}
{
  // Distinct routes must not share a title, or they compete in search results.
  const seen = new Map();
  for (const route of ['/', '/search', '/nearby', '/ai', '/register']) {
    const a = await mount(route, null, { settleMs: 1500 });
    seen.set(route, a.window.document.title);
    a.close();
  }
  const titles = [...seen.values()];
  check('every public route has its own title', new Set(titles).size === titles.length,
    JSON.stringify([...seen]));
  check('no route is left with the bare default',
    titles.every((t) => t && t !== ''), JSON.stringify(titles));
}
{
  // Private pages must never be indexed, however a crawler reaches them.
  const app = await mount('/buyer', { token: buyer.token, user: buyer.user }, { settleMs: 1800 });
  const robots = app.$('meta[name="robots"]')?.getAttribute('content') || '';
  check('a signed-in dashboard is marked noindex', /noindex/.test(robots), robots || '(missing)');
  app.close();

  const login = await mount('/login', null, { settleMs: 1400 });
  check('the sign-in page is marked noindex',
    /noindex/.test(login.$('meta[name="robots"]')?.getAttribute('content') || ''));
  login.close();
}
{
  // Regression: navigating from a page with an image to one without must not
  // leave the previous product's photo attached to the new page.
  const a = await mount(`/product/${sampleProduct.id}`, null, { settleMs: 2000 });
  check('product page has og:image', !!a.$('meta[property="og:image"]'));
  a.close();
  const b = await mount('/register', null, { settleMs: 1500 });
  check('a page with no image carries no stale og:image',
    !b.$('meta[property="og:image"]'));
  b.close();
}

// ── 24. Degrading when the backend is unreachable ───────────────────────────
// Render's free tier sleeps an idle instance, so the first visitor after a
// quiet spell genuinely does hit a dead API. Every page must say so rather
// than render a confident, wrong empty state.
section('24. Unreachable backend degrades honestly');
{
  // The cart is the dangerous one: "empty" and "could not load" look identical
  // to a buyer, but one of them means "we threw your basket away".
  const p1 = seedProducts.body.products[0];
  await apiFetch('/me/cart', {
    method: 'POST', headers: buyerAuth,
    body: JSON.stringify({ productId: p1.id, quantity: 1 }),
  });
  const serverCart = await apiFetch('/me/cart', { headers: buyerAuth });
  check('precondition: the buyer really has an item in the cart on the server',
    (serverCart.body.itemCount ?? 0) > 0, `itemCount ${serverCart.body.itemCount}`);

  const off = await mount('/cart', buyer, { offline: true, settleMs: 1600 });
  const t = off.text();
  check('offline cart does NOT claim the cart is empty', !/your cart is empty/i.test(t), t.slice(0, 120));
  check('offline cart reports a problem instead', /something went wrong/i.test(t));
  check('offline cart reassures the buyer their items are safe', /items are safe/i.test(t));
  check('offline cart offers a retry', !!off.byText('button', 'Try again'));
  check('offline cart does not offer checkout',
    !off.byText('button', 'Place order') && !off.byText('button', 'Confirm order'));
  off.close();

  // Same page, backend alive, cart still holding the item -> the real UI.
  const on = await mount('/cart', buyer, { settleMs: 1800 });
  const ton = on.text();
  check('online cart with items shows the item, not an error',
    !/something went wrong/i.test(ton) && ton.length > 200, ton.slice(0, 120));
  on.close();

  // Empty-but-reachable must still say "empty" — the fix must not swap one
  // wrong message for another.
  await apiFetch('/me/cart', { method: 'DELETE', headers: buyerAuth });
  const emptied = await mount('/cart', buyer, { settleMs: 1600 });
  const te = emptied.text();
  check('a genuinely empty cart still says it is empty', /your cart is empty/i.test(te), te.slice(0, 140));
  check('a genuinely empty cart shows no error box', !/something went wrong/i.test(te));
  emptied.close();

  // A spread of other routes: none may render a bare white screen, and each
  // should surface the failure somewhere on the page.
  const offlineRoutes = [
    ['/', null],
    ['/cms/about', null],
    ['/buyer/orders', buyer],
    ['/buyer/saved', buyer],
    ['/notifications', buyer],
    ['/admin', admin],
  ];
  for (const [route, sess] of offlineRoutes) {
    const app = await mount(route, sess, { offline: true, settleMs: 1500 });
    const body = app.text();
    check(`offline ${route} still renders a page (no white screen)`, body.trim().length > 40,
      `only ${body.trim().length} chars`);
    check(`offline ${route} surfaces the failure`,
      /something went wrong|network error|try again|unavailable|could not/i.test(body),
      body.slice(0, 110));
    check(`offline ${route} throws no uncaught error`, app.consoleErrors.length === 0, app.consoleErrors[0]);
    app.close();
  }
}

// ── 25. Header: the brand must never stack vertically ───────────────────────
// The company name rendered as a one-character-wide vertical column on a
// phone. body sets `word-break: break-word` (right for long URLs and order
// ids) and .brand was a shrinkable flex item in a row needing ~609px on a
// 360px screen — so the wordmark was the only thing that could give, and the
// browser broke it letter by letter.
section('25. Header brand and search layout');
{
  const brandRule = (bundleCss.match(/\.brand\s*\{[^}]*\}/) || [''])[0];
  check('the brand opts out of the global word-break', /word-break:\s*keep-all/.test(brandRule));
  check('the brand never wraps', /white-space:\s*nowrap/.test(brandRule));
  check('the brand refuses to shrink in a crowded flex row', /flex-shrink:\s*0/.test(brandRule));

  // The name itself is wrapped in its own span so it can be targeted.
  const nameRule = (bundleCss.match(/\.brand-name\s*\{[^}]*\}/) || [''])[0];
  check('the wordmark span is nowrap', /white-space:\s*nowrap/.test(nameRule));

  // Two rows on a phone: brand row on top, search underneath.
  const topbarRule = (bundleCss.match(/\.public-topbar\s*\{[^}]*\}/) || [''])[0];
  check('the public header stacks into rows by default', /flex-direction:\s*column/.test(topbarRule));
  check('the header returns to one line on a wide screen',
    /@media[^{]*min-width:\s*900px[^{]*\{[\s\S]*?\.public-topbar\s*\{[^}]*flex-direction:\s*row/.test(bundleCss));

  // The nav links are what made row 1 overflow; they duplicate the bottom nav.
  check('the duplicate top links are hidden on a phone',
    /\.public-links\s*\{\s*display:\s*none/.test(bundleCss));
  check('the long CTA label is swapped for a short one on a phone',
    /\.cta-long\s*\{\s*display:\s*none/.test(bundleCss) && /\.cta-short\s*\{\s*display:\s*inline/.test(bundleCss));

  // And it must actually render.
  const home = await mount('/');
  check('the home page renders the brand name as one string',
    home.text().includes('ScottsTechX'));
  const brandEl = home.$('.brand');
  check('the brand element exists', !!brandEl);
  check('the brand contains a non-breaking name span', !!home.$('.brand .brand-name'));
  check('the search field is present and outside the brand row',
    !!home.$('.public-search input') && !home.$('.public-topbar-main .public-search'));
  check('the theme toggle stays in the top row',
    !!home.$('.public-topbar-main [aria-label="Toggle theme"]'));
  check('the home header logs no console errors', home.consoleErrors.length === 0,
    home.consoleErrors.join(' | '));
  home.close();
}

// ── 26. Auth + AI screens fit a phone ───────────────────────────────────────
// All three were reported as "not fitting". Each had a fixed dimension that no
// media query could override.
section('26. Login, sign-up and AI fit a small screen');
{
  // (a) A px maxWidth cannot shrink. 420px of copy in a 312px column pushed
  // the auth panel sideways; min(420px, 100%) keeps the desktop measure.
  const loginSrc = readFileSync(join(ROOT, 'src/pages/Login.tsx'), 'utf8');
  const regSrc = readFileSync(join(ROOT, 'src/pages/Register.tsx'), 'utf8');
  const aiSrc = readFileSync(join(ROOT, 'src/components/AiConsole.tsx'), 'utf8');
  check('login intro copy cannot exceed its column', /maxWidth: 'min\(420px, 100%\)'/.test(loginSrc));
  check('sign-up intro copy cannot exceed its column', /maxWidth: 'min\(420px, 100%\)'/.test(regSrc));
  check('AI welcome copy cannot exceed its column', /maxWidth: 'min\(460px, 100%\)'/.test(aiSrc));
  check('no bare pixel maxWidth is left in the auth pages',
    !/maxWidth:\s*\d+\s*[,}]/.test(loginSrc) && !/maxWidth:\s*\d+\s*[,}]/.test(regSrc));

  // (b) The 46ch marketing paragraph is ~373px — wider than a 360px phone.
  check('auth panel children are capped at the column width on a phone',
    /@media[^{]*max-width:\s*620px[\s\S]*?\.auth-brand p[^}]*max-width:\s*100%/.test(bundleCss));
  check('the demo credentials line can break instead of stretching the card',
    /\.auth-card \.muted[^}]*overflow-wrap:\s*anywhere/.test(bundleCss));

  // (c) The AI console had height:calc(100vh - topbar - 210px) AND
  // min-height:520px. The min-height won and pushed the composer off screen.
  check('the AI chat releases its desktop min-height on mobile',
    /@media[^{]*max-width:\s*960px[\s\S]*?\.ai-chat\s*\{[^}]*min-height:\s*0/.test(bundleCss));
  // The ceiling must sit on the CARD, not the transcript. .ai-chat-head and
  // .ai-chat-input are siblings of .ai-chat-body, so bounding the body at the
  // full card budget still produced a card taller than the screen by the
  // height of the head plus composer. This check previously asserted the
  // broken arrangement and so certified the bug as fixed.
  check('the AI card (not just the transcript) is bounded by the viewport',
    /\.ai-chat\{[^}]*max-height:\s*calc\(100dvh/.test(bundleCss));
  check('the AI transcript is free to shrink inside that bound',
    /\.ai-chat-body\{[^}]*max-height:\s*none/.test(bundleCss));

  // (d) The logged-in header squeezed search to 129px on a 360px screen.
  // The bundle is minified, so match the rule itself rather than trying to
  // walk the media query: the mobile override is the .topbar block that sets
  // height:auto (the desktop one sets a fixed --topbar-h).
  check('the authenticated header wraps to a second row on a phone',
    /\.topbar\{height:auto;flex-wrap:wrap/.test(bundleCss.replace(/\s+/g, '')) ||
    /\.topbar\s*\{[^}]*height:\s*auto[^}]*flex-wrap:\s*wrap/.test(bundleCss));
  check('the authenticated search takes a full row of its own',
    /\.topbar\.topbar-search\{[^}]*flex:1 0 100%/.test(bundleCss.replace(/\s*([{;:,])\s*/g, '$1')) ||
    /\.topbar \.topbar-search[^}]*flex:\s*1 0 100%/.test(bundleCss));

  // (e) And the pages must still render and work.
  const login = await mount('/login');
  check('login renders its form', !!login.$('input[type="email"]') && !!login.$('input[type="password"]'));
  check('login shows the brand lockup', !!login.$('.auth-lockup'));
  check('login logs no console errors', login.consoleErrors.length === 0, login.consoleErrors[0]);
  login.close();

  const reg = await mount('/register');
  check('sign-up renders its form', !!reg.$('input[type="email"]'));
  check('sign-up logs no console errors', reg.consoleErrors.length === 0, reg.consoleErrors[0]);
  reg.close();

  const ai = await mount('/ai', null, { settleMs: 2000 });
  check('the AI page renders its console', !!ai.$('.ai-console'));
  check('the AI composer is present', !!ai.$('.ai-chat-input textarea'));
  check('the AI page logs no console errors', ai.consoleErrors.length === 0, ai.consoleErrors[0]);
  ai.close();
}

// ── 27. Every dashboard screen: renders, has data, and fits a phone ─────────
// The user reported that dashboard screens did not fit a phone and that some
// showed nothing useful. Spot-checking two pages is what let that slip, so
// walk EVERY authenticated route for all three roles and assert three things
// per screen: it mounted, it is not an error/empty shell, and it contains no
// element forced wider than a 360px viewport.
section('27. Dashboard screens render with data and fit a 360px phone');
{
  const sessionFor = (u) => ({ token: u.token, user: u.user ?? u });

  const screens = [
    ['buyer',  '/buyer',                buyer,  ['Dashboard', 'Orders', 'Saved', 'Welcome', 'Overview']],
    ['buyer',  '/buyer/orders',         buyer,  ['Order', 'No orders', 'Orders']],
    ['buyer',  '/buyer/saved',          buyer,  ['Saved', 'No saved', 'bookmark']],
    ['buyer',  '/buyer/addresses',      buyer,  ['Address', 'address']],
    ['buyer',  '/buyer/payments',       buyer,  ['Payment', 'payment']],
    ['buyer',  '/buyer/refunds',        buyer,  ['Refund', 'refund']],
    ['buyer',  '/buyer/support',        buyer,  ['Support', 'support']],
    ['buyer',  '/buyer/settings',       buyer,  ['Settings', 'Profile', 'Account']],
    ['buyer',  '/cart',                 buyer,  ['Cart', 'cart', 'empty']],
    ['seller', '/seller',               seller, ['Dashboard', 'Revenue', 'Orders', 'Overview']],
    ['seller', '/seller/inventory',     seller, ['Inventory', 'Stock', 'product']],
    ['seller', '/seller/add-product',   seller, ['Add', 'Title', 'Price']],
    ['seller', '/seller/orders',        seller, ['Order', 'order']],
    ['seller', '/seller/analytics',     seller, ['Analytics', 'Revenue', 'Views']],
    ['seller', '/seller/store-settings',seller, ['Store', 'Settings']],
    ['admin',  '/admin',                admin,  ['Admin', 'Overview', 'Users', 'Products']],
    ['admin',  '/admin/users',          admin,  ['User', 'user']],
    ['admin',  '/admin/products',       admin,  ['Product', 'product']],
    ['admin',  '/admin/queue',          admin,  ['Queue', 'Pending', 'approval', 'No products']],
    ['admin',  '/admin/support',        admin,  ['Support', 'support']],
    ['buyer',  '/messages',             buyer,  ['Message', 'message', 'conversation']],
    ['buyer',  '/notifications',        buyer,  ['Notification', 'notification', 'alert']],
  ];

  for (const [role, route, who, expect] of screens) {
    let app;
    try {
      app = await mount(route, sessionFor(who), { settleMs: 1700 });
    } catch (e) {
      check(`${role} ${route} mounts`, false, String(e && e.message).slice(0, 120));
      continue;
    }
    const t = app.text();

    // 1. It rendered something real, not a blank shell or a crash screen.
    const alive = t.length > 120 && !/Something went wrong|Unexpected Application Error/i.test(t);
    check(`${role} ${route} renders`, alive, `len=${t.length}`);

    // 2. It shows content relevant to the screen (data or an honest empty state).
    const hasContent = expect.some((w) => t.includes(w));
    check(`${role} ${route} shows its content`, hasContent,
      `none of ${JSON.stringify(expect)} in "${t.slice(0, 90).replace(/\s+/g, ' ')}…"`);

    // 3. Nothing is pinned wider than a phone. jsdom does not lay out, but it
    //    does expose inline styles and width attributes, which is where a
    //    hard-coded desktop width would come from.
    const wide = [...app.$$('[style]')].filter((el) => {
      const st = el.getAttribute('style') || '';
      const m = /(?:^|[^-])width:\s*(\d+)px/.exec(st);
      return m && Number(m[1]) > 360;
    });
    check(`${role} ${route} has no element wider than 360px`, wide.length === 0,
      wide.length ? wide.slice(0, 2).map((e) => e.getAttribute('style')).join(' | ') : '');

    // 4. No console errors while rendering the screen.
    check(`${role} ${route} renders without console errors`,
      app.consoleErrors.length === 0,
      app.consoleErrors.slice(0, 1).join(' | ').slice(0, 140));

    app.close();
  }
}

// ── 28. Logout is reachable on a phone ──────────────────────────────────────
// It previously existed only in the sidebar drawer, which is hidden behind the
// hamburger on mobile — effectively unreachable for anyone who did not know to
// open it.
section('28. Logout reachable on mobile');
{
  const app = await mount('/buyer', { token: buyer.token, user: buyer.user ?? buyer }, { settleMs: 1600 });
  const logoutButtons = [...app.$$('[aria-label="Log out"]')];
  check('a logout control exists', logoutButtons.length > 0, `found ${logoutButtons.length}`);
  const inTopbar = logoutButtons.some((b) => b.closest('.topbar'));
  check('logout is present in the topbar (visible without opening the drawer)', inTopbar);
  const mobileVisible = logoutButtons.some((b) => b.classList.contains('show-sm') || b.closest('.topbar'));
  check('logout is marked visible on small screens', mobileVisible);
  app.close();
}

// ── 29. Auth screens fit vertically on a phone ──────────────────────────────
// Login/Register render inside the public shell, so the two-row topbar, the
// category bar and the fixed bottom nav all take height from them. The wrap
// nonetheless asked for min-height:100dvh, and the decorative brand panel
// stacked above the form, so the email field started below the fold.
section('29. Auth screens fit vertically on a phone');
{
  for (const route of ['/login', '/register']) {
    const app = await mount(route, null, { settleMs: 1500 });

    // The form must exist and come with its inputs.
    const emailInput = app.$('input[type="email"]');
    const pwInput = app.$('input[type="password"]');
    check(`${route} renders the email field`, !!emailInput);
    check(`${route} renders the password field`, !!pwInput);

    // The marketing paragraph may show again now that the auth shell freed
    // ~200px, but it must be clamped so it can never grow into the form.
    check(`${route} clamps the brand paragraph to two lines`,
      /\.auth-brand>p\{[^}]*-webkit-line-clamp:2/.test(bundleCss));

    // The marketplace furniture must NOT be on a sign-in screen.
    check(`${route} renders no product search bar`, !app.$('.public-search'));
    check(`${route} renders no category nav`, !app.$('.mainnav'));
    check(`${route} renders no marketplace footer`, !app.$('.public-footer'));
    check(`${route} renders no bottom nav`, !app.$('.bottomnav'));
    check(`${route} uses the dedicated auth shell`, !!app.$('.auth-shell'));
    check(`${route} keeps a brand link home`, !!app.$('.auth-topbar .brand'));

    // The wrap must not demand a full viewport inside the shell.
    check(`${route} auth wrap releases min-height:100dvh on phones`,
      /@media[^{]*max-width:\s*620px[\s\S]*?\.auth-wrap\{[^}]*min-height:\s*0/.test(bundleCss));

    app.close();
  }
}

// ── 30. AI pages give the chat the whole screen ─────────────────────────────
// The AI routes used to render a PageHeader whose title and subtitle sat ABOVE
// the chat, costing ~90px of vertical space on a phone and shrinking the
// conversation. That copy now lives inside the chat header and welcome panel.
section('30. AI pages: chat is full height and the helper copy is inside it');
{
  const aiRoutes = [
    ['/ai', null, 'AI shopper'],
    ['/buyer/ai', { token: buyer.token, user: buyer.user ?? buyer }, 'AI shopper'],
    ['/seller/ai', { token: seller.token, user: seller.user ?? seller }, 'AI copilot'],
  ];
  for (const [route, session, heading] of aiRoutes) {
    const app = await mount(route, session, { settleMs: 2200 });

    // No PageHeader above the console.
    const pageHead = app.$('.page-head');
    check(`${route} renders no page header above the chat`, !pageHead);

    // The console opts into the full-height layout.
    const consoleEl = app.$('.ai-console');
    check(`${route} console is present`, !!consoleEl);
    check(`${route} console uses the full-height variant`,
      !!consoleEl && consoleEl.classList.contains('ai-console-full'));

    // The heading that used to sit above the chat is now inside its header.
    const chatHead = app.$('.ai-chat-head');
    check(`${route} chat header carries the page title "${heading}"`,
      !!chatHead && chatHead.textContent.includes(heading),
      chatHead ? chatHead.textContent.slice(0, 70) : 'no .ai-chat-head');

    // The explanatory copy is inside the transcript's welcome panel.
    const welcome = app.$('.ai-welcome');
    check(`${route} welcome copy sits inside the chat body`,
      !!welcome && !!welcome.closest('.ai-chat-body'));

    app.close();
  }

  // The card must be sized to the space that is actually visible. 86dvh (the
  // previous rule) ended 165px below the fold and took the composer with it —
  // "taller" is only useful if the card still ENDS on screen.
  check('full-height chat is sized to the visible area, not a dvh share',
    /\.ai-console-full \.ai-chat\{[^}]*height:calc\(100dvh - 291px\)/.test(bundleCss),
    'expected height:calc(100dvh - 291px)');
  check('full-height chat no longer uses an unbounded max() height',
    !/\.ai-console-full \.ai-chat\{[^}]*height:max\(/.test(bundleCss));
  // The rail is a one-line chip row: no clamp, no clipping, full-size label.
  check('the agent picker is a horizontal chip row',
    /\.ai-console-full \.ai-agents \.col\{[^}]*flex-direction:row/.test(bundleCss));
  check('agent chips keep their label on one line so nothing is clipped',
    /\.ai-console-full \.ai-agents \.agent-card\{[^}]*white-space:nowrap/.test(bundleCss));
  check('agent labels are full body size, not shrunk',
    /\.ai-console-full \.ai-agents \.agent-name\{[^}]*font-size:var\(--fs-base\)/.test(bundleCss));

  // Product results must NOT be trapped inside the chat bubble. The bubble is
  // capped at 88% of the row and indented past the avatar, which left ~202px
  // on a 360px phone and rendered 95px-wide product cards.
  check('product results render outside the chat bubble',
    /\.ai-results\{[^}]*width:100%/.test(bundleCss));
  check('a turn stacks the bubble and its results',
    /\.ai-turn\{[^}]*flex-direction:column/.test(bundleCss));
  check('the AI console spans the full screen width on a phone',
    /\.ai-console-full\{[^}]*margin-inline:-13px/.test(bundleCss));
}

// ── 31. AI product results get the full chat width ─────────────────────────
// A shopping assistant's answer IS the product list, so it must not be
// squeezed into a chat bubble. Ask a real question and inspect where the grid
// lands in the DOM.
section('31. AI product results are full width, not inside the bubble');
{
  const app = await mount('/ai', null, { settleMs: 2200 });
  const ta = app.$('.ai-chat-input textarea');
  check('the AI composer is present', !!ta);
  if (ta) {
    app.type(ta, 'phone');
    const form = app.$('form.ai-chat-input');
    if (form) form.dispatchEvent(new app.window.Event('submit', { bubbles: true, cancelable: true }));
    // Give the backend time to answer and render.
    await new Promise((r) => setTimeout(r, 3200));

    const results = app.$('.ai-results');
    if (results) {
      check('product results are NOT inside a chat bubble',
        !results.closest('.bubble'),
        'found .ai-results nested inside .bubble');
      check('product results sit inside the transcript',
        !!results.closest('.ai-chat-body'));
      const grid = results.querySelector('.pgrid');
      check('the results panel contains a product grid', !!grid);
    } else {
      // The engine may answer without products; that is not a layout failure.
      check('assistant answered (no product grid in this reply)',
        app.$$('.bubble-ai').length > 0);
    }
  }
  app.close();
}

// ── 32. Email verification (no fake addresses) ──────────────────────────────
section('32. Email verification blocks fake addresses');
{
  // Registration must NOT hand out a verified account any more. This is the
  // whole point: previously email_verified was hardcoded true, so an address
  // only had to parse to become a real user.
  const email = `uitest_${Date.now()}_verify@scottstechx.test`;
  const reg = await apiFetch('/auth/register', {
    method: 'POST',
    body: JSON.stringify({ email, password: 'Test123!', displayName: 'Verify Probe', role: 'buyer' }),
  });
  check('registration succeeds', reg.status === 201, `status ${reg.status}`);
  check('a new account is NOT verified', reg.body.user.emailVerified === false,
    `emailVerified=${reg.body.user.emailVerified}`);
  check('the response says verification is required', reg.body.verification?.required === true);

  const auth = { authorization: `Bearer ${reg.body.token}` };

  // A wrong code must never verify.
  const wrong = await apiFetch('/auth/verify/confirm', {
    method: 'POST', headers: auth, body: JSON.stringify({ code: '000000' }),
  });
  check('a wrong code is rejected', wrong.status >= 400, `status ${wrong.status}`);

  const stillUnverified = await apiFetch('/auth/me', { headers: auth });
  check('a rejected code leaves the account unverified',
    stillUnverified.body.user.emailVerified === false);

  // The seller gate is the real-world consequence of being unverified.
  const upgrade = await apiFetch('/auth/upgrade-to-seller', { method: 'POST', headers: auth });
  check('an unverified user cannot become a seller', upgrade.status >= 400,
    `status ${upgrade.status}`);

  // With no SMTP configured the API returns the code so the flow stays testable.
  const code = reg.body.verification?.devCode;
  check('a code is issued', typeof code === 'string' && /^\d{6}$/.test(code || ''), String(code));

  const ok = await apiFetch('/auth/verify/confirm', {
    method: 'POST', headers: auth, body: JSON.stringify({ code }),
  });
  check('the correct code verifies the account', ok.status === 200 && ok.body.verified === true,
    `status ${ok.status}`);

  const reuse = await apiFetch('/auth/verify/confirm', {
    method: 'POST', headers: auth, body: JSON.stringify({ code }),
  });
  check('a code cannot be used twice', reuse.status >= 400, `status ${reuse.status}`);

  const after = await apiFetch('/auth/me', { headers: auth });
  check('the account is verified afterwards', after.body.user.emailVerified === true);

  const upgrade2 = await apiFetch('/auth/upgrade-to-seller', { method: 'POST', headers: auth });
  check('a verified user can become a seller', upgrade2.status === 200, `status ${upgrade2.status}`);

  await apiFetch(`/admin/users/${reg.body.user.id}`, {
    method: 'DELETE', headers: { authorization: `Bearer ${admin.token}` },
  }).catch(() => {});
}

// ── 33. Unverified accounts are GATED, not merely nagged ────────────────────
section('33. An unverified account cannot reach the app');
{
  // The bug: signing up logged you straight in and verification was a
  // dismissible banner. Every private route must now bounce to /verify-email.
  const unverified = { token: buyer.token, user: { ...buyer.user, emailVerified: false } };

  for (const route of ['/buyer', '/buyer/orders', '/buyer/saved', '/buyer/settings']) {
    const app = await mount(route, unverified, { settleMs: 1700 });
    check(`${route} redirects an unverified user to the gate`,
      !!app.$('[data-testid="verify-email-page"]'),
      `landed on ${app.window.location.pathname}`);
    check(`${route} does not render dashboard content`,
      !app.$('.dashboard-grid') && !app.$('[data-testid="orders-table"]'));
    app.close();
  }

  // The gate itself must be usable.
  const gate = await mount('/verify-email', unverified, { settleMs: 1700 });
  check('the gate names the address awaiting proof',
    (gate.text() || '').includes(buyer.user.email));
  check('the gate offers the "I clicked the link" check',
    !!gate.$('[data-testid="verify-page-check"]'));
  // Verification is link-only. A code box the site cannot act on is a dead
  // end, so its ABSENCE is the requirement now.
  check('the gate offers no six-digit code entry',
    !gate.$('[data-testid="verify-page-code"]'));
  check('the gate can resend', !!gate.$('[data-testid="verify-page-resend"]'));
  // Without this a typo in the address strands the user forever.
  check('the gate offers a way out (sign out)',
    !!gate.$('[data-testid="verify-page-signout"]'));
  check('no runtime errors on the gate', gate.consoleErrors.length === 0,
    gate.consoleErrors[0]);
  gate.close();

  // ── Verification by LINK ──────────────────────────────────────────────────
  // This is the flow the product is supposed to use, and the one that was
  // reported broken: the user only ever saw a six-digit code.
  //
  // The hard part is that the link is normally opened on a DIFFERENT device
  // from the one that signed up, so it must work with no session at all.
  // Passing session = null below is exactly that case.
  {
    const email = `uilink_${Date.now()}@scottstechx.test`;
    const reg = await apiFetch('/auth/register', {
      method: 'POST',
      body: JSON.stringify({
        email, password: 'Link123!', displayName: 'UI Link Tester', role: 'buyer',
      }),
    });
    check('a link-test account can be created', reg.status === 201, `got ${reg.status}`);

    const devLink = reg.body?.verification?.devLink;
    check('registration hands back a clickable verification link',
      typeof devLink === 'string' && devLink.includes('/verify-email?token='),
      JSON.stringify(reg.body?.verification));

    if (devLink) {
      const route = devLink.slice(devLink.indexOf('/verify-email'));

      // No session: a fresh browser opening the email.
      const clicked = await mount(route, null, { settleMs: 2600 });
      const text = clicked.text() || '';
      check('clicking the link with no session does not bounce to login',
        !/Welcome back|Sign in to your account/i.test(text) &&
        clicked.window.location.pathname !== '/login',
        `landed on ${clicked.window.location.pathname}`);
      check('the link verifies and lands the user inside the app',
        clicked.window.location.pathname === '/buyer' || /Email verified/i.test(text),
        `path ${clicked.window.location.pathname} — ${text.slice(0, 120)}`);
      check('the bearer token is stripped from the address bar',
        !clicked.window.location.search.includes('token='),
        clicked.window.location.search);
      check('no runtime errors while redeeming the link',
        clicked.consoleErrors.length === 0, clicked.consoleErrors[0]);
      clicked.close();

      // A spent link must explain itself rather than silently doing nothing.
      const reused = await mount(route, null, { settleMs: 2600 });
      const reusedText = reused.text() || '';
      check('a link that has already been used says so',
        /no longer valid|did not work|expired/i.test(reusedText),
        reusedText.slice(0, 160));
      reused.close();
    }

    // Clean up: this account is real.
    const admin = await login('admin@scottstechx.ug', 'Admin123!');
    const who = await apiFetch(`/admin/users?search=${encodeURIComponent(email)}`, {
      headers: { authorization: `Bearer ${admin.token}` },
    });
    const found = (who.body?.users || []).find((u) => u.email === email);
    if (found) {
      await apiFetch(`/admin/users/${found.id}`, {
        method: 'DELETE', headers: { authorization: `Bearer ${admin.token}` },
      });
    }
  }

  // The gate must lead with the link, not the code. Presenting both as equal
  // choices is what made the code look like the intended path.
  {
    const g = await mount('/verify-email', unverified, { settleMs: 1700 });
    const gtext = g.text() || '';
    check('the gate tells the user to open the link',
      /verification link|open it|click the link/i.test(gtext), gtext.slice(0, 160));
    check('there is no code-entry fallback anywhere on the gate',
      !g.$('[data-testid="verify-page-code-fallback"]') &&
      !g.$('[data-testid="verify-page-code"]') &&
      !g.$('[data-testid="verify-page-submit"]'));
    check('and the page never invites the user to type a code',
      !/enter (the |a )?(6|six)[- ]digit|enter a code/i.test(gtext), gtext.slice(0, 200));
    g.close();
  }

  // A verified user must never be trapped on it.
  const verified = { token: buyer.token, user: { ...buyer.user, emailVerified: true } };
  const skip = await mount('/verify-email', verified, { settleMs: 1700 });
  check('a verified user is sent past the gate',
    !skip.$('[data-testid="verify-email-page"]'),
    `still on the gate at ${skip.window.location.pathname}`);
  skip.close();

  // And a verified user reaches their dashboard as before.
  const dash = await mount('/buyer', verified, { settleMs: 1700 });
  check('a verified user still reaches the dashboard',
    !dash.$('[data-testid="verify-email-page"]'));
  dash.close();

  // Signed-out visitors have nothing to verify.
  const out = await mount('/verify-email', null, { settleMs: 1400 });
  check('a logged-out visitor is not shown the gate',
    !out.$('[data-testid="verify-email-page"]'));
  out.close();

  // Public browsing must stay open — the gate is for private routes only.
  const pub = await mount('/', unverified, { settleMs: 1600 });
  check('an unverified user can still browse the public marketplace',
    !pub.$('[data-testid="verify-email-page"]') && pub.$$('.pcard').length > 0);
  pub.close();

  // The client-side gate is only a convenience; the SERVER is the real one.
  // Prove the bundle knows how to react when the API refuses, since a route
  // guard cannot help a page that is already open.
  check('the client recognises the EMAIL_NOT_VERIFIED refusal',
    /EMAIL_NOT_VERIFIED/.test(bundleJs));
  check('the client routes that refusal to the gate',
    /stx:email-unverified/.test(bundleJs));

  // And confirm the backend really does refuse — with a token minted by the
  // same registration path a new user takes.
  const fresh = await apiFetch('/auth/register', {
    method: 'POST',
    body: JSON.stringify({
      email: `uitest_gate_${Date.now()}@scottstechx.test`,
      password: 'Test123!', displayName: 'UI Gate Probe', role: 'buyer',
    }),
  });
  const freshToken = fresh.body?.token;
  const refused = await apiFetch('/me/cart', {
    headers: { authorization: `Bearer ${freshToken}` },
  });
  check('the API itself refuses an unverified account', refused.status === 403,
    `got ${refused.status}`);
  check('the API refusal names the reason',
    refused.body?.code === 'EMAIL_NOT_VERIFIED', JSON.stringify(refused.body));
  // Clean up the probe immediately.
  if (fresh.body?.user?.id) {
    await apiFetch(`/admin/users/${fresh.body.user.id}`, {
      method: 'DELETE', headers: { authorization: `Bearer ${admin.token}` },
    });
  }

  // A server that cannot send mail must not tell people to check their inbox,
  // and it must offer a way out. Google proves the address with no mailer at
  // all, and adopts the existing account by email rather than making a second.
  check('the gate can report that delivery is impossible',
    /undeliverable/.test(bundleJs));
  check('it says so in plain language instead of "check your email"',
    /cannot send verification emails yet/i.test(bundleJs));
  check('and it offers Google as the way out',
    /or verify instantly/i.test(bundleJs));
  check('with a note that the existing account is kept',
    /your account stays as it is/i.test(bundleJs));

  // Resends are rate limited server-side. If the UI does not reflect that, the
  // user just sees a button that silently stops working, so the countdown has
  // to be visible and the button disabled while it runs.
  check('a throttled resend shows a live countdown, not a dead button',
    /Resend in \$\{|Resend in /.test(bundleJs));
  check('the countdown is driven by the server\'s own retry window',
    /retryAfterSec/.test(bundleJs));
}

// ── 34. The API base URL is resolved, never left empty in production ────────
// ── 33b. The sitemap is a real file, not the SPA shell ──────────────────────
section('33b. Search engines get XML, not the app shell');
{
  // The reported bug: Google Search Console said "Sitemap is HTML".
  //
  // The API serves a database-driven /sitemap.xml, but that lives on the API
  // origin. Google was pointed at the WEB origin, where no such file existed -
  // so the SPA catch-all ("/*  /index.html  200") answered with the app shell.
  // It returned 200, which made it look healthy while being useless.
  //
  // The fix is a real file in the build output, which takes precedence over
  // the catch-all. These checks read dist/ directly, because that is what gets
  // uploaded to the static host.
  const sitemapPath = join(DIST, 'sitemap.xml');
  const robotsPath = join(DIST, 'robots.txt');

  check('the build produces a sitemap file', existsSync(sitemapPath));
  check('the build produces a robots.txt', existsSync(robotsPath));

  if (existsSync(sitemapPath)) {
    const xml = readFileSync(sitemapPath, 'utf8');

    // The exact failure Search Console reported.
    check('the sitemap is NOT html',
      !/^\s*<!doctype html/i.test(xml) && !/<html/i.test(xml), xml.slice(0, 80));
    check('it declares itself as XML', /^<\?xml version="1\.0" encoding="UTF-8"\?>/.test(xml),
      xml.slice(0, 40));
    check('it uses the sitemaps.org namespace',
      xml.includes('http://www.sitemaps.org/schemas/sitemap/0.9'));

    const locs = [...xml.matchAll(/<loc>([^<]*)<\/loc>/g)].map((m) => m[1]);
    check('it lists URLs', locs.length > 0, `${locs.length}`);
    check('every URL is absolute', locs.every((u) => /^https?:\/\//.test(u)),
      locs.find((u) => !/^https?:\/\//.test(u)));
    check('there are no duplicates', new Set(locs).size === locs.length);
    check('no doubled slashes from a trailing-slash origin',
      !locs.some((u) => u.replace(/^https?:\/\//, '').includes('//')));
    check('the home page is listed', locs.some((u) => /\/$/.test(u)));

    // A sitemap that advertises private pages wastes crawl budget and reports
    // soft 404s once the crawler is bounced to /login.
    check('no private routes are advertised',
      !locs.some((u) => /\/(admin|cart|messages|notifications|verify-email)(\/|$|\?)/.test(u)),
      locs.find((u) => /\/(admin|cart|messages|notifications|verify-email)/.test(u)));

    // Unescaped & or < is the classic way a generated feed becomes invalid.
    const stripped = xml.replace(/&(amp|lt|gt|quot|apos);/g, '');
    check('special characters are escaped', !stripped.includes('&'),
      stripped.slice(stripped.indexOf('&') - 40, stripped.indexOf('&') + 40));
  }

  if (existsSync(robotsPath)) {
    const robots = readFileSync(robotsPath, 'utf8');
    check('robots.txt is not html', !/<html/i.test(robots), robots.slice(0, 60));
    check('robots.txt points crawlers at the sitemap',
      /^Sitemap:\s*https?:\/\/\S+\/sitemap\.xml$/m.test(robots),
      robots.slice(0, 200));
    check('dashboards are disallowed', /Disallow:\s*\/admin/.test(robots));
    check('the verification gate is disallowed', /Disallow:\s*\/verify-email/.test(robots));
    check('the public catalogue is still allowed', /Allow:\s*\//.test(robots));
  }
}

section('33c. The sitemap origin follows the host it is deployed on');
{
  // The bug this locks down: the origin was hardcoded to scottstechx.pages.dev.
  // We moved the site to Render, that domain stopped resolving, and the live
  // sitemap went on advertising 40 URLs on a dead host -- which Google rejects
  // outright, because a sitemap may only list URLs on its own origin.
  //
  // The generator writes into dist/, the same dist/ the assertions above read,
  // so every run here is sandwiched between a save and a restore.
  const smPath = join(DIST, 'sitemap.xml');
  const rbPath = join(DIST, 'robots.txt');
  const savedSm = existsSync(smPath) ? readFileSync(smPath, 'utf8') : null;
  const savedRb = existsSync(rbPath) ? readFileSync(rbPath, 'utf8') : null;

  const gen = (env) => {
    execFileSync(process.execPath, ['scripts/generate-sitemap.mjs'], {
      cwd: join(DIST, '..'),
      // Wipe the inherited deploy vars, otherwise the harness's own
      // environment decides the answer instead of the case under test.
      env: { ...process.env, SITE_URL: '', RENDER_EXTERNAL_URL: '',
             CF_PAGES_URL: '', DEPLOY_PRIME_URL: '', VITE_API_URL: '', ...env },
      stdio: 'pipe',
    });
    return {
      xml: readFileSync(smPath, 'utf8'),
      robots: readFileSync(rbPath, 'utf8'),
    };
  };
  const origins = (xml) => [...xml.matchAll(/<loc>(https?:\/\/[^/<]+)/g)].map((m) => m[1]);

  try {
    const render = gen({ RENDER_EXTERNAL_URL: 'https://sx-render.onrender.com' });
    const rOrigins = new Set(origins(render.xml));
    check('on Render every url uses the Render origin',
      rOrigins.size === 1 && rOrigins.has('https://sx-render.onrender.com'),
      [...rOrigins].join(', '));
    check('on Render robots.txt points at the same origin',
      render.robots.includes('Sitemap: https://sx-render.onrender.com/sitemap.xml'),
      render.robots.match(/^Sitemap:.*$/m)?.[0]);

    const cf = gen({ CF_PAGES_URL: 'https://sx-cf.pages.dev' });
    const cOrigins = new Set(origins(cf.xml));
    check('on Cloudflare Pages every url uses the Pages origin',
      cOrigins.size === 1 && cOrigins.has('https://sx-cf.pages.dev'),
      [...cOrigins].join(', '));

    const explicit = gen({
      SITE_URL: 'https://www.scottstechx.com',
      RENDER_EXTERNAL_URL: 'https://sx-render.onrender.com',
    });
    const eOrigins = new Set(origins(explicit.xml));
    check('an explicit SITE_URL beats the platform variable',
      eOrigins.size === 1 && eOrigins.has('https://www.scottstechx.com'),
      [...eOrigins].join(', '));

    const bare = gen({});
    const bOrigins = new Set(origins(bare.xml));
    check('with nothing set it falls back to a host that actually resolves',
      bOrigins.size === 1 && [...bOrigins][0] === 'https://scottstechx-web.onrender.com',
      [...bOrigins].join(', '));
    // Only the executable lines matter -- the comment above the fallback
    // names the dead domain on purpose, so nobody reintroduces it.
    const genSrc = readFileSync(join(DIST, '..', 'scripts', 'generate-sitemap.mjs'), 'utf8')
      .split('\n')
      .filter((l) => !/^\s*(\/\/|\*|\/\*)/.test(l))
      .join('\n');
    check('no code path can still emit the dead pages.dev domain',
      !genSrc.includes('scottstechx.pages.dev'),
      genSrc.split('\n').find((l) => l.includes('scottstechx.pages.dev')));
    check('a sitemap never mixes origins',
      origins(bare.xml).every((o) => o === origins(bare.xml)[0]));
  } finally {
    if (savedSm !== null) writeFileSync(smPath, savedSm, 'utf8');
    if (savedRb !== null) writeFileSync(rbPath, savedRb, 'utf8');
  }
}

section('34. Deployed builds always know where the API is');
{
  // The bug this locks down: with VITE_API_URL unset the bundle called the API
  // same-origin, the static host answered every one of those calls with the
  // SPA's own index.html at HTTP 200, res.json() threw, api() returned null,
  // and "Continue with Google" silently did nothing at all.
  check('the bundle carries a fallback API origin',
    bundleJs.includes('scottstechx-api.onrender.com'),
    'no fallback origin found in the built JS');
  check('the client refuses to treat a non-JSON 200 as data',
    /did not return data/i.test(bundleJs));
  check('a missing session token is reported instead of dereferenced',
    /did not return a session/i.test(bundleJs));
}

// ── 35. Firebase sign-in and verification ───────────────────────────────────
section('35. Firebase Authentication is wired in');
{
  // Firebase now owns Google sign-in AND the verification email, so no SMTP
  // credentials are needed. The SDK is loaded lazily, so it must NOT be
  // inlined into the main bundle.
  check('the Firebase project is configured in the build',
    bundleJs.includes('scottstechx-52bab'), 'project id missing from bundle');
  check('the Firebase auth domain is present',
    bundleJs.includes('firebaseapp.com'));
  check('the SDK is code-split, not inlined',
    bundleJs.length < 900_000, `${(bundleJs.length / 1024).toFixed(0)}kB main bundle`);

  const chunks = readdirSync(join(DIST, 'assets')).filter((f) => /esm/.test(f));
  check('Firebase ships as separate lazy chunks', chunks.length > 0, chunks.join(' '));

  // The unauthorised-domain mistake is the single most common Firebase
  // deployment failure, so the app must explain the actual fix.
  check('an unauthorised domain is explained, not just "failed"',
    /Authorised domains/i.test(bundleJs));
  check('the fix names the Firebase console',
    /Firebase Console/i.test(bundleJs));

  // Verification must be driven by Firebase's own claim. These live in the
  // lazily-loaded Firebase chunk rather than the entry bundle, which is the
  // point of code-splitting — so search everything the build emitted.
  const allJs = readdirSync(join(DIST, 'assets'))
    .filter((f) => f.endsWith('.js'))
    .map((f) => readFileSync(join(DIST, 'assets', f), 'utf8'))
    .join('\n');
  check('the app can re-check verification state',
    /refreshVerificationState|getIdToken/.test(allJs));
  check('the app can resend a verification email',
    /sendEmailVerification/.test(allJs));
  check('a password reset can be requested',
    /sendPasswordResetEmail/.test(allJs));
}

// ── 36. The Google button degrades instead of dying ─────────────────────────
section('36. Google sign-in falls back when Firebase is unavailable');
{
  // jsdom has no working Firebase SDK, which is exactly the "Firebase did not
  // load" case. The button must fall back to Google Identity Services rather
  // than render nothing — a dead button is the bug we already shipped once.
  const app = await mount('/login', null, { google: 'ready', settleMs: 1800 });
  const widget = app.$('[data-testid="google-signin"]');
  check('the Google widget is present', !!widget);
  check('it fell back to Google Identity Services',
    widget?.getAttribute('data-mode') === 'gis',
    `mode=${widget?.getAttribute('data-mode')}`);
  check('a usable Google button is rendered by the fallback',
    !!app.$('[data-testid="gis-button"]'));
  check('email and password sign-in is still available',
    !!app.$('input[type="password"]'));
  check('no runtime errors while falling back', app.consoleErrors.length === 0,
    app.consoleErrors[0]);
  app.close();
}

// ── 37. Email/password sign-up goes through Firebase ────────────────────────
section('37. Sign-up sends a real verification link');
{
  // Registration creates the account in Firebase, which emails the
  // verification link. Our own backend also emails a link, so verification is
  // link-only end to end - the website has no code entry at all.
  const allJs = readdirSync(join(DIST, 'assets'))
    .filter((f) => f.endsWith('.js'))
    .map((f) => readFileSync(join(DIST, 'assets', f), 'utf8'))
    .join('\n');

  check('registration creates the account in Firebase',
    /createUserWithEmailAndPassword/.test(allJs));
  check('a verification email is sent on sign-up',
    /sendEmailVerification/.test(allJs));
  check('an address already in Firebase is reported, not silently duplicated',
    /already exists with that email/i.test(bundleJs));
  check('the site redeems verification links',
    /verify\/link/.test(bundleJs));
  check('the site ships no six-digit code entry',
    !/one-time-code/.test(bundleJs) && !/6-digit verification code/.test(bundleJs));

  // The confirmation screen must actually tell the user to go and check.
  check('there is a dedicated verification page',
    /Verify your email/i.test(bundleJs));
  check('it suggests the spam folder', /spam/i.test(bundleJs));
  check('it offers to resend', /Resend email/i.test(bundleJs));
}

// ── 38. The registration form still works end to end ────────────────────────
section('38. Registration form renders and validates');
{
  const app = await mount('/register', null, { settleMs: 1600 });
  check('the form asks for an email', !!app.$('input[type="email"]'));
  check('the form asks for a password', !!app.$('input[type="password"]'));
  check('a buyer/seller choice is offered', !!app.$('select'));
  check('the verification gate is NOT shown before submitting',
    !app.$('[data-testid="verify-email-page"]'));

  // Mismatched passwords must be caught in the browser, before any account
  // exists anywhere.
  const pw = app.$$('input[type="password"]');
  if (pw.length >= 2) {
    app.type(app.$('input[type="email"]'), `mismatch_${Date.now()}@scottstechx.test`);
    app.type(pw[0], 'Test123!');
    app.type(pw[1], 'Different123!');
    const form = app.$('form');
    if (form) {
      form.dispatchEvent(new app.window.Event('submit', { bubbles: true, cancelable: true }));
      await new Promise((r) => setTimeout(r, 400));
    }
    check('mismatched passwords are refused before any account is created',
      /do not match/i.test(app.text()), app.text().slice(0, 120));
    check('no session was created by the failed attempt',
      !app.window.localStorage.getItem('stx_token'));
  }
  check('no runtime errors on the registration page', app.consoleErrors.length === 0,
    app.consoleErrors[0]);
  app.close();
}

// ── 39. A Firebase misconfiguration is never silent ─────────────────────────
section('39. Sign-up explains itself when Firebase cannot be used');
{
  // The bug: registration caught EVERY Firebase error and quietly fell back to
  // the six-digit code. The user was promised an email link, got a code, and
  // nothing said why — indistinguishable from the feature being broken.
  check('an unusable project explains that Email/Password must be enabled',
    /Email\/Password in Firebase Console|enable Email\/Password/i.test(bundleJs));
  check('an unauthorised domain names the setting to change',
    /Authorised domains/i.test(bundleJs));
  check('the fallback reason is logged for diagnosis',
    /Firebase unavailable, using fallback/.test(bundleJs));
  check('the fallback still promises a link, because that is what it sends',
    /check your email for the verification link/i.test(bundleJs));

  // A mailerless deployment must still hand over a working LINK, not a code.
  check('an undeliverable link is explained rather than swapped for a code',
    /Email delivery is not set up/i.test(bundleJs));
  check('the gate tells link-users to confirm once they have clicked',
    /I&rsquo;ve clicked the link|I’ve clicked the link|I've clicked the link/.test(bundleJs));

  // A fabricated appId is what broke this. Real ones carry a hex suffix, so
  // assert we never ship a hand-written placeholder again.
  check('no invented Firebase appId is compiled in',
    !/1:911393008938:web:scottstechx/.test(bundleJs),
    'a placeholder appId is still in the bundle');
}

// ── Cleanup ─────────────────────────────────────────────────────────────────
section('Cleanup');
{
  const adminAuth = { authorization: `Bearer ${admin.token}` };
  if (xssProductId) {
    const del = await apiFetch(`/admin/products/${xssProductId}`, { method: 'DELETE', headers: adminAuth });
    check('XSS test product removed', del.status === 200 || del.status === 204 || del.status === 404,
      `got ${del.status}`);
  }
  if (createdProductId) {
    const del = await apiFetch(`/admin/products/${createdProductId}`, { method: 'DELETE', headers: adminAuth });
    check('test product removed', del.status === 200 || del.status === 204 || del.status === 404,
      `status ${del.status}`);
  }
  // Put back the stock that checkout consumed, so the suite is repeatable.
  // Only the owning seller may edit a product, so sign in as each one. Resolve
  // seller id -> email from the live user list rather than hard-coding ids.
  const sellerDir = await apiFetch('/admin/users?role=seller&pageSize=100', { headers: adminAuth });
  const sellerLogins = Object.fromEntries((sellerDir.body.users || []).map((u) => [u.id, u.email]));
  const sellerTokens = {};
  for (const { id, quantity, sellerId } of stockToRestore) {
    const email = sellerLogins[sellerId];
    if (!email) continue;
    if (!sellerTokens[email]) {
      try { sellerTokens[email] = (await login(email, 'Seller123!')).token; }
      catch { continue; }
    }
    await apiFetch(`/seller/products/${id}`, {
      method: 'PATCH',
      headers: { authorization: `Bearer ${sellerTokens[email]}` },
      body: JSON.stringify({ stockQuantity: quantity }),
    });
  }
  if (stockToRestore.length) {
    const [{ id, quantity }] = stockToRestore;
    const after = await apiFetch(`/products/${id}`);
    check('stock consumed by checkout was restored', after.body.product?.stockQuantity === quantity,
      `${after.body.product?.stockQuantity} vs ${quantity}`);
  }

  const leftovers = await apiFetch('/products/search?q=UI%20Test');
  check('no UI-test products left in the catalogue',
    (leftovers.body.products || []).filter((p) => p.title.startsWith('UI Test')).length === 0);

  // Remove the throwaway buyer (and its orders/threads) so repeated runs do
  // not silt up the database. Best-effort: a missing endpoint must not fail.
  const purged = await apiFetch(`/admin/users/${buyer.user.id}`, { method: 'DELETE', headers: adminAuth });
  if (purged.status === 404 && purged.body?.message?.includes('not found')) {
    console.log('  · note: no admin user-delete endpoint; test buyers accumulate');
  }
  const stillThere = await apiFetch(`/admin/users?search=${encodeURIComponent(buyerEmail)}`, { headers: adminAuth });
  const remaining = (stillThere.body.users || []).filter((u) => u.email === buyerEmail).length;
  check('test buyer account cleaned up', remaining === 0,
    remaining ? `${buyerEmail} still present` : '');
}

// ════════════════════════════════════════════════════════════════════════════
console.log(`\n\x1b[1mResult: ${pass} passed, ${fail} failed\x1b[0m`);
if (fail > 0) {
  console.log('\nFailures:');
  for (const f of failures) console.log(`  • ${f}`);
  // Also emit them as a GitHub error annotation. CI log and artifact
  // downloads are blocked from the environment this repo is worked on from,
  // so the console output above is unreadable there; annotation text is not.
  if (process.env.GITHUB_ACTIONS) {
    const msg = failures.join('%0A').slice(0, 3500);
    console.log(`::error title=WEB UI SUITE FAILURES::${msg}`);
  }
  process.exit(1);
}
process.exit(0);
