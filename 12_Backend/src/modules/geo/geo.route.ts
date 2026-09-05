/**
 * ScottsTechX — location endpoints.
 *
 * A GPS fix becomes an exact place: village / neighbourhood, suburb, city,
 * region, country. OpenStreetMap is the primary source (it carries LC1-level
 * names across Uganda), Google and the offline gazetteer complete the label.
 */

import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { getPool } from '../../db.js';
import { requireAuth, authedUser } from '../../auth.js';
import { geocoderReady } from '../../geo/gazetteer.js';
import { googleGeocoderConfigured } from '../../geo/google-geocoder.js';
import { osmGeocoderConfigured } from '../../geo/osm-geocoder.js';
import { resolvePlace } from '../../geo/resolve-place.js';
import { ServiceUnavailableError } from '../../errors.js';

const coordSchema = z.object({
  lat: z.coerce.number().min(-90).max(90),
  lng: z.coerce.number().min(-180).max(180),
  // Browsers report accuracy as NaN/Infinity/'' when unknown — treat as absent.
  accuracyM: z.preprocess((v) => {
    if (v === undefined || v === null || v === '') return undefined;
    const n = Number(v);
    return Number.isFinite(n) ? Math.min(Math.max(n, 0), 100000) : undefined;
  }, z.number().optional()),
});

const saveSchema = z.object({
  lat: z.number().min(-90).max(90),
  lng: z.number().min(-180).max(180),
  accuracyM: z.preprocess((v) => {
    if (v === undefined || v === null || v === '') return undefined;
    const n = Number(v);
    return Number.isFinite(n) ? Math.min(Math.max(n, 0), 100000) : undefined;
  }, z.number().optional()),
});

export default async function registerGeoRoute(app: FastifyInstance) {
  const pool = getPool();

  app.get('/api/v1/geo/reverse', async (request) => {
    const { lat, lng, accuracyM } = coordSchema.parse(request.query);
    const place = await resolvePlace(lat, lng);
    if (!place) throw new ServiceUnavailableError('Reverse geocoding is unavailable on this server');
    // A coarse fix (cell-tower / wifi, >250 m) cannot pin one village.
    // Say so rather than name a neighbour's village with false confidence.
    const approximate = typeof accuracyM === 'number' && accuracyM > 250;
    return { place: { ...place, approximate }, query: { lat, lng, accuracyM: accuracyM ?? null } };
  });

  app.get('/api/v1/geo/status', async () => ({
    ready: geocoderReady(),
    source: osmGeocoderConfigured() ? 'osm' : googleGeocoderConfigured() ? 'google' : 'offline-gazetteer',
    providers: {
      osm: osmGeocoderConfigured(),
      google: googleGeocoderConfigured(),
      offline: geocoderReady(),
    },
    coverage: 'global',
  }));

  app.post('/api/v1/me/location', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { lat, lng, accuracyM } = saveSchema.parse(request.body);
    const place = await resolvePlace(lat, lng);

    await pool.query(
      `UPDATE users
       SET lat = $2, lng = $3,
           village = $4, region = $5, country = $6, country_code = $7,
           place_label = $8,
           city = CASE WHEN $9 <> '' THEN $9 ELSE city END,
           location_updated_at = now(), updated_at = now()
       WHERE id = $1`,
      [
        me.id,
        lat,
        lng,
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
        source: osmGeocoderConfigured() ? 'osm' : googleGeocoderConfigured() ? 'google' : 'offline-gazetteer',
      },
      updatedAt: r.location_updated_at,
    };
  });
}
