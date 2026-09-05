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
import { reverseGeocode, geocoderReady } from '../../geo/gazetteer.js';
import type { ReverseResult } from '../../geo/gazetteer.js';
import { googleReverseGeocode, googleGeocoderConfigured } from '../../geo/google-geocoder.js';
import { osmReverseGeocode, osmGeocoderConfigured } from '../../geo/osm-geocoder.js';
import { ServiceUnavailableError } from '../../errors.js';

const geoCache = new Map<string, { at: number; value: ReverseResult | null }>();
const GEO_CACHE_TTL = 60_000;
function geoCacheKey(lat: number, lng: number): string {
  return `${Math.round(lat * 10000) / 10000}:${Math.round(lng * 10000) / 10000}`;
}

/**
 * Resolve a GPS fix to the most exact place we can name.
 *
 * Order: OpenStreetMap (LC1-level village / neighbourhood detail in Uganda),
 * then Google (if a key is set), then the offline gazetteer (always works).
 * Results are MERGED, not just raced: OSM gives the village, but if it has no
 * region for a rural fix the gazetteer's region/country fills the gap, so the
 * label is always as complete as the best of the three.
 */
async function resolvePlace(lat: number, lng: number): Promise<ReverseResult | null> {
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null;
  if (lat < -90 || lat > 90 || lng < -180 || lng > 180) return null;

  const key = geoCacheKey(lat, lng);
  const cached = geoCache.get(key);
  if (cached && Date.now() - cached.at < GEO_CACHE_TTL) return cached.value;

  const offline = reverseGeocode(lat, lng);

  const withTimeout = <T>(p: Promise<T | null>, ms: number): Promise<T | null> =>
    Promise.race([p.catch(() => null), new Promise<null>((res) => setTimeout(() => res(null), ms))]);

  const [viaOsm, viaGoogle] = await Promise.all([
    osmGeocoderConfigured() ? withTimeout(osmReverseGeocode(lat, lng), 3000) : Promise.resolve(null),
    googleGeocoderConfigured() ? withTimeout(googleReverseGeocode(lat, lng), 1200) : Promise.resolve(null),
  ]);

  const best = mergePlaces(viaOsm, viaGoogle, offline);

  geoCache.set(key, { at: Date.now(), value: best });
  if (geoCache.size > 500) {
    const first = geoCache.keys().next().value;
    if (first) geoCache.delete(first);
  }
  return best;
}

/** Field-wise merge: first provider (in priority order) that knows a field wins. */
function mergePlaces(...sources: (ReverseResult | null)[]): ReverseResult | null {
  const known = sources.filter((s): s is ReverseResult => Boolean(s));
  if (!known.length) return null;
  const first = <K extends keyof ReverseResult>(k: K): ReverseResult[K] | null => {
    for (const s of known) {
      const v = s[k];
      if (v !== null && v !== undefined && v !== '') return v;
    }
    return null;
  };
  const village = first('village');
  const suburb = first('suburb');
  const city = first('city');
  const region = first('region');
  const country = first('country');
  const dedupe = (parts: (string | null)[]) => {
    const out: string[] = [];
    for (const p of parts) if (p && !out.some((x) => x.toLowerCase() === p.toLowerCase())) out.push(p);
    return out;
  };
  return {
    village,
    suburb: village && suburb && suburb !== village ? suburb : null,
    road: first('road') ?? null,
    city,
    region,
    country,
    countryCode: first('countryCode'),
    accuracyKm: known[0].accuracyKm,
    label: dedupe([village, village && suburb !== village ? (suburb ?? null) : null, city, region, country]).join(', '),
    shortLabel: dedupe([village || city, village && city ? city : region || country]).join(', '),
    source: known[0].source,
  };
}

const coordSchema = z.object({
  lat: z.coerce.number().min(-90).max(90),
  lng: z.coerce.number().min(-180).max(180),
});

const saveSchema = z.object({
  lat: z.number().min(-90).max(90),
  lng: z.number().min(-180).max(180),
  accuracyM: z.number().min(0).max(100000).optional(),
});

export default async function registerGeoRoute(app: FastifyInstance) {
  const pool = getPool();

  app.get('/api/v1/geo/reverse', async (request) => {
    const { lat, lng } = coordSchema.parse(request.query);
    const place = await resolvePlace(lat, lng);
    if (!place) throw new ServiceUnavailableError('Reverse geocoding is unavailable on this server');
    return { place, query: { lat, lng } };
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
