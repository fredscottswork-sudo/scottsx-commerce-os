/**
 * Build-time sitemap + robots.txt generator for the static web host.
 *
 * Why this exists
 * ---------------
 * The API already serves a database-driven /sitemap.xml, but that lives on the
 * API origin. Google was pointed at the WEB origin, where no such file existed,
 * so the SPA catch-all ("/*  /index.html  200") answered the request with the
 * app shell. Search Console fetched it, saw <!doctype html>, and reported
 * "Sitemap is HTML". A 200 response made it look healthy while being useless.
 *
 * A static host cannot run the dynamic route, so the file has to be baked into
 * the build. That is what this does, and because the catch-all only applies to
 * paths that do not exist on disk, a real file takes precedence.
 *
 * Freshness
 * ---------
 * Product URLs change whenever an admin approves a listing, so the generator
 * asks the API for the current approved catalogue at build time. If the API is
 * unreachable (offline build, cold backend, wrong URL) it still emits a valid
 * sitemap containing the stable public routes. A smaller correct sitemap beats
 * a failed build or an HTML page, so this NEVER breaks `npm run build`.
 *
 * Configuration
 * -------------
 *   SITE_URL       canonical public origin of the website (what Google indexes).
 *                  Falls back to the host platform's own variable
 *                  (RENDER_EXTERNAL_URL / CF_PAGES_URL), so a moved deployment
 *                  cannot keep advertising its old address.
 *   VITE_API_URL   API origin, used to enumerate approved products
 *
 * Both have sensible defaults for this project. SITE_URL matters most: <loc>
 * entries must be absolute, and Google rejects a sitemap whose URLs are on a
 * different host than the sitemap itself.
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const OUT_DIR = resolve(HERE, '..', 'dist');

/** Strip trailing slashes: `${base}/path` would otherwise produce `//path`. */
const trimSlashes = (value) => String(value || '').trim().replace(/\/+$/, '');

/**
 * The canonical public origin of THIS site.
 *
 * Order matters. A hardcoded default was wrong the moment the site moved: the
 * build ran on Render but every <loc> still said scottstechx.pages.dev, so the
 * sitemap advertised a host that no longer resolved - and Google rejects a
 * sitemap whose URLs live on a different host than the sitemap itself.
 *
 * RENDER_EXTERNAL_URL is injected by Render and is always the address the site
 * is actually served from, so it is a far better guess than anything written
 * here. SITE_URL still wins, because a custom domain is the one thing no
 * platform variable can know.
 */
const SITE_URL = trimSlashes(
  process.env.SITE_URL
  || process.env.RENDER_EXTERNAL_URL
  || process.env.CF_PAGES_URL
  || process.env.DEPLOY_PRIME_URL
  || 'https://scottstechx-web.onrender.com'
);
const API_URL = trimSlashes(process.env.VITE_API_URL || 'https://scottstechx-api.onrender.com');

/** How long to let a cold backend think before giving up on product URLs. */
const API_TIMEOUT_MS = 10_000;

/**
 * Public routes a signed-out visitor can actually open.
 *
 * Anything behind auth is deliberately absent: listing a page that bounces to
 * /login wastes crawl budget and shows up in Search Console as a soft 404.
 */
const PUBLIC_ROUTES = [
  { path: '/', changefreq: 'daily', priority: '1.0' },
  { path: '/search', changefreq: 'daily', priority: '0.9' },
  { path: '/nearby', changefreq: 'weekly', priority: '0.8' },
  { path: '/ai', changefreq: 'weekly', priority: '0.6' },
  { path: '/register', changefreq: 'monthly', priority: '0.4' },
  { path: '/login', changefreq: 'monthly', priority: '0.3' },
];

/** Paths that must never be crawled: private, per-user, or infinite. */
const DISALLOWED = [
  '/buyer', '/seller/add-product', '/seller/inventory', '/seller/orders',
  '/seller/analytics', '/seller/ai', '/seller/bulk-import', '/seller/store-settings',
  '/admin', '/cart', '/messages', '/notifications', '/verify-email',
];

