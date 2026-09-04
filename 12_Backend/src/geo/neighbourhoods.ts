/**
 * ScottsTechX — neighbourhood layer (DEPRECATED for village naming)
 *
 * Previously this was a hand-checked table of suburbs used to fill village slot.
 * Per requirements: DO NOT hardcode Kisaasi or any other village, DO NOT create
 * fake village data. Village names must come from reliable reverse-geocoding
 * provider inspecting complete address response, not from hardcoded table.
 *
 * This file is kept for backwards compatibility but findNeighbourhood now
 * returns null — village naming uses Google + offline gazetteer with confidence
 * scoring, not hardcoded entries. GPS (lat/lng) remains source for distance.
 */

export interface Neighbourhood {
  name: string;
  city: string;
  lat: number;
  lng: number;
  radiusKm: number;
}

// Empty — no hardcoded villages. Real data comes from Google/offline gazetteer
export const NEIGHBOURHOODS: Neighbourhood[] = [];

export interface NeighbourhoodHit {
  name: string;
  city: string;
  distanceKm: number;
  approximate: boolean;
}

/**
 * Deprecated — returns null to avoid hardcoding villages.
 * Use reverseGeocode() + googleReverseGeocode() with confidence instead.
 */
export function findNeighbourhood(_lat: number, _lng: number): NeighbourhoodHit | null {
  return null;
}
