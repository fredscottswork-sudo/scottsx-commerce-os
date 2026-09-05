/**
 * ScottsTechX — chat / messaging routes.
 *
 *   GET    /api/v1/conversations                       inbox (buyer or seller side)
 *   POST   /api/v1/conversations                       create or open a thread
 *   GET    /api/v1/conversations/:id                   single thread header
 *   GET    /api/v1/conversations/:id/messages          full transcript
 *   POST   /api/v1/conversations/:id/messages          send text / image / offer
 *   POST   /api/v1/conversations/:id/read              mark read receipts
 *   POST   /api/v1/conversations/:id/typing            heartbeat for typing dots
 *   PATCH  /api/v1/conversations/:id/state             pin / archive / mute
 *   POST   /api/v1/conversations/:id/offers/:msgId     accept | decline | withdraw
 *   DELETE /api/v1/conversations/:id/messages/:msgId   retract own message
 *   GET    /api/v1/me/quick-replies                    saved canned responses
 *   POST   /api/v1/me/quick-replies
 *   DELETE /api/v1/me/quick-replies/:id
 *
 * Design notes
 *  - A conversation always has exactly two participants (buyer + seller), so
 *    "the other party" is unambiguous and read receipts stay cheap.
 *  - Offers live *inside* the transcript as messages of kind='offer'. Only the
 *    recipient may accept/decline; only the sender may withdraw. Accepting an
 *    offer supersedes every other pending offer in the thread so two accepted
 *    prices can never coexist.
 *  - Typing state is a heartbeat: a row is considered "typing" for 6 seconds.
 */
import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { getPool } from '../../db.js';
import { requireAuth, authedUser } from '../../auth.js';
import { NotFoundError, ConflictError, ForbiddenError, ValidationError } from '../../errors.js';
import { notify } from '../notifications/notify.service.js';

/** A message row as the API exposes it. */
const MESSAGE_COLUMNS = `
  m.id,
  m.sender_id      AS "senderId",
  m.conversation_id AS "conversationId",
  CASE WHEN m.deleted_at IS NOT NULL THEN '' ELSE m.text END AS text,
  CASE WHEN m.deleted_at IS NOT NULL THEN NULL ELSE m.image_url END AS "imageUrl",
  m.attachment_name AS "attachmentName",
  m.kind,
  m.product_id     AS "productId",
  m.offer_minor::int AS "offerMinor",
  m.offer_status   AS "offerStatus",
  m.offer_quantity AS "offerQuantity",
  m.reply_to_id    AS "replyToId",
  m.deleted_at     AS "deletedAt",
  m.created_at     AS "createdAt"
`;

/** Seconds a typing heartbeat stays "hot". */
const TYPING_TTL_SECONDS = 6;

