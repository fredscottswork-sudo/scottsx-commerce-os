/**
 * Captures live backend responses for the Kotlin parser harness.
 *
 *   node capture.mjs <outDir> [apiBase]
 *
 * The app's dashboard parsers (DashboardModels.kt) consume two endpoints:
 *   GET  /api/v1/seller/dashboard/stats  -> stats + topProducts + recentOrders + salesSeries
 *   GET  /api/v1/seller/location         -> live pin + sharing + open state
 *
 * The capture records the seller's current (usually 0-state) location, turns
 * live sharing ON to exercise the lat/lng/updatedAt path, records it, and
 * restores the original state so seed data is untouched.
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

// The seeded marketplace seller. The dashboard is seller-scoped, so parsing a
// real payload requires a real seller token.
const seller = await call('/auth/login', {
  method: 'POST',
  body: { email: 'techhub@scottstechx.ug', password: 'Seller123!' },
});
if (seller.status !== 200) {
  console.error('could not log in as the seed seller', seller);
  process.exit(1);
}
const st = seller.data.token;

// The location BEFORE any change — the exact JSON the app sees on first open
// (seed stores default to sharing off, often with null lat/lng).
const before = await call('/seller/location', { token: st });
writeFileSync(`${outDir}/location-before.json`, JSON.stringify(before.data));

// Turn live sharing ON so the parser's lat/lng/updatedAt path is exercised.
const on = await call('/seller/location', {
  method: 'POST',
  token: st,
  body: { lat: 0.3476, lng: 32.5825, sharing: true },
});
if (on.status !== 200) {
  console.error('could not enable live location for the capture', on);
  process.exit(1);
}
writeFileSync(`${outDir}/location.json`, JSON.stringify(on.data.location ?? on.data));

// The full dashboard payload — the object SellerDashboard.fromJson parses.
const dash = await call('/seller/dashboard/stats', { token: st });
if (dash.status !== 200) {
  console.error('could not capture the seller dashboard', dash);
  process.exit(1);
}
writeFileSync(`${outDir}/dashboard.json`, JSON.stringify(dash.data));

// Restore whatever the seller had before. Sharing-on stores are re-enabled at
// their pin; everything else is switched off so seeds stay as they were.
const prev = before.data?.location;
if (prev?.sharing === true && typeof prev.lat === 'number' && typeof prev.lng === 'number') {
  await call('/seller/location', {
    method: 'POST',
    token: st,
    body: { lat: prev.lat, lng: prev.lng, sharing: true },
  });
} else {
  await call('/seller/location', { method: 'DELETE', token: st });
}

console.log('  captured dashboard stats, location (live) + pre-capture state');
