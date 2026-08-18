/**
 * ScottsTechX — offline reverse geocoder.
 *
 * Turns a raw GPS fix into a human place: village / city / region / country.
 * The marketplace is global and the Nearby screen resolves a coordinate on
 * every position update, so this must be instant, private, and must never
 * depend on a third-party service being reachable.
 *
 * Backed by src/geo/gazetteer.bin (built by scripts/build-gazetteer.mjs from
 * all-the-cities + country-state-city; 167k places, 99.2% region coverage).
 *
 * Design notes
 *   • Loaded lazily on first lookup, then cached for the process (~85 MB RSS).
 *   • Rows are latitude-sorted, so a lookup binary-searches a latitude band and
 *     only scans plausible candidates — ~5 µs per lookup, not a full scan.
 *   • "Village" vs "city": the nearest settlement is the locality you are in;
 *     a larger nearby settlement becomes the parent city, but only if it is
 *     close enough to plausibly own the spot (see CITY_TIERS) — Entebbe must
 *     not be reported as a village of Kampala.
 *   • Administrative rows ("Kampala District") never fill the village slot.
 */
import { readFileSync, existsSync } from 'node:fs';
import { findNeighbourhood } from './neighbourhoods.js';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/** dist/geo/ (compiled) has no .bin beside it — fall back to the source tree. */
function gazetteerPath(): string {
  const candidates = [
    path.join(__dirname, 'gazetteer.bin'),
    path.join(__dirname, '..', '..', 'src', 'geo', 'gazetteer.bin'),
  ];
  return candidates.find((p) => existsSync(p)) ?? candidates[0];
}

const NO_REGION = 0xffff;
const ROW = 21;
/** Row flag: the name is an administrative container, not a settlement. */
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

    cache = {
      count, lat, lon, pop, nameOff, region, country, flags,
      regions, countryNames, countryIso, blob,
    };
    return cache;
  } catch (err) {
    loadFailed = true;
    console.warn(`[geo] gazetteer unavailable (${(err as Error).message}) — reverse geocoding disabled`);
    return null;
  }
}

/** Is the offline gazetteer present and usable? */
export function geocoderReady(): boolean {
  return load() !== null;
}

