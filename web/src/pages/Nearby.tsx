import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  MapPin, BadgeCheck, Navigation, Radio, Clock, Store, Truck, Star,
  LocateFixed, LocateOff, Package, Search as SearchIcon,
} from 'lucide-react';
import { productService } from '../api/services';
import type { NearbySeller } from '../api/types';
import { formatUgx } from '../api/types';
import { useToast } from '../store/ToastContext';
import {
  Btn, Empty, ErrorBox, PageHeader, Select, SkeletonRows, Switch, Badge, SearchInput,
} from '../components/ui';

const CITIES = [
  { name: 'Kampala', lat: 0.3476, lng: 32.5825 },
  { name: 'Entebbe', lat: 0.0611, lng: 32.4444 },
  { name: 'Jinja', lat: 0.4255, lng: 33.2041 },
  { name: 'Mbarara', lat: -0.6072, lng: 30.6545 },
  { name: 'Gulu', lat: 2.7724, lng: 32.2881 },
  { name: 'Mbale', lat: 1.0747, lng: 34.1761 },
];

type Sort = 'distance' | 'rating' | 'products' | 'newest';

/** Metres moved before we bother re-querying the server. */
const REFETCH_METRES = 250;

function metresBetween(a: { lat: number; lng: number }, b: { lat: number; lng: number }): number {
  const R = 6371000;
  const dLat = ((b.lat - a.lat) * Math.PI) / 180;
  const dLng = ((b.lng - a.lng) * Math.PI) / 180;
  const la1 = (a.lat * Math.PI) / 180;
  const la2 = (b.lat * Math.PI) / 180;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(la1) * Math.cos(la2) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}

