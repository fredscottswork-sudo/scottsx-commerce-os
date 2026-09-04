/**
 * ScottsTechX — offline reverse geocoder (fixed for village accuracy)
 *
 * Turns a raw GPS fix into a human place, but NEVER confidently claims a
 * wrong village. Separation of concerns:
 *   - GPS (lat/lng) is ALWAYS the source for distance/sorting
 *   - Village name is a HUMAN label that may be uncertain
 *
 * Design:
 *   - Backed by gazetteer.bin (167k places)
 *   - Returns confidence score and alternatives
 *   - If nearest settlement is far or second-nearest is similarly close,
 *     marks as uncertain and does NOT confidently return a village
 *   - Does NOT use hardcoded neighbourhood table as authoritative source
 *     (that table is legacy and would violate "do not hardcode village")
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
    if (buf.toString('ascii', 0, 7) !== 'STXGAZ3') {
      throw new Error('bad magic — rebuild with scripts/build-gazetteer.mjs');
    }
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
  neighbourhood?: string | null;
  suburb?: string | null;
  city: string | null;
  district?: string | null;
  region: string | null;
  country: string | null;
  countryCode: string | null;
  accuracyKm: number;
  label: string;
  shortLabel: string;
  displayLabel?: string;
  source: 'offline-gazetteer' | 'google' | 'user_confirmed';
  confidence: number; // 0..1
  isUncertain: boolean;
  requiresConfirmation?: boolean;
  alternatives?: Array<{ name: string; distanceKm: number; type: string }>;
  gpsAccuracyM?: number;
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
const VILLAGE_MAX_KM = 6;
const CITY_MAX_KM = 25;

/**
 * Find nearest N candidates for confidence estimation
 */
function findNearestCandidates(lat: number, lng: number, limit = 5): Array<{ idx: number; km: number; name: string; pop: number; isAdmin: boolean }> {
  const g = load();
  if (!g) return [];
  const latPad = CITY_RADIUS_KM / 111.32 + 0.02;
  const start = lowerBound(g.lat, lat - latPad);
  const end = lowerBound(g.lat, lat + latPad);
  const cosLat = Math.cos((lat * Math.PI) / 180) || 1e-6;
  const cands: Array<{ idx: number; km: number; name: string; pop: number; isAdmin: boolean }> = [];
  for (let i = start; i < end; i++) {
    const dx = g.lat[i] - lat;
    const dy = (g.lon[i] - lng) * cosLat;
    if (Math.sqrt(dx * dx + dy * dy) * 111.32 > CITY_RADIUS_KM) continue;
    const km = haversineKm(lat, lng, g.lat[i], g.lon[i]);
    const isAdmin = (g.flags[i] & FLAG_ADMIN) !== 0;
    cands.push({ idx: i, km, name: readName(g.blob, g.nameOff[i]), pop: g.pop[i], isAdmin });
  }
  cands.sort((a, b) => a.km - b.km);
  return cands.slice(0, limit);
}

