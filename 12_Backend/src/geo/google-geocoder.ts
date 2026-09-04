/**
 * ScottsTechX — Google Maps reverse geocoding provider.
 *
 * Why this exists
 * ---------------
 * The offline gazetteer answers instantly and works everywhere, but it can
 * only ever return the nearest place it has heard of. Inside a city that is a
 * guess: a fix in Kawempe resolved to "Kampala" 10.67 km away, and a fix in
 * Kabalagala came back as "Kampala Central". The user asked for the real place
 * name for the coordinate, not the closest entry in a table.
 *
 * Google's Geocoding API knows actual administrative boundaries and named
 * sublocalities, so it answers "Kabalagala" because the point is INSIDE
 * Kabalagala — not because Kabalagala happens to be the nearest centroid.
 *
 * How it is wired
 * ---------------
 *   GOOGLE_MAPS_API_KEY set  → Google is tried first, gazetteer is the fallback
 *   key absent / call fails  → gazetteer answers exactly as before
 *
 * The endpoint therefore never breaks and never blocks on the network: a
 * missing key, a quota error, a timeout or an outage all degrade to the
 * offline answer instead of failing the request. `source` on the response says
 * which engine actually answered, so the UI and the tests can tell them apart.
 *
 * Cost control: results are cached by coordinate rounded to ~11 m. A phone
 * sending continuous position updates while the buyer walks around therefore
 * costs a handful of calls, not one per fix.
 */
import type { ReverseResult } from './gazetteer.js';

/** Google address_component types, most specific first. */
const VILLAGE_TYPES = [
  'sublocality_level_1',
  'sublocality',
  'neighborhood',
  'ward',
  'administrative_area_level_4',
  'administrative_area_level_3',
];
const CITY_TYPES = ['locality', 'postal_town', 'administrative_area_level_2'];
const REGION_TYPES = ['administrative_area_level_1'];

interface GoogleComponent {
  long_name: string;
  short_name: string;
  types: string[];
}

interface GoogleResult {
  address_components: GoogleComponent[];
  formatted_address: string;
  types: string[];
}

/** ~11 m of latitude — fine enough to distinguish streets, coarse enough to cache. */
const CACHE_PRECISION = 4;
const CACHE_MAX = 5000;
const TIMEOUT_MS = 2500;

const cache = new Map<string, ReverseResult>();

/** Simple bounded LRU-ish cache: oldest key evicted once full. */
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

/**
 * Resolve a coordinate through Google. Returns null when the provider is not
 * configured, the call fails, or Google has nothing for the point — every one
 * of those cases means "let the gazetteer answer".
 */
export async function googleReverseGeocode(
  lat: number,
  lng: number
): Promise<ReverseResult | null> {
  const key = process.env.GOOGLE_MAPS_API_KEY;
  if (!key) return null;
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null;

  const cacheKey = `${lat.toFixed(CACHE_PRECISION)},${lng.toFixed(CACHE_PRECISION)}`;
  const hit = cache.get(cacheKey);
  if (hit) return hit;

  const url =
    'https://maps.googleapis.com/maps/api/geocode/json' +
    `?latlng=${encodeURIComponent(`${lat},${lng}`)}` +
    // Ask for the granular types first; Google orders results most-specific first.
    '&result_type=street_address|premise|sublocality|neighborhood|locality|administrative_area_level_3' +
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
    // Network down, DNS blocked, timeout — the caller falls back offline.
    return null;
  }

  // ZERO_RESULTS is a legitimate answer (middle of a lake); everything else is
  // a configuration or quota problem. Both mean "fall back", but only the
  // latter is worth surfacing to the operator.
  if (payload.status !== 'OK' || !payload.results?.length) {
    if (payload.status && payload.status !== 'ZERO_RESULTS') {
      console.warn(
        `[geo] Google geocoding returned ${payload.status}` +
          (payload.error_message ? `: ${payload.error_message}` : '')
      );
    }
    return null;
  }

  // Merge components across results: the most specific result may lack the
  // region, which a broader one further down the list carries.
  const all: GoogleComponent[] = payload.results.flatMap((r) => r.address_components ?? []);

  const village = pick(all, VILLAGE_TYPES);
  const city = pick(all, CITY_TYPES);
  const region = pick(all, REGION_TYPES);
  const countryComp = all.find((c) => c.types.includes('country'));

  // Without at least a locality this is no better than the offline answer.
  if (!village && !city) return null;

  const label = [village, city, region, countryComp?.long_name]
    .filter((part, i, arr) => Boolean(part) && arr.indexOf(part) === i)
    .join(', ');

  const result: ReverseResult = {
    village: village && village !== city ? village : null,
    city: city ?? null,
    region: region ?? null,
    country: countryComp?.long_name ?? null,
    countryCode: countryComp?.short_name ?? null,
    // Google resolves the containing area rather than a nearby centroid, so
    // there is no "distance to the named place" to report.
    accuracyKm: 0,
    label,
    shortLabel: [village || city, region || countryComp?.long_name]
      .filter(Boolean)
      .join(', '),
    source: 'google',
  };

  remember(cacheKey, result);
  return result;
}

/** Exposed for tests so one case cannot leak into the next. */
export function clearGeocodeCache() {
  cache.clear();
}
