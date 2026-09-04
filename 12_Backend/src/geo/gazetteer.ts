/**
 * ScottsTechX — offline reverse geocoder (fast fallback)
 * Turns lat/lng into village/city/region/country instantly, no API.
 * Used when Google key missing or Google times out (>800ms).
 */
import { readFileSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

function gazetteerPath(): string {
  const candidates = [
    path.join(__dirname, 'gazetteer.bin'),
    path.join(__dirname, '..', '..', 'src', 'geo', 'gazetteer.bin'),
  ];
  return candidates.find((p) => existsSync(p)) ?? candidates[0];
}

const NO_REGION = 0xffff;
const ROW = 21;
const FLAG_ADMIN = 1;

interface Gazetteer {
  count: number;
  lat: Float32Array;
  lon: Float32Array;
  pop: Int32Array;
  nameOff: Uint32Array;
  region: Uint16Array;
  country: Uint16Array;
  flags: Uint8Array;
  regions: string[];
  countryNames: string[];
  countryIso: string[];
  blob: Buffer;
}

let cache: Gazetteer | null = null;
let loadFailed = false;

function readName(blob: Buffer, off: number): string {
  const len = blob.readUInt16LE(off);
  return blob.toString('utf8', off + 2, off + 2 + len);
}

function load(): Gazetteer | null {
  if (cache) return cache;
  if (loadFailed) return null;
  try {
    const buf = readFileSync(gazetteerPath());
    if (buf.toString('ascii', 0, 7) !== 'STXGAZ3') throw new Error('bad magic');
    const count = buf.readUInt32LE(8);
    const lat = new Float32Array(count);
    const lon = new Float32Array(count);
    const pop = new Int32Array(count);
    const nameOff = new Uint32Array(count);
    const region = new Uint16Array(count);
    const country = new Uint16Array(count);
    const flags = new Uint8Array(count);
    let o = 12;
    for (let i = 0; i < count; i++) {
      lat[i] = buf.readFloatLE(o);
      lon[i] = buf.readFloatLE(o + 4);
      pop[i] = buf.readInt32LE(o + 8);
      nameOff[i] = buf.readUInt32LE(o + 12);
      region[i] = buf.readUInt16LE(o + 16);
      country[i] = buf.readUInt16LE(o + 18);
      flags[i] = buf.readUInt8(o + 20);
      o += ROW;
    }
    const regionCount = buf.readUInt32LE(o);
    o += 4;
    const regions: string[] = new Array(regionCount);
    for (let i = 0; i < regionCount; i++) {
      const len = buf.readUInt16LE(o);
      regions[i] = buf.toString('utf8', o + 2, o + 2 + len);
      o += 2 + len;
    }
    const countryCount = buf.readUInt32LE(o);
    o += 4;
    const countryNames: string[] = new Array(countryCount);
    const countryIso: string[] = new Array(countryCount);
    for (let i = 0; i < countryCount; i++) {
      countryIso[i] = buf.toString('ascii', o, o + 2);
      const len = buf.readUInt16LE(o + 2);
      countryNames[i] = buf.toString('utf8', o + 4, o + 4 + len);
      o += 4 + len;
    }
    const blobLen = buf.readUInt32LE(o);
    o += 4;
    const blob = buf.subarray(o, o + blobLen);
    cache = { count, lat, lon, pop, nameOff, region, country, flags, regions, countryNames, countryIso, blob };
    return cache;
  } catch (err) {
    loadFailed = true;
    console.warn(`[geo] gazetteer unavailable (${(err as Error).message})`);
    return null;
  }
}

export function geocoderReady(): boolean {
  return load() !== null;
}

export interface ReverseResult {
  village: string | null;
  city: string | null;
  region: string | null;
  country: string | null;
  countryCode: string | null;
  accuracyKm: number;
  label: string;
  shortLabel: string;
  source: 'offline-gazetteer' | 'google';
}

function haversineKm(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6371;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos((lat1 * Math.PI) / 180) * Math.cos((lat2 * Math.PI) / 180) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(a));
}

function lowerBound(lat: Float32Array, target: number): number {
  let lo = 0;
  let hi = lat.length;
  while (lo < hi) {
    const mid = (lo + hi) >>> 1;
    if (lat[mid] < target) lo = mid + 1;
    else hi = mid;
  }
  return lo;
}

