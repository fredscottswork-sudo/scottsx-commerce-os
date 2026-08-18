/**
 * Captures real backend responses for the Kotlin parser harness.
 *
 *   node capture.mjs <outDir> [apiBase]
 *
 * Deliberately creates a message-less conversation too, because that is the
 * case that produces `lastTime: null` — the input that broke the old parser.
 */
import { writeFileSync } from 'node:fs';

const outDir = process.argv[2];
const base = (process.argv[3] || 'http://127.0.0.1:3001') + '/api/v1';
if (!outDir) {
  console.error('usage: node capture.mjs <outDir> [apiBase]');
  process.exit(1);
}

async function call(path, { method = 'GET', token, body } = {}) {
  const headers = { 'content-type': 'application/json' };
  if (token) headers.authorization = `Bearer ${token}`;
  const res = await fetch(base + path, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  return { status: res.status, data: await res.json().catch(() => null) };
}

const stamp = Date.now();
const buyer = await call('/auth/register', {
  method: 'POST',
  body: {
    email: `parsercheck_${stamp}@scottstechx.test`,
    password: 'Passw0rd!',
    displayName: 'Parser Check',
    role: 'buyer',
  },
});
if (buyer.status !== 201) {
  console.error('could not create the test buyer', buyer);
  process.exit(1);
}
const bt = buyer.data.token;

const seller = await call('/auth/login', {
  method: 'POST',
  body: { email: 'techhub@scottstechx.ug', password: 'Seller123!' },
});
const st = seller.data.token;
const sid = seller.data.user.id;

const product = (await call(`/products?sellerId=${sid}&pageSize=1`)).data.products[0];

// Thread WITH activity: text + offer + image.
const conv = await call('/conversations', {
  method: 'POST', token: bt, body: { sellerId: sid, productId: product.id },
});
const cid = conv.data.conversation.id;
await call(`/conversations/${cid}/messages`, {
  method: 'POST', token: bt, body: { text: 'hello' },
});
await call(`/conversations/${cid}/messages`, {
  method: 'POST', token: bt, body: { kind: 'offer', offerMinor: 45000000, offerQuantity: 3 },
});
await call(`/conversations/${cid}/messages`, {
  method: 'POST', token: st, body: { kind: 'image', imageUrl: 'https://example.com/p.jpg', attachmentName: 'p.jpg' },
});

// Thread WITHOUT activity -> lastTime is null. This is the regression case.
const others = (await call('/products?pageSize=30')).data.products
  .map((p) => p.seller.id)
  .filter((id) => id !== sid);
if (others.length) {
  await call('/conversations', { method: 'POST', token: bt, body: { sellerId: others[0] } });
}

writeFileSync(`${outDir}/inbox.json`, JSON.stringify((await call('/conversations', { token: bt })).data));
writeFileSync(`${outDir}/transcript.json`, JSON.stringify((await call(`/conversations/${cid}/messages`, { token: bt })).data));
writeFileSync(`${outDir}/head.json`, JSON.stringify((await call(`/conversations/${cid}`, { token: bt })).data));

// Nearby: no radiusKm, so this is the global search the app now performs.
// Kampala is used as the origin because the seed stores are in Uganda.
writeFileSync(
  `${outDir}/nearby.json`,
  JSON.stringify((await call('/sellers/nearby?lat=0.3476&lng=32.5825&sort=distance&limit=60')).data)
);
// A position far from every store, to prove the parser survives a foreign
// place and an empty-ish result rather than assuming Uganda.
writeFileSync(
  `${outDir}/nearby-far.json`,
  JSON.stringify((await call('/sellers/nearby?lat=51.5074&lng=-0.1278&sort=distance&limit=5')).data)
);

// Cart: add two different sellers' products so checkout has to split into
// one order per seller — the case a single-line capture would never catch.
const catalogue = (await call('/products?pageSize=30')).data.products;
const first = catalogue[0];
const second = catalogue.find((p) => p.seller.id !== first.seller.id) || catalogue[1];
await call('/me/cart', { method: 'POST', token: bt, body: { productId: first.id, quantity: 2 } });
await call('/me/cart', { method: 'POST', token: bt, body: { productId: second.id, quantity: 1 } });
writeFileSync(`${outDir}/cart.json`, JSON.stringify((await call('/me/cart', { token: bt })).data));

// A line that moderation pulls *after* it was added — the exact state the
// cart UI must refuse to check out. Captured before the real checkout so the
// suspension cannot interfere with it.
const third = catalogue.find((p) => p.id !== first.id && p.id !== second.id);
const adminEarly = await call('/auth/login', {
  method: 'POST', body: { email: 'admin@scottstechx.ug', password: 'Admin123!' },
});
await call('/me/cart', { method: 'POST', token: bt, body: { productId: third.id, quantity: 1 } });
await call(`/admin/products/${third.id}/suspend`, {
  method: 'POST', token: adminEarly.data.token, body: { reason: 'parser-check' },
});
writeFileSync(`${outDir}/cart-suspended.json`, JSON.stringify((await call('/me/cart', { token: bt })).data));
// Put it back exactly as it was and drop it from the cart before checkout.
await call(`/admin/products/${third.id}/approve`, { method: 'POST', token: adminEarly.data.token });
await call(`/me/cart/${third.id}`, { method: 'DELETE', token: bt });

// Check out for real, then record what the buyer is shown.
const placed = await call('/me/cart/checkout', { method: 'POST', token: bt, body: { phone: '0770000000' } });
writeFileSync(`${outDir}/cart-checkout.json`, JSON.stringify(placed.data));
writeFileSync(`${outDir}/cart-empty.json`, JSON.stringify((await call('/me/cart', { token: bt })).data));

// Put the stock back: this capture places real orders against seeded products.
const restore = [
  { id: first.id, qty: 2, sellerId: first.seller.id },
  { id: second.id, qty: 1, sellerId: second.seller.id },
];

// Clean up the throwaway account.
const admin = await call('/auth/login', {
  method: 'POST', body: { email: 'admin@scottstechx.ug', password: 'Admin123!' },
});
const adminToken = admin.data.token;

// Only the owning seller may edit a product, so resolve each seller's login
// from the live user list and restore the stock the checkout consumed.
const sellerDir = await call('/admin/users?role=seller&pageSize=100', { token: adminToken });
const sellerEmail = Object.fromEntries((sellerDir.data.users || []).map((u) => [u.id, u.email]));
const tokens = {};
for (const line of restore) {
  const email = sellerEmail[line.sellerId];
  if (!email) continue;
  if (!tokens[email]) {
    const s = await call('/auth/login', { method: 'POST', body: { email, password: 'Seller123!' } });
    tokens[email] = s.data?.token;
  }
  const token = tokens[email];
  if (!token) continue;
  const current = await call(`/products/${line.id}`);
  const stock = current.data?.product?.stockQuantity;
  if (typeof stock === 'number') {
    await call(`/seller/products/${line.id}`, {
      method: 'PATCH', token, body: { stockQuantity: stock + line.qty },
    });
  }
}

await call(`/admin/users/${buyer.data.user.id}`, { method: 'DELETE', token: adminToken });

console.log('  captured inbox, transcript, head, nearby, nearby-far, cart, cart-checkout');
