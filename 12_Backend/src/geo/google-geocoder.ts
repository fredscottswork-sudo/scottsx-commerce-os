/**
 * ScottsTechX — Google Maps reverse geocoding provider (fixed)
 *
 * Reliable provider that inspects COMPLETE address response and correctly
 * distinguishes village/neighbourhood/suburb/city/district/region/country.
 *
 * For Uganda: prioritizes containing area (point INSIDE) over nearest centroid.
 * If uncertain between nearby villages, returns low confidence and alternatives
 * instead of confidently claiming wrong village.
 */

import type { ReverseResult } from './gazetteer.js';

interface GoogleComponent {
  long_name: string;
  short_name: string;
  types: string[];
}

interface GoogleResult {
  address_components: GoogleComponent[];
  formatted_address: string;
  types: string[];
  geometry?: {
    location_type?: string; // ROOFTOP, RANGE_INTERPOLATED, GEOMETRIC_CENTER, APPROXIMATE
    bounds?: any;
  };
}

const CACHE_PRECISION = 4;
const CACHE_MAX = 5000;
const TIMEOUT_MS = 2500;
const cache = new Map<string, ReverseResult>();

function remember(key: string, value: ReverseResult) {
  if (cache.size >= CACHE_MAX) {
    const oldest = cache.keys().next().value;
    if (oldest !== undefined) cache.delete(oldest);
  }
  cache.set(key, value);
}

export function googleGeocoderConfigured(): boolean {
  return Boolean(process.env.GOOGLE_MAPS_API_KEY);
}

function pick(components: GoogleComponent[], wanted: string[]): string | null {
  for (const type of wanted) {
    const hit = components.find((c) => c.types.includes(type));
    if (hit) return hit.long_name;
  }
  return null;
}

function pickAll(components: GoogleComponent[], wanted: string[]): string[] {
  const found: string[] = [];
  for (const type of wanted) {
    const hits = components.filter((c) => c.types.includes(type));
    for (const h of hits) {
      if (!found.includes(h.long_name)) found.push(h.long_name);
    }
  }
  return found;
}

/**
 * Detailed parsing of Google address_components into distinct locality levels
 */
function parseComponents(components: GoogleComponent[]) {
  // Most specific to least for each level
  const neighbourhood = pick(components, ['neighborhood', 'sublocality_level_5', 'sublocality_level_4']);
  const suburb = pick(components, ['sublocality_level_3', 'sublocality_level_2', 'sublocality_level_1', 'sublocality']);
  const village = pick(components, ['sublocality_level_1', 'sublocality', 'neighborhood', 'ward', 'administrative_area_level_4']);
  const city = pick(components, ['locality', 'postal_town']);
  const district = pick(components, ['administrative_area_level_3', 'administrative_area_level_2']);
  const region = pick(components, ['administrative_area_level_1']);
  const countryComp = components.find((c) => c.types.includes('country'));

  return {
    neighbourhood,
    suburb,
    village: village && village !== city ? village : suburb || neighbourhood,
    city,
    district,
    region,
    country: countryComp?.long_name ?? null,
    countryCode: countryComp?.short_name ?? null,
  };
}

