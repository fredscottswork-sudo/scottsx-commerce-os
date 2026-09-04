/**
 * Nearby stores — global, self-locating.
 *
 * The marketplace is worldwide, so this screen has no radius control and no
 * hard-coded city list. It detects where you are, names the spot
 * (village / city / region / country) and lists every store sorted by distance,
 * re-sorting continuously as you move.
 *
 * Position semantics the buyer can trust:
 *   • a seller sharing location   → the pin follows their live GPS fix,
 *   • a seller not sharing        → the pin stays at their last known position.
 */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  MapPin, BadgeCheck, Navigation, Radio, Clock, Truck, Star,
  LocateFixed, LocateOff, Package, Globe2, AlertCircle,
} from 'lucide-react';
import { productService, geoService } from '../api/services';
import type { NearbySeller, Place } from '../api/types';
import { formatUgx } from '../api/types';
import { useToast } from '../store/ToastContext';
import { useAuth } from '../store/AuthContext';
import {
  Btn, Empty, ErrorBox, PageHeader, Select, SkeletonRows, Switch, Badge, SearchInput,
} from '../components/ui';
import { useSeo } from '../hooks/useSeo';

type Sort = 'distance' | 'rating' | 'products' | 'newest';

/** Metres the buyer must move before we re-query the server. */
const REFETCH_METRES = 250;

/**
 * A fix at or under this radius is treated as a real GPS lock. Anything much
 * larger is a network estimate (Wi-Fi/cell) that can land in the wrong suburb,
 * so we keep listening for something better and tell the buyer it is rough.
 */
const GOOD_ACCURACY_M = 100;

/**
 * Stop refining early only at this radius. A true GNSS lock outdoors reaches
 * 5-20 m; 100 m is merely "good enough to show something" and can still sit in
 * the wrong suburb, which is exactly the complaint. So we keep listening past
 * GOOD_ACCURACY_M and only cut the watch short once the fix is genuinely
 * precise — the watch is cheap and stops on its own at REFINE_MS.
 */
const EXCELLENT_ACCURACY_M = 25;

/**
 * How long to keep waiting for a sharper fix after a coarse first reading.
 * A cold GPS chip indoors or under cloud routinely needs 20-30 s to go from a
 * Wi-Fi estimate to a satellite lock; cutting off at 12 s often left the buyer
 * pinned to the network fix, in the wrong village.
 */
