/**
 * ScottsTechX — location endpoints.
 *
 *   GET  /api/v1/geo/reverse?lat=&lng=     resolve a fix to village/city/region/country
 *   POST /api/v1/me/location               store my current position (auth)
 *   GET  /api/v1/me/location               my last known position (auth)
 *
 * Reverse geocoding is fully offline (src/geo/gazetteer.bin, 167k places
 * worldwide) so it works for every country, costs nothing, leaks no user
 * coordinates to a third party, and answers in well under a millisecond.
 */
import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { getPool } from '../../db.js';
import { requireAuth, authedUser } from '../../auth.js';
import { reverseGeocode, geocoderReady } from '../../geo/gazetteer.js';
import { ServiceUnavailableError } from '../../errors.js';

const coordSchema = z.object({
  lat: z.coerce.number().min(-90).max(90),
  lng: z.coerce.number().min(-180).max(180),
});

const saveSchema = z.object({
  lat: z.number().min(-90).max(90),
  lng: z.number().min(-180).max(180),
  /** Optional client-side accuracy in metres, purely informational. */
  accuracyM: z.number().min(0).max(100000).optional(),
});

export default async function registerGeoRoute(app: FastifyInstance) {
  const pool = getPool();

  /** Public: anyone (even logged out) can name their own position. */
  app.get('/api/v1/geo/reverse', async (request) => {
    const { lat, lng } = coordSchema.parse(request.query);
    const place = reverseGeocode(lat, lng);
    if (!place) {
      throw new ServiceUnavailableError('Reverse geocoding is unavailable on this server');
    }
    return { place, query: { lat, lng } };
  });

  app.get('/api/v1/geo/status', async () => ({
    ready: geocoderReady(),
    source: 'offline-gazetteer',
    coverage: 'global',
  }));

  /**
   * Persist the signed-in user's position and return the resolved place, so a
   * single round trip both saves the fix and tells the UI where it is.
   */
  app.post('/api/v1/me/location', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { lat, lng, accuracyM } = saveSchema.parse(request.body);
    const place = reverseGeocode(lat, lng);

    await pool.query(
      `UPDATE users
       SET lat = $2, lng = $3,
           village = $4, region = $5, country = $6, country_code = $7,
           place_label = $8,
           city = CASE WHEN $9 <> '' THEN $9 ELSE city END,
           location_updated_at = now(), updated_at = now()
       WHERE id = $1`,
      [
        me.id, lat, lng,
        place?.village ?? null,
        place?.region ?? null,
        place?.country ?? null,
        place?.countryCode ?? null,
        place?.label ?? null,
        place?.city ?? '',
      ]
    );

    return { ok: true, place, position: { lat, lng, accuracyM: accuracyM ?? null } };
  });

  app.get('/api/v1/me/location', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { rows } = await pool.query(
      `SELECT lat, lng, city, village, region, country, country_code, place_label, location_updated_at
       FROM users WHERE id = $1`,
      [me.id]
    );
    const r = rows[0];
    if (!r || r.lat === null || r.lng === null) {
      return { position: null, place: null };
    }
    return {
      position: { lat: Number(r.lat), lng: Number(r.lng) },
      place: {
        village: r.village,
        city: r.city || null,
        region: r.region,
        country: r.country,
        countryCode: r.country_code,
        label: r.place_label ?? '',
        shortLabel: [r.village || r.city, r.region || r.country].filter(Boolean).join(', '),
        accuracyKm: 0,
        source: 'offline-gazetteer' as const,
      },
      updatedAt: r.location_updated_at,
    };
  });
}
