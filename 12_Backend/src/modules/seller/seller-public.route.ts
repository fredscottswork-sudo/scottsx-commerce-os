/**
 * ScottsTechX — public seller endpoints + dashboard stats.
 *
 *   GET /api/v1/sellers/nearby?lat=&lng=[&radiusKm=][&limit=]  (global by default)
 *   GET /api/v1/sellers/:id
 *   GET /api/v1/seller/profile            (auth, seller)
 *   GET /api/v1/seller/dashboard/stats    (auth, seller)
 */
import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { getPool } from '../../db.js';
import { requireAuth, requireSeller } from '../../auth.js';
import { NotFoundError } from '../../errors.js';
import { reverseGeocode } from '../../geo/gazetteer.js';
import { resolvePlace, resolvePlaces } from '../../geo/resolve-place.js';
import { rowsToProducts } from '../products/products.service.js';

const nearbySchema = z.object({
  lat: z.coerce.number().min(-90).max(90),
  lng: z.coerce.number().min(-180).max(180),
  /**
   * Optional. The marketplace is global: with no radius the API returns every
   * store on earth sorted by distance, so a buyer anywhere sees their nearest
   * sellers instead of an empty list outside some arbitrary circle.
   */
  radiusKm: z.coerce.number().min(0).max(20000).optional(),
  /** Cap the result set (distance-sorted) rather than capping the distance. */
  limit: z.coerce.number().min(1).max(500).optional().default(60),
  category: z.string().optional(),
  q: z.string().optional(),
  verifiedOnly: z.coerce.boolean().optional().default(false),
  openOnly: z.coerce.boolean().optional().default(false),
  sort: z.enum(['distance', 'rating', 'products', 'newest']).optional().default('distance'),
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

  /**
   * Live "stores near me".
   *
   * Position semantics (the rule the mobile Nearby screen depends on):
   *   - A seller sharing location   → the live `last_lat/last_lng` fix is used
   *     and `live: true` is reported, so the pin follows the seller in real time.
   *   - A seller NOT sharing        → the LAST known coordinates are used
   *     (falling back to the configured store address), so the store stays
   *     exactly where it was last seen instead of disappearing from the map.
   *
   * Because the buyer sends their own lat/lng on every poll, distances and the
   * ordering recompute continuously as the buyer moves.
   */
  app.get('/api/v1/sellers/nearby', async (request) => {
    const { lat, lng, radiusKm, limit, category, q, verifiedOnly, openOnly, sort } =
      nearbySchema.parse(request.query);

    const values: any[] = [];
    const filters: string[] = [`u.role = 'seller'`];

    if (verifiedOnly) filters.push('COALESCE(s.verified, false) = true');
    if (openOnly) filters.push('COALESCE(s.is_open, true) = true');
    if (q) {
      values.push(`%${q}%`);
      filters.push(
        `(COALESCE(s.store_name, u.display_name) ILIKE $${values.length} OR s.store_description ILIKE $${values.length} OR s.city ILIKE $${values.length})`
      );
    }
    if (category && category !== 'All') {
      values.push(category);
      filters.push(
        `EXISTS (SELECT 1 FROM products p WHERE p.seller_id = u.id AND p.status = 'approved' AND p.category = $${values.length})`
      );
    }

    const { rows } = await pool.query(
      `SELECT
         u.id, u.display_name AS name, u.profile_photo_url AS logo_url, u.created_at,
         s.store_name, s.store_description, s.city, s.address, s.verified, s.rating,
         s.lat, s.lng, s.last_lat, s.last_lng, s.location_sharing, s.location_updated_at,
         s.is_open, s.service_radius_km, s.store_logo_url,
         s.delivery_fee_ugx, s.free_above_ugx, s.cod_enabled,
         (SELECT COUNT(*)::int FROM products p WHERE p.seller_id = u.id AND p.status = 'approved') AS product_count,
         (SELECT COUNT(*)::int FROM products p WHERE p.seller_id = u.id AND p.status = 'approved'
            AND p.created_at > now() - interval '7 days') AS new_this_week
       FROM users u
       JOIN store_settings s ON s.user_id = u.id
       WHERE ${filters.join(' AND ')}
         AND COALESCE(s.last_lat, s.lat) IS NOT NULL
         AND COALESCE(s.last_lng, s.lng) IS NOT NULL`,
      values
    );

    const now = Date.now();
    const sellers = rows
      .map((r) => {
        // Live fix when sharing, otherwise the sticky last-known position.
        const sLat = Number(r.last_lat ?? r.lat);
        const sLng = Number(r.last_lng ?? r.lng);
        const distanceKm = haversineKm(lat, lng, sLat, sLng);
        const updatedAt = r.location_updated_at ? new Date(r.location_updated_at) : null;
        const ageMinutes = updatedAt ? Math.round((now - updatedAt.getTime()) / 60000) : null;
        const live = Boolean(r.location_sharing) && ageMinutes !== null && ageMinutes <= 30;

        return {
          id: r.id,
          name: r.store_name || r.name,
          storeName: r.store_name || r.name,
          description: r.store_description ?? '',
          city: r.city ?? '',
          address: r.address ?? '',
          verified: !!r.verified,
          rating: r.rating ? Number(r.rating) : 0,
          logoUrl: r.store_logo_url || r.logo_url || null,
          lat: sLat,
          lng: sLng,
          /** true = following a live GPS fix; false = last known / fixed address. */
          live,
          locationSharing: !!r.location_sharing,
          locationUpdatedAt: r.location_updated_at ?? null,
          locationAgeMinutes: ageMinutes,
          isOpen: r.is_open ?? true,
          serviceRadiusKm: r.service_radius_km ?? 20,
          deliveryFeeUgx: r.delivery_fee_ugx ?? 0,
          freeAboveUgx: r.free_above_ugx ?? 0,
          codEnabled: !!r.cod_enabled,
          productCount: r.product_count ?? 0,
          newThisWeek: r.new_this_week ?? 0,
          distanceKm: Math.round(distanceKm * 100) / 100,
          /** Rough walking/boda ETA, useful on the card. */
          etaMinutes: Math.max(1, Math.round((distanceKm / 25) * 60)),
          withinServiceRadius: distanceKm <= (r.service_radius_km ?? 20),
          /** Human place for the store's pin: "Kireka, Kampala, Central Region". */
          placeLabel: reverseGeocode(sLat, sLng)?.shortLabel ?? (r.city ?? ''),
        };
      })
      // Global by default: only filter when the caller asked for a radius.
      .filter((s) => radiusKm === undefined || s.distanceKm <= radiusKm);

    switch (sort) {
      case 'rating':
        sellers.sort((a, b) => b.rating - a.rating || a.distanceKm - b.distanceKm);
        break;
      case 'products':
        sellers.sort((a, b) => b.productCount - a.productCount || a.distanceKm - b.distanceKm);
        break;
      case 'newest':
        sellers.sort((a, b) => b.newThisWeek - a.newThisWeek || a.distanceKm - b.distanceKm);
        break;
      default:
        sellers.sort((a, b) => a.distanceKm - b.distanceKm);
    }

    const total = sellers.length;
    const page = sellers.slice(0, limit);

    // Exact village names for the buyer and for every pin on this page
    // (OSM + Google + offline, merged; cached ~110 m; bounded to 2.5s so a
    // slow provider can only degrade a label back to the offline name).
    const [buyerPlace, pinPlaces] = await Promise.all([
      resolvePlace(lat, lng),
      resolvePlaces(page.map((s) => ({ lat: s.lat, lng: s.lng }))),
    ]);
    page.forEach((s, i) => {
      const pl = pinPlaces[i];
      if (pl?.shortLabel) s.placeLabel = pl.shortLabel;
      (s as any).village = pl?.village ?? null;
    });

    return {
      sellers: page,
      count: page.length,
      total,
      liveCount: page.filter((s) => s.live).length,
      /** Where the buyer is, named — "Nsimbiziwoome, Bukoto, Kampala, Central Region, Uganda". */
      place: buyerPlace ?? reverseGeocode(lat, lng),
      center: { lat, lng, radiusKm: radiusKm ?? null },
      generatedAt: new Date().toISOString(),
    };
  });

  app.get('/api/v1/sellers/:id', async (request) => {
    const { id } = request.params as { id: string };
    const { rows } = await pool.query(
      `SELECT
         u.id, u.display_name AS name,
         COALESCE(NULLIF(s.store_logo_url, ''), u.profile_photo_url) AS logo_url,
         u.city AS user_city, u.created_at,
         s.store_name, s.store_description, s.city, s.address, s.verified, s.rating,
         COALESCE(s.last_lat, s.lat) AS lat, COALESCE(s.last_lng, s.lng) AS lng,
         s.service_radius_km, s.contact_email, s.contact_phone,
         s.delivery_fee_ugx, s.free_above_ugx, s.cod_enabled
       FROM users u
       LEFT JOIN store_settings s ON s.user_id = u.id
       WHERE u.id = $1 AND u.role = 'seller'`,
      [id]
    );
    if (!rows[0]) throw new NotFoundError('Seller not found');
    const seller = rows[0];
    // Storefronts are public, so they must expose the same complete, approved
    // product shape as the catalog. Returning pending/draft rows here leaked
    // unpublished listings and left ProductCard with missing seller/price
    // fields, which later crashed favorite and cart actions.
    const products = await pool.query(
      `SELECT
         p.id, p.title, p.description, p.category, p.brand,
         p.price_minor::int AS "priceMinor",
         p.old_price_minor::int AS "oldPriceMinor",
         p.stock_quantity AS "stockQuantity",
         COALESCE((SELECT url FROM product_media pm WHERE pm.product_id = p.id ORDER BY sort_order LIMIT 1), p.image_url) AS "imageUrl",
         COALESCE((SELECT json_agg(url ORDER BY sort_order) FROM product_media pm WHERE pm.product_id = p.id), '[]'::json) AS "mediaUrls",
         p.rating::float AS rating, p.rating_count AS "ratingCount",
         p.is_flash_deal AS "isFlashDeal", p.discount_percent AS "discountPercent",
         p.location, p.status, p.rejection_reason AS "rejectionReason",
         p.view_count AS "viewCount", p.created_at AS "createdAt",
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
       WHERE p.seller_id = $1 AND p.status = 'approved'
       ORDER BY p.created_at DESC`,
      [id]
    );
    const sellerLat = Number(seller.lat);
    const sellerLng = Number(seller.lng);
    const geocoded =
      Number.isFinite(sellerLat) && Number.isFinite(sellerLng)
        ? reverseGeocode(sellerLat, sellerLng)?.shortLabel ?? seller.address ?? ''
        : '';
    return {
      seller: {
        id: seller.id,
        name: seller.store_name || seller.name,
        storeName: seller.store_name || seller.name,
        description: seller.store_description ?? '',
        city: seller.city || seller.user_city || '',
        /** The seller's real place from the store pin — typed text only as fallback. */
        placeLabel: geocoded || seller.city || seller.user_city || '',
        address: seller.address ?? '',
        verified: !!seller.verified,
        rating: seller.rating ? Number(seller.rating) : 0,
        logoUrl: seller.logo_url ?? null,
        lat: seller.lat === null || seller.lat === undefined ? null : Number(seller.lat),
        lng: seller.lng === null || seller.lng === undefined ? null : Number(seller.lng),
        serviceRadiusKm: seller.service_radius_km ?? 20,
        deliveryFeeUgx: seller.delivery_fee_ugx ?? 0,
        freeAboveUgx: seller.free_above_ugx ?? 0,
        codEnabled: !!seller.cod_enabled,
        contactEmail: seller.contact_email ?? '',
        contactPhone: seller.contact_phone ?? '',
      },
      products: rowsToProducts(products.rows),
    };
  });

  app.get('/api/v1/seller/profile', { preHandler: requireAuth }, async (request) => {
    const seller = requireSeller(request);
    const { rows } = await pool.query(
      `SELECT u.id, u.display_name AS name, u.profile_photo_url AS logo_url, u.phone, u.city AS user_city,
              s.store_name, s.store_description, s.city, s.address, s.verified, s.rating,
              s.contact_email, s.contact_phone
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
      },
    };
  });

  /**
   * Seller location heartbeat.
   *
   * The app calls this while the seller has granted location permission. The
   * last fix is stored permanently, so when the seller later revokes
   * permission (or goes offline) the store simply stops being "live" and stays
   * pinned at the last known point — it is never removed from the map.
   */
  app.post('/api/v1/seller/location', { preHandler: requireAuth }, async (request) => {
    const seller = requireSeller(request);
    const body = z
      .object({
        lat: z.coerce.number().min(-90).max(90),
        lng: z.coerce.number().min(-180).max(180),
        sharing: z.boolean().optional().default(true),
        city: z.string().optional(),
      })
      .parse(request.body);

    const { rows } = await pool.query(
      `INSERT INTO store_settings (user_id, last_lat, last_lng, location_sharing, location_updated_at, city, lat, lng)
       VALUES ($1,$2,$3,$4, now(), COALESCE($5,''), $2, $3)
       ON CONFLICT (user_id) DO UPDATE SET
         last_lat = EXCLUDED.last_lat,
         last_lng = EXCLUDED.last_lng,
         location_sharing = EXCLUDED.location_sharing,
         location_updated_at = now(),
         city = CASE WHEN COALESCE($5,'') <> '' THEN $5 ELSE store_settings.city END,
         -- Keep the configured store coordinates in sync only if never set.
         lat = COALESCE(store_settings.lat, EXCLUDED.lat),
         lng = COALESCE(store_settings.lng, EXCLUDED.lng),
         updated_at = now()
       RETURNING last_lat AS "lat", last_lng AS "lng", location_sharing AS "sharing",
                 location_updated_at AS "updatedAt"`,
      [seller.id, body.lat, body.lng, body.sharing, body.city ?? null]
    );
    return { location: rows[0] };
  });

  /** Turn live sharing off (keeps the last known pin). */
  app.delete('/api/v1/seller/location', { preHandler: requireAuth }, async (request) => {
    const seller = requireSeller(request);
    const { rows } = await pool.query(
      `UPDATE store_settings SET location_sharing = false, updated_at = now()
       WHERE user_id = $1
       RETURNING last_lat AS "lat", last_lng AS "lng", location_sharing AS "sharing",
                 location_updated_at AS "updatedAt"`,
      [seller.id]
    );
    if (!rows[0]) throw new NotFoundError('Store settings not found');
    return { location: rows[0], message: 'Live location off — your store stays at its last known position.' };
  });

  app.get('/api/v1/seller/location', { preHandler: requireAuth }, async (request) => {
    const seller = requireSeller(request);
    const { rows } = await pool.query(
      `SELECT COALESCE(last_lat, lat) AS lat, COALESCE(last_lng, lng) AS lng,
              location_sharing AS "sharing", location_updated_at AS "updatedAt", is_open AS "isOpen"
       FROM store_settings WHERE user_id = $1`,
      [seller.id]
    );
    return { location: rows[0] ?? null };
  });

  /** Open / closed toggle shown on the Nearby cards. */
  app.patch('/api/v1/seller/open-state', { preHandler: requireAuth }, async (request) => {
    const seller = requireSeller(request);
    const { isOpen } = z.object({ isOpen: z.boolean() }).parse(request.body);
    const { rows } = await pool.query(
      `INSERT INTO store_settings (user_id, is_open) VALUES ($1,$2)
       ON CONFLICT (user_id) DO UPDATE SET is_open = EXCLUDED.is_open, updated_at = now()
       RETURNING is_open AS "isOpen"`,
      [seller.id, isOpen]
    );
    return rows[0];
  });

  app.get('/api/v1/seller/dashboard/stats', { preHandler: requireAuth }, async (request) => {
    const seller = requireSeller(request);
    const [revenue, products, top, recentOrders, series, unread, followers, statusCounts, views] =
      await Promise.all([
        pool.query(
          `SELECT COALESCE(SUM(price_minor * quantity), 0)::bigint AS revenue,
                  COUNT(*)::int AS orders,
                  COALESCE(SUM(price_minor * quantity) FILTER (WHERE created_at > now() - interval '30 days'), 0)::bigint AS revenue30,
                  COUNT(*) FILTER (WHERE created_at > now() - interval '30 days')::int AS orders30,
                  COALESCE(AVG(price_minor * quantity), 0)::bigint AS "avgOrderValue"
           FROM orders WHERE seller_id = $1 AND status IN ('paid','shipped','delivered')`,
          [seller.id]
        ),
        pool.query(
          `SELECT COUNT(*)::int AS total,
                  COALESCE(SUM(CASE WHEN stock_quantity <= 5 THEN 1 ELSE 0 END), 0)::int AS low_stock,
                  COALESCE(SUM(CASE WHEN stock_quantity = 0 THEN 1 ELSE 0 END), 0)::int AS out_of_stock
           FROM products WHERE seller_id = $1`,
          [seller.id]
        ),
        pool.query(
          `SELECT p.title, COALESCE(SUM(oi.quantity)::int, 0) AS sold
           FROM products p LEFT JOIN order_items oi ON oi.product_id = p.id
           WHERE p.seller_id = $1
           GROUP BY p.id, p.title ORDER BY sold DESC LIMIT 5`,
          [seller.id]
        ),
        pool.query(
          `SELECT o.id, o.buyer_id AS "buyerId", o.product_id AS "productId", o.product_title AS "productTitle",
                  o.price_minor::int AS amount, o.quantity, o.status, o.created_at AS "createdAt",
                  o.delivery_address AS "deliveryAddress", o.delivery_phone AS "deliveryPhone",
                  o.delivery_note AS "deliveryNote",
                  COALESCE(u.display_name, 'Buyer') AS "buyerName"
           FROM orders o LEFT JOIN users u ON u.id = o.buyer_id
           WHERE o.seller_id = $1 ORDER BY o.created_at DESC LIMIT 10`,
          [seller.id]
        ),
        pool.query(
          `SELECT to_char(d.day, 'YYYY-MM-DD') AS date,
                  COALESCE(COUNT(o.id), 0)::int AS orders,
                  COALESCE(SUM(o.price_minor * o.quantity), 0)::bigint AS revenue
           FROM generate_series(now()::date - interval '13 days', now()::date, interval '1 day') AS d(day)
           LEFT JOIN orders o ON o.created_at::date = d.day AND o.seller_id = $1
                             AND o.status IN ('paid','shipped','delivered')
           GROUP BY d.day ORDER BY d.day`,
          [seller.id]
        ),
        pool.query(
          `SELECT COUNT(*)::int AS unread
           FROM messages m
           JOIN conversations c ON c.id = m.conversation_id
           LEFT JOIN message_reads mr ON mr.conversation_id = c.id AND mr.user_id = $1
           WHERE c.seller_id = $1 AND m.sender_id <> $1
             AND (mr.last_read_at IS NULL OR m.created_at > mr.last_read_at)`,
          [seller.id]
        ),
        pool.query(`SELECT COUNT(*)::int AS followers FROM favorite_sellers WHERE seller_id = $1`, [
          seller.id,
        ]),
        pool.query(
          `SELECT status, COUNT(*)::int AS count FROM products WHERE seller_id = $1 GROUP BY status`,
          [seller.id]
        ),
        pool.query(
          `SELECT COALESCE(SUM(view_count), 0)::int AS views FROM products WHERE seller_id = $1`,
          [seller.id]
        ),
      ]);

    const byStatus: Record<string, number> = {
      draft: 0, pending: 0, approved: 0, rejected: 0, suspended: 0,
    };
    for (const r of statusCounts.rows) byStatus[r.status] = r.count;

    return {
      stats: {
        revenueUgx: Number(revenue.rows[0].revenue),
        revenue30Ugx: Number(revenue.rows[0].revenue30),
        orders: revenue.rows[0].orders,
        orders30: revenue.rows[0].orders30,
        avgOrderValueUgx: Number(revenue.rows[0].avgOrderValue),
        totalProducts: products.rows[0].total,
        lowStock: products.rows[0].low_stock,
        outOfStock: products.rows[0].out_of_stock,
        topProduct: top.rows[0]?.title ?? null,
        unreadMessages: unread.rows[0].unread,
        followers: followers.rows[0].followers,
        totalViews: views.rows[0].views,
        productsByStatus: byStatus,
        pendingApproval: byStatus.pending,
      },
      topProducts: top.rows,
      recentOrders: recentOrders.rows,
      salesSeries: series.rows.map((r) => ({ ...r, revenue: Number(r.revenue) })),
    };
  });

  // Seller order list (mirror of the buyer's /me/orders, seller side).
  app.get('/api/v1/seller/orders', { preHandler: requireAuth }, async (request) => {
    const seller = requireSeller(request);
    const { rows } = await pool.query(
      `SELECT id, buyer_id AS "buyerId", product_id AS "productId", product_title AS title, price_minor::int AS amount,
              quantity, status, created_at AS "createdAt",
              delivery_address AS "deliveryAddress", delivery_phone AS "deliveryPhone",
              delivery_note AS "deliveryNote",
              (SELECT display_name FROM users WHERE id = o.buyer_id) AS "buyerName"
       FROM orders o
       WHERE seller_id = $1
       ORDER BY created_at DESC`,
      [seller.id]
    );
    return { orders: rows };
  });
}
