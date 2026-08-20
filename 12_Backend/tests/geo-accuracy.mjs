/**
 * ScottsTechX - reverse geocoding accuracy.
 *
 * Two things are covered:
 *
 * 1. The Google provider. It must parse a real Geocoding API payload into the
 *    ReverseResult shape, cache by coordinate so a walking buyer does not bill
 *    one call per GPS tick, and return null on every failure mode so the route
 *    falls back to the offline gazetteer instead of erroring.
 *
 * 2. The offline gazetteer's accuracy inside Kampala. It answers by finding
 *    the nearest place it knows, which is a guess in a city: a fix in Kawempe
 *    used to resolve to "Kampala" 10.67km away. The neighbourhood layer must
 *    name the actual suburb, and a fix that falls in a gap between mapped
 *    radii must still be named after the closest suburb rather than a distant
 *    town.
 */
import http from 'node:http';

const PAYLOAD = {
  status: 'OK',
  results: [{
    formatted_address: 'Kabalagala, Kampala, Uganda',
    types: ['sublocality'],
    address_components: [
      { long_name: 'Kabalagala', short_name: 'Kabalagala', types: ['sublocality_level_1','sublocality','political'] },
      { long_name: 'Kampala', short_name: 'Kampala', types: ['locality','political'] },
      { long_name: 'Central Region', short_name: 'Central Region', types: ['administrative_area_level_1','political'] },
      { long_name: 'Uganda', short_name: 'UG', types: ['country','political'] },
    ],
  }],
};

const srv = http.createServer((req,res)=>{
  res.setHeader('content-type','application/json');
  if (req.url.includes('fail')) { res.statusCode = 500; res.end('{}'); return; }
  res.end(JSON.stringify(PAYLOAD));
});
await new Promise(r=>srv.listen(0,'127.0.0.1',r));
const port = srv.address().port;

// Point the provider at the mock by overriding fetch.
const realFetch = globalThis.fetch;
let mode = 'ok';
globalThis.fetch = (url, opts) =>
  realFetch(`http://127.0.0.1:${port}/${mode==='fail'?'fail':''}`, opts);

process.env.GOOGLE_MAPS_API_KEY = 'test-key';
const { googleReverseGeocode, clearGeocodeCache, googleGeocoderConfigured } =
  await import('../src/geo/google-geocoder.ts');

let pass=0, fail=0;
const check=(n,c,d='')=>{ if(c){pass++;console.log('  ✓',n);} else {fail++;console.log('  ✗',n,d);} };

check('provider reports configured when key present', googleGeocoderConfigured()===true);

const r = await googleReverseGeocode(0.3040, 32.5980);
check('google answer used', r !== null);
check('village is the real sublocality', r?.village === 'Kabalagala', r?.village);
check('city parsed', r?.city === 'Kampala', r?.city);
check('region parsed', r?.region === 'Central Region', r?.region);
check('country + iso parsed', r?.country==='Uganda' && r?.countryCode==='UG');
check('source marked google', r?.source === 'google', r?.source);
check('label is full chain', r?.label === 'Kabalagala, Kampala, Central Region, Uganda', r?.label);
check('accuracyKm is 0 (containing area, not a centroid)', r?.accuracyKm === 0);

// Cache: second identical call must not hit the network.
let calls = 0;
globalThis.fetch = (url,opts)=>{ calls++; return realFetch(`http://127.0.0.1:${port}/`,opts); };
await googleReverseGeocode(0.3040, 32.5980);
check('repeat coordinate served from cache (no network call)', calls === 0, `calls=${calls}`);

// Failure path must return null so the route falls back.
clearGeocodeCache();
mode='fail';
globalThis.fetch = (url,opts)=>realFetch(`http://127.0.0.1:${port}/fail`,opts);
const bad = await googleReverseGeocode(0.9, 32.9);
check('provider failure returns null so caller falls back', bad === null);

// No key at all -> null without touching the network.
delete process.env.GOOGLE_MAPS_API_KEY;
clearGeocodeCache();
const none = await googleReverseGeocode(0.3040, 32.5980);
check('no key configured returns null', none === null);
check('provider reports not configured', googleGeocoderConfigured()===false);

// ── Offline accuracy: the gazetteer must not guess a distant town ──────────
const { reverseGeocode } = await import('../src/geo/gazetteer.ts');

console.log('\n[offline gazetteer - Kampala accuracy]');

// Every one of these is a real suburb centroid; the layer must name it exactly.
const SUBURBS = [
  ['Kabalagala', 0.3040, 32.5980],
  ['Kawempe',    0.3739, 32.5533],
  ['Muyenga',    0.2921, 32.6151],
  ['Wandegeya',  0.3345, 32.5726],
  ['Ntinda',     0.3494, 32.6117],
  ['Nateete',    0.3021, 32.5389],
];
for (const [name, lat, lng] of SUBURBS) {
  const p = reverseGeocode(lat, lng);
  check(`${name} resolves to itself, not the nearest town`,
    p?.village === name, `got ${p?.village} (${p?.accuracyKm}km)`);
}

// The regression that started this: a fix north of Kawempe was labelled
// "Kampala" from 10.67km away. Whatever we answer now must be far closer.
const north = reverseGeocode(0.4162, 32.5822);
check('a fix in a gap between suburbs is named from close by, not 10km away',
  (north?.accuracyKm ?? 99) < 4, `${north?.label} @ ${north?.accuracyKm}km`);
check('that fix still names a real locality',
  Boolean(north?.village || north?.city), JSON.stringify(north));

// Sanity: somewhere with no mapped suburbs must still answer, and must not
// borrow a Kampala neighbourhood name.
const gulu = reverseGeocode(2.7746, 32.2990);
check('upcountry fixes still resolve', Boolean(gulu?.city || gulu?.village), JSON.stringify(gulu));
check('upcountry fix is not given a Kampala suburb name',
  gulu?.city !== 'Kampala', gulu?.label);

srv.close();
console.log(`\nResult: ${pass} passed, ${fail} failed`);
process.exit(fail?1:0);