const CITY_POP = 40_000;
const CITY_RADIUS_KM = 35;
const CITY_TIERS: [number, number][] = [
  [8, CITY_POP],
  [18, 150_000],
  [35, 1_000_000],
];
const SELF_SUFFICIENT_POP = 25_000;
const MAX_SANE_KM = 150;

export function reverseGeocode(lat: number, lng: number): ReverseResult | null {
  const g = load();
  if (!g) return null;
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null;

  const latPad = CITY_RADIUS_KM / 111.32 + 0.02;
  const start = lowerBound(g.lat, lat - latPad);
  const end = lowerBound(g.lat, lat + latPad);
  const cosLat = Math.cos((lat * Math.PI) / 180) || 1e-6;

  let nearestIdx = -1;
  let nearestKm = Infinity;
  let anyIdx = -1;
  let anyKm = Infinity;
  let cityIdx = -1;
  let cityScore = -1;

  for (let i = start; i < end; i++) {
    const dx = g.lat[i] - lat;
    const dy = (g.lon[i] - lng) * cosLat;
    if (Math.sqrt(dx * dx + dy * dy) * 111.32 > CITY_RADIUS_KM) continue;
    const km = haversineKm(lat, lng, g.lat[i], g.lon[i]);
    const isAdmin = (g.flags[i] & FLAG_ADMIN) !== 0;
    if (km < anyKm) { anyKm = km; anyIdx = i; }
    if (!isAdmin && km < nearestKm) { nearestKm = km; nearestIdx = i; }
    if (!isAdmin) {
      const pop = g.pop[i];
      let qualifies = false;
      for (const [maxKm, minPop] of CITY_TIERS) {
        if (km <= maxKm && pop >= minPop) { qualifies = true; break; }
      }
      if (qualifies) {
        const score = pop / (1 + km * km);
        if (score > cityScore) { cityScore = score; cityIdx = i; }
      }
    }
  }

  if (nearestIdx === -1 && anyIdx !== -1) { nearestIdx = anyIdx; nearestKm = anyKm; }

  if (nearestIdx === -1) {
    const wideStart = lowerBound(g.lat, lat - 3);
    const wideEnd = lowerBound(g.lat, lat + 3);
    for (let i = wideStart; i < wideEnd; i++) {
      const km = haversineKm(lat, lng, g.lat[i], g.lon[i]);
      if (km < nearestKm) { nearestKm = km; nearestIdx = i; }
    }
  }
  if (nearestIdx === -1) return null;

  const localName = readName(g.blob, g.nameOff[nearestIdx]);
  let cityName = cityIdx >= 0 ? readName(g.blob, g.nameOff[cityIdx]) : null;

  if (g.pop[nearestIdx] >= SELF_SUFFICIENT_POP) {
    cityName = localName;
    cityIdx = nearestIdx;
  }

  const anchor = cityIdx >= 0 ? cityIdx : nearestIdx;
  const regionId = g.region[anchor];
  const region = regionId === NO_REGION ? null : g.regions[regionId] ?? null;
  const countryIdx = g.country[anchor];
  const country = g.countryNames[countryIdx] ?? null;
  const countryCode = g.countryIso[countryIdx] ?? null;

  const sameAsCity = cityName !== null && cityName === localName;
  let village: string | null = sameAsCity ? null : localName;
  const city = cityName ?? localName;

  const tooFar = nearestKm > MAX_SANE_KM;
  if (tooFar) village = null;

  const parts = (tooFar ? [region, country] : [village, city, region, country]).filter(Boolean) as string[];
  const shortParts = (tooFar ? [country] : [village ?? city, region ?? country]).filter(Boolean) as string[];
  const dedupe = (a: string[]) => a.filter((p, i) => i === 0 || p !== a[i - 1]);

  return {
    village,
    city: tooFar ? null : city,
    region,
    country,
    countryCode,
    accuracyKm: Math.round(nearestKm * 100) / 100,
    label: dedupe(parts).join(', '),
    shortLabel: dedupe(shortParts).join(', '),
    source: 'offline-gazetteer',
  };
}
