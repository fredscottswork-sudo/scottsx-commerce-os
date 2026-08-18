/**
 * ScottsTechX — support desk (user side), with two modes:
 *
 *   AI mode    — the Support agent answers instantly from the live catalog and
 *                platform knowledge; the exchange is stored as a ticket thread
 *                so a human can take over later without losing context.
 *   Admin mode — the ticket goes to the human support queue (admin console),
 *                and the user is notified when an admin replies.
 *
 *   GET    /api/v1/me/support/threads
 *   POST   /api/v1/me/support/threads          { subject, message, mode }
 *   GET    /api/v1/me/support/threads/:id
 *   POST   /api/v1/me/support/threads/:id/reply { body }
 *   POST   /api/v1/me/support/threads/:id/escalate
 *   POST   /api/v1/me/support/threads/:id/close
 */
import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { getPool } from '../../db.js';
import { requireAuth, authedUser } from '../../auth.js';
import { NotFoundError } from '../../errors.js';
import { ask } from '../ai/assistant.service.js';
import { notify } from '../notifications/notify.service.js';

export default async function registerSupportRoute(app: FastifyInstance) {
  const pool = getPool();

  app.get('/api/v1/me/support/threads', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { rows } = await pool.query(
      `SELECT t.id, t.subject, t.message, t.status, t.mode,
              t.created_at AS "createdAt", t.updated_at AS "updatedAt",
              (SELECT COUNT(*)::int FROM support_replies r WHERE r.ticket_id = t.id) AS "replyCount",
              (SELECT r.body FROM support_replies r WHERE r.ticket_id = t.id
                ORDER BY r.created_at DESC LIMIT 1) AS "lastReply"
       FROM support_tickets t
       WHERE t.user_id = $1
       ORDER BY t.updated_at DESC, t.created_at DESC`,
      [me.id]
    );
    return { threads: rows };
  });

  app.post('/api/v1/me/support/threads', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const body = z
      .object({
        subject: z.string().min(1),
        message: z.string().min(1),
        mode: z.enum(['admin', 'ai']).optional().default('ai'),
      })
      .parse(request.body);

    const { rows } = await pool.query(
      `INSERT INTO support_tickets (user_id, subject, message, status, mode)
       VALUES ($1,$2,$3,'open',$4)
       RETURNING id, subject, message, status, mode, created_at AS "createdAt"`,
      [me.id, body.subject, body.message, body.mode]
    );
    const ticket = rows[0];

    let aiReply: string | null = null;
    if (body.mode === 'ai') {
      const result = await ask({
        db: pool,
        prompt: `${body.subject}\n\n${body.message}`,
        screen: 'support',
        agent: 'support',
        role: me.role,
        userId: me.id,
      });
      aiReply = result.text;
      await pool.query(
        `INSERT INTO support_replies (ticket_id, author_role, body) VALUES ($1,'ai',$2)`,
        [ticket.id, aiReply]
      );
      await pool.query(`UPDATE support_tickets SET status = 'answered', updated_at = now() WHERE id = $1`, [
        ticket.id,
      ]);
    } else {
      const admins = await pool.query(`SELECT id FROM users WHERE role = 'admin'`);
      for (const a of admins.rows) {
        await notify(pool, {
          userId: a.id,
          title: 'New support ticket',
          body: `${me.name || me.email}: ${body.subject}`,
          type: 'general',
          data: { screen: 'admin_support', id: ticket.id },
        }).catch(() => undefined);
      }
    }

    return { thread: { ...ticket, status: aiReply ? 'answered' : 'open' }, aiReply };
  });

  app.get('/api/v1/me/support/threads/:id', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { id } = request.params as { id: string };
    const t = await pool.query(
      `SELECT id, subject, message, status, mode, created_at AS "createdAt", updated_at AS "updatedAt"
       FROM support_tickets WHERE id = $1 AND user_id = $2`,
      [id, me.id]
    );
    if (!t.rows[0]) throw new NotFoundError('Ticket not found');
    const replies = await pool.query(
      `SELECT r.id, r.body, r.author_role AS "authorRole", r.created_at AS "createdAt",
              COALESCE(u.display_name, CASE WHEN r.author_role = 'ai' THEN 'ScottsTechX AI' ELSE 'Support' END) AS "authorName"
       FROM support_replies r LEFT JOIN users u ON u.id = r.author_id
       WHERE r.ticket_id = $1 ORDER BY r.created_at ASC`,
      [id]
    );
    return { thread: t.rows[0], replies: replies.rows };
  });

  app.post('/api/v1/me/support/threads/:id/reply', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { id } = request.params as { id: string };
    const { body } = z.object({ body: z.string().min(1) }).parse(request.body);

    const t = await pool.query('SELECT id, mode, subject FROM support_tickets WHERE id = $1 AND user_id = $2', [
      id,
      me.id,
    ]);
    if (!t.rows[0]) throw new NotFoundError('Ticket not found');

    await pool.query(
      `INSERT INTO support_replies (ticket_id, author_id, author_role, body) VALUES ($1,$2,'user',$3)`,
      [id, me.id, body]
    );
    await pool.query(`UPDATE support_tickets SET status = 'open', updated_at = now() WHERE id = $1`, [id]);

    let aiReply: string | null = null;
    if (t.rows[0].mode === 'ai') {
      const history = await pool.query(
        `SELECT author_role, body FROM support_replies WHERE ticket_id = $1 ORDER BY created_at ASC LIMIT 12`,
        [id]
      );
      const result = await ask({
        db: pool,
        prompt: body,
        screen: 'support',
        agent: 'support',
        role: me.role,
        userId: me.id,
        history: history.rows.map((r) => ({
          role: r.author_role === 'user' ? ('user' as const) : ('assistant' as const),
          content: r.body,
        })),
      });
      aiReply = result.text;
      await pool.query(`INSERT INTO support_replies (ticket_id, author_role, body) VALUES ($1,'ai',$2)`, [
        id,
        aiReply,
      ]);
      await pool.query(`UPDATE support_tickets SET status = 'answered', updated_at = now() WHERE id = $1`, [id]);
    } else {
      const admins = await pool.query(`SELECT id FROM users WHERE role = 'admin'`);
      for (const a of admins.rows) {
        await notify(pool, {
          userId: a.id,
          title: 'Support ticket updated',
          body: `${me.name || me.email} replied: ${body.slice(0, 90)}`,
          type: 'general',
          data: { screen: 'admin_support', id },
        }).catch(() => undefined);
      }
    }
    return { ok: true, aiReply };
  });

  /** Hand an AI thread over to a human without losing the transcript. */
  app.post('/api/v1/me/support/threads/:id/escalate', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { id } = request.params as { id: string };
    const t = await pool.query('SELECT id, subject FROM support_tickets WHERE id = $1 AND user_id = $2', [
      id,
      me.id,
    ]);
    if (!t.rows[0]) throw new NotFoundError('Ticket not found');

    await pool.query(
      `UPDATE support_tickets SET mode = 'admin', status = 'open', updated_at = now() WHERE id = $1`,
      [id]
    );
    await pool.query(
      `INSERT INTO support_replies (ticket_id, author_role, body)
       VALUES ($1,'ai','This conversation has been escalated to the ScottsTechX support team. A human agent will reply here shortly.')`,
      [id]
    );
    const admins = await pool.query(`SELECT id FROM users WHERE role = 'admin'`);
    for (const a of admins.rows) {
      await notify(pool, {
        userId: a.id,
        title: 'Ticket escalated to human support',
        body: `${me.name || me.email}: ${t.rows[0].subject}`,
        type: 'general',
        data: { screen: 'admin_support', id },
      }).catch(() => undefined);
    }
    return { ok: true, mode: 'admin' };
  });

  app.post('/api/v1/me/support/threads/:id/close', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { id } = request.params as { id: string };
    const res = await pool.query(
      `UPDATE support_tickets SET status = 'closed', updated_at = now()
       WHERE id = $1 AND user_id = $2 RETURNING id`,
      [id, me.id]
    );
    if (!res.rows[0]) throw new NotFoundError('Ticket not found');
    return { ok: true };
  });
}
