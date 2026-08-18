/**
 * ScottsTechX — notification fan-out.
 *
 * Every notification is (a) persisted in Postgres so the in-app + web
 * notification centres always show it, and (b) pushed to the user's registered
 * devices through Firebase Cloud Messaging when Firebase credentials exist.
 *
 * The DB write is the source of truth: FCM being unconfigured or failing never
 * breaks the calling flow — notifications simply stay in-app until credentials
 * are dropped in (12_Backend/secrets/firebase-admin-key.json).
 */
import type pg from 'pg';

export type NotificationType =
  | 'general'
  | 'new_product'
  | 'order_update'
  | 'message'
  | 'product_approved'
  | 'product_rejected'
  | 'product_pending'
  | 'support_reply'
  | 'price_drop';

export interface NotifyInput {
  userId: string;
  title: string;
  body: string;
  type?: NotificationType;
  /** Deep-link payload, e.g. { screen: 'product', id: '<uuid>' }. */
  data?: Record<string, string>;
  imageUrl?: string;
}

/** Which user_settings flag gates this notification type. */
function preferenceColumn(type: NotificationType): string | null {
  switch (type) {
    case 'message':
      return 'notify_messages';
    case 'order_update':
      return 'notify_order_updates';
    case 'new_product':
    case 'price_drop':
      return 'notify_marketing';
    default:
      return null; // account-critical: always delivered
  }
}

/** Persist one notification (respecting the user's preferences) + push it. */
export async function notify(db: pg.Pool, input: NotifyInput): Promise<string | null> {
  const type = input.type ?? 'general';

  const prefCol = preferenceColumn(type);
  if (prefCol) {
    const { rows } = await db.query(
      `SELECT COALESCE((SELECT ${prefCol} FROM user_settings WHERE user_id = $1), true) AS allowed`,
      [input.userId]
    );
    if (rows[0] && rows[0].allowed === false) return null;
  }

  const { rows } = await db.query(
    `INSERT INTO notifications (user_id, title, body, type, data, image_url)
     VALUES ($1, $2, $3, $4, $5::jsonb, $6)
     RETURNING id`,
    [
      input.userId,
      input.title,
      input.body,
      type,
      JSON.stringify(input.data ?? {}),
      input.imageUrl ?? '',
    ]
  );
  const id = rows[0].id as string;

  // Push is best-effort and never blocks the caller's response.
  void pushToDevices(db, input.userId, {
    title: input.title,
    body: input.body,
    imageUrl: input.imageUrl,
    data: { ...(input.data ?? {}), type, notificationId: id },
  }).catch(() => undefined);

  return id;
}

/** Fan a notification out to many users (favourite-seller broadcasts). */
export async function notifyMany(
  db: pg.Pool,
  userIds: string[],
  build: (userId: string) => NotifyInput
): Promise<number> {
  let delivered = 0;
  for (const userId of userIds) {
    const id = await notify(db, build(userId)).catch(() => null);
    if (id) delivered++;
  }
  return delivered;
}

/**
 * Notify every buyer who favourited this seller that a new product went live.
 * Called after admin approval (not on submission) so buyers never get pinged
 * about a listing they cannot open yet.
 */
export async function notifyFavoritesOfNewProduct(
  db: pg.Pool,
  sellerId: string,
  product: { id: string; title: string; imageUrl?: string; priceMinor?: number }
): Promise<number> {
  const { rows } = await db.query(
    `SELECT f.user_id AS "userId", COALESCE(s.store_name, u.display_name) AS "storeName"
     FROM favorite_sellers f
     JOIN users u ON u.id = f.seller_id
     LEFT JOIN store_settings s ON s.user_id = f.seller_id
     WHERE f.seller_id = $1`,
    [sellerId]
  );
  if (!rows.length) return 0;
  const storeName = rows[0].storeName || 'A store you follow';
  const price =
    typeof product.priceMinor === 'number'
      ? ` — UGX ${product.priceMinor.toLocaleString('en-UG')}`
      : '';

  return notifyMany(
    db,
    rows.map((r) => r.userId),
    (userId) => ({
      userId,
      title: `${storeName} just added a new product`,
      body: `${product.title}${price}. Tap to view it now.`,
      type: 'new_product',
      imageUrl: product.imageUrl,
      data: { screen: 'product', id: product.id, sellerId },
    })
  );
}

// ── FCM delivery ────────────────────────────────────────────────────────────

interface PushPayload {
  title: string;
  body: string;
  imageUrl?: string;
  data?: Record<string, string>;
}

/**
 * Send to every device token registered by the user.
 * Invalid/expired tokens are pruned automatically so the table stays clean.
 */
export async function pushToDevices(
  db: pg.Pool,
  userId: string,
  payload: PushPayload
): Promise<{ sent: number; failed: number; configured: boolean }> {
  const { rows } = await db.query('SELECT token FROM device_tokens WHERE user_id = $1', [userId]);
  const tokens = rows.map((r) => r.token as string);
  if (!tokens.length) return { sent: 0, failed: 0, configured: true };

  let messaging: any;
  try {
    const mod = await import('../../firebase/admin.js');
    messaging = mod.getMessaging?.();
    if (!messaging) return { sent: 0, failed: 0, configured: false };
  } catch {
    // Firebase not configured — the DB notification is still in place.
    return { sent: 0, failed: 0, configured: false };
  }

  // Data values must be strings for FCM.
  const data: Record<string, string> = {};
  for (const [k, v] of Object.entries(payload.data ?? {})) data[k] = String(v);

  try {
    const res = await messaging.sendEachForMulticast({
      tokens,
      notification: {
        title: payload.title,
        body: payload.body,
        ...(payload.imageUrl ? { imageUrl: payload.imageUrl } : {}),
      },
      data,
      android: {
        priority: 'high',
        notification: { channelId: 'scottstechx_default', sound: 'default' },
      },
    });

    const dead: string[] = [];
    res.responses.forEach((r: any, i: number) => {
      const code = r?.error?.code ?? '';
      if (
        !r.success &&
        (code.includes('registration-token-not-registered') || code.includes('invalid-argument'))
      ) {
        dead.push(tokens[i]);
      }
    });
    if (dead.length) {
      await db
        .query('DELETE FROM device_tokens WHERE token = ANY($1::text[])', [dead])
        .catch(() => undefined);
    }
    return { sent: res.successCount, failed: res.failureCount, configured: true };
  } catch {
    return { sent: 0, failed: tokens.length, configured: true };
  }
}

/** Register (or refresh) a device token for push. */
export async function registerDevice(
  db: pg.Pool,
  userId: string,
  token: string,
  platform: 'android' | 'ios' | 'web' = 'android'
) {
  await db.query(
    `INSERT INTO device_tokens (token, user_id, platform, last_seen)
     VALUES ($1, $2, $3, now())
     ON CONFLICT (token) DO UPDATE
       SET user_id = EXCLUDED.user_id, platform = EXCLUDED.platform, last_seen = now()`,
    [token, userId, platform]
  );
  return { ok: true };
}

export async function unregisterDevice(db: pg.Pool, userId: string, token: string) {
  await db.query('DELETE FROM device_tokens WHERE token = $1 AND user_id = $2', [token, userId]);
  return { ok: true };
}
