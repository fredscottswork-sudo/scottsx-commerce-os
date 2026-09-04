/**
 * ScottsTechX — product queries (optimized for speed)
 *
 * Visibility rule: buyers only see status='approved'
 * Optimizations:
 * - In-memory cache for search (30s) and facets (60s)
 * - Limited expansions (max 5) to avoid heavy OR chains
 * - View counter batching (flush every 5s)
 * - pageSize default 24 not 40 for faster initial load
 */
import type pg from 'pg';
import { NotFoundError, ForbiddenError, ValidationError } from '../../errors.js';
import { expandTerm } from '../ai/catalog-context.js';
import { reverseGeocode } from '../../geo/gazetteer.js';

export const PRODUCT_SELECT = `
  SELECT
    p.id,
    p.title,
    p.description,
    p.category,
    p.brand,
    p.price_minor::int AS "priceMinor",
    p.old_price_minor::int AS "oldPriceMinor",
    p.stock_quantity AS "stockQuantity",
    COALESCE(pm.url, p.image_url) AS "imageUrl",
    (SELECT COALESCE(json_agg(m.url ORDER BY m.sort_order ASC), '[]'::json)
       FROM product_media m WHERE m.product_id = p.id) AS "mediaUrls",
    p.rating::float AS rating,
    p.rating_count AS "ratingCount",
    p.is_flash_deal AS "isFlashDeal",
    p.discount_percent AS "discountPercent",
    p.location,
    p.status,
    p.rejection_reason AS "rejectionReason",
    p.view_count AS "viewCount",
    p.created_at AS "createdAt",
    p.vision_decision AS "visionDecision",
    p.vision_rejection_reasons AS "visionRejectionReasons",
    p.vision_category AS "visionCategory",
    p.vision_subcategory AS "visionSubcategory",
    p.vision_title AS "visionTitle",
    p.vision_tags AS "visionTags",
    p.vision_checked_at AS "visionCheckedAt",
    json_build_object(
      'id', u.id,
      'name', COALESCE(s.store_name, u.display_name),
      'rating', COALESCE(s.rating, 0)::float,
      'location', COALESCE(s.city, p.location),
      'lat', s.lat,
      'lng', s.lng,
      'address', s.address,
      'verified', COALESCE(s.verified, false),
      'logoUrl', COALESCE(NULLIF(s.store_logo_url, ''), u.profile_photo_url)
    ) AS seller
  FROM products p
  JOIN users u ON u.id = p.seller_id
  LEFT JOIN store_settings s ON s.user_id = p.seller_id
  LEFT JOIN LATERAL (
    SELECT url FROM product_media WHERE product_id = p.id ORDER BY sort_order ASC LIMIT 1
  ) pm ON true
`;

// Accurate seller place cache: geocoded lat/lng wins over typed free text.
const placeCache = new Map<string, { at: number; value: string | null }>();
const PLACE_CACHE_TTL = 60_000;
function sellerPlace(seller: any, fallback: string): string {
  const lat = Number(seller?.lat);
  const lng = Number(seller?.lng);
  const key = Number.isFinite(lat) && Number.isFinite(lng) ? `${Math.round(lat * 10000) / 10000}:${Math.round(lng * 10000) / 10000}` : '';
  if (key) {
    const cached = placeCache.get(key);
    if (cached && Date.now() - cached.at < PLACE_CACHE_TTL) return cached.value || fallback;
    const place = reverseGeocode(lat, lng)?.shortLabel ?? null;
    if (placeCache.size > 2000) {
      const first = placeCache.keys().next().value;
      if (first) placeCache.delete(first);
    }
    placeCache.set(key, { at: Date.now(), value: place });
    if (place) return place;
  }
  const address = typeof seller?.address === 'string' ? seller.address.trim() : '';
  return address || fallback;
}

export function rowsToProducts(rows: any[]) {
  return rows.map((r) => {
    const seller = typeof r.seller === 'string' ? JSON.parse(r.seller) : r.seller;
    if (seller) {
      // The seller's real geocoded place beats a typed city/pickup text that
      // may be stale or wrong ("Kampala, Nakasero" from an old form).
      seller.location = sellerPlace(seller, seller.location || 'Uganda');
      delete seller.lat;
      delete seller.lng;
      delete seller.address;
    }
    return { ...r, currency: 'UGX', seller };
  });
}

