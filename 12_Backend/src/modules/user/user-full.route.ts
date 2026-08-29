/**
 * ScottsTechX — the BIG user-full route module (30+ endpoints, all requireAuth).
 *
 *   addresses, payment-methods, bookmarks, orders, refunds, support/tickets,
 *   faqs, notifications, preferences, locations, change-password.
 */
import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { getPool } from '../../db.js';
import { requireAuth, authedUser } from '../../auth.js';
import { NotFoundError, UnauthorizedError } from '../../errors.js';
import { comparePassword, hashPassword } from '../../auth.js';

export default async function registerUserFullRoute(app: FastifyInstance) {
  const pool = getPool();

  // ── Addresses ────────────────────────────────────────────────────────────
  const addressSchema = z.object({
    label: z.string().optional().default('Home'),
    line1: z.string().min(1),
    city: z.string().optional().default(''),
    country: z.string().optional().default('Uganda'),
    isDefault: z.boolean().optional().default(false),
  });

  app.get('/api/v1/me/addresses', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { rows } = await pool.query(
      'SELECT id, label, line1, city, country, is_default AS "isDefault", created_at AS "createdAt" FROM addresses WHERE user_id = $1 ORDER BY created_at DESC',
      [me.id]
    );
    return { addresses: rows };
  });

  app.post('/api/v1/me/addresses', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const body = addressSchema.parse(request.body);
    if (body.isDefault) await pool.query('UPDATE addresses SET is_default = false WHERE user_id = $1', [me.id]);
    const { rows } = await pool.query(
      `INSERT INTO addresses (user_id, label, line1, city, country, is_default)
       VALUES ($1,$2,$3,$4,$5,$6) RETURNING id, label, line1, city, country, is_default AS "isDefault"`,
      [me.id, body.label, body.line1, body.city, body.country, body.isDefault]
    );
    return { address: rows[0] };
  });

  app.patch('/api/v1/me/addresses/:id', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { id } = request.params as { id: string };
    const body = addressSchema.partial().parse(request.body);
    if (body.isDefault) await pool.query('UPDATE addresses SET is_default = false WHERE user_id = $1', [me.id]);
    const { rows } = await pool.query(
      `UPDATE addresses SET label = COALESCE($3, label), line1 = COALESCE($4, line1),
              city = COALESCE($5, city), country = COALESCE($6, country),
              is_default = COALESCE($7, is_default)
       WHERE id = $1 AND user_id = $2 RETURNING id, label, line1, city, country, is_default AS "isDefault"`,
      [id, me.id, body.label ?? null, body.line1 ?? null, body.city ?? null, body.country ?? null, body.isDefault ?? null]
    );
    if (!rows[0]) throw new NotFoundError('Address not found');
    return { address: rows[0] };
  });

  app.delete('/api/v1/me/addresses/:id', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { id } = request.params as { id: string };
    await pool.query('DELETE FROM addresses WHERE id = $1 AND user_id = $2', [id, me.id]);
    return { ok: true };
  });

  // ── Payment methods ──────────────────────────────────────────────────────
  const pmSchema = z.object({
    type: z.enum(['card', 'momo']),
    label: z.string().optional().default(''),
    last4: z.string().optional().default(''),
    phone: z.string().optional().default(''),
    isDefault: z.boolean().optional().default(false),
  });

  app.get('/api/v1/me/payment-methods', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { rows } = await pool.query(
      `SELECT id, type, label, last4, phone, is_default AS "isDefault", created_at AS "createdAt"
       FROM payment_methods WHERE user_id = $1 ORDER BY created_at DESC`,
      [me.id]
    );
    return { paymentMethods: rows };
  });

  app.post('/api/v1/me/payment-methods', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const body = pmSchema.parse(request.body);
    if (body.isDefault) await pool.query('UPDATE payment_methods SET is_default = false WHERE user_id = $1', [me.id]);
    const { rows } = await pool.query(
      `INSERT INTO payment_methods (user_id, type, label, last4, phone, is_default)
       VALUES ($1,$2,$3,$4,$5,$6) RETURNING id, type, label, last4, phone, is_default AS "isDefault"`,
      [me.id, body.type, body.label, body.last4, body.phone, body.isDefault]
    );
    return { paymentMethod: rows[0] };
  });

  app.patch('/api/v1/me/payment-methods/:id', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { id } = request.params as { id: string };
    const body = pmSchema.partial().parse(request.body);
    if (body.isDefault) await pool.query('UPDATE payment_methods SET is_default = false WHERE user_id = $1', [me.id]);
    const { rows } = await pool.query(
      `UPDATE payment_methods SET type = COALESCE($3, type), label = COALESCE($4, label),
              last4 = COALESCE($5, last4), phone = COALESCE($6, phone), is_default = COALESCE($7, is_default)
       WHERE id = $1 AND user_id = $2
       RETURNING id, type, label, last4, phone, is_default AS "isDefault"`,
      [id, me.id, body.type ?? null, body.label ?? null, body.last4 ?? null, body.phone ?? null, body.isDefault ?? null]
    );
    if (!rows[0]) throw new NotFoundError('Payment method not found');
    return { paymentMethod: rows[0] };
  });

  app.delete('/api/v1/me/payment-methods/:id', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { id } = request.params as { id: string };
    await pool.query('DELETE FROM payment_methods WHERE id = $1 AND user_id = $2', [id, me.id]);
    return { ok: true };
  });

  // ── Bookmarks ────────────────────────────────────────────────────────────
  app.get('/api/v1/me/bookmarks', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { rows } = await pool.query(
      `SELECT p.id, p.title, p.category, p.price_minor::int AS "priceMinor", p.stock_quantity AS "stockQuantity",
              COALESCE((SELECT url FROM product_media pm WHERE pm.product_id = p.id ORDER BY sort_order LIMIT 1), p.image_url) AS "imageUrl",
              p.rating::float AS rating, p.rating_count AS "ratingCount",
              json_build_object('id', u.id, 'name', COALESCE(s.store_name, u.display_name)) AS seller,
              p.location
       FROM bookmarks b
       JOIN products p ON p.id = b.product_id
       JOIN users u ON u.id = p.seller_id
       LEFT JOIN store_settings s ON s.user_id = p.seller_id
       WHERE b.user_id = $1 ORDER BY b.created_at DESC`,
      [me.id]
    );
    return { products: rows.map((r) => ({ ...r, currency: 'UGX', seller: typeof r.seller === 'string' ? JSON.parse(r.seller) : r.seller })) };
  });

  app.post('/api/v1/me/bookmarks/toggle', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const body = z.object({ productId: z.string() }).parse(request.body);
    const existing = await pool.query('SELECT 1 FROM bookmarks WHERE user_id = $1 AND product_id = $2', [me.id, body.productId]);
    if ((existing.rowCount ?? 0) > 0) {
      await pool.query('DELETE FROM bookmarks WHERE user_id = $1 AND product_id = $2', [me.id, body.productId]);
      return { bookmarked: false };
    }
    await pool.query('INSERT INTO bookmarks (user_id, product_id) VALUES ($1, $2)', [me.id, body.productId]);
    return { bookmarked: true };
  });

  // ── Orders ───────────────────────────────────────────────────────────────
  app.get('/api/v1/me/orders', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { rows } = await pool.query(
      `SELECT id, seller_id AS "sellerId", product_id AS "productId",
              product_title AS title, price_minor::int AS amount,
              quantity, status, created_at AS "createdAt",
              COALESCE((SELECT url FROM product_media pm WHERE pm.product_id = o.product_id ORDER BY sort_order LIMIT 1),
                       (SELECT image_url FROM products p2 WHERE p2.id = o.product_id)) AS "imageUrl",
              (SELECT store_name FROM store_settings s WHERE s.user_id = o.seller_id) AS "storeName"
       FROM orders o WHERE buyer_id = $1 ORDER BY created_at DESC`,
      [me.id]
    );
    return { orders: rows };
  });

  app.get('/api/v1/me/orders/:id', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { id } = request.params as { id: string };
    const { rows } = await pool.query(
      `SELECT id, seller_id AS "sellerId", product_id AS "productId", product_title AS title,
              price_minor::int AS amount, quantity, status, created_at AS "createdAt"
       FROM orders WHERE id = $1 AND buyer_id = $2`,
      [id, me.id]
    );
    if (!rows[0]) throw new NotFoundError('Order not found');
    return { order: rows[0] };
  });

  // ── Refunds ──────────────────────────────────────────────────────────────
  app.post('/api/v1/me/refunds', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const body = z.object({ orderId: z.string(), reason: z.string().min(3) }).parse(request.body);
    const order = await pool.query('SELECT * FROM orders WHERE id = $1 AND buyer_id = $2', [body.orderId, me.id]);
    if (!order.rows[0]) throw new NotFoundError('Order not found');
    const { rows } = await pool.query(
      `INSERT INTO refund_claims (user_id, order_id, reason, status)
       VALUES ($1,$2,$3,'pending') RETURNING id, order_id AS "orderId", reason, status, created_at AS "createdAt"`,
      [me.id, body.orderId, body.reason]
    );
    return { refund: rows[0] };
  });

  app.get('/api/v1/me/refunds', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { rows } = await pool.query(
      `SELECT id, order_id AS "orderId", reason, status, created_at AS "createdAt"
       FROM refund_claims WHERE user_id = $1 ORDER BY created_at DESC`,
      [me.id]
    );
    return { refunds: rows };
  });

  // ── Support tickets ──────────────────────────────────────────────────────
  app.post('/api/v1/me/support/tickets', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const body = z.object({ subject: z.string().min(1), message: z.string().min(1) }).parse(request.body);
    const { rows } = await pool.query(
      `INSERT INTO support_tickets (user_id, subject, message, status)
       VALUES ($1,$2,$3,'open') RETURNING id, subject, message, status, created_at AS "createdAt"`,
      [me.id, body.subject, body.message]
    );
    return { ticket: rows[0] };
  });

  app.get('/api/v1/me/support/tickets', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { rows } = await pool.query(
      `SELECT id, subject, message, status, created_at AS "createdAt"
       FROM support_tickets WHERE user_id = $1 ORDER BY created_at DESC`,
      [me.id]
    );
    return { tickets: rows };
  });

  // ── FAQs ─────────────────────────────────────────────────────────────────
  app.get('/api/v1/me/faqs', async () => {
    const { rows } = await pool.query('SELECT id, question, answer, category FROM faqs ORDER BY sort_order, id');
    return { faqs: rows };
  });

  // ── Notifications ────────────────────────────────────────────────────────
  app.get('/api/v1/me/notifications', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    // `data` carries the deep-link payload ({ screen, id }) so tapping a
    // notification opens the right screen on mobile and the right route on web.
    const { rows } = await pool.query(
      `SELECT id, title, body, type, read, data, image_url AS "imageUrl",
              created_at AS "createdAt"
       FROM notifications WHERE user_id = $1 ORDER BY created_at DESC LIMIT 100`,
      [me.id]
    );
    const unread = rows.filter((r) => !r.read).length;
    return { notifications: rows, unread };
  });

  /** Badge count only — cheap enough to poll from the nav bar. */
  app.get('/api/v1/me/notifications/unread-count', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { rows } = await pool.query(
      'SELECT COUNT(*)::int AS unread FROM notifications WHERE user_id = $1 AND read = false',
      [me.id]
    );
    return { unread: rows[0].unread };
  });

  // Both verbs are accepted so every client (Android, web, curl) works.
  const markRead = async (request: any) => {
    const me = authedUser(request);
    const { id } = request.params as { id: string };
    await pool.query('UPDATE notifications SET read = true WHERE id = $1 AND user_id = $2', [id, me.id]);
    const { rows } = await pool.query(
      'SELECT COUNT(*)::int AS unread FROM notifications WHERE user_id = $1 AND read = false',
      [me.id]
    );
    return { ok: true, unread: rows[0].unread };
  };
  app.patch('/api/v1/me/notifications/:id/read', { preHandler: requireAuth }, markRead);
  app.post('/api/v1/me/notifications/:id/read', { preHandler: requireAuth }, markRead);

  app.post('/api/v1/me/notifications/read-all', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    await pool.query('UPDATE notifications SET read = true WHERE user_id = $1', [me.id]);
    return { ok: true };
  });

  // ── Preferences ──────────────────────────────────────────────────────────
  app.get('/api/v1/me/preferences', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { rows } = await pool.query('SELECT * FROM user_settings WHERE user_id = $1', [me.id]);
    if (!rows[0]) {
      return {
        preferences: {
          theme: 'system',
          language: 'en',
          currency: 'UGX',
          notifyOrderUpdates: true,
          notifyMessages: true,
          notifyMarketing: false,
        },
      };
    }
    const r = rows[0];
    return {
      preferences: {
        theme: r.theme ?? 'system',
        language: r.language ?? 'en',
        currency: r.currency ?? 'UGX',
        notifyOrderUpdates: !!r.notify_order_updates,
        notifyMessages: !!r.notify_messages,
        notifyMarketing: !!r.notify_marketing,
      },
    };
  });

  app.patch('/api/v1/me/preferences', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const body = z
      .object({
        theme: z.enum(['light', 'dark', 'system']).optional(),
        language: z.string().optional(),
        currency: z.string().optional(),
        notifyOrderUpdates: z.boolean().optional(),
        notifyMessages: z.boolean().optional(),
        notifyMarketing: z.boolean().optional(),
      })
      .parse(request.body);
    await pool.query(
      `INSERT INTO user_settings (user_id, theme, language, currency, notify_order_updates, notify_messages, notify_marketing)
       VALUES ($1, 'system', 'en', 'UGX', true, true, false)
       ON CONFLICT (user_id) DO NOTHING`,
      [me.id]
    );
    const sets: string[] = [];
    const values: any[] = [me.id];
    const map: Record<string, string> = {
      theme: 'theme',
      language: 'language',
      currency: 'currency',
      notifyOrderUpdates: 'notify_order_updates',
      notifyMessages: 'notify_messages',
      notifyMarketing: 'notify_marketing',
    };
    for (const [k, col] of Object.entries(map)) {
      if (body[k as keyof typeof body] !== undefined) {
        values.push(body[k as keyof typeof body] as any);
        sets.push(`${col} = $${values.length}`);
      }
    }
    sets.push('updated_at = now()');
    await pool.query(`UPDATE user_settings SET ${sets.join(', ')} WHERE user_id = $1`, values);
    const { rows } = await pool.query('SELECT * FROM user_settings WHERE user_id = $1', [me.id]);
    const r = rows[0];
    return {
      preferences: {
        theme: r.theme ?? 'system',
        language: r.language ?? 'en',
        currency: r.currency ?? 'UGX',
        notifyOrderUpdates: !!r.notify_order_updates,
        notifyMessages: !!r.notify_messages,
        notifyMarketing: !!r.notify_marketing,
      },
    };
  });

  // ── Locations ────────────────────────────────────────────────────────────
  app.get('/api/v1/me/locations', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { rows } = await pool.query(
      `SELECT id, label, lat, lng, city, is_default AS "isDefault"
       FROM user_locations WHERE user_id = $1 ORDER BY created_at DESC`,
      [me.id]
    );
    return { locations: rows };
  });

  app.post('/api/v1/me/locations', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const body = z
      .object({
        label: z.string().optional().default('My location'),
        lat: z.number(),
        lng: z.number(),
        city: z.string().optional().default(''),
        isDefault: z.boolean().optional().default(false),
      })
      .parse(request.body);
    if (body.isDefault) await pool.query('UPDATE user_locations SET is_default = false WHERE user_id = $1', [me.id]);
    const { rows } = await pool.query(
      `INSERT INTO user_locations (user_id, label, lat, lng, city, is_default)
       VALUES ($1,$2,$3,$4,$5,$6) RETURNING id, label, lat, lng, city, is_default AS "isDefault"`,
      [me.id, body.label, body.lat, body.lng, body.city, body.isDefault]
    );
    return { location: rows[0] };
  });

  app.delete('/api/v1/me/locations/:id', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { id } = request.params as { id: string };
    await pool.query('DELETE FROM user_locations WHERE id = $1 AND user_id = $2', [id, me.id]);
    return { ok: true };
  });

  // ── Change password ──────────────────────────────────────────────────────
  app.post('/api/v1/me/change-password', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const body = z.object({ oldPassword: z.string().min(1), newPassword: z.string().min(6) }).parse(request.body);
    const { rows } = await pool.query('SELECT password_hash FROM users WHERE id = $1', [me.id]);
    if (!rows[0]?.password_hash) throw new UnauthorizedError('Account has no local password');
    const ok = await comparePassword(body.oldPassword, rows[0].password_hash);
    if (!ok) throw new UnauthorizedError('Current password is incorrect');
    const hash = await hashPassword(body.newPassword);
    await pool.query('UPDATE users SET password_hash = $2, updated_at = now() WHERE id = $1', [me.id, hash]);
    return { ok: true };
  });
}
