/**
 * Nearby stores — compact store displayer (desktop + mobile, mostly mobile)
 * Original master restored, plus Google Maps background <1s, now ultra compact.
 */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  MapPin, BadgeCheck, Navigation, Radio, Clock, Truck, Star,
  LocateFixed, LocateOff, Package, Globe2, AlertCircle, Crosshair, ChevronRight,
} from 'lucide-react';
import { productService, geoService } from '../api/services';
import type { NearbySeller, Place } from '../api/types';
import { formatUgx } from '../api/types';
import { useToast } from '../store/ToastContext';
import { useAuth } from '../store/AuthContext';
import {
  Btn, Empty, ErrorBox, PageHeader, Select, SkeletonRows, Switch, Badge, SearchInput,
} from '../components/ui';

type Sort = 'distance' | 'rating' | 'products' | 'newest';
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

/** "Bukoto, Kampala" minus a leading "Bukoto, " → " · Kampala". */
function restOfLabel(label: string, village: string): string {
  if (!label) return '';
  const rest = label.toLowerCase().startsWith(village.toLowerCase())
    ? label.slice(village.length).replace(/^[,\s·]+/, '')
    : label;
  return rest && rest.toLowerCase() !== village.toLowerCase() ? ` · ${rest}` : '';
}

export default function Nearby() {
  const { toast } = useToast();
  const { user } = useAuth();

  const [center, setCenter] = useState<{ lat: number; lng: number } | null>(null);
  const [place, setPlace] = useState<Place | null>(null);
  const [usingGps, setUsingGps] = useState(false);
  const [locating, setLocating] = useState(true);
  const [geoDenied, setGeoDenied] = useState(false);
  /** Reported GPS accuracy radius in metres for the current fix. */
  const [accuracyM, setAccuracyM] = useState<number | null>(null);

  const [sort, setSort] = useState<Sort>('distance');
  const [verifiedOnly, setVerifiedOnly] = useState(false);
  const [openOnly, setOpenOnly] = useState(false);
  const [q, setQ] = useState('');

  const [sellers, setSellers] = useState<NearbySeller[]>([]);
  const [total, setTotal] = useState(0);
  const [liveCount, setLiveCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null);
  const [moved, setMoved] = useState(false);

  const watchId = useRef<number | null>(null);
  const lastFetchCenter = useRef<{ lat: number; lng: number } | null>(null);
  const savedOnce = useRef(false);

  const fetchSellers = useCallback(async (at: { lat: number; lng: number }) => {
    setError('');
    try {
      const r = await productService.nearby({
        lat: at.lat,
        lng: at.lng,
        q: q.trim() || undefined,
        verifiedOnly: verifiedOnly || undefined,
        openOnly: openOnly || undefined,
        sort,
      });
      setSellers(r.sellers);
      setTotal(r.total ?? r.count);
      setLiveCount(r.liveCount);
      // Only accept the sellers' endpoint place if it is at least as exact
      // as what we already have — never downgrade a village to a city.
      if (r.place) setPlace((cur) => (cur?.village && !r.place?.village ? cur : r.place));
      setUpdatedAt(new Date());
      lastFetchCenter.current = at;
      setMoved(false);
    } catch (e: any) {
      setError(e?.message || 'Could not load nearby stores');
    } finally {
      setLoading(false);
    }
  }, [q, verifiedOnly, openOnly, sort]);

  const applyPosition = useCallback((next: { lat: number; lng: number }, accuracyM?: number) => {
    setCenter(next);
    setLocating(false);
    setAccuracyM(typeof accuracyM === 'number' && Number.isFinite(accuracyM) ? accuracyM : null);
    if (user && !savedOnce.current) {
      savedOnce.current = true;
      geoService.saveMyLocation(next.lat, next.lng, accuracyM)
        .then((r) => {
          if (r.place) setPlace((cur) => (cur?.village && !r.place?.village ? cur : r.place));
          // If the save returned only a city, ask again — the resolver
          // holds city-only answers for 5s, so this retry hits providers.
          if (!r.place?.village) {
            return geoService.reverse(next.lat, next.lng, accuracyM)
              .then((rr) => { if (rr.place?.village) setPlace(rr.place); });
          }
        })
        .catch(() => undefined);
    } else {
      geoService.reverse(next.lat, next.lng, accuracyM)
        .then((r) => {
          setPlace((cur) => (cur?.village && !r.place?.village ? cur : r.place));
          if (!r.place?.village) {
            return new Promise((res) => setTimeout(res, 2500))
              .then(() => geoService.reverse(next.lat, next.lng, accuracyM))
              .then((rr) => { if (rr.place?.village) setPlace(rr.place); });
          }
        })
        .catch(() => undefined);
    }
  }, [user]);

  useEffect(() => {
    let cancelled = false;
    async function fallbackToSavedLocation(reason: string) {
      if (cancelled) return;
      if (user) {
        try {
          const r = await geoService.myLocation();
          if (!cancelled && r.position) {
            setCenter(r.position);
            if (r.place) setPlace(r.place);
            setLocating(false);
            // Stored places from older builds are city-level; re-resolve now.
            geoService.reverse(r.position.lat, r.position.lng)
              .then((rr) => { if (!cancelled && rr.place) setPlace(rr.place); })
              .catch(() => undefined);
            return;
          }
        } catch {}
      }
      if (cancelled) return;
      setLocating(false);
      setGeoDenied(true);
      setLoading(false);
      setError(reason);
    }

    if (!navigator.geolocation) {
      void fallbackToSavedLocation('This browser cannot detect your location.');
      return () => { cancelled = true; };
    }

    // Village accuracy depends on the FIX accuracy. The first position a
    // browser hands over is often a cached wifi/cell fix (±500–2000 m), which
    // can land in the neighbouring village. So: show the first fix at once,
    // then keep watching for up to 12s and re-apply whenever a materially
    // better fix (≤60 m, or 2× better than before) arrives.
    let bestAcc = Infinity;
    let gotAny = false;
    const watch = navigator.geolocation.watchPosition(
      (pos) => {
        if (cancelled) return;
        const acc = pos.coords.accuracy ?? Infinity;
        const better = !gotAny || acc <= 60 && acc < bestAcc || acc < bestAcc / 2;
        if (!better) return;
        gotAny = true;
        bestAcc = acc;
        applyPosition({ lat: pos.coords.latitude, lng: pos.coords.longitude }, acc);
        if (acc <= 25) navigator.geolocation.clearWatch(watch);
      },
      (err) => {
        if (gotAny) return;
        void fallbackToSavedLocation(
          err.code === err.PERMISSION_DENIED
            ? 'Location permission denied. Allow location access to see stores near you.'
            : 'Could not detect your location. Check that location services are on.'
        );
      },
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 }
    );
    const stopAfter = setTimeout(() => navigator.geolocation.clearWatch(watch), 12000);

    return () => { cancelled = true; clearTimeout(stopAfter); navigator.geolocation.clearWatch(watch); };
  }, [user, applyPosition]);

  useEffect(() => {
    if (!center) return;
    setLoading(true);
    const t = setTimeout(() => void fetchSellers(center), 220);
    return () => clearTimeout(t);
  }, [center, fetchSellers]);

  const startTracking = useCallback(() => {
    if (!navigator.geolocation) {
      toast('This browser cannot share your location', 'error');
      return;
    }
    setUsingGps(true);
    setGeoDenied(false);
    watchId.current = navigator.geolocation.watchPosition(
      (pos) => {
        const next = { lat: pos.coords.latitude, lng: pos.coords.longitude };
        setSellers((prev) =>
          prev
            .map((s) => ({ ...s, distanceKm: Number((metresBetween(next, s) / 1000).toFixed(2)) }))
            .sort((a, b) => (sort === 'distance' ? a.distanceKm - b.distanceKm : 0))
        );
        const from = lastFetchCenter.current;
        if (!from || metresBetween(from, next) > REFETCH_METRES) {
          setMoved(true);
          savedOnce.current = false;
          applyPosition(next, pos.coords.accuracy);
        }
      },
      (err) => {
        setUsingGps(false);
        toast(
          err.code === err.PERMISSION_DENIED ? 'Location permission denied' : 'Could not get your location',
          'warning'
        );
      },
      { enableHighAccuracy: true, maximumAge: 10000, timeout: 20000 }
    );
  }, [sort, toast, applyPosition]);

  const stopTracking = useCallback(() => {
    if (watchId.current !== null) navigator.geolocation.clearWatch(watchId.current);
    watchId.current = null;
    setUsingGps(false);
  }, []);

  useEffect(() => () => {
    if (watchId.current !== null) navigator.geolocation.clearWatch(watchId.current);
  }, []);

  const retryLocate = useCallback(() => {
    setLocating(true);
    setGeoDenied(false);
    setError('');
    navigator.geolocation?.getCurrentPosition(
      (pos) => applyPosition({ lat: pos.coords.latitude, lng: pos.coords.longitude }, pos.coords.accuracy),
      () => {
        setLocating(false);
        setGeoDenied(true);
        setError('Location permission is still blocked. Enable it in your browser settings.');
      },
      { enableHighAccuracy: true, timeout: 15000 }
    );
  }, [applyPosition]);

  const stats = useMemo(() => ({
    shown: sellers.length,
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
            <Btn variant="danger" icon={<LocateOff size={15} />} onClick={stopTracking} style={{ padding: '8px 12px', fontSize: 13, minHeight: 38 }}>Stop</Btn>
          ) : (
            <Btn variant="primary" icon={<LocateFixed size={15} />} onClick={startTracking} style={{ padding: '8px 12px', fontSize: 13, minHeight: 38 }}>Follow my location</Btn>
          )
        }
      />

      {/* Where you are — exact village from GPS */}
      <div className="card place-banner place-banner--exact">
        <span className="place-ico"><MapPin size={17} /></span>
        <div className="grow" style={{ minWidth: 0 }}>
          <div className="place-kicker">
            Your location
            {accuracyM !== null && !locating && (
              <span className="place-acc" title="GPS accuracy">
                <Crosshair size={10} /> ±{accuracyM < 1000 ? `${Math.round(accuracyM)} m` : `${(accuracyM / 1000).toFixed(1)} km`}
              </span>
            )}
          </div>
          {locating ? (
            <strong className="place-name">Detecting your village…</strong>
          ) : place ? (
            <>
              <strong className="place-name" data-testid="place-label">
                {place.village || place.city || place.region || place.label}
                {place.approximate && <span className="place-approx" title="GPS fix is coarse — move outdoors for an exact village">~ approx.</span>}
              </strong>
              <div className="place-trail" aria-label="Location hierarchy">
                {[place.suburb, place.city, place.region, place.country]
                  .filter((x, i, arr): x is string => Boolean(x) && arr.indexOf(x) === i && x !== (place.village || ''))
                  .map((part, i) => (
                    <span key={part} className="place-trail-part">
                      {i > 0 && <ChevronRight size={11} className="place-trail-sep" aria-hidden />}
                      {part}
                    </span>
                  ))}
              </div>
            </>
          ) : (
            <strong className="place-name">Location unavailable</strong>
          )}
        </div>
        <div className="row" style={{ gap: 8 }}>
          {usingGps && <Badge tone="green" live>Live</Badge>}
          {!usingGps && !locating && (
            <Btn variant="ghost" icon={<LocateFixed size={14} />} onClick={retryLocate} style={{ padding: '6px 10px', fontSize: 12, minHeight: 30 }}>Update</Btn>
          )}
        </div>
      </div>

      {geoDenied && (
        <div className="card card-pad mb-12 row" style={{ gap: 10, padding: '10px 14px', borderColor: 'var(--warning)' }}>
          <AlertCircle size={16} className="t-warning" />
          <div className="grow">
            <strong style={{ fontSize: 13 }}>Location blocked</strong>
            <div className="tiny muted" style={{ fontSize: 11 }}>Allow access to see nearby stores.</div>
          </div>
          <Btn variant="primary" onClick={retryLocate} style={{ padding: '6px 10px', fontSize: 12, minHeight: 30 }}>Try again</Btn>
        </div>
      )}

      <div className="card mb-12" style={{ padding: '10px 12px' }}>
        <div className="row wrap" style={{ gap: 10 }}>
          <div style={{ flex: '1 1 180px', minWidth: 160 }}>
            <SearchInput value={q} onChange={setQ} placeholder="Filter stores…" />
          </div>
          <Select value={sort} onChange={(e) => setSort(e.target.value as Sort)} aria-label="Sort stores" style={{ width: 'auto', padding: '8px 10px', fontSize: 13, minHeight: 36 }}>
            <option value="distance">Nearest</option>
            <option value="rating">Top rated</option>
            <option value="products">Most products</option>
            <option value="newest">Newest</option>
          </Select>
          <Switch checked={verifiedOnly} onChange={setVerifiedOnly} label="Verified" />
          <Switch checked={openOnly} onChange={setOpenOnly} label="Open" />
        </div>
      </div>

      <div className="row wrap mb-12" style={{ gap: 8 }}>
        <Badge tone="primary"><Globe2 size={12} style={{ marginRight: 3 }} />{stats.shown}/{total}</Badge>
        {liveCount > 0 && <Badge tone="green" live>{liveCount} live</Badge>}
        <Badge tone="cyan">{stats.open} open</Badge>
        <Badge tone="violet">{stats.delivering} deliver</Badge>
        {updatedAt && <span className="tiny muted-2" style={{ fontSize: 11 }}><Clock size={12} /> {updatedAt.toLocaleTimeString()}</span>}
        {moved && <span className="tiny t-primary semi" style={{ fontSize: 11 }}>Refreshing…</span>}
      </div>

      {loading ? (
        <SkeletonRows rows={4} height={92} />
      ) : error ? (
        <ErrorBox message={error} onRetry={center ? () => void fetchSellers(center) : retryLocate} />
      ) : sellers.length === 0 ? (
        <Empty
          icon={<MapPin size={28} />}
          title="No stores match"
          subtitle="Clear filters to see every store."
          action={<Btn variant="primary" onClick={() => { setVerifiedOnly(false); setOpenOnly(false); setQ(''); }} style={{ padding: '8px 14px', fontSize: 13 }}>Clear</Btn>}
        />
      ) : (
        <div className="grid grid-2 stagger" style={{ gap: 10 }}>
          {sellers.map((s, i) => (
            <Link key={s.id} to={`/seller/${s.id}`} className="card card-hover store-card stagger-item"
              style={{ '--i': i, padding: '12px 14px', gap: 8 } as React.CSSProperties}>
              <div className="row" style={{ alignItems: 'flex-start', gap: 10 }}>
                <span className="avatar avatar-lg" style={{ width: 40, height: 40, fontSize: 15 }}>
                  {s.logoUrl ? <img src={s.logoUrl} alt="" /> : (s.storeName || s.name || 'S')[0].toUpperCase()}
                </span>
                <div className="grow" style={{ minWidth: 0 }}>
                  <div className="row" style={{ gap: 6 }}>
                    <strong className="ellipsis" style={{ fontSize: 14 }}>{s.storeName || s.name}</strong>
                    {s.verified && <BadgeCheck size={15} className="t-success" />}
                    {s.isOpen ? <Badge tone="green">Open</Badge> : <Badge>Closed</Badge>}
                  </div>
                  <div className="tiny muted mt-4" style={{ fontSize: 11, display: 'flex', gap: 6, alignItems: 'center' }}>
                    <Star size={11} style={{ color: 'var(--warning)' }} fill="currentColor" /> {Number(s.rating || 0).toFixed(1)} · <Package size={11} /> {s.productCount}
                  </div>
                  <div className="tiny muted mt-4 ellipsis store-village" style={{ fontSize: 11 }}>
                    <MapPin size={11} />{' '}
                    {s.village ? (
                      <><strong style={{ color: 'var(--text)' }}>{s.village}</strong>{restOfLabel(s.placeLabel, s.village)}</>
                    ) : (s.placeLabel || s.address || s.city || '—')}
                  </div>
                  <div className="row wrap mt-8" style={{ gap: 5 }}>
                    {s.live ? (
                      <Badge tone="green" live>Live {s.locationAgeMinutes !== null ? `${s.locationAgeMinutes}m` : ''}</Badge>
                    ) : (
                      <Badge tone="amber"><Radio size={10} style={{ marginRight: 2 }} />{s.locationSharing ? 'Seen' : 'Fixed'}</Badge>
                    )}
                    {s.withinServiceRadius ? (
                      <Badge tone="cyan"><Truck size={10} style={{ marginRight: 2 }} />{s.deliveryFeeUgx > 0 ? formatUgx(s.deliveryFeeUgx) : 'Free'}</Badge>
                    ) : (
                      <Badge>Outside</Badge>
                    )}
                    {s.codEnabled && <Badge tone="violet">COD</Badge>}
                  </div>
                </div>
                <div className="store-distance" style={{ minWidth: 56, padding: '6px 8px', gap: 2 }}>
                  <Navigation size={12} />
                  <strong style={{ fontSize: 12 }}>{s.distanceKm}km</strong>
                  <span className="tiny muted-2" style={{ fontSize: 9 }}>~{s.etaMinutes}m</span>
                </div>
              </div>
            </Link>
          ))}
        </div>
      )}
    </>
  );
}
