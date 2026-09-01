/**
 * ScottsTechX — seller store settings (9 sections).
 *
 *   GET   /api/v1/seller/store-settings   (auth, seller)
 *   PATCH /api/v1/seller/store-settings   (auth, seller)
 *
 * PATCH accepts camelCase field names; unknown-but-known keys are ignored
 * silently so the Android client can send whole sections at once.
 */
import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { getPool } from '../../db.js';
import { requireAuth, requireSeller } from '../../auth.js';
import { NotFoundError } from '../../errors.js';

/**
 * camelCase key -> column name. Every setting the app can edit lives here.
 */
const FIELD_MAP: Record<string, string> = {
  storeName: 'store_name',
  storeDescription: 'store_description',
  storeLogoUrl: 'store_logo_url',
  legalName: 'legal_name',
  tin: 'tin',
  businessEmail: 'business_email',
  businessPhone: 'business_phone',
  address: 'address',
  pickupInstructions: 'pickup_instructions',
  serviceRadiusKm: 'service_radius_km',
  deliveryFeeUgx: 'delivery_fee_ugx',
  freeAboveUgx: 'free_above_ugx',
  codEnabled: 'cod_enabled',
  momoNumber: 'momo_number',
  bankName: 'bank_name',
  bankAccount: 'bank_account',
  notifOrderUpdates: 'notif_order_updates',
  notifBuyerMessages: 'notif_buyer_messages',
  notifMarketing: 'notif_marketing',
  notifWeeklyDigest: 'notif_weekly_digest',
  twoFactorEnabled: 'two_factor_enabled',
  returnsWindowDays: 'returns_window_days',
  refundPolicy: 'refund_policy',
  terms: 'terms',
  contactEmail: 'contact_email',
  contactPhone: 'contact_phone',
  city: 'city',
  // `verified` and `rating` are platform-owned facts. Sellers may see them,
  // but must never be able to award themselves verification or alter reviews.
  lat: 'lat',
  lng: 'lng',
};

const patchSchema = z
  .object({
    storeName: z.string().optional(),
    storeDescription: z.string().optional(),
    storeLogoUrl: z.string().optional(),
    legalName: z.string().optional(),
    tin: z.string().optional(),
    businessEmail: z.string().optional(),
    businessPhone: z.string().optional(),
    address: z.string().optional(),
    pickupInstructions: z.string().optional(),
    serviceRadiusKm: z.number().int().min(0).max(1000).optional(),
    deliveryFeeUgx: z.number().int().nonnegative().optional(),
    freeAboveUgx: z.number().int().nonnegative().optional(),
    codEnabled: z.boolean().optional(),
    momoNumber: z.string().optional(),
    bankName: z.string().optional(),
    bankAccount: z.string().optional(),
    notifOrderUpdates: z.boolean().optional(),
    notifBuyerMessages: z.boolean().optional(),
    notifMarketing: z.boolean().optional(),
    notifWeeklyDigest: z.boolean().optional(),
    twoFactorEnabled: z.boolean().optional(),
    returnsWindowDays: z.number().int().min(0).max(365).optional(),
    refundPolicy: z.string().optional(),
    terms: z.string().optional(),
    contactEmail: z.string().optional(),
    contactPhone: z.string().optional(),
    city: z.string().optional(),
    lat: z.number().min(-90).max(90).optional(),
    lng: z.number().min(-180).max(180).optional(),
  })
  .passthrough(); // ignore unknown fields rather than failing

const COLUMNS = Object.keys(FIELD_MAP).map((k) => FIELD_MAP[k]);

function rowToSettings(row: any) {
  if (!row) return null;
  return {
    storeName: row.store_name ?? '',
    storeDescription: row.store_description ?? '',
    storeLogoUrl: row.store_logo_url ?? '',
    legalName: row.legal_name ?? '',
    tin: row.tin ?? '',
    businessEmail: row.business_email ?? '',
    businessPhone: row.business_phone ?? '',
    address: row.address ?? '',
    pickupInstructions: row.pickup_instructions ?? '',
    serviceRadiusKm: row.service_radius_km ?? 20,
    deliveryFeeUgx: row.delivery_fee_ugx ?? 0,
    freeAboveUgx: row.free_above_ugx ?? 0,
    codEnabled: !!row.cod_enabled,
    momoNumber: row.momo_number ?? '',
    bankName: row.bank_name ?? '',
    bankAccount: row.bank_account ?? '',
    notifOrderUpdates: !!row.notif_order_updates,
    notifBuyerMessages: !!row.notif_buyer_messages,
    notifMarketing: !!row.notif_marketing,
    notifWeeklyDigest: !!row.notif_weekly_digest,
    twoFactorEnabled: !!row.two_factor_enabled,
    returnsWindowDays: row.returns_window_days ?? 7,
    refundPolicy: row.refund_policy ?? '',
    terms: row.terms ?? '',
    contactEmail: row.contact_email ?? '',
    contactPhone: row.contact_phone ?? '',
    city: row.city ?? '',
    verified: !!row.verified,
    rating: row.rating ? Number(row.rating) : 0,
    lat: row.lat === null || row.lat === undefined ? null : Number(row.lat),
    lng: row.lng === null || row.lng === undefined ? null : Number(row.lng),
    updatedAt: row.updated_at ?? null,
  };
}

export default async function registerStoreSettingsRoute(app: FastifyInstance) {
  const pool = getPool();

  app.get('/api/v1/seller/store-settings', { preHandler: requireAuth }, async (request) => {
    const seller = requireSeller(request);
    const { rows } = await pool.query('SELECT * FROM store_settings WHERE user_id = $1', [seller.id]);
    if (!rows[0]) throw new NotFoundError('Store settings not found — create your store first');
    return { settings: rowToSettings(rows[0]) };
  });

  app.patch('/api/v1/seller/store-settings', { preHandler: requireAuth }, async (request) => {
    const seller = requireSeller(request);
    const body = patchSchema.parse(request.body);

    const current = await pool.query('SELECT * FROM store_settings WHERE user_id = $1', [seller.id]);
    if (!current.rows[0]) {
      await pool.query(
        `INSERT INTO store_settings (user_id, store_name) VALUES ($1, $2) ON CONFLICT (user_id) DO NOTHING`,
        [seller.id, seller.name || 'My Store']
      );
    }

    const sets: string[] = [];
    const values: any[] = [seller.id];
    for (const [camel, col] of Object.entries(FIELD_MAP)) {
      if (camel in body && body[camel] !== undefined) {
        values.push(body[camel]);
        sets.push(`${col} = $${values.length}`);
      }
    }
    if (sets.length > 0) {
      sets.push('updated_at = now()');
      await pool.query(
        `UPDATE store_settings SET ${sets.join(', ')} WHERE user_id = $1`,
        values
      );
    }

    const { rows } = await pool.query('SELECT * FROM store_settings WHERE user_id = $1', [seller.id]);
    return { settings: rowToSettings(rows[0]) };
  });
}

export { COLUMNS };