export default function Nearby() {
  const { toast } = useToast();

  const [center, setCenter] = useState({ lat: CITIES[0].lat, lng: CITIES[0].lng });
  const [cityName, setCityName] = useState(CITIES[0].name);
  const [usingGps, setUsingGps] = useState(false);
  const [radius, setRadius] = useState(50);
  const [sort, setSort] = useState<Sort>('distance');
  const [verifiedOnly, setVerifiedOnly] = useState(false);
  const [openOnly, setOpenOnly] = useState(false);
  const [q, setQ] = useState('');

  const [sellers, setSellers] = useState<NearbySeller[]>([]);
  const [liveCount, setLiveCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null);
  const [moved, setMoved] = useState(false);

  const watchId = useRef<number | null>(null);
  const lastFetchCenter = useRef(center);

  // ── Fetch (server does the distance maths + last-known-position fallback) ─
  const fetchSellers = useCallback(async (at: { lat: number; lng: number }) => {
    setError('');
    try {
      const r = await productService.nearby({
        lat: at.lat,
        lng: at.lng,
        radiusKm: radius,
        q: q.trim() || undefined,
        verifiedOnly: verifiedOnly || undefined,
        openOnly: openOnly || undefined,
        sort,
      });
      setSellers(r.sellers);
      setLiveCount(r.liveCount);
      setUpdatedAt(new Date());
      lastFetchCenter.current = at;
      setMoved(false);
    } catch (e: any) {
      setError(e?.message || 'Could not load nearby stores');
    } finally {
      setLoading(false);
    }
  }, [radius, q, verifiedOnly, openOnly, sort]);

  useEffect(() => {
    setLoading(true);
    const t = setTimeout(() => void fetchSellers(center), 220);
    return () => clearTimeout(t);
  }, [center, fetchSellers]);

  // ── Continuous tracking: as the buyer moves, stores re-sort by distance ──
  const startTracking = useCallback(() => {
    if (!navigator.geolocation) {
      toast('This browser cannot share your location', 'error');
      return;
    }
    setUsingGps(true);
    watchId.current = navigator.geolocation.watchPosition(
      (pos) => {
        const next = { lat: pos.coords.latitude, lng: pos.coords.longitude };
        setCityName('My location');
        // Re-sort locally on every fix (instant), re-query only when the
        // buyer has actually moved a meaningful distance (cheap).
        setSellers((prev) =>
          prev
            .map((s) => ({ ...s, distanceKm: Number((metresBetween(next, s) / 1000).toFixed(1)) }))
            .sort((a, b) => (sort === 'distance' ? a.distanceKm - b.distanceKm : 0))
        );
        if (metresBetween(lastFetchCenter.current, next) > REFETCH_METRES) {
          setMoved(true);
          setCenter(next);
        } else {
          setCenter((c) => (c.lat === next.lat && c.lng === next.lng ? c : next));
        }
      },
      (err) => {
        setUsingGps(false);
        toast(
          err.code === err.PERMISSION_DENIED
            ? 'Location permission denied — pick a city instead'
            : 'Could not get your location',
          'warning'
        );
      },
      { enableHighAccuracy: true, maximumAge: 10000, timeout: 20000 }
    );
  }, [sort, toast]);

  const stopTracking = useCallback(() => {
    if (watchId.current !== null) navigator.geolocation.clearWatch(watchId.current);
    watchId.current = null;
    setUsingGps(false);
  }, []);

  useEffect(() => () => { if (watchId.current !== null) navigator.geolocation.clearWatch(watchId.current); }, []);

  const stats = useMemo(() => ({
    total: sellers.length,
    open: sellers.filter((s) => s.isOpen).length,
    delivering: sellers.filter((s) => s.withinServiceRadius).length,
  }), [sellers]);

  return (
    <>
      <PageHeader
        title="Stores near you"
        sub="Sellers sharing live location update in real time. Everyone else stays pinned at their last known position."
        actions={
          usingGps ? (
            <Btn variant="danger" icon={<LocateOff size={15} />} onClick={stopTracking}>Stop tracking</Btn>
          ) : (
            <Btn variant="primary" icon={<LocateFixed size={15} />} onClick={startTracking}>Use my location</Btn>
          )
        }
      />

      {/* ── Controls ────────────────────────────────────────────────── */}
      <div className="card mb-16">
        <div className="row wrap mb-12" style={{ gap: 7 }}>
          {CITIES.map((c) => (
            <button
              key={c.name}
              className={`chip ${cityName === c.name && !usingGps ? 'active' : ''}`}
              onClick={() => { stopTracking(); setCityName(c.name); setCenter({ lat: c.lat, lng: c.lng }); }}
            >
              <MapPin size={13} /> {c.name}
            </button>
          ))}
          {usingGps && (
            <span className="chip active">
              <span className="pulse-dot" style={{ marginRight: 5 }} /> Live GPS
            </span>
          )}
        </div>

        <div className="row wrap" style={{ gap: 14 }}>
          <div style={{ flex: '1 1 220px', minWidth: 200 }}>
            <SearchInput value={q} onChange={setQ} placeholder="Filter stores or products…" />
          </div>

          <label className="row tiny semi" style={{ flex: '1 1 240px', minWidth: 210, gap: 9 }}>
            <span style={{ whiteSpace: 'nowrap' }}>Radius <strong>{radius} km</strong></span>
            <input type="range" min={1} max={200} value={radius} className="range grow"
              onChange={(e) => setRadius(Number(e.target.value))} aria-label="Search radius" />
          </label>

          <Select value={sort} onChange={(e) => setSort(e.target.value as Sort)} style={{ width: 'auto' }}>
            <option value="distance">Nearest first</option>
            <option value="rating">Top rated</option>
            <option value="products">Most products</option>
            <option value="newest">Newest stores</option>
          </Select>

          <Switch checked={verifiedOnly} onChange={setVerifiedOnly} label="Verified" />
          <Switch checked={openOnly} onChange={setOpenOnly} label="Open now" />
        </div>
      </div>

      {/* ── Live status strip ───────────────────────────────────────── */}
      <div className="row wrap mb-16" style={{ gap: 9 }}>
        <Badge tone="primary">{stats.total} store{stats.total === 1 ? '' : 's'}</Badge>
        {liveCount > 0 && <Badge tone="green" live>{liveCount} sharing live location</Badge>}
        <Badge tone="cyan">{stats.open} open now</Badge>
        <Badge tone="violet">{stats.delivering} deliver to you</Badge>
        {updatedAt && (
          <span className="tiny muted-2">
            <Clock size={11} style={{ verticalAlign: -1 }} /> Updated {updatedAt.toLocaleTimeString()}
          </span>
        )}
        {moved && <span className="tiny t-primary semi">You moved — refreshing…</span>}
      </div>

      {/* ── Results ─────────────────────────────────────────────────── */}
      {loading ? (
        <SkeletonRows rows={5} height={116} />
      ) : error ? (
        <ErrorBox message={error} onRetry={() => void fetchSellers(center)} />
      ) : sellers.length === 0 ? (
        <Empty
          icon={<MapPin size={28} />}
          title="No stores in range"
          subtitle="Widen the radius, clear the filters, or pick another city."
          action={<Btn variant="primary" onClick={() => { setRadius(200); setVerifiedOnly(false); setOpenOnly(false); setQ(''); }}>
            Widen search
          </Btn>}
        />
      ) : (
        <div className="grid grid-2 stagger">
          {sellers.map((s, i) => (
            <Link key={s.id} to={`/seller/${s.id}`} className="card card-hover store-card stagger-item"
              style={{ '--i': i } as React.CSSProperties}>
              <div className="row" style={{ alignItems: 'flex-start', gap: 12 }}>
                <span className="avatar avatar-lg">
                  {s.logoUrl ? <img src={s.logoUrl} alt="" /> : (s.storeName || s.name || 'S')[0].toUpperCase()}
                </span>

                <div className="grow" style={{ minWidth: 0 }}>
                  <div className="row" style={{ gap: 6 }}>
                    <strong className="ellipsis">{s.storeName || s.name}</strong>
                    {s.verified && <BadgeCheck size={15} className="t-success" aria-label="Verified" />}
                    {s.isOpen ? <Badge tone="green">Open</Badge> : <Badge>Closed</Badge>}
                  </div>

                  <div className="tiny muted mt-4">
                    <Star size={11} style={{ verticalAlign: -1, color: 'var(--warning)' }} fill="currentColor" />
                    {' '}{Number(s.rating || 0).toFixed(1)} · <Package size={11} style={{ verticalAlign: -1 }} /> {s.productCount} products
                    {s.newThisWeek > 0 && <span className="t-success"> · {s.newThisWeek} new</span>}
                  </div>

                  <div className="tiny muted mt-4 ellipsis">
                    <MapPin size={11} style={{ verticalAlign: -1 }} /> {s.address || s.city || 'Uganda'}
                  </div>

                  {/* The live/last-known distinction the buyer must be able to trust. */}
                  <div className="row wrap mt-8" style={{ gap: 6 }}>
                    {s.live ? (
                      <Badge tone="green" live>
                        Live · {s.locationAgeMinutes !== null ? `${s.locationAgeMinutes}m ago` : 'now'}
                      </Badge>
                    ) : (
                      <Badge tone="amber">
                        <Radio size={10} style={{ verticalAlign: -1, marginRight: 3 }} />
                        {s.locationSharing ? 'Last seen' : 'Fixed address'}
                      </Badge>
                    )}
                    {s.withinServiceRadius ? (
                      <Badge tone="cyan"><Truck size={10} style={{ verticalAlign: -1, marginRight: 3 }} />
                        {s.deliveryFeeUgx > 0 ? formatUgx(s.deliveryFeeUgx) : 'Free delivery'}
                      </Badge>
                    ) : (
                      <Badge>Outside delivery zone</Badge>
                    )}
                    {s.codEnabled && <Badge tone="violet">Pay on delivery</Badge>}
                  </div>
                </div>

                <div className="store-distance">
                  <Navigation size={14} />
                  <strong>{s.distanceKm} km</strong>
                  <span className="tiny muted-2">~{s.etaMinutes} min</span>
                </div>
              </div>
            </Link>
          ))}
        </div>
      )}
    </>
  );
}
