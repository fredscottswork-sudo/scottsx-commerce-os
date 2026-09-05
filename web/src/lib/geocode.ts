/**
 * Browser-side reverse geocoding.
 *
 * The API also resolves places, but server-side lookups can fail for reasons
 * the user never sees (cloud egress blocked, Nominatim rate-limiting the
 * host's IP). The buyer's own phone has none of those problems: one request
 * from their IP to OpenStreetMap (and Google, when a key is configured) is
 * exactly what the usage policies expect. So the Nearby page asks BOTH the
 * API and the browser, and keeps whichever one actually knows the village.
 */
import type { Place } from '../api/types';

const OSM_URL = 'https://nominatim.openstreetmap.org/reverse';
const GOOGLE_KEY = (import.meta as any).env?.VITE_GOOGLE_MAPS_KEY as string | undefined;

type Address = Record<string, string | undefined>;

function pick(a: Address, keys: string[]): string | null {
  for (const k of keys) {
    const v = a[k]?.trim();
    if (v) return v;
  }
  return null;
}
function cleanCity(v: string | null): string | null {
  if (!v) return null;
  const c = v.replace(/\s+(Capital City|City Council|Municipality|Town Council)$/i, '').replace(/\s+City$/i, '').trim();
  return c || v;
}
function dedupe(parts: (string | null | undefined)[]): string[] {
  const out: string[] = [];
  for (const p of parts) if (p && !out.some((x) => x.toLowerCase() === p.toLowerCase())) out.push(p);
  return out;
}
function build(
  village: string | null, suburb: string | null, road: string | null, city: string | null,
  region: string | null, country: string | null, countryCode: string | null, source: Place['source']
): Place {
  const v = village && city && village.toLowerCase() === city.toLowerCase() ? null : village;
  const sb = suburb && suburb !== v && !(city && suburb.toLowerCase() === city.toLowerCase()) ? suburb : null;
  const finalVillage = v ?? sb;
  const finalSuburb = v && sb ? sb : null;
  return {
    village: finalVillage,
    suburb: finalSuburb,
    road,
    city,
    region,
    country,
    countryCode,
    accuracyKm: 0,
    label: dedupe([finalVillage, finalSuburb, city, region, country]).join(', '),
    shortLabel: dedupe([finalVillage || city, finalVillage && city ? city : region || country]).join(', '),
    source,
  };
}

/** OpenStreetMap / Nominatim (CORS-enabled, no key). */
export async function osmReverse(lat: number, lng: number, signal?: AbortSignal): Promise<Place | null> {
  try {
    const u = new URL(OSM_URL);
    u.search = new URLSearchParams({
      format: 'jsonv2', lat: String(lat), lon: String(lng), zoom: '18', addressdetails: '1', layer: 'address', 'accept-language': 'en',
    }).toString();
    const res = await fetch(u.toString(), { signal, headers: { Accept: 'application/json' } });
    if (!res.ok) return null;
    const d: any = await res.json();
    const a: Address | undefined = d?.address;
    if (!a) return null;
    const village = pick(a, ['neighbourhood', 'village', 'hamlet', 'quarter', 'residential', 'isolated_dwelling', 'allotments']);
    const suburb = pick(a, ['suburb', 'city_district', 'borough', 'district']);
    const city = cleanCity(pick(a, ['city', 'town', 'municipality'])) || cleanCity(pick(a, ['county', 'state_district']));
    const region = pick(a, ['state', 'region', 'province', 'state_district', 'county']);
    return build(village, suburb, pick(a, ['road', 'pedestrian', 'footway', 'path']), city, region,
      a.country?.trim() || null, a.country_code ? a.country_code.toUpperCase() : null, 'osm');
  } catch {
    return null;
  }
}