export interface SearchParams {
  q?: string;
  category?: string;
  brand?: string;
  minPrice?: number;
  maxPrice?: number;
  minRating?: number;
  verifiedOnly?: boolean;
  inStock?: boolean;
  flashOnly?: boolean;
  sellerId?: string;
  sort?: 'relevance' | 'newest' | 'price_asc' | 'price_desc' | 'rating' | 'popular';
  page?: number;
  pageSize?: number;
}

// Cache for search (30s TTL)
type CacheEntry<T> = { at: number; value: T };
const searchCache = new Map<string, CacheEntry<any>>();
const facetsCache = new Map<string, CacheEntry<any>>();
const CACHE_TTL_SEARCH = 30_000;
const CACHE_TTL_FACETS = 60_000;

function cacheKey(params: SearchParams): string {
  return JSON.stringify(params);
}
function getSearchCache(key: string) {
  const e = searchCache.get(key);
  if (!e) return null;
  if (Date.now() - e.at > CACHE_TTL_SEARCH) { searchCache.delete(key); return null; }
  return e.value;
}
function setSearchCache(key: string, value: any) {
  if (searchCache.size > 200) {
    const first = searchCache.keys().next().value;
    if (first) searchCache.delete(first);
  }
  searchCache.set(key, { at: Date.now(), value });
}

// View counter batching
const viewBuffer = new Map<string, number>();
let viewFlushTimer: NodeJS.Timeout | null = null;
function scheduleViewFlush(db: pg.Pool) {
  if (viewFlushTimer) return;
  viewFlushTimer = setTimeout(async () => {
    const batch = Array.from(viewBuffer.entries());
    viewBuffer.clear();
    viewFlushTimer = null;
    for (const [id, count] of batch) {
      await db.query('UPDATE products SET view_count = view_count + $2 WHERE id = $1', [id, count]).catch(() => {});
    }
  }, 5000);
}

export async function searchProducts(db: pg.Pool, params: SearchParams = {}) {
  const {
    q = '',
    category,
    brand,
    minPrice,
    maxPrice,
    minRating,
    verifiedOnly,
    inStock,
    flashOnly,
    sellerId,
    sort = 'relevance',
    page = 1,
    pageSize = 24,
  } = params;

  const key = cacheKey({ q, category, brand, minPrice, maxPrice, minRating, verifiedOnly, inStock, flashOnly, sellerId, sort, page, pageSize });
  const cached = getSearchCache(key);
  if (cached) return cached;

  const where: string[] = [`p.status = 'approved'`];
  const values: any[] = [];
  const term = q.trim();

  if (term) {
    const clauses: string[] = [];
    values.push(`%${term}%`);
    clauses.push(`p.title ILIKE $${values.length} OR p.brand ILIKE $${values.length} OR p.category ILIKE $${values.length}`);
    values.push(term);
    clauses.push(`to_tsvector('english', p.title || ' ' || p.description || ' ' || p.category || ' ' || p.brand) @@ plainto_tsquery('english', $${values.length})`);
    const words = term.toLowerCase().split(/\s+/).filter((w) => w.length > 2).slice(0, 3);
    const expansions = new Set<string>();
    // No artificial cap: the first few expansions are the word's own stems
    // ("phones" → "phon", "phone", "phoness"), which can fill a small budget
    // before any synonym lands — and then "galaxy" never matches. Keywords are
    // bounded to 3 words and each expands to a dozen terms at most, so a few
    // extra ILIKE clauses are cheap and recall wins.
    for (const w of words) {
      for (const v of expandTerm(w)) expansions.add(v);
    }
    for (const v of expansions) {
      values.push(`%${v}%`);
      clauses.push(`p.title ILIKE $${values.length} OR p.brand ILIKE $${values.length}`);
    }
    where.push(`(${clauses.join(' OR ')})`);
  }
  if (category && category !== 'All') {
    values.push(category);
    where.push(`p.category = $${values.length}`);
  }
  if (brand) {
    values.push(brand);
    where.push(`p.brand = $${values.length}`);
  }
  if (typeof minPrice === 'number') {
    values.push(Math.round(minPrice));
    where.push(`p.price_minor >= $${values.length}`);
  }
  if (typeof maxPrice === 'number') {
    values.push(Math.round(maxPrice));
    where.push(`p.price_minor <= $${values.length}`);
  }
  if (typeof minRating === 'number' && minRating > 0) {
    values.push(minRating);
    where.push(`p.rating >= $${values.length}`);
  }
  if (verifiedOnly) where.push(`COALESCE(s.verified, false) = true`);
  if (inStock) where.push(`p.stock_quantity > 0`);
  if (flashOnly) where.push(`p.is_flash_deal = true`);
  if (sellerId) {
    values.push(sellerId);
    where.push(`p.seller_id = $${values.length}`);
  }

  const whereSql = `WHERE ${where.join(' AND ')}`;
  const countValues = [...values];
  const countSql = `SELECT COUNT(*)::int AS total FROM products p LEFT JOIN store_settings s ON s.user_id = p.seller_id ${whereSql}`;

  let orderSql: string;
  switch (sort) {
    case 'newest': orderSql = 'p.created_at DESC'; break;
    case 'price_asc': orderSql = 'p.price_minor ASC'; break;
    case 'price_desc': orderSql = 'p.price_minor DESC'; break;
    case 'rating': orderSql = 'p.rating DESC, p.rating_count DESC'; break;
    case 'popular': orderSql = 'p.view_count DESC, p.rating_count DESC'; break;
    default:
      if (term) {
        values.push(`${term}%`);
        orderSql = `(CASE WHEN p.title ILIKE $${values.length} THEN 0 ELSE 1 END), p.rating DESC, p.created_at DESC`;
      } else {
        orderSql = `(p.rating * LN(GREATEST(p.rating_count, 1) + 1)) DESC, p.created_at DESC`;
      }
  }

  const safePageSize = Math.min(Math.max(pageSize, 1), 100);
  const offset = (Math.max(page, 1) - 1) * safePageSize;

  const [{ rows: countRows }, { rows }] = await Promise.all([
    db.query(countSql, countValues),
    db.query(`${PRODUCT_SELECT} ${whereSql} ORDER BY ${orderSql} LIMIT ${safePageSize} OFFSET ${offset}`, values),
  ]);

  const result = {
    products: rowsToProducts(rows),
    total: countRows[0]?.total ?? 0,
    page: Math.max(page, 1),
    pageSize: safePageSize,
  };
  setSearchCache(key, result);
  return result;
}

