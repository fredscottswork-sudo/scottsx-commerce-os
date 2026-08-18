/**
 * ScottsTechX — product queries.
 */
import type pg from 'pg';
import { NotFoundError } from '../../errors.js';

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
    p.rating::float AS rating,
    p.rating_count AS "ratingCount",
    p.is_flash_deal AS "isFlashDeal",
    p.discount_percent AS "discountPercent",
    p.location,
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
`;

function rowsToProducts(rows: any[]) {
  return rows.map((r) => ({ ...r, currency: 'UGX', seller: typeof r.seller === 'string' ? JSON.parse(r.seller) : r.seller }));
}

export async function listProducts(db: pg.Pool) {
  const { rows } = await db.query(`${PRODUCT_SELECT} ORDER BY p.created_at DESC`);
  return rowsToProducts(rows);
}

export async function getProductById(db: pg.Pool, id: string) {
  const { rows } = await db.query(`${PRODUCT_SELECT} WHERE p.id = $1`, [id]);
  const product = rowsToProducts(rows)[0];
  if (!product) throw new NotFoundError('Product not found');
  return product;
}

export async function listSellerProducts(db: pg.Pool, sellerId: string) {
  const { rows } = await db.query(`${PRODUCT_SELECT} WHERE p.seller_id = $1 ORDER BY p.created_at DESC`, [sellerId]);
  return rowsToProducts(rows);
}

export async function createProduct(db: pg.Pool, sellerId: string, input: any) {
  const { rows } = await db.query(
    `INSERT INTO products (
       seller_id, title, description, category, brand,
       price_minor, old_price_minor, stock_quantity, image_url, location,
       is_flash_deal, discount_percent
     ) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12)
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
    ]
  );
  const id = rows[0].id;
  if (Array.isArray(input.mediaUrls)) {
    input.mediaUrls.forEach((url: string, i: number) => {
      db.query(
        `INSERT INTO product_media (product_id, url, sort_order) VALUES ($1,$2,$3)`,
        [id, url, i]
      ).catch(() => undefined);
    });
  }
  return getProductById(db, id);
}

export async function deleteProduct(db: pg.Pool, sellerId: string, id: string) {
  const res = await db.query(
    'DELETE FROM products WHERE id = $1 AND seller_id = $2 RETURNING id',
    [id, sellerId]
  );
  if ((res.rowCount ?? 0) === 0) throw new NotFoundError('Product not found');
  return true;
}