/** A title or slug containing & or < would otherwise produce malformed XML. */
function xmlEscape(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

/** W3C date (YYYY-MM-DD), the form Google recommends for <lastmod>. */
function isoDate(value) {
  const d = value ? new Date(value) : new Date();
  return Number.isNaN(d.getTime())
    ? new Date().toISOString().slice(0, 10)
    : d.toISOString().slice(0, 10);
}

/**
 * Ask the API for everything a crawler may legitimately index.
 *
 * Only approved products are public - GET /products/:id returns 404 for
 * anything still pending or suspended, so listing those would advertise known
 * broken URLs.
 */
async function fetchCatalogue() {
  const urls = [];
  if (!API_URL) return urls;

  const get = async (path) => {
    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), API_TIMEOUT_MS);
    try {
      const res = await fetch(`${API_URL}/api/v1${path}`, { signal: ctrl.signal });
      if (!res.ok) return null;
      return await res.json();
    } catch {
      return null;
    } finally {
      clearTimeout(timer);
    }
  };

  // Page through the public catalogue rather than trusting one large request.
  const seenSellers = new Map();
  for (let page = 1; page <= 40; page++) {
    const data = await get(`/products?page=${page}&pageSize=100`);
    const items = data?.products;
    if (!Array.isArray(items) || items.length === 0) break;

    for (const p of items) {
      if (!p?.id) continue;
      urls.push({
        path: `/product/${p.id}`,
        lastmod: isoDate(p.updatedAt || p.createdAt),
        changefreq: 'weekly',
        priority: '0.8',
      });
      // A storefront is only worth indexing if it has something live on it.
      const sellerId = p.seller?.id;
      if (sellerId && !seenSellers.has(sellerId)) {
        seenSellers.set(sellerId, isoDate(p.updatedAt || p.createdAt));
      }
    }
    if (items.length < 100) break;
  }

  for (const [id, lastmod] of seenSellers) {
    urls.push({ path: `/seller/${id}`, lastmod, changefreq: 'weekly', priority: '0.7' });
  }

  // The CMS exposes a fixed set of slugs (GET /cms/:slug) with no index
  // endpoint, so ask for each one and keep the ones that exist.
  for (const slug of ['about', 'terms', 'privacy', 'buyer-protection']) {
    const data = await get(`/cms/${slug}`);
    if (!data?.page) continue;
    urls.push({
      path: `/cms/${slug}`,
      lastmod: isoDate(data.page.updatedAt),
      changefreq: 'monthly',
      priority: '0.4',
    });
  }

  return urls;
}

async function main() {
  const today = isoDate();
  const entries = PUBLIC_ROUTES.map((r) => ({ ...r, lastmod: today }));

  let catalogue = [];
  try {
    catalogue = await fetchCatalogue();
  } catch (err) {
    console.warn(`[sitemap] could not reach the API (${err?.message || err})`);
  }

  if (catalogue.length === 0) {
    console.warn(
      `[sitemap] no product URLs from ${API_URL || '(no API configured)'} - ` +
      'emitting the static routes only. The sitemap stays valid; re-run the ' +
      'build once the API is reachable to include products.'
    );
  }
  entries.push(...catalogue);

  // A duplicate <loc> is a validation warning, and paging can overlap.
  const seen = new Set();
  const unique = entries.filter((e) => {
    const loc = SITE_URL + e.path;
    if (seen.has(loc)) return false;
    seen.add(loc);
    return true;
  });

  const xml =
    '<?xml version="1.0" encoding="UTF-8"?>\n' +
    '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n' +
    unique
      .map(
        (e) =>
          '  <url>\n' +
          `    <loc>${xmlEscape(SITE_URL + e.path)}</loc>\n` +
          `    <lastmod>${e.lastmod}</lastmod>\n` +
          `    <changefreq>${e.changefreq}</changefreq>\n` +
          `    <priority>${e.priority}</priority>\n` +
          '  </url>'
      )
      .join('\n') +
    '\n</urlset>\n';

  const robots = [
    'User-agent: *',
    ...DISALLOWED.map((p) => `Disallow: ${p}`),
    // /search itself is useful; its filter permutations are a crawler trap.
    'Disallow: /search?',
    'Allow: /',
    '',
    `Sitemap: ${SITE_URL}/sitemap.xml`,
    '',
  ].join('\n');

  mkdirSync(OUT_DIR, { recursive: true });
  writeFileSync(resolve(OUT_DIR, 'sitemap.xml'), xml, 'utf8');
  writeFileSync(resolve(OUT_DIR, 'robots.txt'), robots, 'utf8');

  console.log(
    `[sitemap] wrote dist/sitemap.xml (${unique.length} urls, origin ${SITE_URL}) ` +
    'and dist/robots.txt'
  );
}

main().catch((err) => {
  // Never fail the build over SEO metadata.
  console.error('[sitemap] generation failed:', err?.message || err);
  process.exitCode = 0;
});
