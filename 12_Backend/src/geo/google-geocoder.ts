/**
 * ScottsTechX — Google Maps reverse geocoding.
 * Scans every result's components for the smallest named area (neighborhood /
 * sublocality) so the village is found even when the top result is a street.
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

  // No result_type filter: Google returns several results ordered from most
  // to least specific (street address → sublocality → locality …). Scanning
  // ALL of them for the smallest component gives the village even when the
  // first result is a plain street address that lacks a sublocality.
  const url =
    'https://maps.googleapis.com/maps/api/geocode/json' +
    `?latlng=${encodeURIComponent(`${lat},${lng}`)}` +
    '&language=en' +
    `&key=${encodeURIComponent(key)}`;

  try {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 1500);
    const res = await fetch(url, { signal: controller.signal });
    clearTimeout(timer);
    if (!res.ok) return null;
    const payload: any = await res.json();
    if (payload.status !== 'OK' || !payload.results?.length) return null;

    const all: any[] = payload.results.flatMap((r: any) => r.address_components || []);
    const first = (types: string[]) => pick(all, types);

    const village =
      first(['neighborhood', 'sublocality_level_3', 'sublocality_level_2']) ||
      first(['sublocality_level_1', 'sublocality', 'ward']) ||
      null;
    const suburbRaw = first(['sublocality_level_1', 'sublocality']);
    const suburb = suburbRaw && suburbRaw !== village ? suburbRaw : null;
    const road = first(['route']);
    const city = first(['locality', 'postal_town', 'administrative_area_level_3']) || null;
    const region = first(['administrative_area_level_1', 'administrative_area_level_2']) || null;
    const countryComp = all.find((c: any) => c.types.includes('country'));
    const country = countryComp?.long_name ?? null;
    const countryCode = countryComp?.short_name ?? null;

    const dedupe = (a: (string | null)[]) => {
      const out: string[] = [];
      for (const p of a) if (p && !out.some((x) => x.toLowerCase() === p.toLowerCase())) out.push(p);
      return out;
    };

    const result: ReverseResult = {
      village,
      suburb,
      road,
      city,
      region,
      country,
      countryCode,
      accuracyKm: 0,
      label: dedupe([village, suburb, city, region, country]).join(', '),
      shortLabel: dedupe([village || city, village && city ? city : region || country]).join(', '),
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
