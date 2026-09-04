/**
 * ScottsTechX — location endpoints (fixed for village accuracy)
 *
 * Problem fixed: Nearby was showing wrong village (e.g., Kisaasi instead of Kigoowa)
 * because reverse geocoder blindly returned nearest named place.
 *
 * New flow:
 * 1. GPS (lat/lng/accuracy) is ALWAYS separate from village name
 * 2. Reverse geocoding inspects complete address response, distinguishes
 *    village/neighbourhood/suburb/city/district/region/country
 * 3. If uncertain between nearby villages, returns isUncertain + displayLabel
 *    like "Location near X" instead of confidently wrong village
 * 4. User-confirmed villages are stored separately and NEVER overwritten by geocoder
 * 5. GPS accuracy is checked and reported
 */

import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { getPool } from '../../db.js';
import { requireAuth, authedUser } from '../../auth.js';
import { reverseGeocode, geocoderReady, searchPlaces } from '../../geo/gazetteer.js';
import type { ReverseResult } from '../../geo/gazetteer.js';
import { googleReverseGeocode, googleGeocoderConfigured } from '../../geo/google-geocoder.js';
import { ServiceUnavailableError, ValidationError } from '../../errors.js';

/**
 * Sanitize locality strings — prevent injection, trim, limit length
 */
