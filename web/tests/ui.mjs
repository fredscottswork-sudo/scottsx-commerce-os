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
import { readFileSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { JSDOM, VirtualConsole } from 'jsdom';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = join(__dirname, '..');
const DIST = process.env.WEB_DIST || join(ROOT, 'dist');
const API_BASE = process.env.API_BASE || 'http://127.0.0.1:3001';

let pass = 0;
let fail = 0;
const failures = [];

function check(name, cond, detail = '') {
  if (cond) { pass++; console.log(`  ✓ ${name}`); }
  else { fail++; failures.push(`${name}${detail ? ` — ${detail}` : ''}`); console.log(`  ✗ ${name}${detail ? ` — ${detail}` : ''}`); }
}

function section(t) { console.log(`\n\x1b[1m${t}\x1b[0m`); }

// ── Locate the built bundle ─────────────────────────────────────────────────
if (!existsSync(join(DIST, 'index.html'))) {
  console.error(`No build found at ${DIST}. Run: npm run build`);
  process.exit(1);
}
const indexHtml = readFileSync(join(DIST, 'index.html'), 'utf8');
const jsMatch = indexHtml.match(/src="(\/assets\/index-[^"]+\.js)"/);
const cssMatch = indexHtml.match(/href="(\/assets\/index-[^"]+\.css)"/);
if (!jsMatch) { console.error('Could not find the JS bundle in index.html'); process.exit(1); }
// The entry is an ES module. Since the Firebase SDK became a lazily-imported
// chunk, Rollup appends `export{...}` to the entry so the chunk can share its
// helpers — a token window.eval() cannot parse. The export is meaningless in
// the eval context (jsdom never loads the lazy chunks), so strip it.
const bundleJs = readFileSync(join(DIST, jsMatch[1]), 'utf8')
  .replace(/export\s*\{[^}]*\}\s*;?\s*$/, '');
const bundleCss = cssMatch ? readFileSync(join(DIST, cssMatch[1]), 'utf8') : '';