export async function catalogFacets(db: pg.Pool) {
  const cacheKey = 'facets';
  const cached = facetsCache.get(cacheKey);
  if (cached && Date.now() - cached.at < CACHE_TTL_FACETS) return cached.value;

  const [cats, brands, price] = await Promise.all([
    db.query(`SELECT category AS name, COUNT(*)::int AS count FROM products WHERE status = 'approved' GROUP BY category ORDER BY count DESC, name ASC`),
    db.query(`SELECT brand AS name, COUNT(*)::int AS count FROM products WHERE status = 'approved' AND brand <> '' GROUP BY brand ORDER BY count DESC, name ASC LIMIT 40`),
    db.query(`SELECT COALESCE(MIN(price_minor), 0)::int AS "minPrice", COALESCE(MAX(price_minor), 0)::int AS "maxPrice" FROM products WHERE status = 'approved'`),
  ]);
  const result = {
    categories: cats.rows,
    brands: brands.rows,
    priceRange: price.rows[0] ?? { minPrice: 0, maxPrice: 0 },
  };
  facetsCache.set(cacheKey, { at: Date.now(), value: result });
  return result;
}

export async function suggest(db: pg.Pool, term: string, limit = 8) {
  const t = term.trim();
  if (!t) return [];
  const { rows } = await db.query(
    `SELECT DISTINCT ON (label) label, kind FROM (
       SELECT title AS label, 'product' AS kind, rating AS score FROM products WHERE status = 'approved' AND title ILIKE $1
       UNION ALL SELECT DISTINCT brand, 'brand', 5 FROM products WHERE status = 'approved' AND brand ILIKE $1 AND brand <> ''
       UNION ALL SELECT DISTINCT category, 'category', 5 FROM products WHERE status = 'approved' AND category ILIKE $1
     ) s ORDER BY label, score DESC LIMIT $2`,
    [`%${t}%`, limit]
  );
  return rows;
}

export async function listProducts(db: pg.Pool) {
  const { rows } = await db.query(`${PRODUCT_SELECT} WHERE p.status = 'approved' ORDER BY p.created_at DESC LIMIT 100`);
  return rowsToProducts(rows);
}

