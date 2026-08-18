/**
 * ScottsTechX — platform admin routes (web console).
 *
 *   GET    /api/v1/admin/stats
 *   GET    /api/v1/admin/users?search=&role=&page=&pageSize=
 *   PATCH  /api/v1/admin/users/:id/role      { role: buyer|seller|admin }
 *   GET    /api/v1/admin/products?search=&page=&pageSize=
 *   DELETE /api/v1/admin/products/:id        (moderation)
 *
 * Every route requires role='admin' — enforced on the backend, never the UI.
 */
import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { getPool } from '../../db.js';
import { requireAuth, requireAdmin } from '../../auth.js';
import { NotFoundError, ForbiddenError } from '../../errors.js';

export default async function registerAdminRoute(app: FastifyInstance) {
  const pool = getPool();

  app.get('/api/v1/admin/stats', { preHandler: requireAuth }, async (request) => {
    requireAdmin(request);
    const users = await pool.query(
      `SELECT
         COUNT(*)::int AS total,
         COUNT(*) FILTER (WHERE role = 'buyer')::int AS buyers,
         COUNT(*) FILTER (WHERE role = 'seller')::int AS sellers,
         COUNT(*) FILTER (WHERE role = 'admin')::int AS admins,
         COUNT(*) FILTER (WHERE email_verified)::int AS verified
       FROM users`
    );
    const products = await pool.query(
      `SELECT COUNT(*)::int AS total,
              COUNT(*) FILTER (WHERE is_flash_deal)::int AS flash_deals,
              COUNT(*) FILTER (WHERE stock_quantity <= 5)::int AS low_stock
       FROM products`
    );
    const orders = await pool.query(
      `SELECT COUNT(*)::int AS total,
              COUNT(*) FILTER (WHERE status = 'paid')::int AS paid,
              COALESCE(SUM(price_minor * quantity) FILTER (WHERE status = 'paid'), 0)::int AS revenue_ugx
       FROM orders`
    );
    const conversations = await pool.query('SELECT COUNT(*)::int AS total FROM conversations');
    const recentUsers = await pool.query(
      `SELECT id, email, display_name AS "displayName", role, email_verified AS "emailVerified", created_at AS "createdAt"
       FROM users ORDER BY created_at DESC LIMIT 8`
    );
    return {
      stats: {
        users: users.rows[0],
        products: products.rows[0],
        orders: orders.rows[0],
        conversations: conversations.rows[0].total,
      },
      recentUsers: recentUsers.rows,
    };
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
      where.push(`(email ILIKE $${values.length} OR display_name ILIKE $${values.length} OR phone ILIKE $${values.length})`);
    }
    if (q.role) {
      values.push(q.role);
      where.push(`role = $${values.length}`);
    }
    const whereSql = where.length ? `WHERE ${where.join(' AND ')}` : '';
    const offset = (q.page - 1) * q.pageSize;

    const count = await pool.query(`SELECT COUNT(*)::int AS total FROM users ${whereSql}`, values);
    const { rows } = await pool.query(
      `SELECT id, email, display_name AS "displayName", phone, role,
              email_verified AS "emailVerified", city, created_at AS "createdAt"
       FROM users ${whereSql}
       ORDER BY created_at DESC
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
    // Never remove the last admin.
    if (body.role !== 'admin') {
      const admins = await pool.query(`SELECT COUNT(*)::int AS n FROM users WHERE role = 'admin'`);
      if (admins.rows[0].n <= 1) throw new ForbiddenError('Cannot demote the last admin');
    }
    const { rows } = await pool.query(
      `UPDATE users SET role = $2, updated_at = now() WHERE id = $1 RETURNING id, email, role`,
      [id, body.role]
    );
    if (!rows[0]) throw new NotFoundError('User not found');
    return { user: rows[0] };
  });

  app.get('/api/v1/admin/products', { preHandler: requireAuth }, async (request) => {
    requireAdmin(request);
    const q = z
      .object({
        search: z.string().optional().default(''),
        page: z.coerce.number().int().min(1).optional().default(1),
        pageSize: z.coerce.number().int().min(1).max(200).optional().default(25),
      })
      .parse(request.query);
    const where: string[] = [];
    const values: any[] = [];
    if (q.search) {
      values.push(`%${q.search}%`);
      where.push(`(p.title ILIKE $${values.length} OR p.category ILIKE $${values.length} OR p.brand ILIKE $${values.length})`);
    }
    const whereSql = where.length ? `WHERE ${where.join(' AND ')}` : '';
    const offset = (q.page - 1) * q.pageSize;

    const count = await pool.query(`SELECT COUNT(*)::int AS total FROM products p ${whereSql}`, values);
    const { rows } = await pool.query(
      `SELECT p.id, p.title, p.category, p.price_minor::int AS "priceMinor", p.stock_quantity AS "stockQuantity",
              p.image_url AS "imageUrl", p.is_flash_deal AS "isFlashDeal", p.created_at AS "createdAt",
              u.email AS "sellerEmail", COALESCE(s.store_name, u.display_name) AS "sellerName"
       FROM products p
       JOIN users u ON u.id = p.seller_id
       LEFT JOIN store_settings s ON s.user_id = p.seller_id
       ${whereSql}
       ORDER BY p.created_at DESC
       LIMIT ${q.pageSize} OFFSET ${offset}`,
      values
    );
    return { products: rows, total: count.rows[0].total, page: q.page, pageSize: q.pageSize };
  });

  app.delete('/api/v1/admin/products/:id', { preHandler: requireAuth }, async (request) => {
    requireAdmin(request);
    const { id } = request.params as { id: string };
    const res = await pool.query('DELETE FROM products WHERE id = $1 RETURNING id', [id]);
    if ((res.rowCount ?? 0) === 0) throw new NotFoundError('Product not found');
    return { ok: true };
  });
}
