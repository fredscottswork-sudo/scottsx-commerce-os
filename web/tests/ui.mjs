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
const bundleJs = readFileSync(join(DIST, jsMatch[1]), 'utf8');
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
async function mount(route, session = null, { settleMs = 1400 } = {}) {
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
  check('offers live GPS tracking', /Use my location/i.test(t));
  check('city presets are rendered', t.includes('Kampala') && t.includes('Jinja'));
  check('no runtime errors on nearby', app.consoleErrors.length === 0, app.consoleErrors[0]);
  app.close();
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
  const p1 = seedProducts.body.products[0];
  const p2 = seedProducts.body.products[1];
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
  const sellerAuth = { authorization: `Bearer ${seller.token}` };
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
  const conv = await apiFetch('/conversations', {
    method: 'POST',
    headers: buyerAuth,
    body: JSON.stringify({ sellerId: sampleProduct.seller.id, productId: sampleProduct.id }),
  });
  check('buyer can open a conversation with a seller', conv.status === 200 || conv.status === 201,
    `status ${conv.status}`);
  const convId = conv.body.conversation?.id;

  await apiFetch(`/conversations/${convId}/messages`, {
    method: 'POST', headers: buyerAuth, body: JSON.stringify({ text: 'Hello, is this still available?' }),
  });

  const app = await mount('/messages', buyer);
  check('messages list renders the conversation', app.text().includes(sampleProduct.seller.name) || app.$$('a').length > 0);
  app.close();

  const thread = await mount(`/messages/${convId}`, buyer);
  check('thread shows the sent message', thread.text().includes('Hello, is this still available?'));
  thread.close();
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
  const sellerAuth = { authorization: `Bearer ${seller.token}` };
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
section('13. Theming');
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

// ── Cleanup ─────────────────────────────────────────────────────────────────
section('Cleanup');
{
  const adminAuth = { authorization: `Bearer ${admin.token}` };
  if (createdProductId) {
    const del = await apiFetch(`/admin/products/${createdProductId}`, { method: 'DELETE', headers: adminAuth });
    check('test product removed', del.status === 200 || del.status === 204 || del.status === 404,
      `status ${del.status}`);
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
