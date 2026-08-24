/**
 * ScottsTechX — product queries.
 *
 * Visibility rule (enforced here, never in the UI): buyers only ever see
 * products with status='approved'. Sellers see their own drafts/pending/
 * rejected rows, and admins see everything through the admin routes.
 */
import type pg from 'pg';
import { NotFoundError, ForbiddenError, ValidationError } from '../../errors.js';
import { expandTerm } from '../ai/catalog-context.js';

const PRODUCT_SELECT = `
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
    pmm.mediaUrls,
    p.rating::float AS rating,
    p.rating_count AS "ratingCount",
    p.is_flash_deal AS "isFlashDeal",
    p.discount_percent AS "discountPercent",
    p.location,
    p.status,
    p.rejection_reason AS "rejectionReason",
    p.view_count AS "viewCount",
    p.created_at AS "createdAt",
    json_build_object(
      'id', u.id,
      'name', COALESCE(s.store_name, u.display_name),
      'rating', COALESCE(s.rating, 0)::float,
      'location', COALESCE(s.city, p.location),
      'verified', COALESCE(s.verified, false)
    ) AS seller
  FROM products p
  JOIN users u ON u.id = p.seller_id
  LEFT JOIN store_settings s ON s.user_id = p.seller_id
  LEFT JOIN LATERAL (
    SELECT url FROM product_media WHERE product_id = p.id ORDER BY sort_order ASC LIMIT 1
  ) pm ON true
  LEFT JOIN LATERAL (
    SELECT COALESCE(json_agg(url ORDER BY sort_order), '[]'::json) AS "mediaUrls"
    FROM product_media WHERE product_id = p.id
  ) pmm ON true
`;

function rowsToProducts(rows: any[]) {
  return rows.map((r) => ({
    ...r,
    currency: 'UGX',
    seller: typeof r.seller === 'string' ? JSON.parse(r.seller) : r.seller,
  }));
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

/**
 * The one catalog query the whole product surface runs on (buyer home,
 * search bar, category browse, seller storefront). Always approved-only.
 */
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
    pageSize = 40,
  } = params;

  const where: string[] = [`p.status = 'approved'`];
  const values: any[] = [];
  const term = q.trim();

  if (term) {
    // Full phrase match, full-text match, and per-word expansion (so "phones"
    // finds "iPhone", "shoes" finds "sneaker", etc).
    const clauses: string[] = [];

    values.push(`%${term}%`);
    const like = `$${values.length}`;
    clauses.push(
      `p.title ILIKE ${like} OR p.brand ILIKE ${like} OR p.category ILIKE ${like} OR p.description ILIKE ${like}`
    );

    values.push(term);
    clauses.push(
      `to_tsvector('english', p.title || ' ' || p.description || ' ' || p.category || ' ' || p.brand)
         @@ plainto_tsquery('english', $${values.length})`
    );

    const words = term
      .toLowerCase()
      .split(/\s+/)
      .filter((w) => w.length > 2);
    const expansions = new Set<string>();
    for (const w of words) for (const v of expandTerm(w)) expansions.add(v);
    for (const v of expansions) {
      values.push(`%${v}%`);
      const i = values.length;
      clauses.push(`p.title ILIKE $${i} OR p.brand ILIKE $${i} OR p.category ILIKE $${i}`);
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

  // COUNT runs on the WHERE params only — the relevance sort may append one
  // extra param below, and Postgres rejects a bind with unreferenced params.
  const countValues = [...values];
  const countSql = `
    SELECT COUNT(*)::int AS total
    FROM products p
    LEFT JOIN store_settings s ON s.user_id = p.seller_id
    ${whereSql}`;

  // Relevance ranks exact-ish title hits above description matches; with no
  // search term it degrades to "best products first" (rating × popularity).
  let orderSql: string;
  switch (sort) {
    case 'newest':
      orderSql = 'p.created_at DESC';
      break;
    case 'price_asc':
      orderSql = 'p.price_minor ASC';
      break;
    case 'price_desc':
      orderSql = 'p.price_minor DESC';
      break;
    case 'rating':
      orderSql = 'p.rating DESC, p.rating_count DESC';
      break;
    case 'popular':
      orderSql = 'p.view_count DESC, p.rating_count DESC';
      break;
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
    db.query(
      `${PRODUCT_SELECT} ${whereSql} ORDER BY ${orderSql} LIMIT ${safePageSize} OFFSET ${offset}`,
      values
    ),
  ]);

  return {
    products: rowsToProducts(rows),
    total: countRows[0]?.total ?? 0,
    page: Math.max(page, 1),
    pageSize: safePageSize,
  };
}