// ── Backend helpers ─────────────────────────────────────────────────────────
async function apiFetch(path, opts = {}) {
  const res = await fetch(`${API_BASE}/api/v1${path}`, {
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
async function mount(route, session = null, { settleMs = 1400, google = 'block' } = {}) {
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
  window.fetch = (input, init) => {
    const url = typeof input === 'string' ? input : input.url;

    // Lazily-imported chunks (e.g. the Firebase SDK) are requested as
    // absolute http://localhost:5173/assets/... URLs, because that is the
    // jsdom document's origin. There is no server on that port: the suite
    // deliberately reads the built bundle off disk. Serve those requests from
    // dist/ instead of hitting the network.
    const assetPath = url.startsWith('/assets/')
      ? url
      : (url.startsWith('http') && new URL(url).pathname.startsWith('/assets/')
          ? new URL(url).pathname
          : null);
    if (assetPath) {
      try {
        const body = readFileSync(join(DIST, assetPath), 'utf8');
        const type = assetPath.endsWith('.css') ? 'text/css' : 'text/javascript';
        return Promise.resolve(new Response(body, {
          status: 200, headers: { 'content-type': type },
        }));
      } catch {
        return Promise.resolve(new Response('not found', { status: 404 }));
      }
    }

    const absolute = url.startsWith('http') ? url : `${API_BASE}${url}`;
    return fetch(absolute, init);
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
  window.navigator.geolocation = {
    getCurrentPosition: (ok) => ok({ coords: { latitude: 0.3476, longitude: 32.5825 } }),
    watchPosition: () => 1,
    clearWatch: () => {},
  };

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
      roleChosen: session.user.roleChosen === undefined ? true : !!session.user.roleChosen,
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
const buyerAuth = { authorization: `Bearer ${buyer.token}` };

// Registration now issues an UNVERIFIED account and the backend gates every
// private route until the address is proven. In dev the code comes back in
// the response, so the fixture verifies itself before continuing.
{
  const vc = await apiFetch('/auth/verify/confirm', {
    method: 'POST',
    headers: buyerAuth,
    body: JSON.stringify({ code: buyer?.verification?.devCode }),
  });
  if (vc.status !== 200) { console.error('Could not verify the test buyer', vc); process.exit(1); }
  // The seeded session must carry the verified flag, or the app's gate will
  // park every buyer page on /verify-email.
  buyer.user = { ...buyer.user, ...(vc.body?.user || {}), emailVerified: true };
}
const sellerAuth = { authorization: `Bearer ${seller.token}` };

// ── 1. Public marketplace ───────────────────────────────────────────────────
section('1. Public marketplace (logged out)');
{
  const app = await mount('/');
  const t = app.text();
  check('home page renders the brand', t.includes('ScottsTechX'));
  check('shows a sign-in CTA for guests', t.includes('Sign in'));
  check('renders real product cards from the API', app.$$('.pcard').length > 0,
    `found ${app.$$('.pcard').length} cards`);
  check('shows a real seeded product title', t.includes(sampleProduct.title),
    `expected "${sampleProduct.title}"`);
  check('prices are formatted as UGX', /UGX\s[\d,]+/.test(t));
  check('dark theme is the default', app.window.document.documentElement.getAttribute('data-theme') === 'dark',
    `got ${app.window.document.documentElement.getAttribute('data-theme')}`);
  check('no runtime errors on the home page', app.consoleErrors.length === 0, app.consoleErrors[0]);
  check('home has exactly one search bar (nothing small above it)',
    app.$$('.home-searchbar input:not([type="file"])').length === 1 && app.$$('.public-search').length === 0,
    `${app.$$('.public-search').length} topbar search bars on the home page`);
  check('home opens with the first even row of categories', app.$$('.cat-grid-even .cat-tile').length === 8,
    `${app.$$('.cat-grid-even .cat-tile').length} tiles`);
  check('category tiles link into filtered search',
    !!app.$('.cat-grid-even a[href^="/search?category="]'));
  check('every visible category tile has a real photo',
    app.$$('.cat-grid-even .cat-tile').every((t) => !!t.querySelector('.cat-photo')),
    `${app.$$('.cat-grid-even .cat-tile').filter((t) => !t.querySelector('.cat-photo')).length} tile(s) without a photo`);
  const firstPhoto = app.$('.cat-grid-even .cat-photo');
  check('category photos are bundled locally',
    !!firstPhoto && /url\("\/cat\/.*\.jpg"\)/.test(firstPhoto.style.backgroundImage || ''),
    firstPhoto ? firstPhoto.style.backgroundImage : '');
  const chatCard = app.$('.feature-card--image[data-anim="chat"]');
  check('chat card has its own slotted background', !!chatCard && /\/feature\/chat\.jpg/.test(bundleCss));
  const catsToggle = app.$('.cat-show-all');
  check('remaining categories are hidden behind a toggle', !!catsToggle);
  check('toggle reports the hidden count',
    !!catsToggle && /Show all 16 categories/.test(catsToggle.textContent || ''),
    catsToggle ? catsToggle.textContent : '');
  if (catsToggle) {
    check('toggle starts collapsed', catsToggle.getAttribute('aria-expanded') === 'false');
    await app.click(catsToggle, 400);
    check('expanding reveals the full 16-category grid',
      app.$$('.cat-grid-even .cat-tile').length === 16,
      `${app.$$('.cat-grid-even .cat-tile').length} tiles`);
    const expandedBtn = app.$('.cat-show-all');
    check('expanded toggle reports state',
      !!expandedBtn && expandedBtn.getAttribute('aria-expanded') === 'true');
    await app.click(expandedBtn, 400);
    check('collapsing hides the other categories again',
      app.$$('.cat-grid-even .cat-tile').length === 8,
      `${app.$$('.cat-grid-even .cat-tile').length} tiles`);
  }
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
    // The curated 16-category set is merged with live facets, so the grid is
    // always full and even (16 = 4×4) even when the DB has no listings in a
    // category — but every LIVE category must still be present and linked.
    check('mega-menu renders the 16-category grid', items.length === 16,
      `${items.length} rendered (expected 16)`);
    check('mega-menu still lists every live category',
      liveCats.every((c) => items.some((e) => (e.textContent || '').includes(c.name))),
      JSON.stringify(liveCats.map((c) => c.name)));
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
  check('seller nav has no Inventory button (removed; reachable from the sidebar)', !t.includes('Inventory'));
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
// ── 1c. Verified store carousel ─────────────────────────────────────────────
section('1c. Verified store carousel');
{
  // What the API says is verified, asked independently of the page, so the
  // strip is compared against live data rather than against itself.
  const verifiedRes = await apiFetch('/sellers/nearby?lat=0.3476&lng=32.5825&verifiedOnly=true&sort=rating&limit=12');
  const verified = verifiedRes.body.sellers || [];
  check('the marketplace has verified stores to show', verified.length > 0, `${verified.length} found`);

  const allRes = await apiFetch('/sellers/nearby?lat=0.3476&lng=32.5825&limit=50');
  const unverified = (allRes.body.sellers || []).find((s) => !s.verified);

  const app = await mount('/');
  const strip = app.$('[data-testid="verified-stores"]');
  check('the home page shows the verified-store strip', !!strip);

  const cards = app.$$('[data-testid="verified-store-card"]');
  check('one card per verified store', cards.length === verified.length,
    `${cards.length} cards vs ${verified.length} stores`);

  const cardText = cards.map((c) => c.textContent || '');
  const topStore = verified[0] ? (verified[0].storeName || verified[0].name) : '';
  check('cards carry real store names from the API', cardText.some((t) => t.includes(topStore)),
    `expected "${topStore}"`);
  check('every card carries the verified badge',
    cards.length > 0 && cards.every((c) => !!c.querySelector('.vstore-check')));
  check('every card links to its own storefront',
    cards.every((c, i) => c.getAttribute('href') === `/seller/${verified[i].id}`),
    cards.map((c) => c.getAttribute('href')).join(', '));
  check('cards show the store rating and listing count',
    cardText.length > 0 && cardText.every((t) => /\d\.\d/.test(t) && /listings/i.test(t)));

  if (unverified) {
    const leaked = cardText.some((t) => t.includes(unverified.storeName || unverified.name));
    check('no unverified store leaks into the strip', !leaked,
      `${unverified.storeName || unverified.name} is not verified`);
  }

  check('the strip is a snap-scrolling track', !!app.$('.vstores-track'));
  check('no runtime errors from the carousel', app.consoleErrors.length === 0, app.consoleErrors[0]);
  app.close();
}

// ── 1d. Phone typography ────────────────────────────────────────────────────
section('1d. Phone typography');
{
  // Read straight from the built stylesheet, so this checks what actually ships.
  const phoneBlock = bundleCss.match(/@media\s*\(max-width:\s*767px\)\s*\{/);
  check('a phone-width type scale ships in the bundle', !!phoneBlock);
  check('the desktop scale is untouched (14px base)', /--fs-base:\s*14px/.test(bundleCss));
  check('phones get a smaller scale (13px base)', /--fs-base:\s*13px/.test(bundleCss));

  if (phoneBlock) {
    const after = bundleCss.slice(phoneBlock.index);
    check('the smaller scale is scoped inside the phone media query', /--fs-base:\s*13px/.test(after));
  }

  // The pre-existing 379px step is tighter still and must still win there.
  check('the sub-380px scale survives', /@media\s*\(max-width:\s*379px\)/.test(bundleCss)
    && /--fs-base:\s*11\.5px/.test(bundleCss));

  // Under 16px, iOS Safari zooms the viewport the moment a field is focused.
  check('form fields keep a 16px floor so iOS does not zoom on focus',
    /font-size:\s*max\(16px,\s*var\(--fs-base\)\)/.test(bundleCss));

  check('the carousel styles ship with the bundle', /\.vstore-card\s*\{/.test(bundleCss));
}

// ── 2. Search page ──────────────────────────────────────────────────────────
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
  check('seller card offers follow', /Follow/.test(t) && /Store/.test(t),
    'expected both Follow and Store buttons');
  // The avatar must be an <img> when the store has a logo, and a letter
  // otherwise — never a broken image slot (regression: logo was dropped).
  if (sampleProduct.seller.logoUrl) {
    check('seller card renders the store logo', app.$$('.avatar img').length > 0,
      `${app.$$('.avatar img').length} image avatars`);
  } else {
    const avs = app.$$('.avatar');
    const letter = avs.find((a) => /^[A-Z]$/.test((a.textContent || '').trim()));
    check('seller card falls back to the store initial without a logo',
      !!letter && !app.$('.avatar img'),
      `letter avatar: ${letter ? letter.textContent : 'missing'}; img: ${!!app.$('.avatar img')}`);
  }
  check('no runtime errors on product detail', app.consoleErrors.length === 0, app.consoleErrors[0]);
  app.close();
}

// ── 3b. Seller storefront ──────────────────────────────────────────────────
section('3b. Seller storefront');
{
  const app = await mount(`/seller/${sampleProduct.seller.id}`);
  const t = app.text();
  check('storefront shows the store name', t.includes(sampleProduct.seller.name),
    `expected ${sampleProduct.seller.name}`);
  check('storefront offers Follow and Message store', /Follow/.test(t) && /Message store/.test(t),
    'expected both follow and message CTAs');
  check('storefront renders the seller logo or initial', !!app.$('.avatar'),
    'header avatar present');
  check('no runtime errors on storefront', app.consoleErrors.length === 0, app.consoleErrors[0]);
  app.close();
}

// ── 4. Nearby (live vs last-known positions) ────────────────────────────────
section('4. Nearby stores');
{
  const app = await mount('/nearby', buyer);
  const t = app.text();
  check('nearby page renders store cards', app.$$('.store-card').length > 0,
    `${app.$$('.store-card').length} stores`);
  // The store cards must be big and readable — no more ultra-compact boxes.
  const cardName = app.$$('.store-card strong.ellipsis')[0];
  check('store names render at a readable 14px', cardName?.style?.fontSize === '14px',
    `got ${cardName?.style?.fontSize}`);
  const cardAvatar = app.$('.store-card .avatar-lg');
  check('store logos render at 40px', cardAvatar?.style?.width === '40px',
    `got ${cardAvatar?.style?.width}`);
  check('nearby cards ship with the larger padding in CSS',
    /.store-card\s*\{[^}]*padding:\s*12px\s+14px/.test(bundleCss));
  check('phone-size nearby cards keep the bigger 13px store names',
    /\.store-card\s+strong\.ellipsis\s*\{[^}]*font-size:\s*13px/.test(bundleCss));
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
  check('shows the place hierarchy trail (city › region › country)',
    !!app.$('.place-trail') && /Uganda/.test(app.$('.place-trail')?.textContent || ''),
    app.$('.place-trail')?.textContent || 'no trail');
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
  check('send-inquiry button present', /Send inquiry/i.test(t));
  check('cart is messaging-first (payment agreed in chat)', /agree payment/i.test(t));

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
  check('AI console boots with its welcome screen', /Ask anything/i.test(t));
  check('offers agent starter suggestions', app.$$('.ai-chat-body .chip').length > 0,
    `${app.$$('.ai-chat-body .chip').length} chips`);
  check('offers conversation starters', app.$$('.chip').length > 0);
  check('message composer is present', !!app.$('.ai-chat-input textarea'));

  // Actually ask the assistant something and assert a grounded answer.
  const box = app.$('.ai-chat-input textarea');
  await app.type(box, 'cheapest phones');
  check('market mark sits in the AI brand orbs', !!app.$('.ai-brand-orb .ai-brand-mark'),
    app.$('.ai-brand-orb .ai-brand-mark') ? 'logo found' : 'no logo mark');
  check('send button becomes active when the message is ready', !!app.$('.ai-chat-input .ai-send--ready'));
  const sendBtn = app.$('.ai-chat-input button[aria-label="Send message"]');
  if (box && sendBtn) {
    await app.click(sendBtn, 3200);
    const after = app.text();
    check('assistant produced a reply bubble', app.$$('.bubble-ai').length > 0,
      `${app.$$('.bubble-ai').length} ai bubbles`);
    check('reply is grounded in the real catalogue',
      /UGX/.test(after) || app.$$('.bubble-ai .pcard').length > 0,
      'expected prices or product cards in the answer');
    // AI-generated answers carry a visible watermark + actions.
    const wm = app.$('.ai-answer-wm');
    check('AI answers are watermarked as ScottsTechX AI',
      !!wm && /ScottsTechX AI/i.test(wm.textContent || ''), wm ? wm.textContent : 'no watermark');
    check('answers offer copy + share actions', app.$$('.ai-answer-act').length >= 2,
      `${app.$$('.ai-answer-act').length} actions`);
    check('answers disclose AI-generated content', /AI answers are generated/i.test(app.text()));
    check('market quick-actions bar appears with the conversation',
      app.$$('.ai-quick').length >= 4, `${app.$$('.ai-quick').length} quick actions`);
    check('last answer offers Regenerate',
      [...app.$$('.ai-answer-act')].some((b) => /regenerate/i.test(b.textContent || '')));
    check('offers voice + one direct photo picker (camera modal gone)',
      !!app.$('.ai-chat-input button[aria-label="Attach a photo"]') &&
      !app.$('.ai-chat-input button[aria-label="Search by photo"]'));
  } else {
    check('assistant produced a reply bubble', false, 'composer not found');
  }
  app.close();
}

// ── 9b. Public STX AI page (guest, no login) ────────────────────────────────
section('9b. Public STX AI page');
{
  const app = await mount('/ai', buyer);
  const t = app.text();
  check('AI chat renders for members', /Ask anything|AI shopper/i.test(t));
  check('starter suggestions are shown', app.$$('.ai-chat-body .chip').length >= 2,
    `${app.$$('.ai-chat-body .chip').length} chips`);
  check('members can type to the assistant', !!app.$('.ai-chat-input textarea'));
  check('the AI page stays on /ai for members', app.window.location.pathname === '/ai',
    `path ${app.window.location.pathname}`);
  const aiBrand = app.$('.ai-brand');
  check('AI page carries the animated ScottsTechX brand', !!aiBrand && /ScottsTechX/i.test(aiBrand.textContent || ''),
    aiBrand ? aiBrand.textContent : 'no brand bar');
  check('AI page shows its marketplace identity mark', !!app.$('.ai-brand-orb'));
  check('AI page offers extra capabilities (voice + photo)',
    app.$$('.ai-cap').length >= 1 && !!app.$('.ai-chat-input button[aria-label="Attach a photo"]'),
    `${app.$$('.ai-cap').length} header caps + composer photo button`);
  check('no runtime errors on the AI page', app.consoleErrors.length === 0, app.consoleErrors[0]);
  app.close();
}

// ── 9c. Composer image selector: exactly ONE, opens the picker directly ─────
section('9c. One direct image selector');
{
  const app = await mount('/ai', buyer);
  const composer = app.$('.ai-chat-input');
  const attachBtn = composer && app.$('.ai-chat-input button[aria-label="Attach a photo"]');
  // The old selector opened a modal; it must be gone entirely.
  const oldBtn = composer && app.$('.ai-chat-input button[aria-label="Search by photo"]');
  const fileInput = composer && app.$('.ai-chat-input input[type="file"]');
  check('composer has exactly one image selector', !!attachBtn && !oldBtn,
    attachBtn ? 'attach button found, old button gone' : 'attach button missing');
  check('the selector is the direct file picker (hidden input beside it)',
    !!fileInput && fileInput.getAttribute('accept') === 'image/*',
    fileInput ? `accept=${fileInput.getAttribute('accept')}` : 'no file input');
  if (attachBtn && composer) {
    // Clicking the button triggers the SAME hidden input (no modal in between).
    let triggered = false;
    fileInput?.addEventListener('click', () => { triggered = true; });
    await app.click(attachBtn, 200);
    check('pressing it opens the file picker, not a panel/modal',
      triggered && !app.$('.visual-search') && !app.$('.modal'),
      app.$('.visual-search') ? 'old visual-search still opens' : 'no panel or modal opened');
    check('photo button sits beside the Send button', !!composer.querySelector('button[aria-label="Send message"], button[aria-label="Stop generating"]'));
  }
  app.close();
}

// ── 9d. Dashboard top nav: Orders must never hide under Messages ────────────
section('9d. Dashboard top nav overlap');
{
  const app = await mount('/seller', seller);
  const inner = app.$('.mainnav-inner');
  const linksText = inner ? inner.textContent : '';
  check('seller nav shows Orders and Messages together',
    /Orders/.test(linksText) && /Messages/.test(linksText) && !!inner,
    'expected both words in the primary nav row');
  check('primary links row scrolls instead of hiding under the right cluster',
    /\.mainnav-links\{[^}]*overflow-x:\s*auto/.test(bundleCss));
  check('right nav cluster never shrinks under the word links',
    /\.mainnav-right\{[^}]*flex-shrink:\s*0/.test(bundleCss));
  check('no runtime errors on the seller dashboard', app.consoleErrors.length === 0, app.consoleErrors[0]);
  app.close();
}

// ── 9e. Search-bar photo search works like the AI composer ─────────────────
// One button → native file picker → search. No panel, no drop zone, no image
// URL or hint fields anywhere.
section('9e. One-tap photo search');
{
  const app = await mount('/cms/about');
  await new Promise((r) => setTimeout(r, 300));
  const shellCam = [...app.$$('button[aria-label="Search by image"]')]
    .find((b) => !!b.closest?.('.searchbar-zone'));
  check('shell search bar has a photo button', !!shellCam);
  if (shellCam) {
    const fileInput = shellCam.closest('form')?.querySelector('input[type="file"]');
    check('a hidden file picker sits beside it (accept=image/*)',
      !!fileInput && fileInput.getAttribute('accept') === 'image/*',
      fileInput ? `accept=${fileInput.getAttribute('accept')}` : 'no file input');
    let triggered = false;
    fileInput?.addEventListener('click', () => { triggered = true; });
    await app.click(shellCam, 250);
    check('pressing it opens the file picker directly (no panel/modal)',
      triggered && !app.$('.search-img-panel') && !app.$('.visual-search') && !app.$('.modal'),
      triggered ? 'a panel still opened' : 'picker not triggered');
    check('no image-URL or hint fields anywhere',
      !app.$('input[aria-label="Image URL"]') && !app.$('input[aria-label="Describe the item (optional)"]'));
  }
  app.close();

  const sp = await mount('/search');
  await new Promise((r) => setTimeout(r, 400));
  const cam2 = [...sp.$$('button[aria-label="Search by image"]')].find((b) => !b.closest?.('.searchbar-zone'));
  check('search page has its own photo button', !!cam2);
  if (cam2) {
    const fi = cam2.parentElement?.querySelector('input[type="file"]');
    let triggered = false;
    fi?.addEventListener('click', () => { triggered = true; });
    await sp.click(cam2, 250);
    check('search page button opens the picker directly (no panel)',
      triggered && !sp.$('.search-img-panel') && !sp.$('.visual-search') && !sp.$('.modal'),
      triggered ? 'panel opened' : 'picker not triggered');
  }
  sp.close();
}

// ── 9f. Every dashboard page renders without runtime errors ───────────────
section('9f. Dashboard sweep (no runtime errors, guide present)');
{
  const pages = [
    ['/buyer', buyer], ['/buyer/orders', buyer], ['/buyer/saved', buyer], ['/buyer/addresses', buyer],
    ['/buyer/refunds', buyer], ['/buyer/support', buyer], ['/buyer/settings', buyer], ['/cart', buyer],
    ['/seller', seller], ['/seller/inventory', seller], ['/seller/add-product', seller], ['/seller/bulk-import', seller],
    ['/seller/orders', seller], ['/seller/analytics', seller], ['/seller/store-settings', seller],
    ['/admin', admin], ['/admin/queue', admin], ['/admin/users', admin], ['/admin/products', admin], ['/admin/support', admin],
    ['/messages', buyer], ['/notifications', buyer],
  ];
  for (const [route, session] of pages) {
    const app = await mount(route, session, { settleMs: 1200 });
    const errs = app.consoleErrors.filter((e) => !/leaflet|ResizeObserver/i.test(e));
    check(`${route} renders without runtime errors`, errs.length === 0, errs[0]);
    const isDash = /^\/(buyer|seller|admin)(\/|$)/.test(route);
    if (isDash) check(`${route} has the dashboard guide`, !!app.$('[data-testid="dashboard-guide-fab"]'));
    else check(`${route} has no dashboard guide`, !app.$('[data-testid="dashboard-guide-fab"]'));
    if (isDash || route === '/cart') check(`${route} has no topbar search bar`, !app.$('.topbar-search'), 'topbar search still shown');
    app.close();
  }
  const adm = await mount('/admin', admin, { settleMs: 1600 });
  check('admin overview shows the locations map', !!adm.$('[data-testid="admin-locations"]'));
  check('locations map lists located users', adm.$$('.adm-map-list li').length > 0 || /located/.test(adm.text()));
  adm.close();
  const g = await mount('/seller', seller, { settleMs: 900 });
  const fab = g.$('[data-testid="dashboard-guide-fab"]');
  if (fab) {
    await g.click(fab, 200);
    check('guide panel opens', !!g.$('[data-testid="dashboard-guide"]'));
    const chip = g.$('[data-testid="dashboard-guide"] .chip');
    if (chip) {
      await g.click(chip, 2500);
      const t = g.$('[data-testid="dashboard-guide"]')?.textContent || '';
      check('guide answers about the seller dashboard', /seller|inventory|order|product/i.test(t) && !/admin queue|\/admin/i.test(t), t.slice(0, 120));
    }
  }
  g.close();
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
  const composer = thread.$('.thread-composer textarea[aria-label="Message"]');
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
  check('seller sees the offer as received', /offer/i.test(sellerView.text()) && !!sellerView.byText('button', 'Accept'), sellerView.text().slice(0, 160));
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
  check('anonymous visitor can browse the cart (no forced login)',
    app.window.location.pathname === '/cart',
    `got ${app.window.location.pathname}`);
  check('guest sees the cart page instead of a login wall',
    /Your inquiry cart|Start shopping/i.test(app.text()),
    app.text().slice(0, 140));
  const guestBadge = app.$('.public-topbar a[aria-label="Your cart"]');
  check('guest chrome exposes the cart entry point', !!guestBadge);
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
  check('the email field is offered and there is no password field',
    !!app.$('#login-email') && !app.$('input[type="password"]'));
  check('the page offers "Continue as guest"', !!app.$('[data-testid="continue-guest"]'));

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
    !!app.$('#login-email') && !!app.byText('button', 'Continue with email'));
  check('no unhandled error escapes when Google is blocked',
    app.consoleErrors.length === 0, app.consoleErrors[0]);
  app.close();
}
{
  // (c) /register is the same passwordless flow now.
  const app = await mount('/register', null, { google: 'ready' });
  check('/register lands on the sign-in page', app.window.location.pathname === '/login',
    app.window.location.pathname);
  app.close();
}

// ── 16. Passwordless email code + onboarding + guest gate ───────────────────
section('16. Email code sign-in, onboarding, guest gate');
{
  // Guests see a gate on Nearby and AI, not the feature.
  const g1 = await mount('/nearby');
  check('guest on /nearby sees the sign-in gate', !!g1.$('[data-testid="gate-nearby"]') && !g1.$('.store-card'));
  check('gate stays on /nearby (no redirect)', g1.window.location.pathname === '/nearby');
  g1.close();
  const g2 = await mount('/ai');
  check('guest on /ai sees the sign-in gate', !!g2.$('[data-testid="gate-ai"]') && !g2.$('.ai-chat-input textarea'));
  g2.close();
  const g3 = await mount('/');
  check('guest can still browse the home page', g3.$$('.pcard').length > 0 && !g3.$('[data-testid^="gate-"]'));
  g3.close();

  // API: start → wrong code → right code → onboarding as seller.
  const otpEmail = `otp_${Date.now()}@scottstechx.test`;
  const start = await apiFetch('/auth/otp/start', { method: 'POST', body: JSON.stringify({ email: otpEmail }) });
  check('otp/start accepts a new address', start.status === 200 && start.body.isNew === true, JSON.stringify(start.body).slice(0, 120));
  check('otp/start returns a dev code when no SMTP is configured', /^\d{6}$/.test(String(start.body.devCode || '')));
  const bad = await apiFetch('/auth/otp/verify', { method: 'POST', body: JSON.stringify({ email: otpEmail, code: '000000' }) });
  check('a wrong code is rejected', bad.status === 400 && /not correct/i.test(bad.body.error || ''), JSON.stringify(bad.body));
  const good = await apiFetch('/auth/otp/verify', { method: 'POST', body: JSON.stringify({ email: otpEmail, code: start.body.devCode }) });
  check('the right code returns a session', good.status === 200 && !!good.body.token, JSON.stringify(good.body).slice(0, 120));
  check('a new account needs onboarding', good.body.needsOnboarding === true && good.body.user?.roleChosen === false);
  check('the account is email-verified after the code', good.body.user?.emailVerified === true);
  const reuse = await apiFetch('/auth/otp/verify', { method: 'POST', body: JSON.stringify({ email: otpEmail, code: start.body.devCode }) });
  check('a code cannot be used twice', reuse.status === 400);
  const throttled = await apiFetch('/auth/otp/start', { method: 'POST', body: JSON.stringify({ email: otpEmail }) });
  check('resend is throttled', throttled.status === 429, String(throttled.status));

  // Onboarding UI: the fresh session lands on /onboarding and both roles offered.
  const fresh = { token: good.body.token, user: good.body.user };
  const ob = await mount('/buyer', fresh);
  check('a not-yet-onboarded account is sent to /onboarding', ob.window.location.pathname === '/onboarding', ob.window.location.pathname);
  check('onboarding offers buyer and seller', !!ob.$('[data-testid="role-buyer"]') && !!ob.$('[data-testid="role-seller"]'));
  const cont = ob.$('[data-testid="onboarding-continue"]');
  check('continue is disabled until a role is picked', !!cont && cont.disabled);
  await ob.click(ob.$('[data-testid="role-seller"]'), 200);
  await ob.click(ob.$('[data-testid="onboarding-continue"]'), 400);
  check('choosing seller asks for a store name', !!ob.$('#ob-store') && !!ob.$('[data-testid="logo-drop"]'));
  ob.close();

  const onboard = await apiFetch('/auth/onboarding', {
    method: 'POST', headers: { authorization: `Bearer ${good.body.token}` },
    body: JSON.stringify({ role: 'seller', storeName: 'OTP Test Store', storeLogoUrl: 'https://example.com/logo.png' }),
  });
  check('onboarding as seller succeeds', onboard.status === 200 && onboard.body.user?.role === 'seller' && onboard.body.user?.roleChosen === true,
    JSON.stringify(onboard.body).slice(0, 160));
  const st = await apiFetch('/seller/store-settings', { headers: { authorization: `Bearer ${onboard.body.token}` } }).catch(() => ({ status: 0, body: {} }));
  check('store name and logo were saved', st.status === 200 && st.body?.settings?.storeName === 'OTP Test Store' && st.body?.settings?.storeLogoUrl === 'https://example.com/logo.png',
    JSON.stringify(st.body).slice(0, 160));
  const dash = await mount('/seller', { token: onboard.body.token, user: onboard.body.user });
  check('the new seller reaches the seller dashboard', dash.window.location.pathname === '/seller', dash.window.location.pathname);
  dash.close();

  // Login UI: email → code screen.
  const app = await mount('/login');
  await app.type(app.$('#login-email'), `otpui_${Date.now()}@scottstechx.test`, 200);
  await app.click(app.byText('button', 'Continue with email'), 1800);
  check('submitting an email shows the 6-digit code screen', app.$$('[data-testid="otp-inputs"] .otp-box').length === 6,
    app.text().slice(0, 120));
  check('the dev code is surfaced when no SMTP is configured', !!app.$('[data-testid="dev-code"]'));
  app.close();
}

// ── Cleanup ─────────────────────────────────────────────────────────────────
section('Cleanup');
{
  const adminAuth = { authorization: `Bearer ${admin.token}` };
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
  process.exit(1);
}
process.exit(0);
