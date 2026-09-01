/**
 * ScottsTechX — buyer social + acquisition routes.
 *
 * FAVOURITES (drives new-product push notifications)
 *   GET    /api/v1/me/favorites
 *   POST   /api/v1/me/favorites/:sellerId
 *   DELETE /api/v1/me/favorites/:sellerId
 *   GET    /api/v1/me/favorites/feed        newest products from followed stores
 *
 * CART / CHECKOUT PREP
 *   GET    /api/v1/me/cart
 *   POST   /api/v1/me/cart            { productId, quantity }
 *   PATCH  /api/v1/me/cart/:productId { quantity }
 *   DELETE /api/v1/me/cart/:productId
 *   DELETE /api/v1/me/cart
 *   POST   /api/v1/me/cart/checkout   { addressId?, phone?, note? } -> orders
 *
 * DEVICES (real phone push)
 *   POST   /api/v1/me/devices         { token, platform }
 *   DELETE /api/v1/me/devices         { token }
 *
 * RATINGS
 *   GET    /api/v1/products/:id/ratings
 *   POST   /api/v1/products/:id/ratings { stars, comment }
 *
 * SEARCH HISTORY
 *   GET    /api/v1/me/search-history
 *   DELETE /api/v1/me/search-history
 */
import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { getPool } from '../../db.js';
import { requireAuth, authedUser } from '../../auth.js';
import { NotFoundError, ConflictError } from '../../errors.js';
import { registerDevice, unregisterDevice, notify } from '../notifications/notify.service.js';