/** Distinct facets for the search UI (categories, brands, price bounds). */
export async function catalogFacets(db: pg.Pool) {
  const [cats, brands, price] = await Promise.all([
    db.query(
      `SELECT category AS name, COUNT(*)::int AS count
       FROM products WHERE status = 'approved'
       GROUP BY category ORDER BY count DESC, name ASC`
    ),
    db.query(
      `SELECT brand AS name, COUNT(*)::int AS count
       FROM products WHERE status = 'approved' AND brand <> ''
       GROUP BY brand ORDER BY count DESC, name ASC LIMIT 40`
    ),
    db.query(
      `SELECT COALESCE(MIN(price_minor), 0)::int AS "minPrice",
              COALESCE(MAX(price_minor), 0)::int AS "maxPrice"
       FROM products WHERE status = 'approved'`
    ),
  ]);
  return {
    categories: cats.rows,
    brands: brands.rows,
    priceRange: price.rows[0] ?? { minPrice: 0, maxPrice: 0 },
  };
}

/** Typeahead suggestions — titles, brands and categories in one shot. */
export async function suggest(db: pg.Pool, term: string, limit = 8) {
  const t = term.trim();
  if (!t) return [];
  const { rows } = await db.query(
    `SELECT DISTINCT ON (label) label, kind FROM (
       SELECT title AS label, 'product' AS kind, rating AS score
         FROM products WHERE status = 'approved' AND title ILIKE $1
       UNION ALL
       SELECT DISTINCT brand, 'brand', 5 FROM products
         WHERE status = 'approved' AND brand ILIKE $1 AND brand <> ''
       UNION ALL
       SELECT DISTINCT category, 'category', 5 FROM products
         WHERE status = 'approved' AND category ILIKE $1
     ) s ORDER BY label, score DESC LIMIT $2`,
    [`%${t}%`, limit]
  );
  return rows;
}

export async function listProducts(db: pg.Pool) {
  const { rows } = await db.query(
    `${PRODUCT_SELECT} WHERE p.status = 'approved' ORDER BY p.created_at DESC`
  );
  return rowsToProducts(rows);
}

/**
 * Public product fetch. Non-approved rows are only visible to their owner
 * or to an admin — everyone else gets a 404 (never leak pending listings).
 */