export function reverseGeocode(lat: number, lng: number, gpsAccuracyM?: number): ReverseResult | null {
  const g = load();
  if (!g) return null;
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null;

  const candidates = findNearestCandidates(lat, lng, 10);
  if (candidates.length === 0) {
    // widen search for country only
    const wideStart = lowerBound(g.lat, lat - 3);
    const wideEnd = lowerBound(g.lat, lat + 3);
    let bestIdx = -1;
    let bestKm = Infinity;
    for (let i = wideStart; i < wideEnd; i++) {
      const km = haversineKm(lat, lng, g.lat[i], g.lon[i]);
      if (km < bestKm) { bestKm = km; bestIdx = i; }
    }
    if (bestIdx === -1) return null;
    const countryIdx = g.country[bestIdx];
    return {
      village: null,
      city: null,
      region: null,
      country: g.countryNames[countryIdx] ?? null,
      countryCode: g.countryIso[countryIdx] ?? null,
      accuracyKm: Math.round(bestKm * 100) / 100,
      label: g.countryNames[countryIdx] ?? 'Unknown',
      shortLabel: g.countryNames[countryIdx] ?? 'Unknown',
      source: 'offline-gazetteer',
      confidence: 0.2,
      isUncertain: true,
      requiresConfirmation: true,
      displayLabel: `Location detected — confirm your village`,
      gpsAccuracyM,
    };
  }

  // Filter out admin for village candidates
  const settlementCands = candidates.filter(c => !c.isAdmin);
  const nearest = settlementCands[0] ?? candidates[0];
  const second = settlementCands[1];

  let nearestKm = nearest.km;
  let nearestIdx = nearest.idx;

  // Find best city
  let cityIdx = -1;
  let cityScore = -1;
  for (const c of candidates) {
    if (c.isAdmin) continue;
    let qualifies = false;
    for (const [maxKm, minPop] of CITY_TIERS) {
      if (c.km <= maxKm && c.pop >= minPop) { qualifies = true; break; }
    }
    if (qualifies) {
      const score = c.pop / (1 + c.km * c.km);
      if (score > cityScore) { cityScore = score; cityIdx = c.idx; }
    }
  }

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
  let city: string | null = cityName ?? localName;

  const tooFar = nearestKm > MAX_SANE_KM;
  if (tooFar) village = null;

  // Confidence logic
  let confidence = 1;
  let isUncertain = false;
  let requiresConfirmation = false;

  // If village is far, low confidence
  if (nearestKm > VILLAGE_MAX_KM) {
    village = null;
    confidence = 0.3;
    isUncertain = true;
    requiresConfirmation = true;
  } else if (nearestKm > 2) {
    confidence = Math.max(0.4, 1 - nearestKm / 10);
    if (nearestKm > 3) {
      isUncertain = true;
      requiresConfirmation = true;
    }
  }

  // If second nearest is close to first, uncertain between villages
  if (second && village) {
    const diff = second.km - nearestKm;
    if (diff < 0.5 && second.km <= VILLAGE_MAX_KM) {
      // Two villages almost equally close — uncertain
      confidence = Math.min(confidence, 0.5);
      isUncertain = true;
      requiresConfirmation = true;
    } else if (diff < 1.0) {
      confidence = Math.min(confidence, 0.7);
      isUncertain = true;
    }
  }

  // GPS accuracy affects confidence
  if (gpsAccuracyM !== undefined) {
    if (gpsAccuracyM > 1000) {
      confidence = Math.min(confidence, 0.2);
      isUncertain = true;
      requiresConfirmation = true;
    } else if (gpsAccuracyM > 100) {
      confidence = Math.min(confidence, 0.6);
      isUncertain = true;
    }
  }

  if (!tooFar && nearestKm > CITY_MAX_KM) {
    const cityKm = cityIdx >= 0 ? haversineKm(lat, lng, g.lat[cityIdx], g.lon[cityIdx]) : nearestKm;
    if (cityKm > CITY_MAX_KM) {
      city = null;
    }
  }

  const effCity = tooFar ? null : city;
  const parts = (tooFar ? [region, country] : [village, effCity, region, country]).filter(Boolean) as string[];
  const shortParts = (tooFar ? [country] : [village ?? effCity ?? region, region ?? country]).filter(Boolean) as string[];
  const dedupe = (a: string[]) => a.filter((p, i) => i === 0 || p !== a[i - 1]);

  const label = dedupe(parts).join(', ');
  const shortLabel = dedupe(shortParts).join(', ');

  let displayLabel = label;
  if (isUncertain && village) {
    displayLabel = `Location near ${village}`;
  } else if (isUncertain && !village && effCity) {
    displayLabel = `Location near ${effCity}`;
  } else if (requiresConfirmation && !village) {
    displayLabel = `Location detected — confirm your village`;
  }

  const alternatives = settlementCands.slice(0, 3).map(c => ({
    name: c.name,
    distanceKm: Math.round(c.km * 100) / 100,
    type: c.pop >= SELF_SUFFICIENT_POP ? 'city' : 'village',
  }));

  return {
    village,
    city: effCity,
    region,
    country,
    countryCode,
    accuracyKm: Math.round(nearestKm * 100) / 100,
    label,
    shortLabel,
    displayLabel,
    source: 'offline-gazetteer',
    confidence: Math.round(confidence * 100) / 100,
    isUncertain,
    requiresConfirmation: isUncertain || requiresConfirmation,
    alternatives,
    gpsAccuracyM,
  };
}

/**
 * Search villages/towns by name for user correction
 */
export function searchPlaces(query: string, limit = 10): Array<{ name: string; city: string | null; region: string | null; country: string | null; lat: number; lng: number; type: string }> {
  const g = load();
  if (!g || !query.trim()) return [];
  const q = query.trim().toLowerCase();
  const results: Array<{ name: string; city: string | null; region: string | null; country: string | null; lat: number; lng: number; type: string; score: number }> = [];
  // Simple linear scan — gazetteer is 167k, okay for search with limit
  for (let i = 0; i < g.count; i++) {
    if (g.flags[i] & FLAG_ADMIN) continue;
    const name = readName(g.blob, g.nameOff[i]);
    if (!name.toLowerCase().includes(q)) continue;
    const regionId = g.region[i];
    const region = regionId === NO_REGION ? null : g.regions[regionId] ?? null;
    const countryIdx = g.country[i];
    const country = g.countryNames[countryIdx] ?? null;
    // Score by population and exact match
    let score = g.pop[i];
    if (name.toLowerCase() === q) score += 1000000;
    else if (name.toLowerCase().startsWith(q)) score += 100000;
    results.push({
      name,
      city: null,
      region,
      country,
      lat: g.lat[i],
      lng: g.lon[i],
      type: g.pop[i] >= SELF_SUFFICIENT_POP ? 'city' : 'village',
      score,
    });
    if (results.length > 1000) break; // prevent too much scanning
  }
  results.sort((a, b) => b.score - a.score);
  return results.slice(0, limit).map(({ score, ...rest }) => rest);
}
