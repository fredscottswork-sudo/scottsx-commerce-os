/**
 * ScottsTechX — location endpoints (reverted to fast Google Maps)
 *
 * Flow:
 * - Frontend gets lat/lng via navigator.geolocation
 * - Calls GET /api/v1/geo/reverse?lat=&lng= in background
 * - Backend tries Google Maps reverse geocode with 800ms timeout (<1s)
 * - Falls back to offline gazetteer instantly if Google missing/slow
 * - Returns village/city/region/country fast
 */

import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { getPool } from '../../db.js';
import { requireAuth, authedUser } from '../../auth.js';
import { reverseGeocode, geocoderReady } from '../../geo/gazetteer.js';
import type { ReverseResult } from '../../geo/gazetteer.js';
import { googleReverseGeocode, googleGeocoderConfigured } from '../../geo/google-geocoder.js';
import { ServiceUnavailableError } from '../../errors.js';

// Cache for reverse geocode — 60s TTL, ~11m precision (4 decimals)
const geoCache = new Map<string, { at: number; value: ReverseResult | null }>();
const GEO_CACHE_TTL = 60_000;
function geoCacheKey(lat: number, lng: number): string {
  return `${Math.round(lat * 10000) / 10000}:${Math.round(lng * 10000) / 10000}`;
}

async function resolvePlace(lat: number, lng: number): Promise<ReverseResult | null> {
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null;
  if (lat < -90 || lat > 90 || lng < -180 || lng > 180) return null;

  const key = geoCacheKey(lat, lng);
  const cached = geoCache.get(key);
  if (cached && Date.now() - cached.at < GEO_CACHE_TTL) return cached.value;

  // Try Google in background with <1s timeout for speed
  if (googleGeocoderConfigured()) {
    try {
      const googlePromise = googleReverseGeocode(lat, lng);
      const timeout = new Promise<null>((res) => setTimeout(() => res(null), 800));
      const viaGoogle = await Promise.race([googlePromise, timeout]);
      if (viaGoogle) {
        geoCache.set(key, { at: Date.now(), value: viaGoogle });
        if (geoCache.size > 500) {
          const first = geoCache.keys().next().value;
          if (first) geoCache.delete(first);
        }
        return viaGoogle;
      }
    } catch {
      // fall through to offline
    }
  }

  // Offline fallback — instant
  const viaOffline = reverseGeocode(lat, lng);
  geoCache.set(key, { at: Date.now(), value: viaOffline });
  if (geoCache.size > 500) {
    const first = geoCache.keys().next().value;
    if (first) geoCache.delete(first);
  }
  return viaOffline;
}

const coordSchema = z.object({
  lat: z.coerce.number().min(-90).max(90),
  lng: z.coerce.number().min(-180).max(180),
  accuracy: z.coerce.number().min(0).max(100000).optional(),
});

const saveSchema = z.object({
  lat: z.number().min(-90).max(90),
  lng: z.number().min(-180).max(180),
  accuracyM: z.number().min(0).max(100000).optional(),
});

export default async function registerGeoRoute(app: FastifyInstance) {
  const pool = getPool();

  /** Public: reverse geocode any coordinate — fast Google Maps in background */
  app.get('/api/v1/geo/reverse', async (request) => {
    const { lat, lng } = coordSchema.parse(request.query);
    const place = await resolvePlace(lat, lng);
    if (!place) throw new ServiceUnavailableError('Reverse geocoding unavailable');
    return { place, query: { lat, lng } };
  });

  app.get('/api/v1/geo/status', async () => ({
    ready: geocoderReady(),
    source: googleGeocoderConfigured() ? 'google' : 'offline-gazetteer',
    coverage: 'global',
  }));

  /** Persist user's position — simple save, no confirmation logic */
  app.post('/api/v1/me/location', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { lat, lng, accuracyM } = saveSchema.parse(request.body);
    const place = await resolvePlace(lat, lng);

    await pool.query(
      `UPDATE users
       SET lat = $2, lng = $3,
           village = COALESCE($4, village),
           region = COALESCE($5, region),
           country = COALESCE($6, country),
           country_code = COALESCE($7, country_code),
           place_label = COALESCE($8, place_label),
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
        source: googleGeocoderConfigured() ? 'google' : 'offline-gazetteer',
      },
      updatedAt: r.location_updated_at,
    };
  });
}
