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
import { namePins, placeRank, resolveBestPlace } from '../lib/geocode';
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
  /** A finer place lookup is still running (village may still appear). */
  const [resolving, setResolving] = useState(false);
  const placeSeq = useRef(0);

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
      if (r.place) setPlace((cur) => (placeRank(r.place) > placeRank(cur) ? r.place : cur));
      setUpdatedAt(new Date());
      lastFetchCenter.current = at;
      setMoved(false);
    } catch (e: any) {
      setError(e?.message || 'Could not load nearby stores');
    } finally {
      setLoading(false);
    }
  }, [q, verifiedOnly, openOnly, sort]);

  /**
   * Name the fix. One code path for every source: the API (which also saves
   * the position for signed-in users), OpenStreetMap from the browser, and
   * Google when a key is configured. Whatever knows the village wins; a
   * later, coarser answer can never replace a finer one already shown.
   */
  const applyPosition = useCallback((next: { lat: number; lng: number }, rawAcc?: number) => {
    // Some browsers report NaN/Infinity/undefined accuracy; never forward those.
    const acc = typeof rawAcc === 'number' && Number.isFinite(rawAcc) && rawAcc >= 0 ? Math.round(rawAcc) : undefined;
    setCenter(next);
    setLocating(false);
    setAccuracyM(acc ?? null);

    const seq = ++placeSeq.current;
    setResolving(true);
    const server: Promise<Place | null> = user && !savedOnce.current
      ? (savedOnce.current = true, geoService.saveMyLocation(next.lat, next.lng, acc).then((r) => r.place))
      : geoService.reverse(next.lat, next.lng, acc).then((r) => r.place);

    const approximate = typeof acc === 'number' && acc > 250;
    const accept = (p: Place | null) => {
      if (seq !== placeSeq.current || !p) return; // a newer fix superseded this one
      setPlace((cur) => (placeRank(p) >= placeRank(cur) ? { ...p, approximate } : cur));
    };
    // Show the API's answer the moment it lands (at least the city)…
    server.then(accept).catch(() => undefined);
    // …then upgrade to the finest village any source can name.
    void resolveBestPlace(next.lat, next.lng, server).then((best) => {
      if (seq === placeSeq.current) setResolving(false);
      accept(best);
    });
  }, [user]);

  useEffect(() => {
    let cancelled = false;
    async function fallbackToSavedLocation(reason: string) {
      if (cancelled) return;
      if (user) {
        try {
          const r = await geoService.myLocation();
          if (!cancelled && r.position) {
            if (r.place) setPlace(r.place);
            savedOnce.current = true; // already stored — do not re-save a stale fix
            applyPosition(r.position, undefined);
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

    // Two-stage fix.
    //  1. getCurrentPosition with a cached fix allowed → something on screen
    //     within a second (stores + at least the city).
    //  2. watchPosition with maximumAge:0 for up to 12s → re-apply whenever a
    //     materially better fix arrives (≤60 m, or 2× better), because the
    //     village depends on the fix being tighter than the village itself.
    let bestAcc = Infinity;
    let gotAny = false;
    const consider = (pos: GeolocationPosition) => {
      if (cancelled) return;
      const acc = pos.coords.accuracy ?? Infinity;
      const better = !gotAny || (acc <= 60 && acc < bestAcc) || acc < bestAcc / 2;
      if (!better) return;
      gotAny = true;
      bestAcc = acc;
      applyPosition({ lat: pos.coords.latitude, lng: pos.coords.longitude }, acc);
    };
    const fail = (err: GeolocationPositionError) => {
      if (gotAny) return;
      void fallbackToSavedLocation(
        err.code === err.PERMISSION_DENIED
          ? 'Location permission denied. Allow location access to see stores near you.'
          : 'Could not detect your location. Check that location services are on.'
      );
    };
    let watch: number | null = null;
    try {
      navigator.geolocation.getCurrentPosition(consider, fail, { enableHighAccuracy: false, timeout: 8000, maximumAge: 5 * 60_000 });
      watch = navigator.geolocation.watchPosition(
        (pos) => { consider(pos); if ((pos.coords.accuracy ?? Infinity) <= 25 && watch !== null) navigator.geolocation.clearWatch(watch); },
        (err) => { if (err.code === err.PERMISSION_DENIED) fail(err); },
        { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 }
      );
    } catch {
      void fallbackToSavedLocation('This browser cannot detect your location.');
    }
    const stopAfter = setTimeout(() => { if (watch !== null) navigator.geolocation.clearWatch(watch); }, 12000);

    return () => {
      cancelled = true;
      clearTimeout(stopAfter);
      if (watch !== null) navigator.geolocation.clearWatch(watch);
    };
  }, [user, applyPosition]);

  /** Fill in villages for pins the API could not name (browser-side OSM). */
  useEffect(() => {
    const missing = sellers.filter((s) => !s.village && Number.isFinite(s.lat) && Number.isFinite(s.lng));
    if (!missing.length) return;
    return namePins(
      missing.map((s) => ({ id: s.id, lat: s.lat, lng: s.lng })),
      (id, village, shortLabel) => {
        if (!village) return;
        setSellers((prev) => prev.map((s) => (s.id === id ? { ...s, village, placeLabel: shortLabel } : s)));
      }
    );
    // Only when the set of ids changes, not on every distance re-sort.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sellers.map((s) => s.id).join(',')]);

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
    if (!navigator.geolocation) return;
    setLocating(true);
    setGeoDenied(false);
    setError('');
    savedOnce.current = false;
    let best = Infinity;
    let got = false;
    const consider = (pos: GeolocationPosition) => {
      const acc = pos.coords.accuracy ?? Infinity;
      if (got && !(acc < best / 2 || (acc <= 60 && acc < best))) return;
      got = true; best = acc;
      applyPosition({ lat: pos.coords.latitude, lng: pos.coords.longitude }, acc);
    };
    const fail = (err: GeolocationPositionError) => {
      if (got) return;
      setLocating(false);
      setGeoDenied(err.code === err.PERMISSION_DENIED);
      setError(err.code === err.PERMISSION_DENIED
        ? 'Location permission is still blocked. Enable it in your browser settings.'
        : 'Could not get a GPS fix. Move outdoors and try again.');
    };
    navigator.geolocation.getCurrentPosition(consider, fail, { enableHighAccuracy: false, timeout: 8000, maximumAge: 60_000 });
    const w = navigator.geolocation.watchPosition(
      (pos) => { consider(pos); if ((pos.coords.accuracy ?? Infinity) <= 25) navigator.geolocation.clearWatch(w); },
      (err) => { if (err.code === err.PERMISSION_DENIED) fail(err); },
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 }
    );
    window.setTimeout(() => navigator.geolocation.clearWatch(w), 12000);
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
          {locating && !place ? (
            <strong className="place-name">Detecting your village…</strong>
          ) : place ? (
            <>
              <strong className="place-name" data-testid="place-label">
                {place.village || place.city || place.region || place.label}
                {place.approximate && <span className="place-approx" title="GPS fix is coarse — move outdoors for an exact village">~ approx.</span>}
                {!place.village && resolving && <span className="place-approx place-approx--busy">finding village…</span>}
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