export async function getProductById(
  db: pg.Pool,
  id: string,
  viewer?: { id: string; role: string }
) {
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

/** Fire-and-forget popularity counter. */
export async function recordView(db: pg.Pool, id: string) {
  await db
    .query('UPDATE products SET view_count = view_count + 1 WHERE id = $1', [id])
    .catch(() => undefined);
}

/** Related products: same category first, then same seller, approved only. */
export async function relatedProducts(db: pg.Pool, id: string, limit = 8) {
  const { rows } = await db.query(
    `${PRODUCT_SELECT}
     WHERE p.status = 'approved'
       AND p.id <> $1
       AND (
         p.category = (SELECT category FROM products WHERE id = $1)
         OR p.seller_id = (SELECT seller_id FROM products WHERE id = $1)
       )
     ORDER BY (p.category = (SELECT category FROM products WHERE id = $1)) DESC,
              p.rating DESC
     LIMIT $2`,
    [id, limit]
  );
  return rowsToProducts(rows);
}

/** Seller's own list — every status, newest first. */
export async function listSellerProducts(db: pg.Pool, sellerId: string, status?: string) {
  const values: any[] = [sellerId];
  let filter = '';
  if (status && status !== 'all') {
    values.push(status);
    filter = ` AND p.status = $${values.length}`;
  }
  const { rows } = await db.query(
    `${PRODUCT_SELECT} WHERE p.seller_id = $1${filter} ORDER BY p.created_at DESC`,
    values
  );
  return rowsToProducts(rows);
}

/**
 * Create a listing. Sellers can never self-publish: anything submitted lands
 * in 'pending' (or 'draft') and waits for an admin decision.
 */
export async function createProduct(
  db: pg.Pool,
  sellerId: string,
  input: any,
  opts: { asDraft?: boolean } = {}
) {
  const status = opts.asDraft ? 'draft' : 'pending';
  const { rows } = await db.query(
    `INSERT INTO products (
       seller_id, title, description, category, brand,
       price_minor, old_price_minor, stock_quantity, image_url, location,
       is_flash_deal, discount_percent, status, submitted_at
     ) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14)
     RETURNING id`,
    [
      sellerId,
      input.title,
      input.description ?? '',
      input.category ?? 'Other',
      input.brand ?? '',
      Math.round(input.priceMinor ?? 0),
      input.oldPriceMinor ? Math.round(input.oldPriceMinor) : null,
      input.stockQuantity ?? 1,
      input.imageUrl ?? '',
      input.location ?? '',
      !!input.isFlashDeal,
      input.discountPercent ?? 0,
      status,
      status === 'pending' ? new Date() : null,
    ]
  );
  const id = rows[0].id;

  if (Array.isArray(input.mediaUrls) && input.mediaUrls.length) {
    // Sequential + awaited so the gallery is complete before we return.
    for (let i = 0; i < input.mediaUrls.length; i++) {
      await db
        .query('INSERT INTO product_media (product_id, url, sort_order) VALUES ($1,$2,$3)', [
          id,
          input.mediaUrls[i],
          i,
        ])
        .catch(() => undefined);
    }
  }

  if (status === 'pending') {
    await db.query(
      `INSERT INTO product_reviews (product_id, action, reason) VALUES ($1, 'submitted', '')`,
      [id]
    );
  }
  return getProductById(db, id, { id: sellerId, role: 'seller' });
}

/** Edit a listing. Any content change sends an approved product back to review. */
export async function updateProduct(db: pg.Pool, sellerId: string, id: string, input: any) {
  const existing = await db.query('SELECT * FROM products WHERE id = $1 AND seller_id = $2', [
    id,
    sellerId,
  ]);
  if (!existing.rows[0]) throw new NotFoundError('Product not found');
  const prev = existing.rows[0];

  // Price/stock-only edits stay live; content edits need a fresh review.
  const contentChanged =
    (input.title !== undefined && input.title !== prev.title) ||
    (input.description !== undefined && input.description !== prev.description) ||
    (input.category !== undefined && input.category !== prev.category) ||
    (input.imageUrl !== undefined && input.imageUrl !== prev.image_url);

  const nextStatus =
    prev.status === 'approved' && contentChanged ? 'pending' : prev.status === 'rejected' ? 'pending' : prev.status;

  const { rows } = await db.query(
    `UPDATE products SET
       title = COALESCE($3, title),
       description = COALESCE($4, description),
       category = COALESCE($5, category),
       brand = COALESCE($6, brand),
       price_minor = COALESCE($7, price_minor),
       old_price_minor = COALESCE($8, old_price_minor),
       stock_quantity = COALESCE($9, stock_quantity),
       image_url = COALESCE($10, image_url),
       location = COALESCE($11, location),
       is_flash_deal = COALESCE($12, is_flash_deal),
       discount_percent = COALESCE($13, discount_percent),
       status = $14,
       submitted_at = CASE WHEN $14 = 'pending' THEN now() ELSE submitted_at END,
       rejection_reason = CASE WHEN $14 = 'pending' THEN '' ELSE rejection_reason END,
       updated_at = now()
     WHERE id = $1 AND seller_id = $2
     RETURNING id`,
    [
      id,
      sellerId,
      input.title ?? null,
      input.description ?? null,
      input.category ?? null,
      input.brand ?? null,
      input.priceMinor !== undefined ? Math.round(input.priceMinor) : null,
      input.oldPriceMinor !== undefined ? Math.round(input.oldPriceMinor) : null,
      input.stockQuantity ?? null,
      input.imageUrl ?? null,
      input.location ?? null,
      input.isFlashDeal ?? null,
      input.discountPercent ?? null,
      nextStatus,
    ]
  );
  if (!rows[0]) throw new NotFoundError('Product not found');

  if (nextStatus === 'pending' && prev.status !== 'pending') {
    await db.query(
      `INSERT INTO product_reviews (product_id, action, reason) VALUES ($1, 'submitted', 'edited by seller')`,
      [id]
    );
  }
  return getProductById(db, id, { id: sellerId, role: 'seller' });
}

/** Submit a draft for admin review. */
export async function submitForReview(db: pg.Pool, sellerId: string, id: string) {
  // Same quality gate as creation: no photo, no review queue.
  const check = await db.query(
    `SELECT p.title, p.price_minor,
            COALESCE(NULLIF(p.image_url, ''), (SELECT url FROM product_media m WHERE m.product_id = p.id ORDER BY sort_order LIMIT 1), '') AS image
     FROM products p WHERE p.id = $1 AND p.seller_id = $2`,
    [id, sellerId]
  );
  if (!check.rows[0]) throw new NotFoundError('Product not found');
  const row = check.rows[0];
  if (!/^https?:\/\//i.test(row.image || '')) {
    throw new ValidationError('Add a product photo before submitting for review');
  }
  if (!row.price_minor || Number(row.price_minor) <= 0) {
    throw new ValidationError('Set a price greater than zero before submitting');
  }

  const { rows } = await db.query(
    `UPDATE products SET status = 'pending', submitted_at = now(), rejection_reason = ''
     WHERE id = $1 AND seller_id = $2 AND status IN ('draft', 'rejected')
     RETURNING id`,
    [id, sellerId]
  );
  if (!rows[0]) throw new ForbiddenError('Only draft or rejected products can be submitted');
  await db.query(
    `INSERT INTO product_reviews (product_id, action, reason) VALUES ($1, 'submitted', '')`,
    [id]
  );
  return getProductById(db, id, { id: sellerId, role: 'seller' });
}

export async function deleteProduct(db: pg.Pool, sellerId: string, id: string) {
  const res = await db.query('DELETE FROM products WHERE id = $1 AND seller_id = $2 RETURNING id', [
    id,
    sellerId,
  ]);
  if ((res.rowCount ?? 0) === 0) throw new NotFoundError('Product not found');
  return true;
}
