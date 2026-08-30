/**
 * Captures real backend responses for the Kotlin parser harness.
 *
 *   node capture.mjs <outDir> [apiBase]
 *
 * Only endpoints the SHIPPING Android client actually calls are captured —
 * the catalogue feed. (The messaging/nearby/settings surfaces in the old
 * V2Client speak a /chat/v2 and /user/* dialect the backend never
 * implemented; those are tracked as known limits in STATUS.md, and there
 * is no live parser to validate them against.)
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

// The catalogue feed — what the app's home screen parses. Read-only and
// public, so the capture leaves no residue in the database.
const feed = await call('/products?pageSize=30');
if (feed.status !== 200 || !Array.isArray(feed.data?.products)) {
  console.error('could not read the catalogue feed', feed.status, feed.data);
  process.exit(1);
}
writeFileSync(`${outDir}/products.json`, JSON.stringify(feed.data));

console.log(`  captured products (${feed.data.products.length} rows)`);
