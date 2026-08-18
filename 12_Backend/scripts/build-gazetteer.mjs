#!/usr/bin/env node
/**
 * Builds the offline reverse-geocoding gazetteer used by /api/v1/geo/reverse.
 *
 * Why offline: the marketplace is global and the Nearby screen resolves a
 * coordinate to "village, city, region, country" on every GPS fix. Calling a
 * third-party geocoder on each fix would be slow, rate-limited, a privacy leak
 * and a hard dependency on network egress. A ~5 MB local table answers in
 * microseconds and never fails.
 *
 * Sources (both permissively licensed, vendored at BUILD time, not runtime):
 *   • all-the-cities     — 135k populated places worldwide (GeoNames derived),
 *                          gives us village / locality granularity + population.
 *   • country-state-city — 148k cities + 4,963 admin-1 regions + 250 countries,
 *                          gives us authoritative region and country names.
 *
 * Output: src/geo/gazetteer.bin — packed little-endian
 *
 *     magic  "STXGAZ3" + uint8 version
 *     uint32 placeCount
 *     placeCount × { float32 lat, float32 lon, int32 population,
 *                    uint32 nameOff, uint16 regionId, uint16 countryId,
 *                    uint8 flags }   flags bit0 = administrative name
 *     uint32 regionCount, then length-prefixed UTF-8 names
 *     uint32 countryCount, then { 2-byte ISO code + length-prefixed name }
 *     uint32 blobLen, then the UTF-8 name blob
 *
 * Fetch the sources, then run:
 *
 *   mkdir -p /tmp/geobuild && cd /tmp/geobuild
 *   npm pack all-the-cities && tar xzf all-the-cities-*.tgz && mv package atc
 *   npm pack pbf@3.2.1 && tar xzf pbf-3.2.1.tgz --one-top-level=p3
 *   mkdir -p atc/node_modules && cp -r p3/package atc/node_modules/pbf
 *   (cd atc && npm i ieee754 resolve-protobuf-schema --no-save)
 *   npm pack country-state-city && tar xzf country-state-city-*.tgz --one-top-level=cscpkg && mv cscpkg/package csc
 *   node <repo>/12_Backend/scripts/build-gazetteer.mjs
 */
import { writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const __dirname = dirname(fileURLToPath(import.meta.url));
const require = createRequire(import.meta.url);

const srcFlag = process.argv.indexOf('--src');
const SRC = srcFlag > -1 ? process.argv[srcFlag + 1] : '/tmp/geobuild';
const OUT = join(__dirname, '..', 'src', 'geo', 'gazetteer.bin');

const atcPath = join(SRC, 'atc', 'index.js');
const cscPath = join(SRC, 'csc', 'lib', 'cjs', 'index.js');
for (const p of [atcPath, cscPath]) {
  if (!existsSync(p)) {
    console.error(`Missing dataset: ${p}\nSee the header of this file for the fetch commands.`);
    process.exit(1);
  }
}

console.log('Loading source datasets…');
const atc = require(atcPath);
const { City, State, Country } = require(cscPath);
const cscCities = City.getAllCities();
console.log(`  all-the-cities: ${atc.length}   country-state-city: ${cscCities.length}`);

// ── Country + region dictionaries ───────────────────────────────────────────
const countryIndex = new Map();
const countryList = [];
for (const c of Country.getAllCountries()) {
  countryIndex.set(c.isoCode, countryList.length);
  countryList.push({ iso: c.isoCode, name: c.name });
}
function countryId(iso) {
  if (countryIndex.has(iso)) return countryIndex.get(iso);
  const id = countryList.length;
  countryIndex.set(iso, id);
  countryList.push({ iso, name: iso });
  return id;
}

const regionIndex = new Map();
const regionList = [];
for (const s of State.getAllStates()) {
  const key = `${s.countryCode}:${s.isoCode}`;
  if (regionIndex.has(key)) continue;
  regionIndex.set(key, regionList.length);
  regionList.push(s.name);
}
const NO_REGION = 0xffff;
function regionId(countryCode, stateCode) {
  const id = regionIndex.get(`${countryCode}:${stateCode}`);
  return id === undefined ? NO_REGION : id;
}
console.log(`  countries: ${countryList.length}   regions: ${regionList.length}`);

// ── Spatial index over CSC cities so each place can inherit a region ────────
const CELL = 1; // degrees
const cellKey = (la, lo) => `${Math.floor(la / CELL)}:${Math.floor(lo / CELL)}`;
const grid = new Map();
for (let i = 0; i < cscCities.length; i++) {
  const c = cscCities[i];
  const k = cellKey(+c.latitude, +c.longitude);
  let a = grid.get(k);
  if (!a) { a = []; grid.set(k, a); }
  a.push(i);
}

function nearestCsc(la, lo, sameCountry) {
  let best = null;
  let bd = Infinity;
  for (let dla = -1; dla <= 1; dla++) {
    for (let dlo = -1; dlo <= 1; dlo++) {
      const a = grid.get(cellKey(la + dla * CELL, lo + dlo * CELL));
      if (!a) continue;
      for (const i of a) {
        const c = cscCities[i];
        if (sameCountry && c.countryCode !== sameCountry) continue;
        const dx = +c.latitude - la;
        const dy = (+c.longitude - lo) * Math.cos((la * Math.PI) / 180);
        const d = dx * dx + dy * dy;
        if (d < bd) { bd = d; best = c; }
      }
    }
  }
  return best;
}

/**
 * Administrative containers ("Kampala District", "City of Sydney", "Somolu
 * Local Government Area") are useful as a fallback but must never be reported
 * as the *village* someone is standing in — the region field already says that.
 * Flagged here so the runtime can prefer a real settlement name.
 */
const ADMIN_RE = new RegExp(
  [
    '\\b(district|division|county|province|prefecture|governorate|municipality|',
    'municipal|metropolitan|subdistrict|sub-district|department|canton|commune|',
    'oblast|raion|okrug|voivodeship|parish|borough|township|zone|区|市|県)\\b',
    '|^city of\\b|^town of\\b|^municipality of\\b|local government area',
  ].join(''),
  'i'
);
const FLAG_ADMIN = 1;

// ── Merge: every place, enriched with region + country ──────────────────────
console.log('Merging places…');
const places = [];
const seen = new Set();

for (const p of atc) {
  const lon = p.loc.coordinates[0];
  const lat = p.loc.coordinates[1];
  if (!Number.isFinite(lat) || !Number.isFinite(lon)) continue;
  const key = `${p.name}|${lat.toFixed(3)}|${lon.toFixed(3)}`;
  if (seen.has(key)) continue;
  seen.add(key);
  const match = nearestCsc(lat, lon, p.country);
  places.push({
    name: p.name,
    lat,
    lon,
    population: p.population || 0,
    country: countryId(p.country),
    region: match ? regionId(match.countryCode, match.stateCode) : NO_REGION,
    flags: ADMIN_RE.test(p.name) ? FLAG_ADMIN : 0,
  });
}

// CSC-only places that all-the-cities omits (it drops population < 1000).
for (const c of cscCities) {
  const lat = +c.latitude;
  const lon = +c.longitude;
  if (!Number.isFinite(lat) || !Number.isFinite(lon)) continue;
  const key = `${c.name}|${lat.toFixed(3)}|${lon.toFixed(3)}`;
  if (seen.has(key)) continue;
  seen.add(key);
  places.push({
    name: c.name,
    lat,
    lon,
    population: 0,
    country: countryId(c.countryCode),
    region: regionId(c.countryCode, c.stateCode),
    flags: ADMIN_RE.test(c.name) ? FLAG_ADMIN : 0,
  });
}
console.log(`  merged places: ${places.length}`);
const withRegion = places.filter((p) => p.region !== NO_REGION).length;
console.log(`  with a resolved region: ${((100 * withRegion) / places.length).toFixed(1)}%`);
console.log(`  flagged administrative: ${places.filter((p) => p.flags & FLAG_ADMIN).length}`);

// Latitude-sorted so the runtime can binary-search a band instead of scanning.
places.sort((a, b) => a.lat - b.lat);

// ── Pack ────────────────────────────────────────────────────────────────────
const enc = new TextEncoder();
const nameOffsets = new Map();
const blobParts = [];
let blobLen = 0;
function internName(s) {
  const hit = nameOffsets.get(s);
  if (hit !== undefined) return hit;
  const bytes = enc.encode(s);
  const off = blobLen;
  const head = Buffer.alloc(2);
  head.writeUInt16LE(bytes.length, 0);
  blobParts.push(head, Buffer.from(bytes));
  blobLen += 2 + bytes.length;
  nameOffsets.set(s, off);
  return off;
}

const ROW = 4 + 4 + 4 + 4 + 2 + 2 + 1; // lat, lon, pop, nameOff, region, country, flags
const rows = Buffer.alloc(places.length * ROW);
for (let i = 0; i < places.length; i++) {
  const p = places[i];
  const o = i * ROW;
  rows.writeFloatLE(p.lat, o);
  rows.writeFloatLE(p.lon, o + 4);
  rows.writeInt32LE(p.population, o + 8);
  rows.writeUInt32LE(internName(p.name), o + 12);
  rows.writeUInt16LE(p.region, o + 16);
  rows.writeUInt16LE(p.country, o + 18);
  rows.writeUInt8(p.flags, o + 20);
}

function packStrings(list) {
  const parts = [];
  const count = Buffer.alloc(4);
  count.writeUInt32LE(list.length, 0);
  parts.push(count);
  for (const s of list) {
    const b = enc.encode(s);
    const h = Buffer.alloc(2);
    h.writeUInt16LE(b.length, 0);
    parts.push(h, Buffer.from(b));
  }
  return Buffer.concat(parts);
}

const header = Buffer.alloc(12);
header.write('STXGAZ3', 0, 'ascii');
header.writeUInt8(3, 7);
header.writeUInt32LE(places.length, 8);

const countryBuf = (() => {
  const parts = [];
  const count = Buffer.alloc(4);
  count.writeUInt32LE(countryList.length, 0);
  parts.push(count);
  for (const c of countryList) {
    const iso = Buffer.alloc(2);
    iso.write((c.iso || '??').slice(0, 2).padEnd(2, '?'), 0, 'ascii');
    const b = enc.encode(c.name);
    const h = Buffer.alloc(2);
    h.writeUInt16LE(b.length, 0);
    parts.push(iso, h, Buffer.from(b));
  }
  return Buffer.concat(parts);
})();

const blobHead = Buffer.alloc(4);
blobHead.writeUInt32LE(blobLen, 0);

const out = Buffer.concat([header, rows, packStrings(regionList), countryBuf, blobHead, ...blobParts]);

mkdirSync(dirname(OUT), { recursive: true });
writeFileSync(OUT, out);
console.log(`\nWrote ${OUT}  (${(out.length / 1048576).toFixed(2)} MB, ${places.length} places)`);