export interface ReverseResult {
  /** Smallest named locality at the fix — the "village" / neighbourhood. */
  village: string | null;
  /** The town or city the fix belongs to. */
  city: string | null;
  /** Admin-1 area: region / state / province / district. */
  region: string | null;
  country: string | null;
  countryCode: string | null;
  /** Distance from the fix to the matched locality, km. */
  accuracyKm: number;
  /** "Kabalagala, Kampala, Central Region, Uganda" */
  label: string;
  /** Short two-part form for dense UI: "Kabalagala, Central Region". */
  shortLabel: string;
  source: 'offline-gazetteer';
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

/** First index whose latitude is >= target (rows are latitude-sorted). */
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

/** A place is "a city" (rather than a hamlet) once it passes this population. */
const CITY_POP = 40_000;
/** Widest distance any parent city may be from the fix. */
const CITY_RADIUS_KM = 35;

/**
 * A distant metropolis must not swallow a town with its own identity: standing
 * in Entebbe (pop 63k, 32 km from Kampala) you are in *Entebbe*, not "a village
 * of Kampala". A candidate city must therefore be closer the smaller it is.
 * Each tier is [maxKm, minPopulation].
 */
const CITY_TIERS: [number, number][] = [
  [8, CITY_POP],    // anything sizeable within 8 km is plausibly "your city"
  [18, 150_000],    // further out it must be a proper city
  [35, 1_000_000],  // only a metropolis reaches 35 km
];

/** Population above which a place stands alone and needs no parent city. */
const SELF_SUFFICIENT_POP = 25_000;

/** Beyond this the nearest name is meaningless (open ocean): country only. */
const MAX_SANE_KM = 150;

/**
 * How close the nearest settlement must be before we are willing to say "you
 * are IN this village".
 *
 * The packed gazetteer only keeps places above population 1,000, so most rural
 * villages are simply absent. Without a cap the nearest surviving row wins the
 * village slot no matter how far away it is, and a user standing in an unnamed
 * village 34 km from Butalangu was told they were in Butalangu. That is not a
 * small rounding error — it is a different village, and it made the Nearby
 * screen untrustworthy.
 *
 * Past this radius we stop naming a village and fall back to the city/district,
 * which is still true, just less precise. Being vague is acceptable; being
 * confidently wrong is not.
 */
const VILLAGE_MAX_KM = 6;

/**
 * Same idea one level up: a town only "owns" the fix if it is near enough.
 * Beyond this we report the region only rather than pinning the user to a town
 * they may be 30 km away from.
 */
const CITY_MAX_KM = 25;

/**
 * Resolve a coordinate to village / city / region / country, entirely offline.
 * Returns null only when the gazetteer file is missing or the input is invalid.
 */
export function reverseGeocode(lat: number, lng: number): ReverseResult | null {
  const g = load();
  if (!g) return null;
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null;

  const latPad = CITY_RADIUS_KM / 111.32 + 0.02;
  const start = lowerBound(g.lat, lat - latPad);
  const end = lowerBound(g.lat, lat + latPad);
  const cosLat = Math.cos((lat * Math.PI) / 180) || 1e-6;

  let nearestIdx = -1;   // nearest real settlement → the village
  let nearestKm = Infinity;
  let anyIdx = -1;       // nearest row of any kind → fallback
  let anyKm = Infinity;
  let cityIdx = -1;
  let cityScore = -1;

  for (let i = start; i < end; i++) {
    // Cheap planar reject before the trig.
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
        // Prefer big, but penalise distance so a nearby town beats a far metropolis.
        const score = pop / (1 + km * km);
        if (score > cityScore) { cityScore = score; cityIdx = i; }
      }
    }
  }

  // Only an administrative name was in range — better than nothing.
  if (nearestIdx === -1 && anyIdx !== -1) { nearestIdx = anyIdx; nearestKm = anyKm; }

  // Nothing within 35 km (ocean, desert): widen once so we can still name a country.
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

  // If the place you are standing in is itself a town, it IS the city — do not
  // file it under a larger neighbour.
  if (g.pop[nearestIdx] >= SELF_SUFFICIENT_POP) {
    cityName = localName;
    cityIdx = nearestIdx;
  }

  // Prefer the region/country of the city — a hamlet on a border can otherwise
  // carry a neighbouring admin area.
  const anchor = cityIdx >= 0 ? cityIdx : nearestIdx;
  const regionId = g.region[anchor];
  const region = regionId === NO_REGION ? null : g.regions[regionId] ?? null;
  const countryIdx = g.country[anchor];
  const country = g.countryNames[countryIdx] ?? null;
  const countryCode = g.countryIso[countryIdx] ?? null;

  const sameAsCity = cityName !== null && cityName === localName;
  let village: string | null = sameAsCity ? null : localName;
  let city = cityName ?? localName;

  // The packed gazetteer drops every place under population 1,000, which
  // removes essentially all urban neighbourhoods — inside Kampala it can only
  // ever answer "Kampala", a 3-7 km error in the city where most users are.
  // A hand-checked neighbourhood layer fills that gap and wins the village
  // slot when the fix is inside one.
  const hood = findNeighbourhood(lat, lng);
  if (hood) {
    village = hood.name === city ? null : hood.name;
    // Trust the layer's parent city only when the gazetteer agreed we are in a
    // built-up area; otherwise keep whatever the binary resolved.
    if (hood.city) city = hood.city;
    nearestKm = Math.min(nearestKm, hood.distanceKm);
  }

  // Too far from anything to claim a locality: region/country only.
  const tooFar = nearestKm > MAX_SANE_KM;
  if (tooFar) village = null;

  // Distance honesty. `nearestKm` is the distance to whatever row won the
  // village slot; if that is far away we must not present it as the user's
  // village. A neighbourhood hit from findNeighbourhood() is a hand-checked
  // polygon and already carries its own (small) distance, so it is exempt.
  const villageFromHood = Boolean(hood);
  if (!villageFromHood && village !== null && nearestKm > VILLAGE_MAX_KM) {
    village = null;
  }

  // If even the city is far, drop it too and let region/country answer.
  let cityFar = false;
  if (!tooFar && nearestKm > CITY_MAX_KM) {
    const cityKm = cityIdx >= 0
      ? haversineKm(lat, lng, g.lat[cityIdx], g.lon[cityIdx])
      : nearestKm;
    if (cityKm > CITY_MAX_KM) cityFar = true;
  }

  const effCity = tooFar || cityFar ? null : city;
  const parts = (tooFar ? [region, country] : [village, effCity, region, country])
    .filter(Boolean) as string[];
  const shortParts = (tooFar
    ? [country]
    : [village ?? effCity ?? region, region ?? country])
    .filter(Boolean) as string[];
  const dedupe = (a: string[]) => a.filter((p, i) => i === 0 || p !== a[i - 1]);

  return {
    village,
    city: effCity,
    region,
    country,
    countryCode,
    accuracyKm: Math.round(nearestKm * 100) / 100,
    label: dedupe(parts).join(', '),
    shortLabel: dedupe(shortParts).join(', '),
    source: 'offline-gazetteer',
  };
}