export default async function registerChatRoute(app: FastifyInstance) {
  const pool = getPool();

  /** Load a conversation the caller is allowed to see, or throw. */
  async function requireMembership(convId: string, userId: string) {
    const { rows } = await pool.query(
      `SELECT id, buyer_id AS "buyerId", seller_id AS "sellerId", product_id AS "productId"
       FROM conversations
       WHERE id = $1 AND (buyer_id = $2 OR seller_id = $2)`,
      [convId, userId]
    );
    if (!rows[0]) throw new NotFoundError('Conversation not found');
    const conv = rows[0] as { id: string; buyerId: string; sellerId: string; productId: string | null };
    return { ...conv, otherId: conv.buyerId === userId ? conv.sellerId : conv.buyerId };
  }

  /** Push a notification to the other party unless they muted the thread. */
  async function notifyOther(
    convId: string,
    recipientId: string,
    title: string,
    body: string
  ) {
    const { rows } = await pool.query(
      `SELECT COALESCE(muted, false) AS muted
       FROM conversation_state WHERE conversation_id = $1 AND user_id = $2`,
      [convId, recipientId]
    );
    if (rows[0]?.muted) return;
    await notify(pool, {
      userId: recipientId,
      title,
      body: body.length > 140 ? `${body.slice(0, 137)}…` : body,
      type: 'message',
      data: { conversationId: convId },
    }).catch(() => undefined);
  }

  // ------------------------------------------------------------------ inbox
  app.get('/api/v1/conversations', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const query = z
      .object({
        filter: z.enum(['all', 'unread', 'archived', 'pinned', 'offers']).optional(),
        q: z.string().optional(),
      })
      .parse(request.query ?? {});
    const filter = query.filter ?? 'all';

    const { rows } = await pool.query(
      `SELECT
         c.id,
         c.product_id AS "productId",
         c.last_message AS "lastMessage",
         c.last_time AS "lastTime",
         c.created_at AS "createdAt",
         c.message_count AS "messageCount",
         c.last_sender_id AS "lastSenderId",
         CASE WHEN c.buyer_id = $1 THEN 'buyer' ELSE 'seller' END AS "mySide",
         json_build_object(
           'id', other.id,
           'name', COALESCE(NULLIF(s.store_name, ''), other.display_name),
           'role', other.role,
           'photoUrl', COALESCE(NULLIF(s.store_logo_url, ''), other.profile_photo_url),
           'verified', COALESCE(s.verified, false)
         ) AS "otherParty",
         p.title AS "productTitle",
         COALESCE(NULLIF(p.image_url, ''), (SELECT pm.url FROM product_media pm WHERE pm.product_id = p.id ORDER BY pm.sort_order ASC LIMIT 1)) AS "productImageUrl",
         p.price_minor::int AS "productPriceMinor",
         COALESCE(st.pinned, false) AS pinned,
         COALESCE(st.archived, false) AS archived,
         COALESCE(st.muted, false) AS muted,
         COALESCE(mr.last_read_at IS NOT NULL AND mr.last_read_at >= c.last_time, false) AS "readByMe",
         (SELECT COUNT(*)::int FROM messages om
            WHERE om.conversation_id = c.id AND om.offer_status = 'pending') AS "pendingOffers"
       FROM conversations c
       JOIN users other ON other.id = CASE WHEN c.buyer_id = $1 THEN c.seller_id ELSE c.buyer_id END
       LEFT JOIN store_settings s ON s.user_id = other.id
       LEFT JOIN products p ON p.id = c.product_id
       LEFT JOIN message_reads mr ON mr.conversation_id = c.id AND mr.user_id = $1
       LEFT JOIN conversation_state st ON st.conversation_id = c.id AND st.user_id = $1
       WHERE (c.buyer_id = $1 OR c.seller_id = $1)
       ORDER BY COALESCE(st.pinned, false) DESC, c.last_time DESC NULLS LAST`,
      [me.id]
    );

    const conversations = rows.map((r) => ({
      ...r,
      otherParty: typeof r.otherParty === 'string' ? JSON.parse(r.otherParty) : r.otherParty,
    }));

    // Unread counts per conversation.
    const ids = conversations.map((c) => c.id);
    const unreadMap = new Map<string, number>();
    if (ids.length) {
      const { rows: unread } = await pool.query(
        `SELECT m.conversation_id AS id, COUNT(*)::int AS unread
         FROM messages m
         LEFT JOIN message_reads mr ON mr.conversation_id = m.conversation_id AND mr.user_id = $1
         WHERE m.sender_id <> $1
           AND m.deleted_at IS NULL
           AND (mr.last_read_at IS NULL OR m.created_at > mr.last_read_at)
           AND m.conversation_id = ANY($2::uuid[])
         GROUP BY m.conversation_id`,
        [me.id, ids]
      );
      for (const u of unread) unreadMap.set(u.id, u.unread);
    }

    let list = conversations.map((c) => ({ ...c, unread: unreadMap.get(c.id) ?? 0 }));

    // Filters are applied in JS so the counts below always describe the whole
    // inbox rather than the filtered slice.
    const totalUnread = list.reduce((n, c) => n + (c.archived ? 0 : c.unread), 0);
    const counts = {
      all: list.filter((c) => !c.archived).length,
      unread: list.filter((c) => !c.archived && c.unread > 0).length,
      pinned: list.filter((c) => c.pinned && !c.archived).length,
      archived: list.filter((c) => c.archived).length,
      offers: list.filter((c) => !c.archived && c.pendingOffers > 0).length,
    };

    if (filter === 'archived') list = list.filter((c) => c.archived);
    else if (filter === 'unread') list = list.filter((c) => !c.archived && c.unread > 0);
    else if (filter === 'pinned') list = list.filter((c) => c.pinned && !c.archived);
    else if (filter === 'offers') list = list.filter((c) => !c.archived && c.pendingOffers > 0);
    else list = list.filter((c) => !c.archived);

    const term = (query.q ?? '').trim().toLowerCase();
    if (term) {
      list = list.filter(
        (c) =>
          String(c.otherParty?.name ?? '').toLowerCase().includes(term) ||
          String(c.lastMessage ?? '').toLowerCase().includes(term) ||
          String(c.productTitle ?? '').toLowerCase().includes(term)
      );
    }

    return { conversations: list, counts, totalUnread, filter };
  });

  // --------------------------------------------------------------- open one
  app.post('/api/v1/conversations', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const body = z
      .object({ sellerId: z.string().min(1), productId: z.string().optional() })
      .parse(request.body);
    if (body.sellerId === me.id) throw new ConflictError('Cannot message yourself');

    const seller = await pool.query('SELECT id FROM users WHERE id = $1', [body.sellerId]);
    if (!seller.rows[0]) throw new NotFoundError('Seller not found');

    const existing = await pool.query(
      `SELECT id FROM conversations
       WHERE buyer_id = $1 AND seller_id = $2
       ORDER BY last_time DESC NULLS LAST LIMIT 1`,
      [me.id, body.sellerId]
    );
    if (existing.rows[0]) {
      // Re-opening from a different product re-points the thread context.
      if (body.productId) {
        await pool.query('UPDATE conversations SET product_id = $2 WHERE id = $1', [
          existing.rows[0].id,
          body.productId,
        ]);
      }
      return { conversation: { id: existing.rows[0].id, existing: true } };
    }

    const { rows } = await pool.query(
      `INSERT INTO conversations (buyer_id, seller_id, product_id)
       VALUES ($1, $2, $3) RETURNING id`,
      [me.id, body.sellerId, body.productId ?? null]
    );
    return { conversation: { id: rows[0].id, existing: false } };
  });

  // ------------------------------------------------------------ thread head
  app.get('/api/v1/conversations/:id', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { id } = request.params as { id: string };
    await requireMembership(id, me.id);

    const { rows } = await pool.query(
      `SELECT
         c.id,
         c.product_id AS "productId",
         c.last_message AS "lastMessage",
         c.last_time AS "lastTime",
         c.message_count AS "messageCount",
         CASE WHEN c.buyer_id = $1 THEN 'buyer' ELSE 'seller' END AS "mySide",
         json_build_object(
           'id', other.id,
           'name', COALESCE(NULLIF(s.store_name, ''), other.display_name),
           'role', other.role,
           'photoUrl', COALESCE(NULLIF(s.store_logo_url, ''), other.profile_photo_url),
           'verified', COALESCE(s.verified, false),
           'location', COALESCE(NULLIF(s.city, ''), other.city)
         ) AS "otherParty",
         p.title AS "productTitle",
         COALESCE(NULLIF(p.image_url, ''), (SELECT pm.url FROM product_media pm WHERE pm.product_id = p.id ORDER BY pm.sort_order ASC LIMIT 1)) AS "productImageUrl",
         p.price_minor::int AS "productPriceMinor",
         COALESCE(st.pinned, false) AS pinned,
         COALESCE(st.archived, false) AS archived,
         COALESCE(st.muted, false) AS muted,
         (SELECT MAX(mr2.last_read_at) FROM message_reads mr2
            WHERE mr2.conversation_id = c.id AND mr2.user_id <> $1) AS "otherLastReadAt",
         (SELECT EXISTS (
            SELECT 1 FROM typing_state t
            WHERE t.conversation_id = c.id AND t.user_id <> $1
              AND t.typing_at > now() - interval '${TYPING_TTL_SECONDS} seconds'
          )) AS "otherTyping"
       FROM conversations c
       JOIN users other ON other.id = CASE WHEN c.buyer_id = $1 THEN c.seller_id ELSE c.buyer_id END
       LEFT JOIN store_settings s ON s.user_id = other.id
       LEFT JOIN products p ON p.id = c.product_id
       LEFT JOIN conversation_state st ON st.conversation_id = c.id AND st.user_id = $1
       WHERE c.id = $2`,
      [me.id, id]
    );

    const conv = rows[0];
    return {
      conversation: {
        ...conv,
        otherParty: typeof conv.otherParty === 'string' ? JSON.parse(conv.otherParty) : conv.otherParty,
      },
    };
  });

  // -------------------------------------------------------------- transcript
  app.get('/api/v1/conversations/:id/messages', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { id } = request.params as { id: string };
    await requireMembership(id, me.id);

    const { rows } = await pool.query(
      `SELECT ${MESSAGE_COLUMNS},
              p.title AS "productTitle",
              COALESCE(NULLIF(p.image_url, ''), (SELECT pm.url FROM product_media pm WHERE pm.product_id = p.id ORDER BY pm.sort_order ASC LIMIT 1)) AS "productImageUrl",
              p.price_minor::int AS "productPriceMinor"
       FROM messages m
       LEFT JOIN products p ON p.id = m.product_id
       WHERE m.conversation_id = $1
       ORDER BY m.created_at ASC`,
      [id]
    );

    // How far the other side has read — drives the ✓✓ receipts.
    const receipt = await pool.query(
      `SELECT MAX(last_read_at) AS "otherLastReadAt"
       FROM message_reads WHERE conversation_id = $1 AND user_id <> $2`,
      [id, me.id]
    );
    const typing = await pool.query(
      `SELECT EXISTS (
         SELECT 1 FROM typing_state
         WHERE conversation_id = $1 AND user_id <> $2
           AND typing_at > now() - interval '${TYPING_TTL_SECONDS} seconds'
       ) AS "otherTyping"`,
      [id, me.id]
    );

    const otherLastReadAt = receipt.rows[0]?.otherLastReadAt ?? null;
    const messages = rows.map((m) => ({
      ...m,
      readByOther:
        m.senderId === me.id &&
        !!otherLastReadAt &&
        new Date(m.createdAt).getTime() <= new Date(otherLastReadAt).getTime(),
    }));

    return {
      messages,
      otherLastReadAt,
      otherTyping: typing.rows[0]?.otherTyping ?? false,
    };
  });

  // -------------------------------------------------------------------- send
  app.post('/api/v1/conversations/:id/messages', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { id } = request.params as { id: string };
    const body = z
      .object({
        text: z.string().max(4000).optional(),
        imageUrl: z.string().optional(),
        attachmentName: z.string().max(200).optional(),
        kind: z.enum(['text', 'image', 'offer']).optional(),
        productId: z.string().uuid().optional(),
        offerMinor: z.number().int().positive().optional(),
        offerQuantity: z.number().int().positive().max(10000).optional(),
        replyToId: z.string().uuid().optional(),
      })
      .parse(request.body);

    const conv = await requireMembership(id, me.id);
    const kind = body.kind ?? (body.imageUrl && !body.text ? 'image' : 'text');

    if (kind === 'offer' && !body.offerMinor) {
      throw new ValidationError('An offer needs a price');
    }
    if (kind !== 'offer' && !body.text?.trim() && !body.imageUrl) {
      throw new ValidationError('Message cannot be empty');
    }

    // The product an offer refers to: explicit, else the thread's product.
    const productId = body.productId ?? conv.productId ?? null;
    if (kind === 'offer' && !productId) {
      throw new ValidationError('Offers must reference a product');
    }

    const offerText =
      kind === 'offer'
        ? body.text?.trim() ||
          `Offer: UGX ${(body.offerMinor! / 100).toLocaleString('en-UG')}${
            body.offerQuantity && body.offerQuantity > 1 ? ` × ${body.offerQuantity}` : ''
          }`
        : body.text?.trim() ?? '';

    const { rows } = await pool.query(
      `INSERT INTO messages
         (conversation_id, sender_id, text, image_url, attachment_name, kind,
          product_id, offer_minor, offer_status, offer_quantity, reply_to_id)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)
       RETURNING ${MESSAGE_COLUMNS.replace(/\bm\./g, '')}`,
      [
        id,
        me.id,
        offerText,
        body.imageUrl ?? null,
        body.attachmentName ?? null,
        kind,
        kind === 'offer' ? productId : body.productId ?? null,
        kind === 'offer' ? body.offerMinor : null,
        kind === 'offer' ? 'pending' : null,
        body.offerQuantity ?? 1,
        body.replyToId ?? null,
      ]
    );

    const preview = kind === 'image' ? '📷 Photo' : offerText;
    await pool.query(
      `UPDATE conversations
       SET last_message = $2, last_time = now(), last_sender_id = $3,
           message_count = message_count + 1
       WHERE id = $1`,
      [id, preview, me.id]
    );

    // Sending implies reading everything before it.
    await pool.query(
      `INSERT INTO message_reads (conversation_id, user_id, last_read_at)
       VALUES ($1, $2, now())
       ON CONFLICT (conversation_id, user_id) DO UPDATE SET last_read_at = now()`,
      [id, me.id]
    );
    // Sending clears your own typing indicator.
    await pool.query('DELETE FROM typing_state WHERE conversation_id = $1 AND user_id = $2', [
      id,
      me.id,
    ]);
    // An archived thread resurfaces for both sides on new activity.
    await pool.query(
      `UPDATE conversation_state SET archived = false, updated_at = now()
       WHERE conversation_id = $1 AND archived = true`,
      [id]
    );

    await notifyOther(id, conv.otherId, `New message from ${me.name || 'a user'}`, preview);

    return { message: rows[0] };
  });

  // -------------------------------------------------------------------- read
  app.post('/api/v1/conversations/:id/read', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { id } = request.params as { id: string };
    await requireMembership(id, me.id);
    await pool.query(
      `INSERT INTO message_reads (conversation_id, user_id, last_read_at)
       VALUES ($1, $2, now())
       ON CONFLICT (conversation_id, user_id) DO UPDATE SET last_read_at = now()`,
      [id, me.id]
    );
    return { ok: true };
  });

  // ------------------------------------------------------------------ typing
  app.post('/api/v1/conversations/:id/typing', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { id } = request.params as { id: string };
    await requireMembership(id, me.id);
    const body = z.object({ typing: z.boolean().optional() }).parse(request.body ?? {});
    if (body.typing === false) {
      await pool.query('DELETE FROM typing_state WHERE conversation_id = $1 AND user_id = $2', [
        id,
        me.id,
      ]);
    } else {
      await pool.query(
        `INSERT INTO typing_state (conversation_id, user_id, typing_at)
         VALUES ($1, $2, now())
         ON CONFLICT (conversation_id, user_id) DO UPDATE SET typing_at = now()`,
        [id, me.id]
      );
    }
    return { ok: true };
  });

  // ---------------------------------------------------- pin / archive / mute
  app.patch('/api/v1/conversations/:id/state', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { id } = request.params as { id: string };
    await requireMembership(id, me.id);
    const body = z
      .object({
        pinned: z.boolean().optional(),
        archived: z.boolean().optional(),
        muted: z.boolean().optional(),
      })
      .parse(request.body ?? {});

    if (body.pinned === undefined && body.archived === undefined && body.muted === undefined) {
      throw new ValidationError('Nothing to update');
    }

    const { rows } = await pool.query(
      `INSERT INTO conversation_state (conversation_id, user_id, pinned, archived, muted)
       VALUES ($1, $2, COALESCE($3, false), COALESCE($4, false), COALESCE($5, false))
       ON CONFLICT (conversation_id, user_id) DO UPDATE SET
         pinned   = COALESCE($3, conversation_state.pinned),
         archived = COALESCE($4, conversation_state.archived),
         muted    = COALESCE($5, conversation_state.muted),
         updated_at = now()
       RETURNING pinned, archived, muted`,
      [id, me.id, body.pinned ?? null, body.archived ?? null, body.muted ?? null]
    );
    return { state: rows[0] };
  });

  // ------------------------------------------------------------------ offers
  app.post(
    '/api/v1/conversations/:id/offers/:msgId',
    { preHandler: requireAuth },
    async (request) => {
      const me = authedUser(request);
      const { id, msgId } = request.params as { id: string; msgId: string };
      const body = z
        .object({ action: z.enum(['accept', 'decline', 'withdraw']) })
        .parse(request.body);
      const conv = await requireMembership(id, me.id);

      const { rows } = await pool.query(
        `SELECT id, sender_id AS "senderId", offer_status AS "offerStatus",
                offer_minor::int AS "offerMinor", offer_quantity AS "offerQuantity",
                product_id AS "productId"
         FROM messages
         WHERE id = $1 AND conversation_id = $2 AND kind = 'offer'`,
        [msgId, id]
      );
      const offer = rows[0];
      if (!offer) throw new NotFoundError('Offer not found');
      if (offer.offerStatus !== 'pending') {
        throw new ConflictError(`This offer was already ${offer.offerStatus}`);
      }

      const mine = offer.senderId === me.id;
      if (body.action === 'withdraw' && !mine) {
        throw new ForbiddenError('Only the sender can withdraw an offer');
      }
      if (body.action !== 'withdraw' && mine) {
        throw new ForbiddenError('You cannot respond to your own offer');
      }

      const nextStatus =
        body.action === 'accept' ? 'accepted' : body.action === 'decline' ? 'declined' : 'withdrawn';

      await pool.query('UPDATE messages SET offer_status = $2 WHERE id = $1', [msgId, nextStatus]);

      // Accepting settles the negotiation: everything else pending is void.
      if (body.action === 'accept') {
        await pool.query(
          `UPDATE messages SET offer_status = 'declined'
           WHERE conversation_id = $1 AND offer_status = 'pending' AND id <> $2`,
          [id, msgId]
        );
      }

      const priceText = `UGX ${(Number(offer.offerMinor) / 100).toLocaleString('en-UG')}`;
      const systemText =
        body.action === 'accept'
          ? `Offer accepted at ${priceText}. The buyer can now check out at this price.`
          : body.action === 'decline'
            ? `Offer of ${priceText} was declined.`
            : `Offer of ${priceText} was withdrawn.`;

      const sys = await pool.query(
        `INSERT INTO messages (conversation_id, sender_id, text, kind, product_id)
         VALUES ($1, $2, $3, 'system', $4)
         RETURNING ${MESSAGE_COLUMNS.replace(/\bm\./g, '')}`,
        [id, me.id, systemText, offer.productId]
      );

      await pool.query(
        `UPDATE conversations
         SET last_message = $2, last_time = now(), last_sender_id = $3,
             message_count = message_count + 1
         WHERE id = $1`,
        [id, systemText, me.id]
      );

      await notifyOther(id, conv.otherId, 'Offer update', systemText);

      return { ok: true, status: nextStatus, message: sys.rows[0] };
    }
  );

  // ----------------------------------------------------------- retract a msg
  app.delete(
    '/api/v1/conversations/:id/messages/:msgId',
    { preHandler: requireAuth },
    async (request) => {
      const me = authedUser(request);
      const { id, msgId } = request.params as { id: string; msgId: string };
      await requireMembership(id, me.id);

      const { rows } = await pool.query(
        `SELECT id, sender_id AS "senderId", deleted_at AS "deletedAt"
         FROM messages WHERE id = $1 AND conversation_id = $2`,
        [msgId, id]
      );
      if (!rows[0]) throw new NotFoundError('Message not found');
      if (rows[0].senderId !== me.id) {
        throw new ForbiddenError('You can only delete your own messages');
      }
      if (rows[0].deletedAt) return { ok: true, alreadyDeleted: true };

      await pool.query(
        `UPDATE messages SET deleted_at = now(), text = '', image_url = NULL WHERE id = $1`,
        [msgId]
      );
      return { ok: true };
    }
  );

  // ----------------------------------------------------------- quick replies
  app.get('/api/v1/me/quick-replies', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { rows } = await pool.query(
      `SELECT id, text, sort_order AS "sortOrder"
       FROM quick_replies WHERE user_id = $1
       ORDER BY sort_order ASC, created_at ASC`,
      [me.id]
    );
    return { quickReplies: rows };
  });

  app.post('/api/v1/me/quick-replies', { preHandler: requireAuth }, async (request, reply) => {
    const me = authedUser(request);
    const body = z
      .object({ text: z.string().min(1).max(300), sortOrder: z.number().int().optional() })
      .parse(request.body);
    const count = await pool.query('SELECT COUNT(*)::int AS n FROM quick_replies WHERE user_id = $1', [
      me.id,
    ]);
    if (count.rows[0].n >= 20) throw new ConflictError('You can save up to 20 quick replies');
    const { rows } = await pool.query(
      `INSERT INTO quick_replies (user_id, text, sort_order)
       VALUES ($1, $2, $3)
       RETURNING id, text, sort_order AS "sortOrder"`,
      [me.id, body.text.trim(), body.sortOrder ?? count.rows[0].n]
    );
    reply.code(201);
    return { quickReply: rows[0] };
  });

  app.delete('/api/v1/me/quick-replies/:id', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { id } = request.params as { id: string };
    const { rowCount } = await pool.query(
      'DELETE FROM quick_replies WHERE id = $1 AND user_id = $2',
      [id, me.id]
    );
    if (!rowCount) throw new NotFoundError('Quick reply not found');
    return { ok: true };
  });
}
