import { useEffect, useState } from 'react';
import { MapPin, BadgeCheck } from 'lucide-react';
import { productService } from '../api/services';
import type { NearbySeller } from '../api/types';
import { Card, Empty, ErrorBox, Loading, PageHeader } from '../components/ui';

const CITIES = [
  { name: 'Kampala', lat: 0.3476, lng: 32.5825 },
  { name: 'Entebbe', lat: 0.0611, lng: 32.4444 },
  { name: 'Jinja', lat: 0.4255, lng: 33.2041 },
  { name: 'Mbarara', lat: -0.6072, lng: 30.6545 },
  { name: 'Gulu', lat: 2.7724, lng: 32.2881 },
  { name: 'Mbale', lat: 1.0747, lng: 34.1761 },
];

type SortMode = 'nearest' | 'rating' | 'products';

export default function Nearby() {
  const [city, setCity] = useState(CITIES[0]);
  const [radius, setRadius] = useState(50);
  const [sellers, setSellers] = useState<NearbySeller[]>([]);
  const [sort, setSort] = useState<SortMode>('nearest');
  const [verifiedOnly, setVerifiedOnly] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    setLoading(true);
    setError('');
    productService.nearby(city.lat, city.lng, radius)
      .then((r) => setSellers(r.sellers))
      .catch((e: any) => setError(e.message))
      .finally(() => setLoading(false));
  }, [city, radius]);

  const filtered = sellers
    .filter((s) => (verifiedOnly ? s.verified : true))
    .sort((a, b) =>
      sort === 'rating' ? b.rating - a.rating : sort === 'products' ? b.productCount - a.productCount : a.distanceKm - b.distanceKm
    );

  return (
    <>
      <PageHeader title="Nearby sellers" sub="Real Ugandan stores — filtered & sorted client-side from the same API the mobile app uses." />

      <Card className="mb-16">
        <div className="row wrap mb-16">
          {CITIES.map((c) => (
            <button key={c.name} className={`chip ${city.name === c.name ? 'active' : ''}`} onClick={() => setCity(c)}>
              <MapPin size={13} /> {c.name}
            </button>
          ))}
        </div>
        <div className="row wrap">
          <button className="chip" onClick={() => setSort(sort === 'nearest' ? 'rating' : sort === 'rating' ? 'products' : 'nearest')}>
            ⇅ {sort === 'nearest' ? 'Nearest' : sort === 'rating' ? 'Top rated' : 'Most products'}
          </button>
          <button className={`chip ${verifiedOnly ? 'active' : ''}`} onClick={() => setVerifiedOnly(!verifiedOnly)}>
            <BadgeCheck size={13} /> Verified only
          </button>
          <label className="muted row" style={{ flex: 1, minWidth: 220 }}>
            Radius: <strong>{radius} km</strong>
            <input type="range" min={1} max={100} value={radius} onChange={(e) => setRadius(Number(e.target.value))} style={{ flex: 1 }} />
          </label>
        </div>
      </Card>

      {loading ? <Loading /> : error ? <ErrorBox message={error} /> :
        filtered.length === 0 ? <Empty emoji="📍" title="No sellers nearby" subtitle="Try a bigger radius or another city." /> :
        <div className="grid grid-2">
          {filtered.map((s) => (
            <Card key={s.id}>
              <div className="row-between">
                <div className="row">
                  <span className="avatar">{s.name[0]}</span>
                  <div>
                    <div className="row">
                      <strong>{s.name}</strong>
                      {s.verified && <BadgeCheck size={15} style={{ color: 'var(--success)' }} />}
                    </div>
                    <span className="muted" style={{ fontSize: 13 }}>
                      {s.rating} ★ · {s.productCount} products · {s.city || 'Uganda'}
                    </span>
                  </div>
                </div>
                <span className="badge badge-blue">{s.distanceKm} km</span>
              </div>
            </Card>
          ))}
        </div>}
    </>
  );
}