/** Google Geocoding — only when VITE_GOOGLE_MAPS_KEY is set at build time. */
export async function googleReverse(lat: number, lng: number, signal?: AbortSignal): Promise<Place | null> {
  if (!GOOGLE_KEY) return null;
  try {
    const u = `https://maps.googleapis.com/maps/api/geocode/json?latlng=${lat},${lng}&language=en&key=${encodeURIComponent(GOOGLE_KEY)}`;
    const res = await fetch(u, { signal });
    if (!res.ok) return null;
    const d: any = await res.json();
    if (d.status !== 'OK' || !d.results?.length) return null;
    const all: any[] = d.results.flatMap((r: any) => r.address_components || []);
    const first = (types: string[]) => {
      for (const t of types) { const hit = all.find((c) => c.types?.includes(t)); if (hit) return hit.long_name as string; }
      return null;
    };
    const village = first(['neighborhood', 'sublocality_level_3', 'sublocality_level_2']) || first(['sublocality_level_1', 'sublocality', 'ward']);
    const suburbRaw = first(['sublocality_level_1', 'sublocality']);
    const cc = all.find((c) => c.types?.includes('country'));
    return build(village, suburbRaw && suburbRaw !== village ? suburbRaw : null, first(['route']),
      first(['locality', 'postal_town', 'administrative_area_level_3']),
      first(['administrative_area_level_1', 'administrative_area_level_2']),
      cc?.long_name ?? null, cc?.short_name ?? null, 'google');
  } catch {
    return null;
  }
}

/** How exact a place is: 2 = village + suburb, 1 = village, 0 = city or less. */
export function placeRank(p: Place | null | undefined): number {
  if (!p?.village) return 0;
  return p.suburb ? 2 : 1;
}

/** Field-wise merge in the given priority; the finest village wins. */
export function mergePlaces(...sources: (Place | null | undefined)[]): Place | null {
  const known = sources.filter((s): s is Place => Boolean(s)).sort((a, b) => placeRank(b) - placeRank(a));
  if (!known.length) return null;
  const first = <K extends keyof Place>(k: K): Place[K] | null => {
    for (const s of known) { const v = s[k]; if (v !== null && v !== undefined && v !== '') return v; }
    return null;
  };
  const city = first('city');
  const lead = known[0];
  return build(lead.village ?? null, lead.suburb ?? null, first('road') ?? null, city, first('region'), first('country'), first('countryCode'), lead.source);
}

/**
 * Best place for a fix from every source we have, within `budgetMs`.
 * `serverPromise` is the API's own answer (may be city-only).
 */
export async function resolveBestPlace(
  lat: number, lng: number, serverPromise: Promise<Place | null>, budgetMs = 6000
): Promise<Place | null> {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), budgetMs);
  try {
    const [server, osm, google] = await Promise.all([
      serverPromise.catch(() => null),
      osmReverse(lat, lng, ctrl.signal),
      googleReverse(lat, lng, ctrl.signal),
    ]);
    return mergePlaces(osm, google, server);
  } finally {
    clearTimeout(timer);
  }
}

/* ── Pin naming for lists (rate-limited, cached) ───────────────────────── */
const PIN_CACHE_KEY = 'stx.geo.pins.v1';
type PinCache = Record<string, { v: string | null; l: string; at: number }>;
function pinKey(lat: number, lng: number) { return `${lat.toFixed(3)}:${lng.toFixed(3)}`; }
function loadPinCache(): PinCache {
  try { return JSON.parse(localStorage.getItem(PIN_CACHE_KEY) || '{}'); } catch { return {}; }
}
function savePinCache(c: PinCache) {
  try {
    const entries = Object.entries(c).sort((a, b) => b[1].at - a[1].at).slice(0, 400);
    localStorage.setItem(PIN_CACHE_KEY, JSON.stringify(Object.fromEntries(entries)));
  } catch { /* quota */ }
}
let pinChain: Promise<unknown> = Promise.resolve();

/**
 * Resolve villages for a list of pins, one request per second (public OSM
 * policy), most-relevant first, from cache when possible. `onUpdate` fires
 * per pin so cards fill in progressively. Returns a cancel function.
 */
export function namePins(
  pins: { id: string; lat: number; lng: number }[],
  onUpdate: (id: string, village: string | null, shortLabel: string) => void,
  max = 12
): () => void {
  let cancelled = false;
  const cache = loadPinCache();
  const todo: typeof pins = [];
  for (const p of pins.slice(0, max)) {
    const hit = cache[pinKey(p.lat, p.lng)];
    if (hit && Date.now() - hit.at < 30 * 86_400_000) onUpdate(p.id, hit.v, hit.l);
    else todo.push(p);
  }
  for (const p of todo) {
    pinChain = pinChain.then(async () => {
      if (cancelled) return;
      const place = await osmReverse(p.lat, p.lng);
      if (cancelled) return;
      if (place) {
        cache[pinKey(p.lat, p.lng)] = { v: place.village, l: place.shortLabel, at: Date.now() };
        savePinCache(cache);
        onUpdate(p.id, place.village, place.shortLabel);
      }
      await new Promise((r) => setTimeout(r, 1100));
    });
  }
  return () => { cancelled = true; };
}
