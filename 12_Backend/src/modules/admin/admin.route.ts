/**
 * ScottsTechX — platform admin routes (web console).
 *
 *   GET    /api/v1/admin/stats
 *   GET    /api/v1/admin/users?search=&role=&page=&pageSize=
 *   PATCH  /api/v1/admin/users/:id/role
 *   DELETE /api/v1/admin/users/:id               remove an account
 *   GET    /api/v1/admin/products?search=&status=&page=&pageSize=
 *   GET    /api/v1/admin/products/queue           pending review queue
 *   POST   /api/v1/admin/products/:id/approve
 *   POST   /api/v1/admin/products/:id/reject      { reason }
 *   POST   /api/v1/admin/products/:id/suspend     { reason }
 *   POST   /api/v1/admin/products/bulk            { ids, action, reason? }
 *   DELETE /api/v1/admin/products/:id
 *   PATCH  /api/v1/admin/sellers/:id/verify       { verified }
 *   GET    /api/v1/admin/support/tickets          all tickets
 *   POST   /api/v1/admin/support/tickets/:id/reply
 *
 * Every route requires role='admin' — enforced on the backend, never the UI.
 */
import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { getPool } from '../../db.js';
import { resolvePlace } from '../../geo/resolve-place.js';
import { requireAuth, requireAdmin } from '../../auth.js';
import { NotFoundError, ForbiddenError, ConflictError } from '../../errors.js';
import { notify, notifyFavoritesOfNewProduct } from '../notifications/notify.service.js';

let statsCache: { at: number; value: any } | null = null;
const STATS_TTL = 30_000;