export default async function registerSocialRoute(app: FastifyInstance) {
  const pool = getPool();

  // ── Favourite sellers ─────────────────────────────────────────────────────
  app.get('/api/v1/me/favorites', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { rows } = await pool.query(
      `SELECT u.id, COALESCE(s.store_name, u.display_name) AS "storeName",
              COALESCE(s.city, '') AS city, COALESCE(s.rating, 0)::float AS rating,
              COALESCE(s.verified, false) AS verified,
              COALESCE(s.store_logo_url, u.profile_photo_url, '') AS "logoUrl",
              f.created_at AS "followedAt",
              (SELECT COUNT(*)::int FROM products p WHERE p.seller_id = u.id AND p.status = 'approved') AS "productCount",
              (SELECT COUNT(*)::int FROM products p WHERE p.seller_id = u.id AND p.status = 'approved'
                 AND p.created_at > now() - interval '7 days') AS "newThisWeek"
       FROM favorite_sellers f
       JOIN users u ON u.id = f.seller_id
       LEFT JOIN store_settings s ON s.user_id = u.id
       WHERE f.user_id = $1
       ORDER BY f.created_at DESC`,
      [me.id]
    );
    return { sellers: rows };
  });

  app.post('/api/v1/me/favorites/:sellerId', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { sellerId } = request.params as { sellerId: string };
    if (sellerId === me.id) throw new ConflictError('You cannot follow your own store');
    const seller = await pool.query(`SELECT id FROM users WHERE id = $1 AND role = 'seller'`, [sellerId]);
    if (!seller.rows[0]) throw new NotFoundError('Seller not found');
    await pool.query(
      `INSERT INTO favorite_sellers (user_id, seller_id) VALUES ($1,$2) ON CONFLICT DO NOTHING`,
      [me.id, sellerId]
    );
    return { ok: true, following: true };
  });

  app.delete('/api/v1/me/favorites/:sellerId', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { sellerId } = request.params as { sellerId: string };
    await pool.query('DELETE FROM favorite_sellers WHERE user_id = $1 AND seller_id = $2', [
      me.id,
      sellerId,
    ]);
    return { ok: true, following: false };
  });

  /** "Updates from stores you follow" — the buyer home feed. */
  app.get('/api/v1/me/favorites/feed', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { limit } = z
      .object({ limit: z.coerce.number().int().min(1).max(60).optional().default(20) })
      .parse(request.query);
    const { rows } = await pool.query(
      `SELECT p.id, p.title, p.category, p.price_minor::int AS "priceMinor",
              p.old_price_minor::int AS "oldPriceMinor",
              COALESCE((SELECT url FROM product_media pm WHERE pm.product_id = p.id ORDER BY sort_order LIMIT 1), p.image_url) AS "imageUrl",
              p.rating::float AS rating, p.rating_count AS "ratingCount",
              p.is_flash_deal AS "isFlashDeal", p.discount_percent AS "discountPercent",
              p.created_at AS "createdAt",
              json_build_object(
                'id', u.id,
                'name', COALESCE(s.store_name, u.display_name),
                'rating', COALESCE(s.rating, 0)::float,
                'location', COALESCE(s.city, p.location),
                'verified', COALESCE(s.verified, false)
              ) AS seller
       FROM favorite_sellers f
       JOIN products p ON p.seller_id = f.seller_id AND p.status = 'approved'
       JOIN users u ON u.id = p.seller_id
       LEFT JOIN store_settings s ON s.user_id = p.seller_id
       WHERE f.user_id = $1
       ORDER BY p.created_at DESC
       LIMIT $2`,
      [me.id, limit]
    );
    return {
      products: rows.map((r) => ({
        ...r,
        currency: 'UGX',
        seller: typeof r.seller === 'string' ? JSON.parse(r.seller) : r.seller,
      })),
    };
  });

  // ── Cart ──────────────────────────────────────────────────────────────────
  /**
   * Single source of truth for the cart payload. Every mutation returns the
   * recomputed cart so clients never need a follow-up GET (and can't drift).
   */
  async function loadCart(userId: string) {
    const { rows } = await pool.query(
      `SELECT c.product_id AS "productId", c.quantity,
              p.title, p.price_minor::int AS "priceMinor", p.stock_quantity AS "stockQuantity",
              COALESCE((SELECT url FROM product_media pm WHERE pm.product_id = p.id ORDER BY sort_order LIMIT 1), p.image_url) AS "imageUrl",
              p.status,
              u.id AS "sellerId", COALESCE(s.store_name, u.display_name) AS "sellerName"
       FROM cart_items c
       JOIN products p ON p.id = c.product_id
       JOIN users u ON u.id = p.seller_id
       LEFT JOIN store_settings s ON s.user_id = p.seller_id
       WHERE c.user_id = $1
       ORDER BY c.created_at DESC`,
      [userId]
    );
    const items = rows.map((r) => ({ ...r, lineTotalMinor: r.priceMinor * r.quantity }));
    const subtotalMinor = items.reduce((sum, i) => sum + i.lineTotalMinor, 0);
    return {
      items,
      subtotalMinor,
      itemCount: items.reduce((n, i) => n + i.quantity, 0),
      currency: 'UGX',
    };
  }

  app.get('/api/v1/me/cart', { preHandler: requireAuth }, async (request) =>
    loadCart(authedUser(request).id)
  );

  app.post('/api/v1/me/cart', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { productId, quantity } = z
      .object({
        productId: z.string().min(1),
        // Keep malformed/absurd input bounded, but let the stock rule below
        // return the useful 409 conflict when a client asks for more than the
        // seller actually has (including the existing contract's large probe).
        quantity: z.coerce.number().int().min(1).max(100000).optional().default(1),
      })
      .parse(request.body);

    // Lock the product while checking the existing line. Checking only the
    // increment allowed an existing quantity plus the new quantity to exceed
    // stock, and concurrent adds could both pass the check before either
    // upserted its line.
    const client = await pool.connect();
    try {
      await client.query('BEGIN');
      const product = await client.query(
        `SELECT id, stock_quantity FROM products
         WHERE id = $1 AND status = 'approved' FOR UPDATE`,
        [productId]
      );
      if (!product.rows[0]) throw new NotFoundError('Product not available');

      const existing = await client.query(
        'SELECT quantity FROM cart_items WHERE user_id = $1 AND product_id = $2 FOR UPDATE',
        [me.id, productId]
      );
      const nextQuantity = Number(existing.rows[0]?.quantity ?? 0) + quantity;
      const stock = Number(product.rows[0].stock_quantity);
      if (nextQuantity > stock) {
        throw new ConflictError(`Only ${stock} left in stock`);
      }

      await client.query(
        `INSERT INTO cart_items (user_id, product_id, quantity) VALUES ($1,$2,$3)
         ON CONFLICT (user_id, product_id) DO UPDATE SET quantity = EXCLUDED.quantity`,
        [me.id, productId, nextQuantity]
      );
      await client.query('COMMIT');
    } catch (err) {
      await client.query('ROLLBACK').catch(() => undefined);
      throw err;
    } finally {
      client.release();
    }
    return loadCart(me.id);
  });

  app.patch('/api/v1/me/cart/:productId', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { productId } = request.params as { productId: string };
    const { quantity } = z.object({ quantity: z.coerce.number().int().min(0).max(99) }).parse(request.body);
    if (quantity === 0) {
      await pool.query('DELETE FROM cart_items WHERE user_id = $1 AND product_id = $2', [me.id, productId]);
      return loadCart(me.id);
    }
    // Match the POST guard and lock the product before changing a line. A
    // stale cart can outlive a stock edit; accepting an impossible quantity
    // here only moves the error to checkout and makes the cart misleading.
    const client = await pool.connect();
    try {
      await client.query('BEGIN');
      const product = await client.query(
        `SELECT stock_quantity FROM products
         WHERE id = $1 AND status = 'approved' FOR UPDATE`,
        [productId]
      );
      if (!product.rows[0]) throw new ConflictError('Product is no longer available');
      const res = await client.query(
        'UPDATE cart_items SET quantity = $3 WHERE user_id = $1 AND product_id = $2 RETURNING product_id',
        [me.id, productId, quantity]
      );
      if (!res.rows[0]) throw new NotFoundError('Item not in cart');
      const stock = Number(product.rows[0].stock_quantity);
      if (quantity > stock) throw new ConflictError(`Only ${stock} left in stock`);
      await client.query('COMMIT');
    } catch (err) {
      await client.query('ROLLBACK').catch(() => undefined);
      throw err;
    } finally {
      client.release();
    }
    return loadCart(me.id);
  });

  app.delete('/api/v1/me/cart/:productId', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { productId } = request.params as { productId: string };
    await pool.query('DELETE FROM cart_items WHERE user_id = $1 AND product_id = $2', [me.id, productId]);
    return loadCart(me.id);
  });

  app.delete('/api/v1/me/cart', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    await pool.query('DELETE FROM cart_items WHERE user_id = $1', [me.id]);
    return loadCart(me.id);
  });

  /**
   * Cart checkout. Creates one order per cart line (orders are per-product in
   * this schema), decrements stock atomically, notifies each seller, and
   * empties the cart. Payment stays 'pending' — the buyer settles on delivery
   * or via the payment link from POST /orders/:id/pay when a gateway is
   * configured. Everything runs in a single transaction so a mid-flight stock
   * conflict leaves the cart untouched.
   */
  app.post('/api/v1/me/cart/checkout', { preHandler: requireAuth }, async (request, reply) => {
    const me = authedUser(request);
    const body = z
      .object({
        addressId: z.string().uuid().optional(),
        phone: z.string().trim().max(40).optional().default(''),
        note: z.string().trim().max(500).optional().default(''),
      })
      .parse(request.body ?? {});

    const client = await pool.connect();
    try {
      await client.query('BEGIN');

      let deliveryAddress = '';
      if (body.addressId) {
        const { rows: addresses } = await client.query(
          `SELECT line1, city, country FROM addresses WHERE id = $1 AND user_id = $2`,
          [body.addressId, me.id]
        );
        if (!addresses[0]) throw new NotFoundError('Delivery address not found');
        deliveryAddress = [addresses[0].line1, addresses[0].city, addresses[0].country]
          .filter((part) => String(part || '').trim())
          .join(', ');
      }

      // Lock the product rows we are about to sell so two buyers can't
      // oversell the same unit.
      const { rows: lines } = await client.query(
        `SELECT c.product_id AS "productId", c.quantity,
                p.title, p.price_minor::int AS "priceMinor",
                p.stock_quantity AS "stockQuantity", p.status, p.seller_id AS "sellerId"
         FROM cart_items c
         JOIN products p ON p.id = c.product_id
         WHERE c.user_id = $1
         ORDER BY c.product_id
         FOR UPDATE OF p`,
        [me.id]
      );

      if (lines.length === 0) throw new ConflictError('Your cart is empty');

      for (const l of lines) {
        if (l.status !== 'approved') {
          throw new ConflictError(`"${l.title}" is no longer available`);
        }
        if (l.stockQuantity < l.quantity) {
          throw new ConflictError(`Only ${l.stockQuantity} left of "${l.title}"`);
        }
      }

      const created: any[] = [];
      for (const l of lines) {
        const { rows } = await client.query(
          `INSERT INTO orders (
             buyer_id, seller_id, product_id, product_title, price_minor, quantity,
             status, payment_provider, delivery_address, delivery_phone, delivery_note,
             stock_reserved
           )
           VALUES ($1,$2,$3,$4,$5,$6,'pending','cod',$7,$8,$9,true)
           RETURNING id, seller_id AS "sellerId", product_id AS "productId", product_title AS title,
                     price_minor::int AS amount, quantity, status, created_at AS "createdAt",
                     delivery_address AS "deliveryAddress", delivery_phone AS "deliveryPhone",
                     delivery_note AS "deliveryNote"`,
          [me.id, l.sellerId, l.productId, l.title, l.priceMinor, l.quantity,
            deliveryAddress, body.phone, body.note]
        );
        await client.query(
          'UPDATE products SET stock_quantity = stock_quantity - $2 WHERE id = $1',
          [l.productId, l.quantity]
        );
        created.push(rows[0]);
      }

      await client.query('DELETE FROM cart_items WHERE user_id = $1', [me.id]);
      await client.query('COMMIT');

      // Notify sellers outside the transaction — a push failure must never
      // roll back a placed order.
      for (const o of created) {
        await notify(pool, {
          userId: o.sellerId,
          title: 'New order received',
          body: `${me.name || 'A buyer'} ordered ${o.quantity} × ${o.title}`,
          type: 'order_update',
          data: { screen: 'order', id: o.id },
        }).catch(() => undefined);
      }

      const totalMinor = created.reduce((sum, o) => sum + o.amount * o.quantity, 0);
      return reply.code(201).send({
        orders: created,
        orderCount: created.length,
        totalMinor,
        currency: 'UGX',
        paymentMode: 'cod',
        message:
          created.length === 1
            ? 'Order placed. The seller has been notified.'
            : `${created.length} orders placed across ${new Set(created.map((o) => o.sellerId)).size} seller(s).`,
        note: body.note,
      });
    } catch (err) {
      await client.query('ROLLBACK').catch(() => undefined);
      throw err;
    } finally {
      client.release();
    }
  });

  // ── Device tokens (push) ──────────────────────────────────────────────────
  app.post('/api/v1/me/devices', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { token, platform } = z
      .object({
        token: z.string().min(10),
        platform: z.enum(['android', 'ios', 'web']).optional().default('android'),
      })
      .parse(request.body);
    return registerDevice(pool, me.id, token, platform);
  });

  app.delete('/api/v1/me/devices', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { token } = z.object({ token: z.string().min(1) }).parse(request.body);
    return unregisterDevice(pool, me.id, token);
  });

  // ── Product ratings ───────────────────────────────────────────────────────
  app.get('/api/v1/products/:id/ratings', async (request) => {
    const { id } = request.params as { id: string };
    const { rows } = await pool.query(
      `SELECT r.id, r.stars, r.comment, r.created_at AS "createdAt",
              COALESCE(u.display_name, 'Buyer') AS "authorName"
       FROM product_ratings r JOIN users u ON u.id = r.user_id
       WHERE r.product_id = $1 ORDER BY r.created_at DESC LIMIT 50`,
      [id]
    );
    const agg = await pool.query(
      `SELECT COALESCE(AVG(stars), 0)::float AS average, COUNT(*)::int AS count
       FROM product_ratings WHERE product_id = $1`,
      [id]
    );
    return { ratings: rows, summary: agg.rows[0] };
  });

  app.post('/api/v1/products/:id/ratings', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { id } = request.params as { id: string };
    const { stars, comment } = z
      .object({ stars: z.coerce.number().int().min(1).max(5), comment: z.string().optional().default('') })
      .parse(request.body);

    const product = await pool.query('SELECT id FROM products WHERE id = $1', [id]);
    if (!product.rows[0]) throw new NotFoundError('Product not found');

    await pool.query(
      `INSERT INTO product_ratings (product_id, user_id, stars, comment)
       VALUES ($1,$2,$3,$4)
       ON CONFLICT (product_id, user_id) DO UPDATE SET stars = EXCLUDED.stars, comment = EXCLUDED.comment`,
      [id, me.id, stars, comment]
    );
    // Keep the denormalised rating on products in sync.
    await pool.query(
      `UPDATE products p SET
         rating = sub.avg, rating_count = sub.n
       FROM (SELECT COALESCE(AVG(stars),0)::numeric(3,2) AS avg, COUNT(*)::int AS n
             FROM product_ratings WHERE product_id = $1) sub
       WHERE p.id = $1`,
      [id]
    );
    const agg = await pool.query(
      `SELECT rating::float AS rating, rating_count AS "ratingCount" FROM products WHERE id = $1`,
      [id]
    );
    return { ok: true, ...agg.rows[0] };
  });

  // ── Search history ────────────────────────────────────────────────────────
  app.get('/api/v1/me/search-history', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { rows } = await pool.query(
      `SELECT DISTINCT ON (query) id, query, mode, results, created_at AS "createdAt"
       FROM search_history WHERE user_id = $1
       ORDER BY query, created_at DESC`,
      [me.id]
    );
    const sorted = rows
      .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
      .slice(0, 12);
    return { history: sorted };
  });

  app.delete('/api/v1/me/search-history', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    await pool.query('DELETE FROM search_history WHERE user_id = $1', [me.id]);
    return { ok: true };
  });
}
