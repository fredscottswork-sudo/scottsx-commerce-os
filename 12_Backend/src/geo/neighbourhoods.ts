/**
 * ScottsTechX — urban neighbourhood layer.
 *
 * Why this exists
 * ---------------
 * The packed gazetteer is built from `all-the-cities`, which drops every place
 * with a population under 1,000. That filter removes essentially all *urban
 * neighbourhoods*: the binary holds 155 Ugandan entries, and all of them are
 * towns. The practical effect is that a buyer standing in Ntinda, Kabalagala,
 * Muyenga or Wandegeya is told they are in "Kampala" — the reverse geocoder
 * reports a 3-7 km error inside the one city where most of the marketplace's
 * users actually are, and every store shows the same meaningless place label.
 *
 * This layer supplies the missing granularity for the cities the marketplace
 * serves. It is a small, hand-checked table of real suburbs with their centre
 * coordinates, consulted BEFORE the packed gazetteer. When a fix falls within a
 * neighbourhood's radius the neighbourhood wins the "village" slot and the
 * parent city is taken from the entry, so "Ntinda, Kampala" replaces "Kampala".
 *
 * Coordinates are neighbourhood centroids; the radius is a deliberate
 * over-estimate of each area's extent so that adjacent suburbs tile without
 * gaps. Where two overlap, the nearest centre wins.
 *
 * Adding a city: append its suburbs with `city` set to the parent settlement
 * exactly as the gazetteer spells it, so region/country lookups stay consistent.
 */

export interface Neighbourhood {
  name: string;
  city: string;
  lat: number;
  lng: number;
  /** Kilometres from the centre still considered inside this area. */
  radiusKm: number;
}

/**
 * Greater Kampala and the main upcountry towns.
 *
 * Kampala's divisions (Central, Kawempe, Makindye, Nakawa, Rubaga) and the
 * Wakiso suburbs that everyone treats as Kampala are all represented, because a
 * seller in Ntinda and a seller in Nateete are 12 km apart and must not share a
 * label.
 */