export default async function registerAdminRoute(app: FastifyInstance) {
  const pool = getPool();

  // ── Dashboard ─────────────────────────────────────────────────────────────
  app.get('/api/v1/admin/stats', { preHandler: requireAuth }, async (request) => {
    requireAdmin(request);
    if (statsCache && Date.now() - statsCache.at < STATS_TTL) return statsCache.value;
    const [users, products, orders, conversations, recentUsers, queue, topSellers, salesSeries] =
      await Promise.all([
        pool.query(
          `SELECT COUNT(*)::int AS total,
                  COUNT(*) FILTER (WHERE role = 'buyer')::int AS buyers,
                  COUNT(*) FILTER (WHERE role = 'seller')::int AS sellers,
                  COUNT(*) FILTER (WHERE role = 'admin')::int AS admins,
                  COUNT(*) FILTER (WHERE email_verified)::int AS verified,
                  COUNT(*) FILTER (WHERE created_at > now() - interval '7 days')::int AS "newThisWeek"
           FROM users`
        ),
        pool.query(
          `SELECT COUNT(*)::int AS total,
                  COUNT(*) FILTER (WHERE status = 'approved')::int AS approved,
                  COUNT(*) FILTER (WHERE status = 'pending')::int AS pending,
                  COUNT(*) FILTER (WHERE status = 'rejected')::int AS rejected,
                  COUNT(*) FILTER (WHERE status = 'draft')::int AS draft,
                  COUNT(*) FILTER (WHERE status = 'suspended')::int AS suspended,
                  COUNT(*) FILTER (WHERE is_flash_deal)::int AS flash_deals,
                  COUNT(*) FILTER (WHERE stock_quantity <= 5)::int AS low_stock
           FROM products`
        ),
        pool.query(
          `SELECT COUNT(*)::int AS total,
                  COUNT(*) FILTER (WHERE status = 'paid')::int AS paid,
                  COUNT(*) FILTER (WHERE status = 'pending')::int AS pending,
                  COALESCE(SUM(price_minor * quantity) FILTER (WHERE status IN ('paid','shipped','delivered')), 0)::bigint AS revenue_ugx
           FROM orders`
        ),
        pool.query('SELECT COUNT(*)::int AS total FROM conversations'),
        pool.query(
          `SELECT id, email, display_name AS "displayName", role, email_verified AS "emailVerified", created_at AS "createdAt"
           FROM users ORDER BY created_at DESC LIMIT 8`
        ),
        pool.query(
          `SELECT p.id, p.title, p.category, p.price_minor::int AS "priceMinor", p.image_url AS "imageUrl",
                  p.submitted_at AS "submittedAt",
                  COALESCE(s.store_name, u.display_name) AS "sellerName", u.email AS "sellerEmail"
           FROM products p
           JOIN users u ON u.id = p.seller_id
           LEFT JOIN store_settings s ON s.user_id = p.seller_id
           WHERE p.status = 'pending'
           ORDER BY p.submitted_at ASC NULLS LAST LIMIT 6`
        ),
        pool.query(
          `SELECT u.id, COALESCE(s.store_name, u.display_name) AS "storeName",
                  COALESCE(s.verified, false) AS verified, COALESCE(s.rating, 0)::float AS rating,
                  (SELECT COUNT(*)::int FROM products p WHERE p.seller_id = u.id AND p.status = 'approved') AS "productCount",
                  (SELECT COALESCE(SUM(o.price_minor * o.quantity), 0)::bigint FROM orders o
                    WHERE o.seller_id = u.id AND o.status IN ('paid','shipped','delivered')) AS "revenueUgx"
           FROM users u LEFT JOIN store_settings s ON s.user_id = u.id
           WHERE u.role = 'seller'
           ORDER BY "revenueUgx" DESC, "productCount" DESC LIMIT 6`
        ),
        pool.query(
          `SELECT to_char(d.day, 'YYYY-MM-DD') AS date,
                  COALESCE(COUNT(o.id), 0)::int AS orders,
                  COALESCE(SUM(o.price_minor * o.quantity), 0)::bigint AS revenue
           FROM generate_series(now()::date - interval '13 days', now()::date, interval '1 day') AS d(day)
           LEFT JOIN orders o ON o.created_at::date = d.day AND o.status IN ('paid','shipped','delivered')
           GROUP BY d.day ORDER BY d.day`
        ),
      ]);

    const result = {
      stats: {
        users: users.rows[0],
        products: products.rows[0],
        orders: { ...orders.rows[0], revenue_ugx: Number(orders.rows[0].revenue_ugx) },
        conversations: conversations.rows[0].total,
      },
      recentUsers: recentUsers.rows,
      reviewQueue: queue.rows,
      topSellers: topSellers.rows.map((r) => ({ ...r, revenueUgx: Number(r.revenueUgx) })),
      salesSeries: salesSeries.rows.map((r) => ({ ...r, revenue: Number(r.revenue) })),
    };
    statsCache = { at: Date.now(), value: result };
    return result;
  });

  // ── Users ─────────────────────────────────────────────────────────────────
  // ── Locations map: every user who ever shared a position ────────────────
  app.get('/api/v1/admin/locations', { preHandler: requireAuth }, async (request) => {
    requireAdmin(request);
    const q = (request.query ?? {}) as Record<string, string>;
    const role = q.role === 'buyer' || q.role === 'seller' || q.role === 'admin' ? q.role : null;
    const since = q.since === '24h' ? "now() - interval '24 hours'" : q.since === '7d' ? "now() - interval '7 days'" : q.since === '30d' ? "now() - interval '30 days'" : null;
    const { rows } = await pool.query(
      `SELECT u.id, u.display_name AS name, u.email, u.role, u.profile_photo_url AS "photoUrl",
              u.created_at AS "joinedAt", u.email_verified AS verified,
              u.lat, u.lng, u.village, u.city, u.region, u.country, u.place_label AS "placeLabel",
              u.location_updated_at AS "locationAt",
              s.store_name AS "storeName", s.verified AS "storeVerified",
              s.location_sharing AS "liveTracking",
              (SELECT COUNT(*)::int FROM location_pings lp WHERE lp.user_id = u.id) AS "pingCount",
              (SELECT lp.accuracy_m FROM location_pings lp WHERE lp.user_id = u.id ORDER BY lp.created_at DESC LIMIT 1) AS "accuracyM"
       FROM users u
       LEFT JOIN store_settings s ON s.user_id = u.id
       WHERE u.lat IS NOT NULL AND u.lng IS NOT NULL
         ${role ? `AND u.role = '${role}'` : ''}
         ${since ? `AND u.location_updated_at > ${since}` : ''}
       ORDER BY u.location_updated_at DESC NULLS LAST
       LIMIT 2000`
    );
    const summary = await pool.query(
      `SELECT COUNT(*) FILTER (WHERE lat IS NOT NULL)::int AS located,
              COUNT(*)::int AS total,
              COUNT(*) FILTER (WHERE lat IS NOT NULL AND role = 'buyer')::int AS buyers,
              COUNT(*) FILTER (WHERE lat IS NOT NULL AND role = 'seller')::int AS sellers,
              COUNT(*) FILTER (WHERE lat IS NOT NULL AND location_updated_at > now() - interval '24 hours')::int AS "activeToday"
       FROM users`
    );
    // Older rows have coordinates but no place name (saved before the
    // geocoder existed). Name them from lat/lng now — the offline gazetteer
    // answers instantly (city/region/country); the online lookup upgrades
    // to the village in the background and the next load has it.
    const users = await Promise.all(rows.map(async (r) => {
      const out = { ...r, lat: Number(r.lat), lng: Number(r.lng) };
      if (!out.placeLabel || !out.region) {
        const p = await resolvePlace(out.lat, out.lng, 'low').catch(() => null);
        if (p) {
          out.village = p.village; out.city = p.city; out.region = p.region; out.country = p.country; out.placeLabel = p.label;
          pool.query(
            `UPDATE users SET village = COALESCE(village, $2), city = CASE WHEN city = '' OR city IS NULL THEN $3 ELSE city END,
                    region = COALESCE(region, $4), country = COALESCE(country, $5), place_label = COALESCE(place_label, $6) WHERE id = $1`,
            [out.id, p.village, p.city ?? '', p.region, p.country, p.label]
          ).catch(() => undefined);
        }
      }
      return out;
    }));
    return { users, summary: summary.rows[0] };
  });

  app.get('/api/v1/admin/locations/:userId/history', { preHandler: requireAuth }, async (request) => {
    requireAdmin(request);
    const { userId } = request.params as { userId: string };
    const { rows } = await pool.query(
      `SELECT lat, lng, accuracy_m AS "accuracyM", village, city, region, source, created_at AS "at"
       FROM location_pings WHERE user_id = $1 ORDER BY created_at DESC LIMIT 200`,
      [userId]
    );
    return { history: rows.map((r) => ({ ...r, lat: Number(r.lat), lng: Number(r.lng) })) };
  });

  app.get('/api/v1/admin/users', { preHandler: requireAuth }, async (request) => {
    requireAdmin(request);
    const q = z
      .object({
        search: z.string().optional().default(''),
        role: z.enum(['buyer', 'seller', 'admin']).optional(),
        page: z.coerce.number().int().min(1).optional().default(1),
        pageSize: z.coerce.number().int().min(1).max(200).optional().default(25),
      })
      .parse(request.query);

    const where: string[] = [];
    const values: any[] = [];
    if (q.search) {
      values.push(`%${q.search}%`);
      where.push(
        `(u.email ILIKE $${values.length} OR u.display_name ILIKE $${values.length} OR u.phone ILIKE $${values.length})`
      );
    }
    if (q.role) {
      values.push(q.role);
      where.push(`u.role = $${values.length}`);
    }
    const whereSql = where.length ? `WHERE ${where.join(' AND ')}` : '';
    const offset = (q.page - 1) * q.pageSize;

    const count = await pool.query(`SELECT COUNT(*)::int AS total FROM users u ${whereSql}`, values);
    const { rows } = await pool.query(
      `SELECT u.id, u.email, u.display_name AS "displayName", u.phone, u.role,
              u.email_verified AS "emailVerified", u.city, u.created_at AS "createdAt",
              COALESCE(s.store_name, '') AS "storeName", COALESCE(s.verified, false) AS "storeVerified",
              (SELECT COUNT(*)::int FROM products p WHERE p.seller_id = u.id) AS "productCount",
              (SELECT COUNT(*)::int FROM orders o WHERE o.buyer_id = u.id) AS "orderCount"
       FROM users u
       LEFT JOIN store_settings s ON s.user_id = u.id
       ${whereSql}
       ORDER BY u.created_at DESC
       LIMIT ${q.pageSize} OFFSET ${offset}`,
      values
    );
    return { users: rows, total: count.rows[0].total, page: q.page, pageSize: q.pageSize };
  });

  app.patch('/api/v1/admin/users/:id/role', { preHandler: requireAuth }, async (request) => {
    const admin = requireAdmin(request);
    const { id } = request.params as { id: string };
    const body = z.object({ role: z.enum(['buyer', 'seller', 'admin']) }).parse(request.body);

    if (id === admin.id && body.role !== 'admin') {
      throw new ForbiddenError('You cannot demote your own admin account');
    }
    if (body.role !== 'admin') {
      const admins = await pool.query(`SELECT COUNT(*)::int AS n FROM users WHERE role = 'admin'`);
      if (admins.rows[0].n <= 1) throw new ForbiddenError('Cannot demote the last admin');
    }
    const { rows } = await pool.query(
      `UPDATE users SET role = $2, updated_at = now() WHERE id = $1 RETURNING id, email, role`,
      [id, body.role]
    );
    if (!rows[0]) throw new NotFoundError('User not found');

    // A promoted seller needs a store_settings row to exist.
    if (body.role === 'seller') {
      await pool.query(
        `INSERT INTO store_settings (user_id, store_name)
         VALUES ($1, (SELECT display_name FROM users WHERE id = $1))
         ON CONFLICT (user_id) DO NOTHING`,
        [id]
      );
    }
    await notify(pool, {
      userId: id,
      title: 'Your account role changed',
      body: `An administrator set your role to ${body.role}.`,
      type: 'general',
    }).catch(() => undefined);

    return { user: rows[0] };
  });

  /**
   * Delete a user account. Guardrails mirror the role endpoint: an admin can
   * never delete themselves, and the last remaining admin cannot be removed.
   * Sellers with live listings must be cleared first so buyers never end up
   * with orphaned products in their cart or order history.
   */
  app.delete('/api/v1/admin/users/:id', { preHandler: requireAuth }, async (request) => {
    const admin = requireAdmin(request);
    const { id } = request.params as { id: string };

    if (id === admin.id) throw new ForbiddenError('You cannot delete your own account');

    const target = await pool.query('SELECT id, email, role FROM users WHERE id = $1', [id]);
    if (!target.rows[0]) throw new NotFoundError('User not found');

    if (target.rows[0].role === 'admin') {
      const admins = await pool.query(`SELECT COUNT(*)::int AS n FROM users WHERE role = 'admin'`);
      if (admins.rows[0].n <= 1) throw new ForbiddenError('Cannot delete the last admin');
    }

    const live = await pool.query(
      `SELECT COUNT(*)::int AS n FROM products WHERE seller_id = $1 AND status = 'approved'`,
      [id]
    );
    if (live.rows[0].n > 0) {
      throw new ConflictError(
        `This seller still has ${live.rows[0].n} live listing(s). Suspend or delete them first.`
      );
    }

    await pool.query('DELETE FROM users WHERE id = $1', [id]);
    return { ok: true, deleted: target.rows[0].email };
  });

  // ── Product moderation ────────────────────────────────────────────────────
  app.get('/api/v1/admin/products', { preHandler: requireAuth }, async (request) => {
    requireAdmin(request);
    const q = z
      .object({
        search: z.string().optional().default(''),
        status: z.string().optional(),
        page: z.coerce.number().int().min(1).optional().default(1),
        pageSize: z.coerce.number().int().min(1).max(200).optional().default(25),
      })
      .parse(request.query);

    const where: string[] = [];
    const values: any[] = [];
    if (q.search) {
      values.push(`%${q.search}%`);
      where.push(
        `(p.title ILIKE $${values.length} OR p.category ILIKE $${values.length} OR p.brand ILIKE $${values.length})`
      );
    }
    if (q.status && q.status !== 'all') {
      values.push(q.status);
      where.push(`p.status = $${values.length}`);
    }
    const whereSql = where.length ? `WHERE ${where.join(' AND ')}` : '';
    const offset = (q.page - 1) * q.pageSize;

    const count = await pool.query(`SELECT COUNT(*)::int AS total FROM products p ${whereSql}`, values);
    const { rows } = await pool.query(
      `SELECT p.id, p.title, p.description, p.category, p.brand,
              p.price_minor::int AS "priceMinor", p.stock_quantity AS "stockQuantity",
              p.image_url AS "imageUrl", p.is_flash_deal AS "isFlashDeal",
              p.status, p.rejection_reason AS "rejectionReason",
              p.submitted_at AS "submittedAt", p.reviewed_at AS "reviewedAt",
              p.created_at AS "createdAt", p.view_count AS "viewCount",
              u.id AS "sellerId", u.email AS "sellerEmail",
              COALESCE(s.store_name, u.display_name) AS "sellerName"
       FROM products p
       JOIN users u ON u.id = p.seller_id
       LEFT JOIN store_settings s ON s.user_id = p.seller_id
       ${whereSql}
       ORDER BY CASE WHEN p.status = 'pending' THEN 0 ELSE 1 END, p.created_at DESC
       LIMIT ${q.pageSize} OFFSET ${offset}`,
      values
    );

    const counts = await pool.query(
      `SELECT status, COUNT(*)::int AS count FROM products GROUP BY status`
    );
    const byStatus: Record<string, number> = {
      draft: 0, pending: 0, approved: 0, rejected: 0, suspended: 0,
    };
    for (const r of counts.rows) byStatus[r.status] = r.count;

    return { products: rows, total: count.rows[0].total, page: q.page, pageSize: q.pageSize, counts: byStatus };
  });

  app.get('/api/v1/admin/products/queue', { preHandler: requireAuth }, async (request) => {
    requireAdmin(request);
    const { rows } = await pool.query(
      `SELECT p.id, p.title, p.description, p.category, p.brand,
              p.price_minor::int AS "priceMinor", p.stock_quantity AS "stockQuantity",
              p.image_url AS "imageUrl", p.submitted_at AS "submittedAt",
              u.id AS "sellerId", u.email AS "sellerEmail",
              COALESCE(s.store_name, u.display_name) AS "sellerName",
              COALESCE(s.verified, false) AS "sellerVerified",
              (SELECT COUNT(*)::int FROM products x WHERE x.seller_id = u.id AND x.status = 'approved') AS "sellerApprovedCount"
       FROM products p
       JOIN users u ON u.id = p.seller_id
       LEFT JOIN store_settings s ON s.user_id = p.seller_id
       WHERE p.status = 'pending'
       ORDER BY p.submitted_at ASC NULLS LAST`
    );
    return { products: rows, total: rows.length };
  });

  /** Approve → product becomes publicly visible AND followers get pushed. */
  app.post('/api/v1/admin/products/:id/approve', { preHandler: requireAuth }, async (request) => {
    const admin = requireAdmin(request);
    const { id } = request.params as { id: string };

    const { rows } = await pool.query(
      `UPDATE products
         SET status = 'approved', reviewed_at = now(), reviewed_by = $2, rejection_reason = ''
       WHERE id = $1
       RETURNING id, seller_id AS "sellerId", title, image_url AS "imageUrl", price_minor::int AS "priceMinor"`,
      [id, admin.id]
    );
    const product = rows[0];
    if (!product) throw new NotFoundError('Product not found');

    await pool.query(
      `INSERT INTO product_reviews (product_id, admin_id, action, reason) VALUES ($1,$2,'approved','')`,
      [id, admin.id]
    );
    await notify(pool, {
      userId: product.sellerId,
      title: 'Product approved 🎉',
      body: `"${product.title}" is now live and visible to buyers.`,
      type: 'product_approved',
      imageUrl: product.imageUrl,
      data: { screen: 'product', id: product.id },
    });

    // Followers of this seller get a real push about the new product.
    const notified = await notifyFavoritesOfNewProduct(pool, product.sellerId, {
      id: product.id,
      title: product.title,
      imageUrl: product.imageUrl,
      priceMinor: product.priceMinor,
    });
    statsCache = null;

    return { ok: true, product, followersNotified: notified };
  });

  app.post('/api/v1/admin/products/:id/reject', { preHandler: requireAuth }, async (request) => {
    const admin = requireAdmin(request);
    const { id } = request.params as { id: string };
    const { reason } = z.object({ reason: z.string().min(1) }).parse(request.body);

    const { rows } = await pool.query(
      `UPDATE products SET status = 'rejected', reviewed_at = now(), reviewed_by = $2, rejection_reason = $3
       WHERE id = $1 RETURNING id, seller_id AS "sellerId", title`,
      [id, admin.id, reason]
    );
    const product = rows[0];
    if (!product) throw new NotFoundError('Product not found');

    await pool.query(
      `INSERT INTO product_reviews (product_id, admin_id, action, reason) VALUES ($1,$2,'rejected',$3)`,
      [id, admin.id, reason]
    );
    await notify(pool, {
      userId: product.sellerId,
      title: 'Product needs changes',
      body: `"${product.title}" was not approved: ${reason}`,
      type: 'product_rejected',
      data: { screen: 'seller_product', id: product.id },
    });
    statsCache = null;
    return { ok: true, product };
  });

  app.post('/api/v1/admin/products/:id/suspend', { preHandler: requireAuth }, async (request) => {
    const admin = requireAdmin(request);
    const { id } = request.params as { id: string };
    const { reason } = z.object({ reason: z.string().optional().default('Policy violation') }).parse(
      request.body ?? {}
    );
    const { rows } = await pool.query(
      `UPDATE products SET status = 'suspended', reviewed_at = now(), reviewed_by = $2, rejection_reason = $3
       WHERE id = $1 RETURNING id, seller_id AS "sellerId", title`,
      [id, admin.id, reason]
    );
    const product = rows[0];
    if (!product) throw new NotFoundError('Product not found');
    await pool.query(
      `INSERT INTO product_reviews (product_id, admin_id, action, reason) VALUES ($1,$2,'suspended',$3)`,
      [id, admin.id, reason]
    );
    await notify(pool, {
      userId: product.sellerId,
      title: 'Product suspended',
      body: `"${product.title}" was removed from the catalog: ${reason}`,
      type: 'product_rejected',
      data: { screen: 'seller_product', id: product.id },
    });
    statsCache = null;
    return { ok: true, product };
  });

  /** Bulk moderation for the queue's "approve all" style actions. */
  app.post('/api/v1/admin/products/bulk', { preHandler: requireAuth }, async (request) => {
    const admin = requireAdmin(request);
    const body = z
      .object({
        ids: z.array(z.string()).min(1),
        action: z.enum(['approve', 'reject', 'suspend', 'delete']),
        reason: z.string().optional().default(''),
      })
      .parse(request.body);

    let affected = 0;
    let followersNotified = 0;

    for (const id of body.ids) {
      if (body.action === 'delete') {
        const res = await pool.query('DELETE FROM products WHERE id = $1 RETURNING id', [id]);
        affected += res.rowCount ?? 0;
        continue;
      }
      const status =
        body.action === 'approve' ? 'approved' : body.action === 'reject' ? 'rejected' : 'suspended';
      const { rows } = await pool.query(
        `UPDATE products SET status = $2, reviewed_at = now(), reviewed_by = $3,
                rejection_reason = CASE WHEN $2 = 'approved' THEN '' ELSE $4 END
         WHERE id = $1
         RETURNING id, seller_id AS "sellerId", title, image_url AS "imageUrl", price_minor::int AS "priceMinor"`,
        [id, status, admin.id, body.reason || 'Reviewed by admin']
      );
      const p = rows[0];
      if (!p) continue;
      affected++;
      await pool.query(
        `INSERT INTO product_reviews (product_id, admin_id, action, reason) VALUES ($1,$2,$3,$4)`,
        [id, admin.id, body.action === 'approve' ? 'approved' : body.action === 'reject' ? 'rejected' : 'suspended', body.reason]
      );
      if (body.action === 'approve') {
        await notify(pool, {
          userId: p.sellerId,
          title: 'Product approved 🎉',
          body: `"${p.title}" is now live.`,
          type: 'product_approved',
          data: { screen: 'product', id: p.id },
        }).catch(() => undefined);
        followersNotified += await notifyFavoritesOfNewProduct(pool, p.sellerId, {
          id: p.id, title: p.title, imageUrl: p.imageUrl, priceMinor: p.priceMinor,
        }).catch(() => 0);
      } else {
        await notify(pool, {
          userId: p.sellerId,
          title: body.action === 'reject' ? 'Product needs changes' : 'Product suspended',
          body: `"${p.title}": ${body.reason || 'Reviewed by admin'}`,
          type: 'product_rejected',
          data: { screen: 'seller_product', id: p.id },
        }).catch(() => undefined);
      }
    }
    statsCache = null;
    return { ok: true, affected, followersNotified };
  });

  /** Full moderation history for one product. */
  app.get('/api/v1/admin/products/:id/history', { preHandler: requireAuth }, async (request) => {
    requireAdmin(request);
    const { id } = request.params as { id: string };
    const { rows } = await pool.query(
      `SELECT r.id, r.action, r.reason, r.created_at AS "createdAt",
              COALESCE(u.display_name, u.email, 'system') AS "adminName"
       FROM product_reviews r
       LEFT JOIN users u ON u.id = r.admin_id
       WHERE r.product_id = $1 ORDER BY r.created_at DESC`,
      [id]
    );
    return { history: rows };
  });

  app.delete('/api/v1/admin/products/:id', { preHandler: requireAuth }, async (request) => {
    requireAdmin(request);
    const { id } = request.params as { id: string };
    const res = await pool.query('DELETE FROM products WHERE id = $1 RETURNING id', [id]);
    if ((res.rowCount ?? 0) === 0) throw new NotFoundError('Product not found');
    statsCache = null;
    return { ok: true };
  });

  // ── Seller verification ───────────────────────────────────────────────────
  app.patch('/api/v1/admin/sellers/:id/verify', { preHandler: requireAuth }, async (request) => {
    requireAdmin(request);
    const { id } = request.params as { id: string };
    const { verified } = z.object({ verified: z.boolean() }).parse(request.body);
    const { rows } = await pool.query(
      `INSERT INTO store_settings (user_id, verified) VALUES ($1, $2)
       ON CONFLICT (user_id) DO UPDATE SET verified = EXCLUDED.verified, updated_at = now()
       RETURNING user_id AS "userId", verified`,
      [id, verified]
    );
    await notify(pool, {
      userId: id,
      title: verified ? 'Store verified ✓' : 'Store verification removed',
      body: verified
        ? 'Your store now shows the verified badge to buyers.'
        : 'Your store verification badge was removed. Contact support for details.',
      type: 'general',
    }).catch(() => undefined);
    return { store: rows[0] };
  });

  // ── Support desk (admin mode of the support screen) ───────────────────────
  app.get('/api/v1/admin/support/tickets', { preHandler: requireAuth }, async (request) => {
    requireAdmin(request);
    const { status } = z.object({ status: z.string().optional() }).parse(request.query);
    const values: any[] = [];
    let filter = '';
    if (status && status !== 'all') {
      values.push(status);
      filter = `WHERE t.status = $${values.length}`;
    }
    const { rows } = await pool.query(
      `SELECT t.id, t.subject, t.message, t.status, t.mode, t.created_at AS "createdAt",
              t.updated_at AS "updatedAt",
              u.id AS "userId", u.email AS "userEmail", u.display_name AS "userName", u.role AS "userRole",
              (SELECT COUNT(*)::int FROM support_replies r WHERE r.ticket_id = t.id) AS "replyCount"
       FROM support_tickets t
       JOIN users u ON u.id = t.user_id
       ${filter}
       ORDER BY CASE WHEN t.status = 'open' THEN 0 ELSE 1 END, t.created_at DESC`,
      values
    );
    return { tickets: rows };
  });

  app.get('/api/v1/admin/support/tickets/:id', { preHandler: requireAuth }, async (request) => {
    requireAdmin(request);
    const { id } = request.params as { id: string };
    const t = await pool.query(
      `SELECT t.*, u.email AS "userEmail", u.display_name AS "userName"
       FROM support_tickets t JOIN users u ON u.id = t.user_id WHERE t.id = $1`,
      [id]
    );
    if (!t.rows[0]) throw new NotFoundError('Ticket not found');
    const replies = await pool.query(
      `SELECT r.id, r.body, r.author_role AS "authorRole", r.created_at AS "createdAt",
              COALESCE(u.display_name, 'ScottsTechX') AS "authorName"
       FROM support_replies r LEFT JOIN users u ON u.id = r.author_id
       WHERE r.ticket_id = $1 ORDER BY r.created_at ASC`,
      [id]
    );
    return { ticket: t.rows[0], replies: replies.rows };
  });

  app.post('/api/v1/admin/support/tickets/:id/reply', { preHandler: requireAuth }, async (request) => {
    const admin = requireAdmin(request);
    const { id } = request.params as { id: string };
    const { body, close } = z
      .object({ body: z.string().min(1), close: z.boolean().optional().default(false) })
      .parse(request.body);

    const ticket = await pool.query('SELECT user_id, subject FROM support_tickets WHERE id = $1', [id]);
    if (!ticket.rows[0]) throw new NotFoundError('Ticket not found');

    const { rows } = await pool.query(
      `INSERT INTO support_replies (ticket_id, author_id, author_role, body)
       VALUES ($1,$2,'admin',$3)
       RETURNING id, body, author_role AS "authorRole", created_at AS "createdAt"`,
      [id, admin.id, body]
    );
    await pool.query(
      `UPDATE support_tickets SET status = $2, updated_at = now() WHERE id = $1`,
      [id, close ? 'closed' : 'answered']
    );
    await notify(pool, {
      userId: ticket.rows[0].user_id,
      title: 'Support replied to your ticket',
      body: body.slice(0, 120),
      type: 'support_reply',
      data: { screen: 'support', id },
    }).catch(() => undefined);

    return { reply: rows[0] };
  });
}
