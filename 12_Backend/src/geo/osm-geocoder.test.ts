import { describe, expect, it } from 'vitest';
import { placeFromOsmAddress } from './osm-geocoder.js';

/** Real Nominatim responses captured for Kampala / Entebbe coordinates. */
describe('placeFromOsmAddress', () => {
  it('names the LC1 neighbourhood inside a suburb (Bukoto)', () => {
    const p = placeFromOsmAddress({
      neighbourhood: 'Nsimbiziwoome', suburb: 'Bukoto', city: 'Kampala',
      state: 'Central Region', country: 'Uganda', country_code: 'ug',
    });
    expect(p.village).toBe('Nsimbiziwoome');
    expect(p.suburb).toBe('Bukoto');
    expect(p.city).toBe('Kampala');
    expect(p.label).toBe('Nsimbiziwoome, Bukoto, Kampala, Central Region, Uganda');
    expect(p.shortLabel).toBe('Nsimbiziwoome, Kampala');
    expect(p.countryCode).toBe('UG');
  });

  it('falls back to the suburb when there is no smaller unit (Kisaasi)', () => {
    const p = placeFromOsmAddress({
      suburb: 'Kisaasi', city: 'Kampala', state: 'Central Region', country: 'Uganda', country_code: 'ug',
    });
    expect(p.village).toBe('Kisaasi');
    expect(p.suburb).toBeNull();
    expect(p.label).toBe('Kisaasi, Kampala, Central Region, Uganda');
  });

  it('prefers village over suburb and strips "City" from the town (Entebbe)', () => {
    const p = placeFromOsmAddress({
      road: 'Musoke Road', suburb: 'Namate', village: 'Kitooro Central', city: 'Entebbe City',
      state: 'Central Region', country: 'Uganda', country_code: 'ug',
    });
    expect(p.village).toBe('Kitooro Central');
    expect(p.suburb).toBe('Namate');
    expect(p.city).toBe('Entebbe');
    expect(p.road).toBe('Musoke Road');
  });

  it('never repeats a name across levels', () => {
    const p = placeFromOsmAddress({ town: 'Jinja', county: 'Jinja', state: 'Eastern Region', country: 'Uganda' });
    expect(p.label).toBe('Jinja, Eastern Region, Uganda');
  });
});