export const NEIGHBOURHOODS: Neighbourhood[] = [
  // ── Kampala Central ──────────────────────────────────────────────────────
  { name: 'Kampala Central', city: 'Kampala', lat: 0.3152, lng: 32.5816, radiusKm: 1.1 },
  { name: 'Nakasero', city: 'Kampala', lat: 0.3225, lng: 32.5811, radiusKm: 1.1 },
  { name: 'Old Kampala', city: 'Kampala', lat: 0.3136, lng: 32.5686, radiusKm: 1.0 },
  { name: 'Kisenyi', city: 'Kampala', lat: 0.3098, lng: 32.5730, radiusKm: 0.9 },
  { name: 'Kamwokya', city: 'Kampala', lat: 0.3392, lng: 32.5877, radiusKm: 1.2 },
  { name: 'Wandegeya', city: 'Kampala', lat: 0.3345, lng: 32.5726, radiusKm: 1.1 },
  { name: 'Makerere', city: 'Kampala', lat: 0.3311, lng: 32.5656, radiusKm: 1.2 },
  { name: 'Kololo', city: 'Kampala', lat: 0.3316, lng: 32.5951, radiusKm: 1.4 },
  { name: 'Nakawa', city: 'Kampala', lat: 0.3300, lng: 32.6155, radiusKm: 1.5 },
  { name: 'Bugolobi', city: 'Kampala', lat: 0.3182, lng: 32.6135, radiusKm: 1.2 },
  { name: 'Luzira', city: 'Kampala', lat: 0.3038, lng: 32.6431, radiusKm: 1.7 },
  { name: 'Mutungo', city: 'Kampala', lat: 0.3172, lng: 32.6383, radiusKm: 1.4 },
  { name: 'Ntinda', city: 'Kampala', lat: 0.3494, lng: 32.6117, radiusKm: 1.7 },
  { name: 'Naguru', city: 'Kampala', lat: 0.3387, lng: 32.6035, radiusKm: 1.3 },
  { name: 'Kiwatule', city: 'Kampala', lat: 0.3623, lng: 32.6216, radiusKm: 1.5 },
  { name: 'Najjera', city: 'Kampala', lat: 0.3819, lng: 32.6265, radiusKm: 2.0 },
  { name: 'Kyanja', city: 'Kampala', lat: 0.3798, lng: 32.6045, radiusKm: 1.7 },
  { name: 'Bukoto', city: 'Kampala', lat: 0.3479, lng: 32.5985, radiusKm: 1.3 },
  { name: 'Kisaasi', city: 'Kampala', lat: 0.3690, lng: 32.6085, radiusKm: 1.1 },
  { name: 'Kigoowa', city: 'Kampala', lat: 0.3625, lng: 32.6150, radiusKm: 1.3 },
  { name: 'Kulambiro', city: 'Kampala', lat: 0.3750, lng: 32.6100, radiusKm: 1.3 },
  { name: 'Kikaya', city: 'Kampala', lat: 0.3765, lng: 32.5950, radiusKm: 1.2 },
  { name: 'Kanyanya', city: 'Kampala', lat: 0.3850, lng: 32.5620, radiusKm: 1.4 },
  { name: 'Mpererwe', city: 'Kampala', lat: 0.3950, lng: 32.5650, radiusKm: 1.5 },
  { name: 'Kubamutwe', city: 'Kampala', lat: 0.3550, lng: 32.6050, radiusKm: 1.0 },
  { name: 'Kyebando Central', city: 'Kampala', lat: 0.3610, lng: 32.5820, radiusKm: 1.0 },
  { name: 'Kawempe Central', city: 'Kampala', lat: 0.3739, lng: 32.5533, radiusKm: 1.2 },
  { name: 'Komamboga Central', city: 'Kampala', lat: 0.3893, lng: 32.5751, radiusKm: 1.2 },
  { name: 'Buwate', city: 'Wakiso', lat: 0.3950, lng: 32.6150, radiusKm: 1.6 },
  { name: 'Kungu', city: 'Wakiso', lat: 0.4020, lng: 32.6300, radiusKm: 1.5 },
  { name: 'Kiwologoma', city: 'Wakiso', lat: 0.3950, lng: 32.6450, radiusKm: 1.5 },
  { name: 'Kiwatule Central', city: 'Kampala', lat: 0.3623, lng: 32.6216, radiusKm: 1.2 },

  // ── Makindye / south ─────────────────────────────────────────────────────
  { name: 'Kabalagala', city: 'Kampala', lat: 0.3040, lng: 32.5980, radiusKm: 1.2 },
  { name: 'Kansanga', city: 'Kampala', lat: 0.2932, lng: 32.6021, radiusKm: 1.2 },
  { name: 'Muyenga', city: 'Kampala', lat: 0.2921, lng: 32.6151, radiusKm: 1.5 },
  { name: 'Bunga', city: 'Kampala', lat: 0.2755, lng: 32.6156, radiusKm: 1.4 },
  { name: 'Ggaba', city: 'Kampala', lat: 0.2647, lng: 32.6329, radiusKm: 1.5 },
  { name: 'Makindye', city: 'Kampala', lat: 0.2932, lng: 32.5836, radiusKm: 1.5 },
  { name: 'Katwe', city: 'Kampala', lat: 0.2985, lng: 32.5757, radiusKm: 1.2 },
  { name: 'Nsambya', city: 'Kampala', lat: 0.3015, lng: 32.5895, radiusKm: 1.2 },
  { name: 'Kibuye', city: 'Kampala', lat: 0.2914, lng: 32.5680, radiusKm: 1.2 },
  { name: 'Najjanankumbi', city: 'Kampala', lat: 0.2820, lng: 32.5620, radiusKm: 1.4 },
  { name: 'Zzana', city: 'Kampala', lat: 0.2645, lng: 32.5556, radiusKm: 1.5 },
  { name: 'Kajjansi', city: 'Wakiso', lat: 0.2168, lng: 32.5476, radiusKm: 2.0 },

  // ── Rubaga / west ────────────────────────────────────────────────────────
  { name: 'Rubaga', city: 'Kampala', lat: 0.3053, lng: 32.5555, radiusKm: 1.3 },
  { name: 'Mengo', city: 'Kampala', lat: 0.3013, lng: 32.5661, radiusKm: 1.1 },
  { name: 'Namirembe', city: 'Kampala', lat: 0.3086, lng: 32.5588, radiusKm: 1.0 },
  { name: 'Nateete', city: 'Kampala', lat: 0.3021, lng: 32.5389, radiusKm: 1.5 },
  { name: 'Busega', city: 'Kampala', lat: 0.3068, lng: 32.5253, radiusKm: 1.4 },
  { name: 'Ndeeba', city: 'Kampala', lat: 0.2942, lng: 32.5615, radiusKm: 1.2 },
  { name: 'Kabowa', city: 'Kampala', lat: 0.2887, lng: 32.5527, radiusKm: 1.2 },
  { name: 'Lungujja', city: 'Kampala', lat: 0.3153, lng: 32.5432, radiusKm: 1.3 },
  { name: 'Kasubi', city: 'Kampala', lat: 0.3273, lng: 32.5527, radiusKm: 1.2 },
  { name: 'Bwaise', city: 'Kampala', lat: 0.3524, lng: 32.5622, radiusKm: 1.3 },

  // ── Kawempe / north ──────────────────────────────────────────────────────
  { name: 'Kawempe', city: 'Kampala', lat: 0.3739, lng: 32.5533, radiusKm: 1.6 },
  { name: 'Kalerwe', city: 'Kampala', lat: 0.3556, lng: 32.5738, radiusKm: 1.2 },
  { name: 'Mulago', city: 'Kampala', lat: 0.3439, lng: 32.5762, radiusKm: 1.2 },
  { name: 'Kyebando', city: 'Kampala', lat: 0.3628, lng: 32.5842, radiusKm: 1.4 },
  { name: 'Komamboga', city: 'Kampala', lat: 0.3893, lng: 32.5751, radiusKm: 1.5 },
  { name: 'Kanyanya', city: 'Kampala', lat: 0.3833, lng: 32.5617, radiusKm: 1.4 },

  // ── Greater Kampala / Wakiso ─────────────────────────────────────────────
  { name: 'Kireka', city: 'Kampala', lat: 0.3457, lng: 32.6478, radiusKm: 1.6 },
  { name: 'Bweyogerere', city: 'Kampala', lat: 0.3556, lng: 32.6620, radiusKm: 1.7 },
  { name: 'Namugongo', city: 'Wakiso', lat: 0.3808, lng: 32.6669, radiusKm: 2.0 },
  { name: 'Kyaliwajjala', city: 'Wakiso', lat: 0.3806, lng: 32.6470, radiusKm: 1.7 },
  { name: 'Kira', city: 'Wakiso', lat: 0.3990, lng: 32.6440, radiusKm: 2.2 },
  { name: 'Seeta', city: 'Mukono', lat: 0.3617, lng: 32.6905, radiusKm: 2.0 },
  { name: 'Kasangati', city: 'Wakiso', lat: 0.4290, lng: 32.5990, radiusKm: 2.2 },
  { name: 'Gayaza', city: 'Wakiso', lat: 0.4560, lng: 32.6060, radiusKm: 2.2 },
  { name: 'Matugga', city: 'Wakiso', lat: 0.4560, lng: 32.5450, radiusKm: 2.2 },
  { name: 'Nansana', city: 'Wakiso', lat: 0.3630, lng: 32.5230, radiusKm: 2.2 },
  { name: 'Kawanda', city: 'Wakiso', lat: 0.3960, lng: 32.5340, radiusKm: 1.8 },
  { name: 'Kyengera', city: 'Wakiso', lat: 0.2920, lng: 32.5080, radiusKm: 1.8 },
  { name: 'Nsangi', city: 'Wakiso', lat: 0.2790, lng: 32.4740, radiusKm: 2.0 },
  { name: 'Bulenga', city: 'Wakiso', lat: 0.3120, lng: 32.4920, radiusKm: 1.8 },
  { name: 'Namasuba', city: 'Wakiso', lat: 0.2710, lng: 32.5720, radiusKm: 1.5 },
  { name: 'Seguku', city: 'Wakiso', lat: 0.2530, lng: 32.5620, radiusKm: 1.5 },
  { name: 'Bweyos', city: 'Kampala', lat: 0.2830, lng: 32.6040, radiusKm: 1.2 },
  { name: 'Munyonyo', city: 'Kampala', lat: 0.2560, lng: 32.6120, radiusKm: 1.8 },

  // ── Entebbe ──────────────────────────────────────────────────────────────
  { name: 'Entebbe Town', city: 'Entebbe', lat: 0.0512, lng: 32.4633, radiusKm: 2.0 },
  { name: 'Kitoro', city: 'Entebbe', lat: 0.0578, lng: 32.4740, radiusKm: 1.3 },
  { name: 'Kiwafu', city: 'Entebbe', lat: 0.0430, lng: 32.4670, radiusKm: 1.3 },
  { name: 'Abayita Ababiri', city: 'Entebbe', lat: 0.1000, lng: 32.4880, radiusKm: 1.8 },
  { name: 'Nkumba', city: 'Entebbe', lat: 0.0830, lng: 32.4790, radiusKm: 1.6 },

  // ── Other main towns: enough to beat a 3 km "city centre" error ──────────
  { name: 'Jinja Central', city: 'Jinja', lat: 0.4478, lng: 33.2026, radiusKm: 2.0 },
  { name: 'Bugembe', city: 'Jinja', lat: 0.4560, lng: 33.2380, radiusKm: 1.8 },
  { name: 'Njeru', city: 'Njeru', lat: 0.4400, lng: 33.1700, radiusKm: 2.2 },
  { name: 'Mbarara Central', city: 'Mbarara', lat: 0.6072, lng: 30.6545, radiusKm: 2.2 },
  { name: 'Kakoba', city: 'Mbarara', lat: 0.5960, lng: 30.6600, radiusKm: 1.6 },
  { name: 'Gulu Central', city: 'Gulu', lat: 2.7746, lng: 32.2990, radiusKm: 2.2 },
  { name: 'Lira Central', city: 'Lira', lat: 2.2470, lng: 32.8998, radiusKm: 2.2 },
  { name: 'Mbale Central', city: 'Mbale', lat: 1.0820, lng: 34.1750, radiusKm: 2.2 },
  { name: 'Masaka Central', city: 'Masaka', lat: 0.3400, lng: 31.7340, radiusKm: 2.2 },
  { name: 'Mukono Central', city: 'Mukono', lat: 0.3536, lng: 32.7554, radiusKm: 2.2 },
  { name: 'Fort Portal Central', city: 'Fort Portal', lat: 0.6540, lng: 30.2750, radiusKm: 2.0 },
  { name: 'Arua Central', city: 'Arua', lat: 3.0200, lng: 30.9110, radiusKm: 2.0 },
  { name: 'Soroti Central', city: 'Soroti', lat: 1.7150, lng: 33.6110, radiusKm: 2.0 },
  { name: 'Kabale Central', city: 'Kabale', lat: -1.2410, lng: 29.9890, radiusKm: 2.0 },
  { name: 'Hoima Central', city: 'Hoima', lat: 1.4350, lng: 31.3520, radiusKm: 2.0 },
];

