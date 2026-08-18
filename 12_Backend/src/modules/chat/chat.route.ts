/**
 * ScottsTechX — chat / messaging routes.
 *
 *   GET  /api/v1/conversations                    inbox (buyer or seller side)
 *   POST /api/v1/conversations                    create or open a thread
 *   GET  /api/v1/conversations/:id/messages
 *   POST /api/v1/conversations/:id/messages
 *   POST /api/v1/conversations/:id/read           mark read receipts
 */
import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { getPool } from '../../db.js';
import { requireAuth, authedUser } from '../../auth.js';
import { NotFoundError, ConflictError } from '../../errors.js';

export default async function registerChatRoute(app: FastifyInstance) {
  const pool = getPool();

  app.get('/api/v1/conversations', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { rows } = await pool.query(
      `SELECT
         c.id,
         c.product_id AS "productId",
         c.last_message AS "lastMessage",
         c.last_time AS "lastTime",
         c.created_at AS "createdAt",
         CASE WHEN c.buyer_id = $1 THEN 'buyer' ELSE 'seller' END AS "mySide",
         json_build_object(
           'id', other.id,
           'name', COALESCE(s.store_name, other.display_name),
           'role', other.role
         ) AS "otherParty",
         p.title AS "productTitle",
         COALESCE(mr.last_read_at IS NOT NULL AND mr.last_read_at >= c.last_time, false) AS "readByMe"
       FROM conversations c
       JOIN users other ON other.id = CASE WHEN c.buyer_id = $1 THEN c.seller_id ELSE c.buyer_id END
       LEFT JOIN store_settings s ON s.user_id = other.id
       LEFT JOIN products p ON p.id = c.product_id
       LEFT JOIN message_reads mr ON mr.conversation_id = c.id AND mr.user_id = $1
       WHERE c.buyer_id = $1 OR c.seller_id = $1
       ORDER BY c.last_time DESC NULLS LAST`,
      [me.id]
    );
    const conversations = rows.map((r) => ({
      ...r,
      otherParty: typeof r.otherParty === 'string' ? JSON.parse(r.otherParty) : r.otherParty,
    }));
    // Unread counts per conversation.
    const { rows: unread } = await pool.query(
      `SELECT m.conversation_id AS id, COUNT(*)::int AS unread
       FROM messages m
       LEFT JOIN message_reads mr ON mr.conversation_id = m.conversation_id AND mr.user_id = $1
       WHERE m.sender_id <> $1
         AND (mr.last_read_at IS NULL OR m.created_at > mr.last_read_at)
         AND m.conversation_id = ANY($2::uuid[])
       GROUP BY m.conversation_id`,
      [me.id, conversations.map((c) => c.id)]
    );
    const unreadMap = new Map(unread.map((u) => [u.id, u.unread]));
    return {
      conversations: conversations.map((c) => ({ ...c, unread: unreadMap.get(c.id) ?? 0 })),
    };
  });

  app.post('/api/v1/conversations', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const body = z
      .object({ sellerId: z.string().min(1), productId: z.string().optional() })
      .parse(request.body);
    if (body.sellerId === me.id) throw new ConflictError('Cannot message yourself');
    const existing = await pool.query(
      `SELECT id FROM conversations
       WHERE buyer_id = $1 AND seller_id = $2
       ORDER BY last_time DESC NULLS LAST LIMIT 1`,
      [me.id, body.sellerId]
    );
    if (existing.rows[0]) return { conversation: { id: existing.rows[0].id, existing: true } };
    const { rows } = await pool.query(
      `INSERT INTO conversations (buyer_id, seller_id, product_id)
       VALUES ($1, $2, $3) RETURNING id`,
      [me.id, body.sellerId, body.productId ?? null]
    );
    return { conversation: { id: rows[0].id, existing: false } };
  });

  app.get('/api/v1/conversations/:id/messages', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { id } = request.params as { id: string };
    const conv = await pool.query(
      'SELECT id FROM conversations WHERE id = $1 AND (buyer_id = $2 OR seller_id = $2)',
      [id, me.id]
    );
    if (!conv.rows[0]) throw new NotFoundError('Conversation not found');
    const { rows } = await pool.query(
      `SELECT id, sender_id AS "senderId", text, image_url AS "imageUrl", created_at AS "createdAt"
       FROM messages WHERE conversation_id = $1 ORDER BY created_at ASC`,
      [id]
    );
    return { messages: rows };
  });

  app.post('/api/v1/conversations/:id/messages', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { id } = request.params as { id: string };
    const body = z.object({ text: z.string().min(1), imageUrl: z.string().optional() }).parse(request.body);
    const conv = await pool.query(
      'SELECT * FROM conversations WHERE id = $1 AND (buyer_id = $2 OR seller_id = $2)',
      [id, me.id]
    );
    if (!conv.rows[0]) throw new NotFoundError('Conversation not found');
    const { rows } = await pool.query(
      `INSERT INTO messages (conversation_id, sender_id, text, image_url)
       VALUES ($1, $2, $3, $4) RETURNING id, sender_id AS "senderId", text, image_url AS "imageUrl", created_at AS "createdAt"`,
      [id, me.id, body.text, body.imageUrl ?? null]
    );
    await pool.query(
      `UPDATE conversations SET last_message = $2, last_time = now() WHERE id = $1`,
      [id, body.text]
    );
    // Mark as read by the sender automatically.
    await pool.query(
      `INSERT INTO message_reads (conversation_id, user_id, last_read_at)
       VALUES ($1, $2, now())
       ON CONFLICT (conversation_id, user_id) DO UPDATE SET last_read_at = now()`,
      [id, me.id]
    );
    return { message: rows[0] };
  });

  app.post('/api/v1/conversations/:id/read', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { id } = request.params as { id: string };
    await pool.query(
      `INSERT INTO message_reads (conversation_id, user_id, last_read_at)
       VALUES ($1, $2, now())
       ON CONFLICT (conversation_id, user_id) DO UPDATE SET last_read_at = now()`,
      [id, me.id]
    );
    return { ok: true };
  });
}
