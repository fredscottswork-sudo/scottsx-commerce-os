/**
 * ScottsTechX — sitemap and robots.txt.
 *
 *   GET /sitemap.xml   every publicly reachable URL, generated from the database
 *   GET /robots.txt    crawl policy, pointing at the sitemap
 *
 * Why these are served by the API rather than shipped as static files in the
 * web build: the interesting URLs are product and storefront pages, and those
 * change every time an admin approves a listing. A file baked at build time
 * would be stale within hours and would list products that are still pending
 * review — or worse, keep advertising ones that were later suspended.
 *
 * Only content that a signed-out visitor can actually load is included. A
 * sitemap that lists pages returning 404 (an unapproved product) or a login
 * redirect (a dashboard) wastes crawl budget and is reported in Search Console
 * as an error.
 *
 * The public site is a separate origin from this API, so the canonical host
 * comes from PUBLIC_WEB_URL. Without it there is no way to emit correct
 * absolute URLs — <loc> must be absolute — so the endpoints return 503 rather
 * than publishing links to the wrong domain.
 */
import type { FastifyInstance } from 'fastify';
import { getPool } from '../../db.js';

/** Static routes a logged-out visitor can open, with a crawl priority. */
const PUBLIC_ROUTES: { path: string; priority: string; changefreq: string }[] = [
  { path: '/', priority: '1.0', changefreq: 'daily' },
  { path: '/search', priority: '0.9', changefreq: 'daily' },
  { path: '/nearby', priority: '0.8', changefreq: 'weekly' },
  { path: '/ai', priority: '0.6', changefreq: 'weekly' },
  { path: '/login', priority: '0.3', changefreq: 'monthly' },
  { path: '/register', priority: '0.4', changefreq: 'monthly' },
];

/**
 * Paths that must never be crawled: they are behind auth, they are per-user,
 * or they are infinite (every filter permutation of /search is a new URL).
 */
const DISALLOWED = [
  '/buyer', '/seller/add-product', '/seller/inventory', '/seller/orders',
  '/seller/analytics', '/seller/ai', '/seller/bulk-import', '/seller/store-settings',
  '/admin', '/cart', '/messages', '/notifications',
];

/** XML text escaping. A product title containing & or < would break the feed. */
function xmlEscape(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

/** Trailing slashes produce '//path' when concatenated, which 404s on most hosts. */
function siteUrl(): string | null {
  const raw = process.env.PUBLIC_WEB_URL?.trim();
  if (!raw) return null;
  return raw.replace(/\/+$/, '');
}

/** W3C date (YYYY-MM-DD) — the form Google recommends for <lastmod>. */
function isoDate(value: unknown): string {
  const d = value instanceof Date ? value : new Date(String(value));
  return Number.isNaN(d.getTime())
    ? new Date().toISOString().slice(0, 10)
    : d.toISOString().slice(0, 10);
}

export default async function registerSitemapRoute(app: FastifyInstance) {
  const pool = getPool();

  app.get('/sitemap.xml', async (request, reply) => {
    const base = siteUrl();
    if (!base) {
      return reply.code(503).send({
        error: 'Sitemap unavailable: set PUBLIC_WEB_URL to the public site origin',
      });
    }

    const entries: string[] = [];
    const push = (path: string, lastmod: string, changefreq: string, priority: string) => {
      entries.push(
        `  <url>\n` +
        `    <loc>${xmlEscape(base + path)}</loc>\n` +
        `    <lastmod>${lastmod}</lastmod>\n` +
        `    <changefreq>${changefreq}</changefreq>\n` +
        `    <priority>${priority}</priority>\n` +
        `  </url>`
      );
    };

    const today = new Date().toISOString().slice(0, 10);
    for (const r of PUBLIC_ROUTES) push(r.path, today, r.changefreq, r.priority);

    // Approved products only. GET /products/:id returns 404 for anything else,
    // so listing them would publish known-broken URLs.
    const products = await pool.query(
      `SELECT id, updated_at, created_at
         FROM products
        WHERE status = 'approved'
        ORDER BY COALESCE(updated_at, created_at) DESC
        LIMIT 40000`
    );
    for (const row of products.rows) {
      push(`/product/${row.id}`, isoDate(row.updated_at ?? row.created_at), 'weekly', '0.8');
    }

    // Storefronts for sellers who actually have something to show. A store page
    // with no live products is a thin page and hurts more than it helps.
    const sellers = await pool.query(
      `SELECT u.id, MAX(COALESCE(p.updated_at, p.created_at)) AS touched
         FROM users u
         JOIN products p ON p.seller_id = u.id AND p.status = 'approved'
        WHERE u.role = 'seller'
        GROUP BY u.id
        ORDER BY touched DESC
        LIMIT 10000`
    );
    for (const row of sellers.rows) {
      push(`/seller/${row.id}`, isoDate(row.touched), 'weekly', '0.7');
    }

    const cms = await pool.query(`SELECT slug, updated_at FROM cms_pages ORDER BY slug`);
    for (const row of cms.rows) {
      push(`/cms/${row.slug}`, isoDate(row.updated_at), 'monthly', '0.4');
    }

    const xml =
      `<?xml version="1.0" encoding="UTF-8"?>\n` +
      `<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n` +
      entries.join('\n') +
      `\n</urlset>\n`;

    return reply
      .type('application/xml; charset=utf-8')
      // Crawlers revisit often; an hour keeps load trivial without going stale.
      .header('cache-control', 'public, max-age=3600')
      .send(xml);
  });

  app.get('/robots.txt', async (_request, reply) => {
    const base = siteUrl();
    const lines = [
      'User-agent: *',
      ...DISALLOWED.map((p) => `Disallow: ${p}`),
      // /search itself is useful; its filter permutations are a crawler trap.
      'Disallow: /search?',
      'Allow: /',
      '',
    ];
    if (base) lines.push(`Sitemap: ${base}/sitemap.xml`, '');

    return reply
      .type('text/plain; charset=utf-8')
      .header('cache-control', 'public, max-age=86400')
      .send(lines.join('\n'));
  });
}