/** Cheap bounding pre-filter, then exact haversine. */
function haversineKm(aLat: number, aLng: number, bLat: number, bLng: number): number {
  const R = 6371;
  const toRad = (d: number) => (d * Math.PI) / 180;
  const dLat = toRad(bLat - aLat);
  const dLng = toRad(bLng - aLng);
  const s =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(aLat)) * Math.cos(toRad(bLat)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(s));
}

export interface NeighbourhoodHit {
  name: string;
  city: string;
  distanceKm: number;
  /**
   * True when the fix was not inside the area's radius but was the closest
   * mapped suburb within NEAR_MISS_KM. Still far better than the packed
   * gazetteer's answer, but the caller should present it as "near X".
   */
  approximate: boolean;
}

/**
 * The neighbourhood containing this fix, or null when it is outside every
 * known area. Where areas overlap the nearest centre wins.
 */
/**
 * How far outside every mapped radius a fix may still be named after the
 * closest suburb. The radii are centroid estimates, so adjacent areas leave
 * small uncovered gaps — a fix in one of those gaps used to fall through to
 * the packed gazetteer and be labelled with a town up to 10 km away. Naming
 * the suburb 2.5 km away is strictly more accurate than that, and the caller
 * still receives the true distance so it can decide how to phrase it.
 */
const NEAR_MISS_KM = 3;

export function findNeighbourhood(lat: number, lng: number): NeighbourhoodHit | null {
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null;
  let inside: NeighbourhoodHit | null = null;
  let nearest: NeighbourhoodHit | null = null;

  for (const n of NEIGHBOURHOODS) {
    // Skip anything obviously far before doing the trig (~0.9 deg lat = 100 km).
    if (Math.abs(n.lat - lat) > 0.09 || Math.abs(n.lng - lng) > 0.09) continue;
    const km = haversineKm(lat, lng, n.lat, n.lng);
    const hit: NeighbourhoodHit = {
      name: n.name,
      city: n.city,
      distanceKm: Math.round(km * 100) / 100,
      approximate: false,
    };
    // A containing area always beats a merely close one.
    if (km <= n.radiusKm) {
      if (inside === null || km < inside.distanceKm) inside = hit;
    }
    if (km <= NEAR_MISS_KM && (nearest === null || km < nearest.distanceKm)) {
      nearest = { ...hit, approximate: true };
    }
  }

  return inside ?? nearest;
}