const REFINE_MS = 30000;

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
  useSeo({
    title: 'Stores near you',
    description:
      'Find shops near you on ScottsTechX. Stores are sorted by distance and ' +
      're-sort as you move, so the closest seller is always first.',
  });

  const { toast } = useToast();
  const { user } = useAuth();

  const [center, setCenter] = useState<{ lat: number; lng: number } | null>(null);
  const [place, setPlace] = useState<Place | null>(null);
  const [usingGps, setUsingGps] = useState(false);
  const [locating, setLocating] = useState(true);
  const [geoDenied, setGeoDenied] = useState(false);
  /** Radius of uncertainty, in metres, reported by the device for the fix in use. */
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
  /** Accuracy of the fix currently displayed, so a worse one cannot replace it. */
  const bestAccuracy = useRef<number>(Number.POSITIVE_INFINITY);

  // ── Fetch: no radius — the API returns every store, nearest first ────────
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
      if (r.place) setPlace(r.place);
      setUpdatedAt(new Date());
      lastFetchCenter.current = at;
      setMoved(false);
    } catch (e: any) {
      setError(e?.message || 'Could not load nearby stores');
    } finally {
      setLoading(false);
    }
  }, [q, verifiedOnly, openOnly, sort]);

  /** Apply a new position: name it, remember it, and refresh the list. */
  const applyPosition = useCallback((next: { lat: number; lng: number }, accuracy?: number) => {
    setCenter(next);
    setAccuracyM(typeof accuracy === 'number' ? Math.round(accuracy) : null);
    setLocating(false);
    // Persist for signed-in users so the account knows where they are.
    if (user && !savedOnce.current) {
      savedOnce.current = true;
      geoService.saveMyLocation(next.lat, next.lng, accuracy)
        .then((r) => { if (r.place) setPlace(r.place); })
        .catch(() => undefined);
    } else {
      geoService.reverse(next.lat, next.lng)
        .then((r) => setPlace(r.place))
        .catch(() => undefined);
    }
  }, [user]);

  /**
   * Briefly watch for a sharper reading after a coarse first fix. The GPS chip
   * needs a few seconds to acquire satellites; until then the browser answers
   * from Wi-Fi. Without this the buyer is shown the wrong neighbourhood and it
   * never corrects itself while they stand still.
   */
  const refineFix = useCallback(() => {
    if (!navigator.geolocation) return;
    const id = navigator.geolocation.watchPosition(
      (pos) => {
        const acc = pos.coords.accuracy ?? Number.POSITIVE_INFINITY;
        if (acc < bestAccuracy.current) {
          bestAccuracy.current = acc;
          savedOnce.current = false; // a better fix is worth persisting
          applyPosition({ lat: pos.coords.latitude, lng: pos.coords.longitude }, acc);
        }
        // Only stop early on a genuinely precise lock. Stopping at 100 m used
        // to freeze the buyer on a coarse network fix that named the wrong
        // suburb, even though a satellite fix was seconds away.
        if (acc <= EXCELLENT_ACCURACY_M) navigator.geolocation.clearWatch(id);
      },
      () => undefined,
      { enableHighAccuracy: true, maximumAge: 0, timeout: REFINE_MS }
    );
    window.setTimeout(() => navigator.geolocation.clearWatch(id), REFINE_MS);
  }, [applyPosition]);

  // ── Detect the buyer's position automatically on arrival ─────────────────
  useEffect(() => {
    let cancelled = false;

    async function fallbackToSavedLocation(reason: string) {
      if (cancelled) return;
      // A signed-in user has a stored last-known position — use it so the page
      // is never empty just because the browser refused a fresh fix.
      if (user) {
        try {
          const r = await geoService.myLocation();
          if (!cancelled && r.position) {
            setCenter(r.position);
            if (r.place) setPlace(r.place);
            setLocating(false);
            return;
          }
        } catch { /* fall through */ }
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

    // The first callback is often a cached, network-derived fix accurate to
    // several kilometres — enough to name the wrong suburb. Take it so the page
    // is not empty, then let watchPosition refine it as the GPS chip warms up.
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        if (cancelled) return;
        bestAccuracy.current = pos.coords.accuracy ?? Number.POSITIVE_INFINITY;
        applyPosition({ lat: pos.coords.latitude, lng: pos.coords.longitude }, pos.coords.accuracy);
        // Refine unless the very first reading is already precise. The old
        // threshold (100 m) accepted a coarse Wi-Fi fix as final, which is how
        // a buyer ended up being shown a village they were not in.
        if (bestAccuracy.current > EXCELLENT_ACCURACY_M) refineFix();
      },
      (err) => {
        void fallbackToSavedLocation(
          err.code === err.PERMISSION_DENIED
            ? 'Location permission denied. Allow location access to see stores near you.'
            : 'Could not detect your location. Check that location services are on.'
        );
      },
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 }
    );

    return () => { cancelled = true; };
  }, [user, applyPosition, refineFix]);

  // Refresh whenever the position or a filter changes.
  useEffect(() => {
    if (!center) return;
    setLoading(true);
    const t = setTimeout(() => void fetchSellers(center), 220);
    return () => clearTimeout(t);
  }, [center, fetchSellers]);

  // ── Continuous tracking: stores re-sort as the buyer moves ───────────────
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
        const acc = pos.coords.accuracy ?? Number.POSITIVE_INFINITY;
        const from = lastFetchCenter.current;
        const movedM = from ? metresBetween(from, next) : Infinity;

        // Ignore a fix that is much vaguer than the one on screen unless the
        // buyer has genuinely travelled: GPS periodically drops back to a
        // cell-tower estimate, which would otherwise yank the pin kilometres
        // away and reshuffle the whole list.
        if (acc > bestAccuracy.current * 2 && acc > GOOD_ACCURACY_M && movedM < REFETCH_METRES) return;
        bestAccuracy.current = acc;
        setAccuracyM(Math.round(acc));

        // Re-sort locally on every fix (instant); re-query only after real movement.
        setSellers((prev) =>
          prev
            .map((s) => ({ ...s, distanceKm: Number((metresBetween(next, s) / 1000).toFixed(2)) }))
            .sort((a, b) => (sort === 'distance' ? a.distanceKm - b.distanceKm : 0))
        );
        if (!from || movedM > REFETCH_METRES) {
          setMoved(true);
          savedOnce.current = false; // a real move is worth persisting again
          applyPosition(next, pos.coords.accuracy);
        }
      },
      (err) => {
        setUsingGps(false);
        toast(
          err.code === err.PERMISSION_DENIED
            ? 'Location permission denied'
            : 'Could not get your location',
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

  /** Ask the browser again after a denial (or first visit without a fix). */
  const retryLocate = useCallback(() => {
    setLocating(true);
    setGeoDenied(false);
    setError('');
    bestAccuracy.current = Number.POSITIVE_INFINITY;
    navigator.geolocation?.getCurrentPosition(
      (pos) => {
        bestAccuracy.current = pos.coords.accuracy ?? Number.POSITIVE_INFINITY;
        applyPosition({ lat: pos.coords.latitude, lng: pos.coords.longitude }, pos.coords.accuracy);
      },
      () => {
        setLocating(false);
        setGeoDenied(true);
        setError('Location permission is still blocked. Enable it in your browser settings.');
      },
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 }
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
            <Btn variant="danger" icon={<LocateOff size={15} />} onClick={stopTracking}>Stop tracking</Btn>
          ) : (
            <Btn variant="primary" icon={<LocateFixed size={15} />} onClick={startTracking}>Follow my location</Btn>
          )
        }
      />

      {/* ── Where you are ───────────────────────────────────────────── */}
      <div className="card card-pad mb-16 place-banner">
        <span className="place-ico"><MapPin size={18} /></span>
        <div className="grow" style={{ minWidth: 0 }}>
          <div className="tiny muted-2 semi">Your location</div>
          {locating ? (
            <strong className="place-name">Detecting your location…</strong>
          ) : place ? (
            <>
              <strong className="place-name" data-testid="place-label">{place.label}</strong>
              <div className="tiny muted mt-4 place-parts">
                {accuracyM !== null && (
                  <span data-testid="gps-accuracy">
                    <span className="muted-2">Precision:</span>{' '}
                    {accuracyM <= GOOD_ACCURACY_M
                      ? `±${accuracyM} m`
                      : `±${accuracyM >= 1000 ? `${(accuracyM / 1000).toFixed(1)} km` : `${accuracyM} m`} — approximate`}
                  </span>
                )}
                {place.village && <span><span className="muted-2">Village:</span> {place.village}</span>}
                {place.city && <span><span className="muted-2">City:</span> {place.city}</span>}
                {place.region && <span><span className="muted-2">Region:</span> {place.region}</span>}
                {place.country && <span><span className="muted-2">Country:</span> {place.country}</span>}
              </div>
            </>
          ) : (
            <strong className="place-name">Location unavailable</strong>
          )}
        </div>
        <div className="row" style={{ gap: 8 }}>
          {usingGps && <Badge tone="green" live>Live GPS</Badge>}
          {!usingGps && !locating && (
            <Btn variant="ghost" icon={<LocateFixed size={14} />} onClick={retryLocate}>Update</Btn>
          )}
        </div>
      </div>

      {geoDenied && (
        <div className="card card-pad mb-16 row" style={{ gap: 10, borderColor: 'var(--warning)' }}>
          <AlertCircle size={18} className="t-warning" />
          <div className="grow">
            <strong>We could not detect your location</strong>
            <div className="tiny muted">Allow location access, then try again — no city list needed, it works anywhere in the world.</div>
          </div>
          <Btn variant="primary" onClick={retryLocate}>Try again</Btn>
        </div>
      )}

      {/* ── Filters (no radius: the whole world is in range) ─────────── */}
      <div className="card mb-16">
        <div className="row wrap" style={{ gap: 14 }}>
          <div style={{ flex: '1 1 220px', minWidth: 200 }}>
            <SearchInput value={q} onChange={setQ} placeholder="Filter stores or products…" />
          </div>

          <Select aria-label="Sort stores" value={sort} onChange={(e) => setSort(e.target.value as Sort)} style={{ width: 'auto' }}>
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
        <Badge tone="primary">
          <Globe2 size={11} style={{ verticalAlign: -1, marginRight: 4 }} />
          {stats.shown} of {total} store{total === 1 ? '' : 's'}
        </Badge>
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
        <ErrorBox message={error} onRetry={center ? () => void fetchSellers(center) : retryLocate} />
      ) : sellers.length === 0 ? (
        <Empty
          icon={<MapPin size={28} />}
          title="No stores match"
          subtitle="Clear the filters to see every store, sorted by distance from you."
          action={<Btn variant="primary" onClick={() => { setVerifiedOnly(false); setOpenOnly(false); setQ(''); }}>
            Clear filters
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
                    {' '}{Number(s.rating || 0).toFixed(1)}
                    {s.newThisWeek > 0 && <span className="t-success"> · {s.newThisWeek} new</span>}
                  </div>

                  <div className="tiny muted mt-4 ellipsis">
                    <MapPin size={11} style={{ verticalAlign: -1 }} /> {s.placeLabel || s.address || s.city || '—'}
                  </div>

                  {/* The live / last-known distinction the buyer must be able to trust. */}
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
