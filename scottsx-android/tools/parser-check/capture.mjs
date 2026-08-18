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

// Clean up the throwaway account.
const admin = await call('/auth/login', {
  method: 'POST', body: { email: 'admin@scottstechx.ug', password: 'Admin123!' },
});
await call(`/admin/users/${buyer.data.user.id}`, { method: 'DELETE', token: admin.data.token });

console.log('  captured inbox.json, transcript.json, head.json, nearby.json, nearby-far.json');