export async function getProductById(db: pg.Pool, id: string, viewer?: { id: string; role: string }) {
  const { rows } = await db.query(`${PRODUCT_SELECT} WHERE p.id = $1`, [id]);
  const product = rowsToProducts(rows)[0];
  if (!product) throw new NotFoundError('Product not found');
  if (product.status !== 'approved') {
    const owner = viewer && viewer.id === product.seller.id;
    const admin = viewer?.role === 'admin';
    if (!owner && !admin) throw new NotFoundError('Product not found');
  }
  return product;
}

export async function recordView(db: pg.Pool, id: string) {
  viewBuffer.set(id, (viewBuffer.get(id) ?? 0) + 1);
  scheduleViewFlush(db);
}

export async function relatedProducts(db: pg.Pool, id: string, limit = 8) {
  const { rows } = await db.query(
    `${PRODUCT_SELECT}
     WHERE p.status = 'approved' AND p.id <> $1
       AND (p.category = (SELECT category FROM products WHERE id = $1) OR p.seller_id = (SELECT seller_id FROM products WHERE id = $1))
     ORDER BY (p.category = (SELECT category FROM products WHERE id = $1)) DESC, p.rating DESC LIMIT $2`,
    [id, limit]
  );
  return rowsToProducts(rows);
}

export async function listSellerProducts(db: pg.Pool, sellerId: string, status?: string) {
  const values: any[] = [sellerId];
  let filter = '';
  if (status && status !== 'all') {
    values.push(status);
    filter = ` AND p.status = $${values.length}`;
  }
  const { rows } = await db.query(`${PRODUCT_SELECT} WHERE p.seller_id = $1${filter} ORDER BY p.created_at DESC`, values);
  return rowsToProducts(rows);
}

export async function createProduct(db: pg.Pool, sellerId: string, input: any, opts: { asDraft?: boolean } = {}) {
  const status = opts.asDraft ? 'draft' : 'pending';
  const { rows } = await db.query(
    `INSERT INTO products (seller_id, title, description, category, brand, price_minor, old_price_minor, stock_quantity, image_url, location, is_flash_deal, discount_percent, status, submitted_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14) RETURNING id`,
    [sellerId, input.title, input.description ?? '', input.category ?? 'Other', input.brand ?? '', Math.round(input.priceMinor ?? 0), input.oldPriceMinor ? Math.round(input.oldPriceMinor) : null, input.stockQuantity ?? 1, input.imageUrl ?? '', input.location ?? '', !!input.isFlashDeal, input.discountPercent ?? 0, status, status === 'pending' ? new Date() : null]
  );
  const id = rows[0].id;
  if (Array.isArray(input.mediaUrls) && input.mediaUrls.length) {
    for (let i = 0; i < input.mediaUrls.length; i++) {
      await db.query('INSERT INTO product_media (product_id, url, sort_order) VALUES ($1,$2,$3)', [id, input.mediaUrls[i], i]).catch(() => undefined);
    }
  }
  if (status === 'pending') {
    await db.query(`INSERT INTO product_reviews (product_id, action, reason) VALUES ($1, 'submitted', '')`, [id]);
  }
  return getProductById(db, id, { id: sellerId, role: 'seller' });
}