export async function googleReverseGeocode(
  lat: number,
  lng: number,
  gpsAccuracyM?: number
): Promise<ReverseResult | null> {
  const key = process.env.GOOGLE_MAPS_API_KEY;
  if (!key) return null;
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null;

  const cacheKey = `${lat.toFixed(CACHE_PRECISION)},${lng.toFixed(CACHE_PRECISION)}`;
  const hit = cache.get(cacheKey);
  if (hit) return { ...hit, gpsAccuracyM };

  const url =
    'https://maps.googleapis.com/maps/api/geocode/json' +
    `?latlng=${encodeURIComponent(`${lat},${lng}`)}` +
    '&result_type=street_address|premise|sublocality|neighborhood|locality|administrative_area_level_2|administrative_area_level_1' +
    `&key=${encodeURIComponent(key)}`;

  let payload: { status: string; results?: GoogleResult[]; error_message?: string };
  try {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
    const res = await fetch(url, { signal: controller.signal });
    clearTimeout(timer);
    if (!res.ok) return null;
    payload = (await res.json()) as typeof payload;
  } catch {
    return null;
  }

  if (payload.status !== 'OK' || !payload.results?.length) {
    if (payload.status && payload.status !== 'ZERO_RESULTS') {
      console.warn(`[geo] Google geocoding returned ${payload.status}${payload.error_message ? `: ${payload.error_message}` : ''}`);
    }
    return null;
  }

  // Analyze all results for confidence and alternatives
  const results = payload.results;
  const primary = results[0];
  const allComponents = results.flatMap((r) => r.address_components ?? []);

  // Parse primary result
  const parsed = parseComponents(primary.address_components);

  // Collect alternative village names from other results
  const alternativeVillages = new Set<string>();
  for (const r of results.slice(1, 5)) {
    const p = parseComponents(r.address_components);
    if (p.village && p.village !== parsed.village) alternativeVillages.add(p.village);
    if (p.suburb && p.suburb !== parsed.suburb && p.suburb !== parsed.village) alternativeVillages.add(p.suburb);
    if (p.neighbourhood && p.neighbourhood !== parsed.neighbourhood && p.neighbourhood !== parsed.village) alternativeVillages.add(p.neighbourhood);
  }

  // Also collect all sublocality/neighbourhood types as potential villages
  const allSublocalities = pickAll(allComponents, ['sublocality_level_1', 'sublocality', 'neighborhood', 'sublocality_level_2', 'sublocality_level_3']);
  for (const name of allSublocalities) {
    if (name !== parsed.village && name !== parsed.suburb && name !== parsed.city) {
      alternativeVillages.add(name);
    }
  }

  // Confidence calculation
  let confidence = 0.9;
  let isUncertain = false;

  // Location type affects confidence
  const locType = primary.geometry?.location_type;
  if (locType === 'APPROXIMATE') {
    confidence = 0.4;
    isUncertain = true;
  } else if (locType === 'GEOMETRIC_CENTER') {
    confidence = 0.6;
    isUncertain = true;
  } else if (locType === 'RANGE_INTERPOLATED') {
    confidence = 0.7;
  } else if (locType === 'ROOFTOP') {
    confidence = 0.95;
  }

  // If we have alternative villages, we're uncertain between nearby localities
  if (alternativeVillages.size > 0) {
    confidence = Math.min(confidence, 0.6);
    isUncertain = true;
  }
  if (alternativeVillages.size >= 2) {
    confidence = Math.min(confidence, 0.4);
    isUncertain = true;
  }

  // If no specific village/suburb, only city — uncertain
  if (!parsed.village && !parsed.suburb && parsed.city) {
    confidence = 0.5;
    isUncertain = true;
  }

  // GPS accuracy affects confidence
  if (gpsAccuracyM !== undefined) {
    if (gpsAccuracyM > 1000) {
      confidence = Math.min(confidence, 0.2);
      isUncertain = true;
    } else if (gpsAccuracyM > 100) {
      confidence = Math.min(confidence, 0.6);
      isUncertain = true;
    }
  }

  // For Uganda: if we have both village and city, prefer village as most appropriate locality
  // But if confidence low, don't claim village confidently
  let village = parsed.village ?? null;
  let displayLabel: string;
  const city = parsed.city ?? null;
  const district = parsed.district ?? null;
  const region = parsed.region ?? null;
  const country = parsed.country ?? null;

  const labelParts = [village, city, district, region, country].filter((p, i, arr) => Boolean(p) && arr.indexOf(p) === i) as string[];
  const shortParts = [village || city, region || country].filter(Boolean) as string[];

  const label = labelParts.join(', ');
  const shortLabel = shortParts.join(', ');

  if (isUncertain && village) {
    displayLabel = `Location near ${village}`;
  } else if (isUncertain && city) {
    displayLabel = `Location near ${city}`;
  } else if (!village && city) {
    displayLabel = `Location detected — confirm your village`;
  } else {
    displayLabel = label;
  }

  // For Uganda specifically: ensure we don't return a distant district as village
  // If village equals district or region, it's not a village
  if (village && (village === district || village === region)) {
    village = parsed.suburb || parsed.neighbourhood || null;
  }

  const alternatives = Array.from(alternativeVillages).slice(0, 3).map(name => ({
    name,
    distanceKm: 0, // Google doesn't give distance, it's containing area
    type: 'village',
  }));

  const result: ReverseResult = {
    village,
    neighbourhood: parsed.neighbourhood ?? null,
    suburb: parsed.suburb ?? null,
    city,
    district,
    region,
    country,
    countryCode: parsed.countryCode ?? null,
    accuracyKm: 0,
    label,
    shortLabel,
    displayLabel,
    source: 'google',
    confidence: Math.round(confidence * 100) / 100,
    isUncertain,
    requiresConfirmation: isUncertain || confidence < 0.7,
    alternatives,
    gpsAccuracyM,
  };

  remember(cacheKey, result);
  return result;
}

export function clearGeocodeCache() {
  cache.clear();
}
