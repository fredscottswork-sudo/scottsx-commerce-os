/**
 * ScottsTechX — public seller endpoints + dashboard stats.
 *
 *   GET /api/v1/sellers/nearby?lat=&lng=&radiusKm=
 *   GET /api/v1/sellers/:id
 *   GET /api/v1/seller/profile            (auth, seller)
 *   GET /api/v1/seller/dashboard/stats    (auth, seller)
 */
import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { getPool } from '../../db.js';
import { requireAuth, requireSeller } from '../../auth.js';
import { NotFoundError } from '../../errors.js';

const nearbySchema = z.object({
  lat: z.coerce.number().min(-90).max(90),
  lng: z.coerce.number().min(-180).max(180),
  radiusKm: z.coerce.number().min(0).max(1000).optional().default(50),
});

function haversineKm(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const R = 6371;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLng = ((lng2 - lng1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos((lat1 * Math.PI) / 180) * Math.cos((lat2 * Math.PI) / 180) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(a));
}

export default async function registerSellerPublicRoute(app: FastifyInstance) {
  const pool = getPool();

  app.get('/api/v1/sellers/nearby', async (request) => {
    const { lat, lng, radiusKm } = nearbySchema.parse(request.query);

    const { rows } = await pool.query(
      `SELECT
         u.id, u.display_name AS name, u.profile_photo_url AS logo_url, u.created_at,
         s.store_name, s.store_description, s.city, s.address, s.verified, s.rating,
         s.lat, s.lng, s.service_radius_km,
         (SELECT COUNT(*)::int FROM products p WHERE p.seller_id = u.id) AS product_count
       FROM users u
       JOIN store_settings s ON s.user_id = u.id
       WHERE u.role = 'seller' AND s.lat IS NOT NULL AND s.lng IS NOT NULL`
    );

    const sellers = rows
      .map((r) => {
        const distanceKm = haversineKm(lat, lng, Number(r.lat), Number(r.lng));
        return {
          id: r.id,
          name: r.store_name || r.name,
          storeName: r.store_name || r.name,
          description: r.store_description ?? '',
          city: r.city ?? '',
          address: r.address ?? '',
          verified: !!r.verified,
          rating: r.rating ? Number(r.rating) : 0,
          logoUrl: r.logo_url ?? null,
          lat: Number(r.lat),
          lng: Number(r.lng),
          serviceRadiusKm: r.service_radius_km ?? 20,
          productCount: r.product_count ?? 0,
          distanceKm: Math.round(distanceKm * 100) / 100,
        };
      })
      .filter((s) => s.distanceKm <= radiusKm)
      .sort((a, b) => a.distanceKm - b.distanceKm);

    return { sellers, count: sellers.length, center: { lat, lng, radiusKm } };
  });

  app.get('/api/v1/sellers/:id', async (request) => {
    const { id } = request.params as { id: string };
    const { rows } = await pool.query(
      `SELECT
         u.id, u.display_name AS name, u.profile_photo_url AS logo_url, u.created_at,
         s.store_name, s.store_description, s.city, s.address, s.verified, s.rating,
         s.lat, s.lng, s.service_radius_km, s.contact_email, s.contact_phone,
         s.delivery_fee_ugx, s.free_above_ugx, s.cod_enabled
       FROM users u
       JOIN store_settings s ON s.user_id = u.id
       WHERE u.id = $1 AND u.role = 'seller'`,
      [id]
    );
    if (!rows[0]) throw new NotFoundError('Seller not found');
    const seller = rows[0];
    const products = await pool.query(
      `SELECT id, title, category, price_minor::int AS "priceMinor", stock_quantity AS "stockQuantity",
              COALESCE((SELECT url FROM product_media pm WHERE pm.product_id = p.id ORDER BY sort_order LIMIT 1), p.image_url) AS "imageUrl",
              rating::float AS rating, rating_count AS "ratingCount", is_flash_deal AS "isFlashDeal"
       FROM products p WHERE p.seller_id = $1 ORDER BY p.created_at DESC`,
      [id]
    );
    return {
      seller: {
        id: seller.id,
        name: seller.store_name || seller.name,
        storeName: seller.store_name || seller.name,
        description: seller.store_description ?? '',
        city: seller.city ?? '',
        address: seller.address ?? '',
        verified: !!seller.verified,
        rating: seller.rating ? Number(seller.rating) : 0,
        logoUrl: seller.logo_url ?? null,
        lat: seller.lat ? Number(seller.lat) : null,
        lng: seller.lng ? Number(seller.lng) : null,
        serviceRadiusKm: seller.service_radius_km ?? 20,
        deliveryFeeUgx: seller.delivery_fee_ugx ?? 0,
        freeAboveUgx: seller.free_above_ugx ?? 0,
        codEnabled: !!seller.cod_enabled,
        contactEmail: seller.contact_email ?? '',
        contactPhone: seller.contact_phone ?? '',
      },
      products: products.rows.map((p) => ({ ...p, currency: 'UGX' })),
    };
  });

  app.get('/api/v1/seller/profile', { preHandler: requireAuth }, async (request) => {
    const seller = requireSeller(request);
    const { rows } = await pool.query(
      `SELECT u.id, u.display_name AS name, u.profile_photo_url AS logo_url, u.phone, u.city AS user_city,
              s.store_name, s.store_description, s.city, s.address, s.verified, s.rating,
              s.contact_email, s.contact_phone, s.momo_number, s.bank_name
       FROM users u
       LEFT JOIN store_settings s ON s.user_id = u.id
       WHERE u.id = $1`,
      [seller.id]
    );
    if (!rows[0]) throw new NotFoundError('Seller profile not found');
    const r = rows[0];
    return {
      seller: {
        id: r.id,
        name: r.store_name || r.name,
        storeName: r.store_name || r.name,
        description: r.store_description ?? '',
        city: r.city ?? '',
        address: r.address ?? '',
        verified: !!r.verified,
        rating: r.rating ? Number(r.rating) : 0,
        logoUrl: r.logo_url ?? null,
        phone: r.phone ?? '',
        contactEmail: r.contact_email ?? '',
        contactPhone: r.contact_phone ?? '',
        momoNumber: r.momo_number ?? '',
        bankName: r.bank_name ?? '',
      },
    };
  });

  app.get('/api/v1/seller/dashboard/stats', { preHandler: requireAuth }, async (request) => {
    const seller = requireSeller(request);
    const revenue = await pool.query(
      `SELECT COALESCE(SUM(price_minor * quantity)::int, 0) AS revenue, COUNT(*)::int AS orders
       FROM orders WHERE seller_id = $1 AND status IN ('paid','shipped','delivered')`,
      [seller.id]
    );
    const products = await pool.query(
      `SELECT COUNT(*)::int AS total, COALESCE(SUM(CASE WHEN stock_quantity <= 5 THEN 1 ELSE 0 END), 0)::int AS low_stock
       FROM products WHERE seller_id = $1`,
      [seller.id]
    );
    const top = await pool.query(
      `SELECT p.title, COALESCE(SUM(oi.quantity)::int, 0) AS sold
       FROM products p LEFT JOIN order_items oi ON oi.product_id = p.id
       WHERE p.seller_id = $1
       GROUP BY p.id, p.title ORDER BY sold DESC LIMIT 1`,
      [seller.id]
    );
    const recentOrders = await pool.query(
      `SELECT id, buyer_id, product_title, price_minor::int AS amount, quantity, status, created_at
       FROM orders WHERE seller_id = $1 ORDER BY created_at DESC LIMIT 10`,
      [seller.id]
    );
    return {
      stats: {
        revenueUgx: revenue.rows[0].revenue,
        orders: revenue.rows[0].orders,
        totalProducts: products.rows[0].total,
        lowStock: products.rows[0].low_stock,
        topProduct: top.rows[0]?.title ?? null,
      },
      recentOrders: recentOrders.rows,
    };
  });

  // Seller order list (mirror of the buyer's /me/orders, seller side).
  app.get('/api/v1/seller/orders', { preHandler: requireAuth }, async (request) => {
    const seller = requireSeller(request);
    const { rows } = await pool.query(
      `SELECT id, buyer_id AS "buyerId", product_title AS title, price_minor::int AS amount,
              quantity, status, created_at AS "createdAt",
              (SELECT display_name FROM users WHERE id = o.buyer_id) AS "buyerName"
       FROM orders o
       WHERE seller_id = $1
       ORDER BY created_at DESC`,
      [seller.id]
    );
    return { orders: rows };
  });
}