export async function updateProduct(db: pg.Pool, sellerId: string, id: string, input: any) {
  const existing = await db.query('SELECT * FROM products WHERE id = $1 AND seller_id = $2', [id, sellerId]);
  if (!existing.rows[0]) throw new NotFoundError('Product not found');
  const prev = existing.rows[0];
  // A gallery change is a content change: an approved listing with new photos
  // must go back to review so an admin sees what buyers will.
  const prevMediaRes = await db.query(
    'SELECT url FROM product_media WHERE product_id = $1 ORDER BY sort_order ASC',
    [id]
  );
  const prevMedia = prevMediaRes.rows.map((r: any) => r.url);
  const mediaChanged =
    Array.isArray(input.mediaUrls) &&
    JSON.stringify(input.mediaUrls.map((u: any) => String(u || '').trim())) !== JSON.stringify(prevMedia);
  const contentChanged = (input.title !== undefined && input.title !== prev.title) || (input.description !== undefined && input.description !== prev.description) || (input.category !== undefined && input.category !== prev.category) || (input.imageUrl !== undefined && input.imageUrl !== prev.image_url) || mediaChanged;
  const nextStatus = prev.status === 'approved' && contentChanged ? 'pending' : prev.status === 'rejected' ? 'pending' : prev.status;
  const { rows } = await db.query(
    `UPDATE products SET title = COALESCE($3, title), description = COALESCE($4, description), category = COALESCE($5, category), brand = COALESCE($6, brand), price_minor = COALESCE($7, price_minor), old_price_minor = COALESCE($8, old_price_minor), stock_quantity = COALESCE($9, stock_quantity), image_url = COALESCE($10, image_url), location = COALESCE($11, location), is_flash_deal = COALESCE($12, is_flash_deal), discount_percent = COALESCE($13, discount_percent), status = $14, submitted_at = CASE WHEN $14 = 'pending' THEN now() ELSE submitted_at END, rejection_reason = CASE WHEN $14 = 'pending' THEN '' ELSE rejection_reason END, updated_at = now() WHERE id = $1 AND seller_id = $2 RETURNING id`,
    [id, sellerId, input.title ?? null, input.description ?? null, input.category ?? null, input.brand ?? null, input.priceMinor !== undefined ? Math.round(input.priceMinor) : null, input.oldPriceMinor !== undefined ? Math.round(input.oldPriceMinor) : null, input.stockQuantity ?? null, input.imageUrl ?? null, input.location ?? null, input.isFlashDeal ?? null, input.discountPercent ?? null, nextStatus]
  );
  if (!rows[0]) throw new NotFoundError('Product not found');
  if (Array.isArray(input.mediaUrls)) {
    await db.query('DELETE FROM product_media WHERE product_id = $1', [id]);
    for (let i = 0; i < input.mediaUrls.length; i++) {
      const url = String(input.mediaUrls[i] || '').trim();
      if (!url) continue;
      await db.query('INSERT INTO product_media (product_id, url, sort_order) VALUES ($1,$2,$3)', [id, url, i]).catch(() => undefined);
    }
    const cover = String(input.mediaUrls[0] || '').trim();
    if (cover) {
      await db.query('UPDATE products SET image_url = $2 WHERE id = $1', [id, cover]).catch(() => undefined);
    } else if (input.mediaUrls.length === 0) {
      await db.query('UPDATE products SET image_url = $2 WHERE id = $1', [id, '']).catch(() => undefined);
    }
  }
  if (nextStatus === 'pending' && prev.status !== 'pending') {
    await db.query(`INSERT INTO product_reviews (product_id, action, reason) VALUES ($1, 'submitted', 'edited by seller')`, [id]);
  }
  // Invalidate search cache on update
  searchCache.clear();
  facetsCache.clear();
  return getProductById(db, id, { id: sellerId, role: 'seller' });
}

export async function submitForReview(db: pg.Pool, sellerId: string, id: string) {
  const check = await db.query(`SELECT p.title, p.price_minor, COALESCE(NULLIF(p.image_url, ''), (SELECT url FROM product_media m WHERE m.product_id = p.id ORDER BY sort_order LIMIT 1), '') AS image FROM products p WHERE p.id = $1 AND p.seller_id = $2`, [id, sellerId]);
  if (!check.rows[0]) throw new NotFoundError('Product not found');
  const row = check.rows[0];
  if (!/^https?:\/\//i.test(row.image || '')) throw new ValidationError('Add a product photo before submitting for review');
  if (!row.price_minor || Number(row.price_minor) <= 0) throw new ValidationError('Set a price greater than zero before submitting');
  const { rows } = await db.query(`UPDATE products SET status = 'pending', submitted_at = now(), rejection_reason = '' WHERE id = $1 AND seller_id = $2 AND status IN ('draft', 'rejected') RETURNING id`, [id, sellerId]);
  if (!rows[0]) throw new ForbiddenError('Only draft or rejected products can be submitted');
  await db.query(`INSERT INTO product_reviews (product_id, action, reason) VALUES ($1, 'submitted', '')`, [id]);
  searchCache.clear();
  facetsCache.clear();
  return getProductById(db, id, { id: sellerId, role: 'seller' });
}

export async function deleteProduct(db: pg.Pool, sellerId: string, id: string) {
  const res = await db.query('DELETE FROM products WHERE id = $1 AND seller_id = $2 RETURNING id', [id, sellerId]);
  if ((res.rowCount ?? 0) === 0) throw new NotFoundError('Product not found');
  searchCache.clear();
  facetsCache.clear();
  return true;
}
