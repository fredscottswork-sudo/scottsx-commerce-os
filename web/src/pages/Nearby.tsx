/**
 * Nearby stores — fast Google Maps village detection
 * - Gets lat/lng via navigator.geolocation (high accuracy)
 * - In background calls /geo/reverse which uses Google Maps with <1s timeout
 * - Shows village name on frontend within 1 second
 * - Distance calc uses GPS lat/lng directly
 */

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  MapPin, BadgeCheck, Navigation, Radio, Clock, Truck, Star,
  LocateFixed, LocateOff, Package, Globe2,
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
  useSeo({
    title: 'Stores near you',
    description: 'Find shops near you on ScottsTechX. Uses GPS + Google Maps to identify your village in under 1 second.',
  });

  const { toast } = useToast();
  const { user } = useAuth();

  const [center, setCenter] = useState<{ lat: number; lng: number } | null>(null);
  const [place, setPlace] = useState<Place | null>(null);
  const [usingGps, setUsingGps] = useState(false);
  const [locating, setLocating] = useState(true);
  const [geoDenied, setGeoDenied] = useState(false);
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
      setUpdatedAt(new Date());
      lastFetchCenter.current = at;
      setMoved(false);
    } catch (e: any) {
      setError(e?.message || 'Could not load nearby stores');
    } finally {
      setLoading(false);
    }
  }, [q, verifiedOnly, openOnly, sort]);

  // Fast Google Maps reverse in background — must be <1s
  const resolveVillage = useCallback(async (lat: number, lng: number) => {
    try {
      // Fire in background, don't block seller fetch
      const rev = await geoService.reverse(lat, lng);
      if (rev.place) {
        setPlace(rev.place);
      }
    } catch {
      // silent — offline fallback already tried on backend
    }
  }, []);

  const applyPosition = useCallback(async (next: { lat: number; lng: number }, accuracy?: number) => {
    setCenter(next);
    setAccuracyM(typeof accuracy === 'number' ? Math.round(accuracy) : null);
    setLocating(false);

    // Background Google Maps village lookup — <1s
    void resolveVillage(next.lat, next.lng);

    // Save my location in background
    if (user && !savedOnce.current) {
      savedOnce.current = true;
      geoService.saveMyLocation(next.lat, next.lng, accuracy).catch(() => {});
    }
  }, [user, resolveVillage]);

  useEffect(() => {
    let cancelled = false;
    async function fallbackToSaved(reason: string) {
      if (cancelled) return;
      if (user) {
        try {
          const r = await geoService.myLocation();
          if (!cancelled && r.position) {
            setCenter(r.position);
            if (r.place) setPlace(r.place);
            setLocating(false);
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
      void fallbackToSaved('This browser cannot detect your location.');
      return () => { cancelled = true; };
    }

    navigator.geolocation.getCurrentPosition(
      (pos) => {
        if (cancelled) return;
        applyPosition({ lat: pos.coords.latitude, lng: pos.coords.longitude }, pos.coords.accuracy);
      },
      (err) => {
        void fallbackToSaved(
          err.code === err.PERMISSION_DENIED
            ? 'Location permission denied. Allow location access to see stores near you.'
            : 'Could not detect your location. Check that location services are on.'
        );
      },
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 }
    );
    return () => { cancelled = true; };
  }, [user, applyPosition]);

  useEffect(() => {
    if (!center) return;
    setLoading(true);
    const t = setTimeout(() => void fetchSellers(center), 150);
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
        const from = lastFetchCenter.current;
        const movedM = from ? metresBetween(from, next) : Infinity;
        setAccuracyM(Math.round(pos.coords.accuracy ?? 0));
        setSellers((prev) =>
          prev
            .map((s) => ({ ...s, distanceKm: Number((metresBetween(next, s) / 1000).toFixed(2)) }))
            .sort((a, b) => (sort === 'distance' ? a.distanceKm - b.distanceKm : 0))
        );
        if (!from || movedM > REFETCH_METRES) {
          setMoved(true);
          savedOnce.current = false;
          applyPosition(next, pos.coords.accuracy);
        }
      },
      (err) => {
        setUsingGps(false);
        toast(err.code === err.PERMISSION_DENIED ? 'Location permission denied' : 'Could not get your location', 'warning');
      },
      { enableHighAccuracy: true, maximumAge: 5000, timeout: 15000 }
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
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 }
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
        sub="GPS + Google Maps finds your village in under 1 second. Sellers update in real time."
        actions={
          usingGps ? (
            <Btn variant="danger" icon={<LocateOff size={15} />} onClick={stopTracking}>Stop tracking</Btn>
          ) : (
            <Btn variant="primary" icon={<LocateFixed size={15} />} onClick={startTracking}>Follow my location</Btn>
          )
        }
      />

      <div className="card card-pad mb-16 place-banner">
        <span className="place-ico"><MapPin size={18} /></span>
        <div className="grow" style={{ minWidth: 0 }}>
          <div className="tiny muted-2 semi">Your location {place?.source && <Badge tone={place.source === 'google' ? 'violet' : 'default'}>{place.source}</Badge>}</div>
          {locating ? (
            <strong className="place-name">Detecting your location…</strong>
          ) : place ? (
            <>
              <strong className="place-name" data-testid="place-label">{place.label}</strong>
              <div className="tiny muted mt-4 place-parts row wrap" style={{ gap: 8 }}>
                {accuracyM !== null && <span>GPS ±{accuracyM} m</span>}
                {place.village && <span>Village: {place.village}</span>}
                {place.city && <span>City: {place.city}</span>}
                {place.region && <span>Region: {place.region}</span>}
                {place.country && <span>Country: {place.country}</span>}
              </div>
            </>
          ) : (
            <strong className="place-name">Location unavailable</strong>
          )}
        </div>
      </div>

      {geoDenied && (
        <div className="card card-pad mb-16 row" style={{ gap: 10, borderColor: 'var(--warning)' }}>
          <div className="grow">
            <strong>We could not detect your location</strong>
            <div className="tiny muted">Allow location access, then try again — Google Maps will name your village in under 1 second.</div>
          </div>
          <Btn variant="primary" onClick={retryLocate}>Try again</Btn>
        </div>
      )}

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

      <div className="row wrap mb-16" style={{ gap: 9 }}>
        <Badge tone="primary"><Globe2 size={11} style={{ verticalAlign: -1, marginRight: 4 }} />{stats.shown} of {total} stores</Badge>
        {liveCount > 0 && <Badge tone="green" live>{liveCount} sharing live</Badge>}
        <Badge tone="cyan">{stats.open} open now</Badge>
        <Badge tone="violet">{stats.delivering} deliver to you</Badge>
        {updatedAt && <span className="tiny muted-2"><Clock size={11} style={{ verticalAlign: -1 }} /> Updated {updatedAt.toLocaleTimeString()}</span>}
        {moved && <span className="tiny t-primary semi">You moved — refreshing…</span>}
      </div>

      {loading ? (
        <SkeletonRows rows={5} height={116} />
      ) : error ? (
        <ErrorBox message={error} onRetry={center ? () => void fetchSellers(center) : retryLocate} />
      ) : sellers.length === 0 ? (
        <Empty icon={<MapPin size={28} />} title="No stores match" subtitle="Clear filters to see every store, sorted by GPS distance." action={<Btn variant="primary" onClick={() => { setVerifiedOnly(false); setOpenOnly(false); setQ(''); }}>Clear filters</Btn>} />
      ) : (
        <div className="grid grid-2 stagger">
          {sellers.map((s, i) => (
            <Link key={s.id} to={`/seller/${s.id}`} className="card card-hover store-card stagger-item" style={{ '--i': i } as React.CSSProperties}>
              <div className="row" style={{ alignItems: 'flex-start', gap: 12 }}>
                <span className="avatar avatar-lg">{s.logoUrl ? <img src={s.logoUrl} alt="" /> : (s.storeName || s.name || 'S')[0].toUpperCase()}</span>
                <div className="grow" style={{ minWidth: 0 }}>
                  <div className="row" style={{ gap: 6 }}>
                    <strong className="ellipsis">{s.storeName || s.name}</strong>
                    {s.verified && <BadgeCheck size={15} className="t-success" />}
                    {s.isOpen ? <Badge tone="green">Open</Badge> : <Badge>Closed</Badge>}
                  </div>
                  <div className="tiny muted mt-4"><Star size={11} style={{ verticalAlign: -1, color: 'var(--warning)' }} fill="currentColor" /> {Number(s.rating || 0).toFixed(1)}{s.newThisWeek > 0 && <span className="t-success"> · {s.newThisWeek} new</span>}</div>
                  <div className="tiny muted mt-4 ellipsis"><MapPin size={11} style={{ verticalAlign: -1 }} /> {s.placeLabel || s.address || s.city || '—'}</div>
                  <div className="row wrap mt-8" style={{ gap: 6 }}>
                    {s.live ? <Badge tone="green" live>Live · {s.locationAgeMinutes !== null ? `${s.locationAgeMinutes}m ago` : 'now'}</Badge> : <Badge tone="amber"><Radio size={10} style={{ verticalAlign: -1, marginRight: 3 }} />{s.locationSharing ? 'Last seen' : 'Fixed address'}</Badge>}
                    {s.withinServiceRadius ? <Badge tone="cyan"><Truck size={10} style={{ verticalAlign: -1, marginRight: 3 }} />{s.deliveryFeeUgx > 0 ? formatUgx(s.deliveryFeeUgx) : 'Free delivery'}</Badge> : <Badge>Outside delivery zone</Badge>}
                    {s.codEnabled && <Badge tone="violet">Pay on delivery</Badge>}
                  </div>
                </div>
                <div className="store-distance"><Navigation size={14} /><strong>{s.distanceKm} km</strong><span className="tiny muted-2">~{s.etaMinutes} min</span></div>
              </div>
            </Link>
          ))}
        </div>
      )}
    </>
  );
}
