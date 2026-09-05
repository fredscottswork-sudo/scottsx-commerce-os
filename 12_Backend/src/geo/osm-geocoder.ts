/**
 * ScottsTechX — OpenStreetMap (Nominatim) reverse geocoding.
 *
 * Why a second online provider: Google's reverse geocoder is thin in East
 * Africa below `locality` — for most of Kampala it returns "Kampala, Uganda"
 * and nothing smaller. OpenStreetMap has the LC1-level detail people actually
 * use to say where they are (Nsimbiziwoome → Bukoto → Nakawa → Kampala;
 * Kitooro Central → Entebbe), so it becomes the primary village source and
 * Google / the offline gazetteer fill in whatever it cannot.
 *
 * Verified responses (zoom=18, addressdetails=1, layer=address):
 *   0.3580,32.6050 → neighbourhood "Nsimbiziwoome", suburb "Bukoto", city "Kampala"
 *   0.3745,32.6070 → suburb "Kisaasi", city "Kampala"
 *   0.0512,32.4637 → suburb "Namate", village "Kitooro Central", city "Entebbe City"
 *
 * Provider selection (first configured wins):
 *   • NOMINATIM_URL      – a self-hosted / commercial Nominatim base URL
 *                          (e.g. https://us1.locationiq.com/v1 with
 *                          NOMINATIM_KEY set, or your own instance).
 *   • public OSM         – https://nominatim.openstreetmap.org (usage policy:
 *                          max 1 req/s, identify with a User-Agent). Requests
 *                          are cached at ~110 m resolution and serialised
 *                          through a small queue so bursts never exceed it.
 */
import type { ReverseResult } from './gazetteer.js';

const PUBLIC_BASE = 'https://nominatim.openstreetmap.org';
const USER_AGENT = process.env.NOMINATIM_USER_AGENT || 'ScottsTechX-Marketplace/1.0 (geo@scottstechx.com)';
const TIMEOUT_MS = Number(process.env.NOMINATIM_TIMEOUT_MS || 2500);

const CACHE_MAX = 4000;
const CACHE_TTL = 6 * 60 * 60_000; // places do not move — 6h
const cache = new Map<string, { at: number; value: ReverseResult | null }>();

/** ~110 m grid. A finer key would make every GPS jitter a new request. */
function cacheKey(lat: number, lng: number): string {
  return `${Math.round(lat * 1000) / 1000}:${Math.round(lng * 1000) / 1000}`;
}

export function osmGeocoderConfigured(): boolean {
  return process.env.OSM_GEOCODER !== 'off';
}

function baseUrl(): string {
  return (process.env.NOMINATIM_URL || PUBLIC_BASE).replace(/\/+$/, '');
}

function isPublicOsm(): boolean {
  return baseUrl() === PUBLIC_BASE;
}

/* ── 1 request/second gate for the public endpoint ─────────────────────── */
/* Two-priority queue. `high` = a person asking where THEY are (must answer
   first); `low` = naming seller pins in the background. Public OSM allows
   1 req/s, so a page of 12 pins must never delay the buyer's own village. */
let lastPublicCall = 0;
const highQ: (() => void)[] = [];
const lowQ: (() => void)[] = [];
let pumping = false;
async function pump() {
  if (pumping) return;
  pumping = true;
  try {
    while (highQ.length || lowQ.length) {
      const wait = Math.max(0, lastPublicCall + 1050 - Date.now());
      if (wait) await new Promise((r) => setTimeout(r, wait));
      const next = highQ.shift() ?? lowQ.shift();
      if (!next) break;
      lastPublicCall = Date.now();
      next();
    }
  } finally {
    pumping = false;
  }
}
function gate<T>(fn: () => Promise<T>, priority: 'high' | 'low'): Promise<T> {
  if (!isPublicOsm()) return fn();
  return new Promise<T>((resolve, reject) => {
    (priority === 'high' ? highQ : lowQ).push(() => { fn().then(resolve, reject); });
    void pump();
  });
}
/** How long a caller would wait in the public queue right now (ms). */
export function osmQueueDelayMs(priority: 'high' | 'low'): number {
  if (!isPublicOsm()) return 0;
  const ahead = priority === 'high' ? highQ.length : highQ.length + lowQ.length;
  return Math.max(0, lastPublicCall + 1050 - Date.now()) + ahead * 1050;
}

