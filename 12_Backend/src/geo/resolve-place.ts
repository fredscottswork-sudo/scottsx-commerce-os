/**
 * ScottsTechX — one place resolver for the whole API.
 *
 * GPS fix → exact place (village / suburb / city / region / country), merged
 * from OpenStreetMap, Google Maps and the offline gazetteer. Used by the
 * Nearby endpoint for the buyer AND for every seller pin on the page, so both
 * sides of the map speak the same names.
 */
import { reverseGeocode } from './gazetteer.js';
import type { ReverseResult } from './gazetteer.js';
import { googleReverseGeocode, googleGeocoderConfigured } from './google-geocoder.js';
import { osmReverseGeocode, osmGeocoderConfigured, osmQueueDelayMs } from './osm-geocoder.js';

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
export async function resolvePlace(lat: number, lng: number, priority: 'high' | 'low' = 'high'): Promise<ReverseResult | null> {
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null;
  if (lat < -90 || lat > 90 || lng < -180 || lng > 180) return null;

  const key = geoCacheKey(lat, lng);
  const cached = geoCache.get(key);
  if (cached && Date.now() - cached.at < GEO_CACHE_TTL) return cached.value;
  const joined = pending.get(key);
  if (joined) {
    const waitMs = priority === 'high' ? 2500 : 300;
    const r = await Promise.race([joined, new Promise<'wait'>((res) => setTimeout(() => res('wait'), waitMs))]);
    return r === 'wait' ? reverseGeocode(lat, lng) : r;
  }

  const offline = reverseGeocode(lat, lng);

  // Online lookup runs to completion in the background (so the NEXT request
  // is a cache hit), but this request never waits more than `waitMs`: if the
  // village has not arrived by then the caller gets the instant offline
  // answer and the client upgrades on its next poll.
  const inflight = onlineLookup(lat, lng, priority, offline).then((best) => {
    const at = best?.village ? Date.now() : Date.now() - GEO_CACHE_TTL + 5_000;
    geoCache.set(key, { at, value: best });
    if (geoCache.size > 500) {
      const first = geoCache.keys().next().value;
      if (first) geoCache.delete(first);
    }
    return best;
  });
  pending.set(key, inflight);
  inflight.finally(() => { if (pending.get(key) === inflight) pending.delete(key); }).catch(() => undefined);

  const waitMs = priority === 'high' ? 2500 : 300;
  const timed = await Promise.race([inflight, new Promise<'wait'>((res) => setTimeout(() => res('wait'), waitMs))]);
  return timed === 'wait' ? offline : timed;
}

/** In-flight online lookups keyed like the cache, so a poll joins the wait. */
const pending = new Map<string, Promise<ReverseResult | null>>();

async function onlineLookup(
  lat: number, lng: number, priority: 'high' | 'low', offline: ReverseResult | null
): Promise<ReverseResult | null> {
  const withTimeout = <T>(p: Promise<T | null>, ms: number): Promise<T | null> =>
    Promise.race([p.catch(() => null), new Promise<null>((res) => setTimeout(() => res(null), ms))]);
  const osmBudget = 6000 + osmQueueDelayMs(priority);
  const [viaOsm, viaGoogle] = await Promise.all([
    osmGeocoderConfigured() ? withTimeout(osmReverseGeocode(lat, lng, priority), osmBudget) : Promise.resolve(null),
    googleGeocoderConfigured() ? withTimeout(googleReverseGeocode(lat, lng), 3000) : Promise.resolve(null),
  ]);
  const rank = (r: ReverseResult | null) => (r?.village ? (r.suburb ? 2 : 1) : 0);
  const ordered = rank(viaGoogle) > rank(viaOsm) ? [viaGoogle, viaOsm] : [viaOsm, viaGoogle];
  return mergePlaces(...ordered, offline);
}

/** Field-wise merge: first provider (in priority order) that knows a field wins. */
export function mergePlaces(...sources: (ReverseResult | null)[]): ReverseResult | null {
  const known = sources.filter((s): s is ReverseResult => Boolean(s));
  if (!known.length) return null;
  const first = <K extends keyof ReverseResult>(k: K): ReverseResult[K] | null => {
    for (const s of known) {
      const v = s[k];
      if (v !== null && v !== undefined && v !== '') return v;
    }
    return null;
  };
  const city = first('city');
  // A "village" that is just the city's own name (the offline gazetteer does
  // this for city centres) is not a village. Skip it so a real one from any
  // other provider can win.
  const isRealVillage = (v: string | null | undefined, c: string | null) =>
    Boolean(v) && (!c || v!.toLowerCase() !== c.toLowerCase());
  let village: string | null = null;
  for (const s of known) {
    if (isRealVillage(s.village, s.city ?? city)) { village = s.village!; break; }
  }
  let suburb: string | null = null;
  for (const s of known) {
    if (s.suburb && s.suburb !== village && isRealVillage(s.suburb, city)) { suburb = s.suburb; break; }
  }
  if (!village && suburb) { village = suburb; suburb = null; }
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


/** Best-effort, bounded: name many pins at once without stalling the response. */
export async function resolvePlaces(
  points: { lat: number; lng: number }[],
  budgetMs = 1500
): Promise<(ReverseResult | null)[]> {
  const deadline = new Promise<'timeout'>((res) => setTimeout(() => res('timeout'), budgetMs));
  const results: (ReverseResult | null)[] = points.map((p) => reverseGeocode(p.lat, p.lng));
  const work = Promise.all(
    points.map((p, i) => resolvePlace(p.lat, p.lng, 'low').then((r) => { if (r) results[i] = r; }).catch(() => undefined))
  );
  await Promise.race([work, deadline]);
  return results;
}