function sanitizeLocality(input: string | null | undefined): string | null {
  if (!input) return null;
  const trimmed = input.trim().slice(0, 100);
  // Allow letters, numbers, spaces, hyphen, apostrophe, comma, period
  if (!/^[\p{L}\p{N}\s\-',.()]+$/u.test(trimmed)) {
    // If contains suspicious chars, still allow but escape — but for safety, strip to safe chars
    return trimmed.replace(/[^\p{L}\p{N}\s\-',.()]/gu, '').trim() || null;
  }
  return trimmed || null;
}

const geoCache = new Map<string, { at: number; value: ReverseResult | null }>();
const GEO_CACHE_TTL = 60_000;
function geoCacheKey(lat: number, lng: number): string {
  return `${Math.round(lat * 10000) / 10000}:${Math.round(lng * 10000) / 10000}`;
}

/**
 * Resolve place with proper separation of GPS vs village naming
 * - GPS is used for distance, never for village alone
 * - Inspects complete address response
 * - Returns confidence and uncertainty flags
 * - Cached 60s for speed
 */
async function resolvePlace(lat: number, lng: number, accuracyM?: number): Promise<ReverseResult | null> {
  const key = geoCacheKey(lat, lng);
  const cached = geoCache.get(key);
  if (cached && Date.now() - cached.at < GEO_CACHE_TTL && (accuracyM === undefined || accuracyM > 100)) {
    return cached.value;
  }
  // Validate coords
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null;
  if (lat < -90 || lat > 90 || lng < -180 || lng > 180) return null;

  // Offline is instant — get it first for fallback
  const viaOffline = reverseGeocode(lat, lng, accuracyM);

  // If GPS accuracy is very poor (>1km), offline is already uncertain
  if (accuracyM !== undefined && accuracyM > 1000) {
    if (viaOffline) {
      const res = {
        ...viaOffline,
        confidence: Math.min(viaOffline.confidence, 0.2),
        isUncertain: true,
        requiresConfirmation: true,
        displayLabel: viaOffline.city ? `Location near ${viaOffline.city}` : `Location detected — confirm your village`,
      };
      geoCache.set(key, { at: Date.now(), value: res });
      return res;
    }
  }

  // Try Google if configured
  if (googleGeocoderConfigured()) {
    try {
      const googlePromise = googleReverseGeocode(lat, lng, accuracyM);
      const timeoutMs = accuracyM !== undefined && accuracyM <= 100 ? 800 : 500;
      const timeout = new Promise<null>((res) => setTimeout(() => res(null), timeoutMs));
      const viaGoogle = await Promise.race([googlePromise, timeout]);

      if (viaGoogle) {
        let result: ReverseResult;
        if (viaOffline?.village && viaGoogle.village && viaOffline.village !== viaGoogle.village) {
          const offlineAlts = viaOffline.alternatives?.map(a => a.name) ?? [];
          if (offlineAlts.includes(viaGoogle.village) || viaGoogle.alternatives?.some(a => a.name === viaOffline.village)) {
            result = {
              ...viaGoogle,
              confidence: Math.min(viaGoogle.confidence, viaOffline.confidence, 0.5),
              isUncertain: true,
              requiresConfirmation: true,
              displayLabel: `Location near ${viaGoogle.village}`,
              alternatives: [...(viaGoogle.alternatives ?? []), ...(viaOffline.alternatives ?? [])].slice(0, 5),
            };
          } else {
            result = {
              ...viaGoogle,
              confidence: Math.min(viaGoogle.confidence, 0.7),
              isUncertain: true,
              requiresConfirmation: true,
              displayLabel: `Location near ${viaGoogle.village}`,
            };
          }
        } else if (viaGoogle.isUncertain) {
          result = viaGoogle;
        } else {
          result = {
            ...viaGoogle,
            alternatives: [...(viaGoogle.alternatives ?? []), ...(viaOffline?.alternatives?.filter(a => a.name !== viaGoogle.village) ?? [])].slice(0, 5),
          };
        }
        geoCache.set(key, { at: Date.now(), value: result });
        if (geoCache.size > 500) { const first = geoCache.keys().next().value; if (first) geoCache.delete(first); }
        return result;
      }
    } catch {}
  }

  // Fallback to offline
  if (viaOffline) {
    geoCache.set(key, { at: Date.now(), value: viaOffline });
    if (geoCache.size > 500) { const first = geoCache.keys().next().value; if (first) geoCache.delete(first); }
    return viaOffline;
  }

  // Last resort: try Google without timeout
  try {
    const viaGoogle = await googleReverseGeocode(lat, lng, accuracyM);
    if (viaGoogle) {
      geoCache.set(key, { at: Date.now(), value: viaGoogle });
      if (geoCache.size > 500) { const first = geoCache.keys().next().value; if (first) geoCache.delete(first); }
      return viaGoogle;
    }
  } catch {}

  geoCache.set(key, { at: Date.now(), value: null });
  return null;
}

const coordSchema = z.object({
  lat: z.coerce.number().min(-90).max(90),
  lng: z.coerce.number().min(-180).max(180),
  accuracy: z.coerce.number().min(0).max(100000).optional(),
});

const searchSchema = z.object({
  q: z.string().min(1).max(100),
  limit: z.coerce.number().min(1).max(20).optional().default(10),
});

const saveSchema = z.object({
  lat: z.number().min(-90).max(90),
  lng: z.number().min(-180).max(180),
  accuracyM: z.number().min(0).max(100000).optional(),
  // Optional user-confirmed village data
  village: z.string().max(100).optional(),
  city: z.string().max(100).optional(),
  district: z.string().max(100).optional(),
  region: z.string().max(100).optional(),
  country: z.string().max(100).optional(),
  villageSource: z.enum(['gps_geocoder', 'user_confirmed', 'manual_search', 'google', 'offline_gazetteer']).optional(),
  villageConfirmed: z.boolean().optional(),
});

export default async function registerGeoRoute(app: FastifyInstance) {
  const pool = getPool();

  /** Public: reverse geocode any coordinate */
  app.get('/api/v1/geo/reverse', async (request) => {
    const { lat, lng, accuracy } = coordSchema.parse(request.query);
    const place = await resolvePlace(lat, lng, accuracy);
    if (!place) {
      throw new ServiceUnavailableError('Reverse geocoding is unavailable');
    }
    return { place, query: { lat, lng, accuracy: accuracy ?? null } };
  });

  /** Public: search villages/towns for user correction */
  app.get('/api/v1/geo/search', async (request) => {
    const { q, limit } = searchSchema.parse(request.query);
    const sanitized = sanitizeLocality(q);
    if (!sanitized) throw new ValidationError('Invalid search query');

    // Search offline gazetteer — fast and no API cost
    const offlineResults = searchPlaces(sanitized, limit);

    // If Google configured, also try to enrich with Google? For now return offline
    // to avoid latency and cost on search
    return {
      query: sanitized,
      results: offlineResults.map(r => ({
        name: r.name,
        city: r.region, // region is like Central Region
        region: r.region,
        country: r.country,
        lat: r.lat,
        lng: r.lng,
        type: r.type,
        label: [r.name, r.region, r.country].filter(Boolean).join(', '),
      })),
      source: 'offline-gazetteer',
    };
  });

  app.get('/api/v1/geo/status', async () => ({
    ready: geocoderReady(),
    source: googleGeocoderConfigured() ? 'google' : 'offline-gazetteer',
    precise: googleGeocoderConfigured(),
    fallback: 'offline-gazetteer',
    coverage: 'global',
    version: 'v2-fixed',
  }));

  /**
   * Persist user's position with separation of GPS vs village
   * - GPS (lat/lng/accuracy) is always updated
   * - Village is only updated if not previously confirmed by user
   * - If user explicitly confirms village, save as user_confirmed and never overwrite
   */
  app.post('/api/v1/me/location', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const body = saveSchema.parse(request.body);
    const { lat, lng, accuracyM } = body;

    // Get current user location data to check if village was previously confirmed
    const { rows: existingRows } = await pool.query(
      `SELECT village, village_confirmed, village_source, city, district, region, country
       FROM users WHERE id = $1`,
      [me.id]
    );
    const existing = existingRows[0];

    // Resolve place from GPS
    const place = await resolvePlace(lat, lng, accuracyM);

    // Determine what village data to save
    let villageToSave: string | null = null;
    let cityToSave: string | null = null;
    let districtToSave: string | null = null;
    let regionToSave: string | null = null;
    let countryToSave: string | null = null;
    let countryCodeToSave: string | null = null;
    let placeLabelToSave: string | null = null;
    let villageSourceToSave: string = 'gps_geocoder';
    let villageConfirmedToSave = false;
    let confidenceToSave: number | null = null;
    let alternativesToSave: any = null;
    let neighbourhoodToSave: string | null = null;
    let suburbToSave: string | null = null;

    if (place) {
      placeLabelToSave = place.displayLabel || place.label;
      confidenceToSave = place.confidence;
      alternativesToSave = place.alternatives ? JSON.stringify(place.alternatives) : null;
      neighbourhoodToSave = sanitizeLocality(place.neighbourhood ?? null);
      suburbToSave = sanitizeLocality(place.suburb ?? null);
    }

    // If user explicitly provides village (user correction flow)
    if (body.village && body.villageSource === 'user_confirmed') {
      // User confirmed — save their choice and mark as confirmed
      villageToSave = sanitizeLocality(body.village);
      cityToSave = sanitizeLocality(body.city ?? null) || sanitizeLocality(place?.city ?? null);
      districtToSave = sanitizeLocality(body.district ?? null) || sanitizeLocality(place?.district ?? null);
      regionToSave = sanitizeLocality(body.region ?? null) || sanitizeLocality(place?.region ?? null);
      countryToSave = sanitizeLocality(body.country ?? null) || sanitizeLocality(place?.country ?? null);
      villageSourceToSave = 'user_confirmed';
      villageConfirmedToSave = true;
      placeLabelToSave = body.village; // Use user-confirmed as label
    } else if (body.village && body.villageSource === 'manual_search') {
      villageToSave = sanitizeLocality(body.village);
      cityToSave = sanitizeLocality(body.city ?? null) || sanitizeLocality(place?.city ?? null);
      districtToSave = sanitizeLocality(body.district ?? null);
      regionToSave = sanitizeLocality(body.region ?? null) || sanitizeLocality(place?.region ?? null);
      countryToSave = sanitizeLocality(body.country ?? null) || sanitizeLocality(place?.country ?? null);
      villageSourceToSave = 'manual_search';
      villageConfirmedToSave = true;
    } else {
      // No explicit user village — check if existing was user-confirmed
      if (existing?.village_confirmed) {
        // NEVER overwrite user-confirmed village with geocoder result
        villageToSave = existing.village;
        cityToSave = existing.city;
        districtToSave = existing.district ?? null;
        regionToSave = existing.region;
        countryToSave = existing.country;
        villageSourceToSave = existing.village_source;
        villageConfirmedToSave = true;
        // Keep existing place_label if we have a confirmed village
        placeLabelToSave = existing.village || placeLabelToSave;
      } else {
        // Not confirmed before — use geocoder result
        villageToSave = sanitizeLocality(place?.village ?? null);
        cityToSave = sanitizeLocality(place?.city ?? null);
        districtToSave = sanitizeLocality(place?.district ?? null);
        regionToSave = sanitizeLocality(place?.region ?? null);
        countryToSave = sanitizeLocality(place?.country ?? null);
        countryCodeToSave = sanitizeLocality(place?.countryCode ?? null);
        villageSourceToSave = place?.source === 'google' ? 'google' : 'offline_gazetteer';
        villageConfirmedToSave = false;
      }
    }

    // Always update GPS coords and accuracy, but preserve confirmed village
    await pool.query(
      `UPDATE users
       SET lat = $2, lng = $3,
           gps_accuracy = $4,
           village = COALESCE($5, village),
           city = CASE WHEN $6 <> '' AND $6 IS NOT NULL THEN $6 ELSE city END,
           district = COALESCE($7, district),
           region = COALESCE($8, region),
           country = COALESCE($9, country),
           country_code = COALESCE($10, country_code),
           place_label = COALESCE($11, place_label),
           village_source = $12,
           village_confirmed = $13,
           neighbourhood = COALESCE($14, neighbourhood),
           suburb = COALESCE($15, suburb),
           location_confidence = COALESCE($16, location_confidence),
           location_alternatives = COALESCE($17::jsonb, location_alternatives),
           location_updated_at = now(),
           updated_at = now()
       WHERE id = $1`,
      [
        me.id,
        lat,
        lng,
        accuracyM ?? null,
        villageToSave,
        cityToSave ?? '',
        districtToSave,
        regionToSave,
        countryToSave,
        countryCodeToSave,
        placeLabelToSave,
        villageSourceToSave,
        villageConfirmedToSave,
        neighbourhoodToSave,
        suburbToSave,
        confidenceToSave,
        alternativesToSave,
      ]
    );

    return {
      ok: true,
      place: place ? {
        ...place,
        // If user had confirmed village, return confirmed in place
        ...(existing?.village_confirmed ? {
          village: existing.village,
          city: existing.city,
          district: existing.district,
          region: existing.region,
          country: existing.country,
          source: existing.village_source as any,
          isUserConfirmed: true,
        } : {}),
      } : null,
      position: { lat, lng, accuracyM: accuracyM ?? null },
      saved: {
        village: villageToSave,
        city: cityToSave,
        district: districtToSave,
        region: regionToSave,
        country: countryToSave,
        villageSource: villageSourceToSave,
        villageConfirmed: villageConfirmedToSave,
      },
    };
  });

  /** Explicit endpoint to confirm/correct village separately from GPS */
  app.post('/api/v1/me/location/confirm', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const body = z.object({
      village: z.string().min(1).max(100),
      city: z.string().max(100).optional(),
      district: z.string().max(100).optional(),
      region: z.string().max(100).optional(),
      country: z.string().max(100).optional(),
      lat: z.number().min(-90).max(90).optional(),
      lng: z.number().min(-180).max(180).optional(),
    }).parse(request.body);

    const village = sanitizeLocality(body.village);
    if (!village) throw new ValidationError('Invalid village name');

    await pool.query(
      `UPDATE users
       SET village = $2,
           city = COALESCE($3, city),
           district = COALESCE($4, district),
           region = COALESCE($5, region),
           country = COALESCE($6, country),
           place_label = $2,
           village_source = 'user_confirmed',
           village_confirmed = true,
           location_updated_at = now(),
           updated_at = now(),
           lat = COALESCE($7, lat),
           lng = COALESCE($8, lng)
       WHERE id = $1`,
      [
        me.id,
        village,
        sanitizeLocality(body.city ?? null) ?? null,
        sanitizeLocality(body.district ?? null) ?? null,
        sanitizeLocality(body.region ?? null) ?? null,
        sanitizeLocality(body.country ?? null) ?? null,
        body.lat ?? null,
        body.lng ?? null,
      ]
    );

    return { ok: true, village, villageSource: 'user_confirmed', villageConfirmed: true };
  });

  app.get('/api/v1/me/location', { preHandler: requireAuth }, async (request) => {
    const me = authedUser(request);
    const { rows } = await pool.query(
      `SELECT lat, lng, gps_accuracy, city, village, district, region, country, country_code,
              place_label, location_updated_at, village_source, village_confirmed,
              neighbourhood, suburb, location_confidence, location_alternatives
       FROM users WHERE id = $1`,
      [me.id]
    );
    const r = rows[0];
    if (!r || r.lat === null || r.lng === null) {
      return { position: null, place: null };
    }
    return {
      position: {
        lat: Number(r.lat),
        lng: Number(r.lng),
        accuracyM: r.gps_accuracy ? Number(r.gps_accuracy) : null,
      },
      place: {
        village: r.village,
        neighbourhood: r.neighbourhood,
        suburb: r.suburb,
        city: r.city || null,
        district: r.district,
        region: r.region,
        country: r.country,
        countryCode: r.country_code,
        label: r.place_label ?? '',
        shortLabel: [r.village || r.city, r.region || r.country].filter(Boolean).join(', '),
        displayLabel: r.village_confirmed ? r.village : (r.place_label ?? ''),
        accuracyKm: 0,
        source: (r.village_source ?? 'offline-gazetteer') as any,
        confidence: r.location_confidence ? Number(r.location_confidence) : null,
        isUncertain: !r.village_confirmed && (r.location_confidence ? Number(r.location_confidence) < 0.7 : false),
        requiresConfirmation: !r.village_confirmed,
        isUserConfirmed: !!r.village_confirmed,
        villageSource: r.village_source,
        villageConfirmed: !!r.village_confirmed,
        alternatives: r.location_alternatives,
      },
      updatedAt: r.location_updated_at,
    };
  });
}
