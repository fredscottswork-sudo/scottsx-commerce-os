/**
 * ScottsTechX — quick smoke test.
 *
 *   npm run smoke
 *
 * Requires the server to be running (npm run dev). Verifies health, products,
 * CMS about page and auth, printing a short PASS/FAIL summary.
 */
import { getPool } from './db.js';

const BASE = process.env.BASE_URL || 'http://127.0.0.1:3001';

async function check(name: string, fn: () => Promise<boolean>) {
  try {
    const ok = await fn();
    console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}`);
    return ok;
  } catch (err) {
    console.log(`FAIL  ${name} — ${(err as Error).message}`);
    return false;
  }
}

async function main() {
  const results: boolean[] = [];

  results.push(
    await check('GET /healthz returns ok', async () => {
      const r = await fetch(`${BASE}/healthz`);
      return r.ok && ((await r.json()) as any).ok === true;
    })
  );

  results.push(
    await check('GET /api/v1/products returns 24 with Unsplash images', async () => {
      const r = await fetch(`${BASE}/api/v1/products`);
      if (!r.ok) return false;
      const { products } = (await r.json()) as any;
      return (
        products.length === 24 &&
        products.every((p: any) => String(p.imageUrl).startsWith('https://images.unsplash.com/'))
      );
    })
  );

  results.push(
    await check('GET /api/v1/cms/about contains founder bio', async () => {
      const r = await fetch(`${BASE}/api/v1/cms/about`);
      if (!r.ok) return false;
      const { page } = (await r.json()) as any;
      return page.body.includes('Kato Fred, Ugandan cybersecurity analyst, web dev and software dev.');
    })
  );

  results.push(
    await check('POST /api/v1/auth/register + login roundtrip', async () => {
      const email = `smoke${Date.now()}@example.com`;
      const reg = await fetch(`${BASE}/api/v1/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password: 'secret123', displayName: 'Smoke' }),
      });
      if (!reg.ok) return false;
      const login = await fetch(`${BASE}/api/v1/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password: 'secret123' }),
      });
      return login.ok;
    })
  );

  results.push(
    await check('GET /api/v1/me/faqs returns entries', async () => {
      const r = await fetch(`${BASE}/api/v1/me/faqs`);
      if (!r.ok) return false;
      const { faqs } = (await r.json()) as any;
      return Array.isArray(faqs) && faqs.length > 0;
    })
  );

  results.push(
    await check('DB pool connects', async () => {
      const { rows } = await getPool().query('SELECT 1 AS one');
      return Number(rows[0].one) === 1;
    })
  );

  const passed = results.filter(Boolean).length;
  console.log(`\n${passed}/${results.length} checks passed`);
  if (passed < results.length) process.exit(1);
}

main().catch((err) => {
  console.error('smoke failed:', err);
  process.exit(1);
});
