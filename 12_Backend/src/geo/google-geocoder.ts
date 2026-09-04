/**
 * ScottsTechX — Google Maps reverse geocoding (fast path)
 * Uses lat/lng to identify village via Google Maps in background, <1s
 */
import type { ReverseResult } from './gazetteer.js';

const CACHE_MAX = 2000;
const cache = new Map<string, { at: number; value: ReverseResult }>();
const CACHE_TTL = 60_000;

function cacheKey(lat: number, lng: number): string {
  return `${Math.round(lat * 1000) / 1000}:${Math.round(lng * 1000) / 1000}`;
}

export function googleGeocoderConfigured(): boolean {
  return Boolean(process.env.GOOGLE_MAPS_API_KEY);
}

function pick(components: any[], types: string[]): string | null {
  for (const t of types) {
    const hit = components.find((c: any) => c.types.includes(t));
    if (hit) return hit.long_name;
  }
  return null;
}

export async function googleReverseGeocode(lat: number, lng: number): Promise<ReverseResult | null> {
  const key = process.env.GOOGLE_MAPS_API_KEY;
  if (!key) return null;
  const k = cacheKey(lat, lng);
  const cached = cache.get(k);
  if (cached && Date.now() - cached.at < CACHE_TTL) return cached.value;

  const url =
    'https://maps.googleapis.com/maps/api/geocode/json' +
    `?latlng=${encodeURIComponent(`${lat},${lng}`)}` +
    '&result_type=sublocality|neighborhood|locality|administrative_area_level_2|administrative_area_level_1|country' +
    `&key=${encodeURIComponent(key)}`;

  try {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 800); // must be <1s
    const res = await fetch(url, { signal: controller.signal });
    clearTimeout(timer);
    if (!res.ok) return null;
    const payload: any = await res.json();
    if (payload.status !== 'OK' || !payload.results?.length) return null;

    const primary = payload.results[0];
    const comps = primary.address_components || [];

    // Parse village-like names: sublocality, neighborhood, ward
    const village =
      pick(comps, ['sublocality_level_1', 'sublocality', 'neighborhood', 'ward']) ||
      pick(comps, ['sublocality_level_2', 'sublocality_level_3']) ||
      null;
    const city = pick(comps, ['locality', 'postal_town']) || null;
    const region = pick(comps, ['administrative_area_level_1', 'administrative_area_level_2']) || null;
    const countryComp = comps.find((c: any) => c.types.includes('country'));
    const country = countryComp?.long_name ?? null;
    const countryCode = countryComp?.short_name ?? null;

    const parts = [village, city, region, country].filter(Boolean) as string[];
    const shortParts = [village || city, region || country].filter(Boolean) as string[];
    const dedupe = (a: string[]) => a.filter((p, i) => i === 0 || p !== a[i - 1]);

    const result: ReverseResult = {
      village,
      city,
      region,
      country,
      countryCode,
      accuracyKm: 0,
      label: dedupe(parts).join(', '),
      shortLabel: dedupe(shortParts).join(', '),
      source: 'google',
    };

    if (cache.size >= CACHE_MAX) {
      const first = cache.keys().next().value;
      if (first) cache.delete(first);
    }
    cache.set(k, { at: Date.now(), value: result });
    return result;
  } catch {
    return null;
  }
}

export function clearGeocodeCache() {
  cache.clear();
}
