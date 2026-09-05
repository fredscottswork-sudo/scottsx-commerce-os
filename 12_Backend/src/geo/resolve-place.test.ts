import { describe, expect, it } from 'vitest';
import { mergePlaces } from './resolve-place.js';
import type { ReverseResult } from './gazetteer.js';

const base = { accuracyKm: 0, label: '', shortLabel: '' };
const osm: ReverseResult = { ...base, village: 'Nsimbiziwoome', suburb: 'Bukoto', city: 'Kampala', region: null, country: 'Uganda', countryCode: 'UG', source: 'osm' };
const google: ReverseResult = { ...base, village: null, city: 'Kampala', region: 'Central Region', country: 'Uganda', countryCode: 'UG', source: 'google' };
const offline: ReverseResult = { ...base, village: 'Kampala', city: 'Kampala', region: 'Central Region', country: 'Uganda', countryCode: 'UG', source: 'offline-gazetteer' };

describe('mergePlaces', () => {
  it('keeps the village from the finest provider and fills region from the others', () => {
    const m = mergePlaces(osm, google, offline)!;
    expect(m.village).toBe('Nsimbiziwoome');
    expect(m.suburb).toBe('Bukoto');
    expect(m.region).toBe('Central Region');
    expect(m.label).toBe('Nsimbiziwoome, Bukoto, Kampala, Central Region, Uganda');
    expect(m.shortLabel).toBe('Nsimbiziwoome, Kampala');
  });

  it('never reports the city as the village when a real village exists', () => {
    const m = mergePlaces(google, offline, osm)!;
    // google is first but has no village → OSM's village must still win over
    // the gazetteer's city-as-village.
    expect(m.village).not.toBe('Kampala');
  });

  it('falls back cleanly when only the gazetteer answers', () => {
    const m = mergePlaces(null, null, offline)!;
    expect(m.label).toBe('Kampala, Central Region, Uganda');
  });
});