type Address = Record<string, string | undefined>;

/** First non-empty field, in order of preference. */
function pick(a: Address, keys: string[]): string | null {
  for (const k of keys) {
    const v = a[k]?.trim();
    if (v) return v;
  }
  return null;
}

/** Strip generic suffixes Uganda's OSM data carries ("Kampala Capital City"). */
function cleanCity(v: string | null): string | null {
  if (!v) return null;
  return v.replace(/\s+(Capital City|City Council|Municipality|Town Council)$/i, '').replace(/\s+City$/i, '').trim() || v;
}

/** Map an OSM address block onto the marketplace's place model. */
export function placeFromOsmAddress(a: Address): ReverseResult {
  // Smallest named locality first. In OSM Uganda: neighbourhood ≈ LC1 village
  // or zone, `village` ≈ parish/village, suburb ≈ larger neighbourhood,
  // quarter/hamlet/residential are what rural or informal areas carry.
  const village = pick(a, ['neighbourhood', 'village', 'hamlet', 'quarter', 'residential', 'isolated_dwelling', 'allotments']);
  const suburb = pick(a, ['suburb', 'city_district', 'borough', 'district']);
  const town = pick(a, ['city', 'town', 'municipality']);
  const city = cleanCity(town) || cleanCity(pick(a, ['county', 'state_district']));
  const region = pick(a, ['state', 'region', 'province', 'state_district', 'county']);
  const country = a.country?.trim() || null;
  const countryCode = a.country_code ? a.country_code.toUpperCase() : null;
  const road = pick(a, ['road', 'pedestrian', 'footway', 'path']);

  // The village slot must never be empty when OSM knows *something* smaller
  // than the city: fall through to the suburb, then the road.
  const finalVillage = village ?? suburb ?? null;
  const finalSuburb = village && suburb && suburb !== village ? suburb : null;

  const dedupe = (parts: (string | null)[]) => {
    const out: string[] = [];
    for (const p of parts) if (p && !out.some((x) => x.toLowerCase() === p.toLowerCase())) out.push(p);
    return out;
  };
  const parts = dedupe([finalVillage, finalSuburb, city, region, country]);
  const shortParts = dedupe([finalVillage || city, city && finalVillage ? city : region || country]);

  return {
    village: finalVillage,
    suburb: finalSuburb,
    road,
    city,
    region,
    country,
    countryCode,
    accuracyKm: 0,
    label: parts.join(', '),
    shortLabel: shortParts.join(', '),
    source: 'osm',
  };
}

export async function osmReverseGeocode(lat: number, lng: number, priority: 'high' | 'low' = 'high'): Promise<ReverseResult | null> {
  if (!osmGeocoderConfigured()) return null;
  const k = cacheKey(lat, lng);
  const hit = cache.get(k);
  if (hit && Date.now() - hit.at < CACHE_TTL) return hit.value;

  const params = new URLSearchParams({
    format: 'jsonv2',
    lat: String(lat),
    lon: String(lng),
    zoom: '18',
    addressdetails: '1',
    layer: 'address',
    'accept-language': 'en',
  });
  if (process.env.NOMINATIM_KEY) params.set('key', process.env.NOMINATIM_KEY);
  const url = `${baseUrl()}/reverse?${params.toString()}`;

  const value = await gate(async (): Promise<ReverseResult | null> => {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
    try {
      const res = await fetch(url, {
        signal: controller.signal,
        headers: { 'User-Agent': USER_AGENT, Accept: 'application/json' },
      });
      if (!res.ok) return null;
      const payload: any = await res.json();
      if (!payload || payload.error || !payload.address) return null;
      return placeFromOsmAddress(payload.address as Address);
    } catch {
      return null;
    } finally {
      clearTimeout(timer);
    }
  }, priority);

  if (cache.size >= CACHE_MAX) {
    const first = cache.keys().next().value;
    if (first) cache.delete(first);
  }
  // Failures are cached briefly too, so a flapping upstream does not get
  // hammered once per GPS tick.
  cache.set(k, { at: value ? Date.now() : Date.now() - CACHE_TTL + 30_000, value });
  return value;
}

export function clearOsmGeocodeCache() {
  cache.clear();
}
