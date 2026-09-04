/**
 * Nearby stores — fixed for village accuracy
 *
 * Key fix: GPS positioning is SEPARATE from village naming.
 * - GPS (lat/lng/accuracy) is used for distance, sorting, map
 * - Village name is human label that may be uncertain and requires confirmation
 * - If uncertain between villages, shows "Location near X" not confidently wrong village
 * - User can confirm/search actual village, saved as user_confirmed and never overwritten
 */

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  MapPin, BadgeCheck, Navigation, Radio, Clock, Truck, Star,
  LocateFixed, LocateOff, Package, Globe2, AlertCircle, CheckCircle2,
  Search, Edit3, Crosshair, ShieldCheck, Info,
} from 'lucide-react';
import { productService, geoService } from '../api/services';
import type { NearbySeller, Place } from '../api/types';
import { formatUgx } from '../api/types';
import { useToast } from '../store/ToastContext';
import { useAuth } from '../store/AuthContext';
import {
  Btn, Empty, ErrorBox, PageHeader, Select, SkeletonRows, Switch, Badge, SearchInput, Modal, Field, Input,
} from '../components/ui';
import { useSeo } from '../hooks/useSeo';

type Sort = 'distance' | 'rating' | 'products' | 'newest';
const REFETCH_METRES = 250;
const GOOD_ACCURACY_M = 100;
const EXCELLENT_ACCURACY_M = 25;
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
    description: 'Find shops near you on ScottsTechX. Stores sorted by distance, GPS for distance, village confirmed separately.',
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

  // New: location confirmation flow
  const [showUpdateModal, setShowUpdateModal] = useState(false);
  const [villageSearch, setVillageSearch] = useState('');
  const [villageResults, setVillageResults] = useState<Array<{ name: string; label: string; lat: number; lng: number; type: string }>>([]);
  const [searchingVillage, setSearchingVillage] = useState(false);
  const [confirming, setConfirming] = useState(false);

  const watchId = useRef<number | null>(null);
  const lastFetchCenter = useRef<{ lat: number; lng: number } | null>(null);
  const savedOnce = useRef(false);
  const bestAccuracy = useRef<number>(Number.POSITIVE_INFINITY);

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
      // Only update place from sellers endpoint if we don't have a more precise one
      // and if user hasn't confirmed village
      if (r.place && !place?.villageConfirmed) {
        // Don't overwrite if we already have a confident place
        if (!place || place.confidence === undefined || (r.place.confidence ?? 0) > (place.confidence ?? 0)) {
          // Only if not user-confirmed
          if (!place?.isUserConfirmed) {
            setPlace(r.place as any);
          }
        }
      }
      setUpdatedAt(new Date());
      lastFetchCenter.current = at;
      setMoved(false);
    } catch (e: any) {
      setError(e?.message || 'Could not load nearby stores');
    } finally {
      setLoading(false);
    }
  }, [q, verifiedOnly, openOnly, sort, place]);

  const applyPosition = useCallback(async (next: { lat: number; lng: number }, accuracy?: number) => {
    setCenter(next);
    setAccuracyM(typeof accuracy === 'number' ? Math.round(accuracy) : null);
    setLocating(false);

    // Always use GPS for seller distance — this is separate from village naming
    // Now resolve village name with confidence handling
    try {
      const rev = await geoService.reverse(next.lat, next.lng, accuracy);
      if (rev.place) {
        // If we have a user-confirmed village saved, don't overwrite it
        if (place?.isUserConfirmed || place?.villageConfirmed) {
          // Keep confirmed village, but update GPS position for distance
          // The place label will still show confirmed village
        } else {
          setPlace(rev.place);
        }
      }
    } catch {
      // reverse failed — still keep GPS for distance
    }

    // Persist GPS, but backend will NOT overwrite confirmed village
    if (user && !savedOnce.current) {
      savedOnce.current = true;
      try {
        const saved = await geoService.saveMyLocation(next.lat, next.lng, accuracy);
        if (saved.place && !place?.isUserConfirmed) {
          // Only update if not confirmed
          if (!saved.place.isUserConfirmed) {
            setPlace(saved.place);
          }
        }
      } catch {}
    }
  }, [user, place]);

  const refineFix = useCallback(() => {
    if (!navigator.geolocation) return;
    const id = navigator.geolocation.watchPosition(
      (pos) => {
        const acc = pos.coords.accuracy ?? Number.POSITIVE_INFINITY;
        if (acc < bestAccuracy.current) {
          bestAccuracy.current = acc;
          savedOnce.current = false;
          applyPosition({ lat: pos.coords.latitude, lng: pos.coords.longitude }, acc);
        }
        if (acc <= EXCELLENT_ACCURACY_M) navigator.geolocation.clearWatch(id);
      },
      () => undefined,
      { enableHighAccuracy: true, maximumAge: 0, timeout: REFINE_MS }
    );
    window.setTimeout(() => navigator.geolocation.clearWatch(id), REFINE_MS);
  }, [applyPosition]);

  useEffect(() => {
    let cancelled = false;
    async function fallbackToSavedLocation(reason: string) {
      if (cancelled) return;
      if (user) {
        try {
          const r = await geoService.myLocation();
          if (!cancelled && r.position) {
            setCenter(r.position);
            setAccuracyM(r.position.accuracyM ?? null);
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
      void fallbackToSavedLocation('This browser cannot detect your location.');
      return () => { cancelled = true; };
    }

    navigator.geolocation.getCurrentPosition(
      (pos) => {
        if (cancelled) return;
        bestAccuracy.current = pos.coords.accuracy ?? Number.POSITIVE_INFINITY;
        applyPosition({ lat: pos.coords.latitude, lng: pos.coords.longitude }, pos.coords.accuracy);
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
        const acc = pos.coords.accuracy ?? Number.POSITIVE_INFINITY;
        const from = lastFetchCenter.current;
        const movedM = from ? metresBetween(from, next) : Infinity;
        if (acc > bestAccuracy.current * 2 && acc > GOOD_ACCURACY_M && movedM < REFETCH_METRES) return;
        bestAccuracy.current = acc;
        setAccuracyM(Math.round(acc));
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

  // Village search for correction
  const searchVillage = useCallback(async (query: string) => {
    if (!query.trim()) { setVillageResults([]); return; }
    setSearchingVillage(true);
    try {
      const res = await geoService.search(query.trim(), 10);
      setVillageResults(res.results as any);
    } catch {
      setVillageResults([]);
    } finally {
      setSearchingVillage(false);
    }
  }, []);

  useEffect(() => {
    const t = setTimeout(() => { if (villageSearch) searchVillage(villageSearch); else setVillageResults([]); }, 300);
    return () => clearTimeout(t);
  }, [villageSearch, searchVillage]);

  const confirmDetectedVillage = useCallback(async () => {
    if (!place?.village || !center) return;
    setConfirming(true);
    try {
      await geoService.confirmVillage({
        village: place.village,
        city: place.city ?? undefined,
        district: place.district ?? undefined,
        region: place.region ?? undefined,
        country: place.country ?? undefined,
        lat: center.lat,
        lng: center.lng,
      });
      setPlace((p) => p ? { ...p, isUserConfirmed: true, villageConfirmed: true, villageSource: 'user_confirmed', displayLabel: p.village ?? undefined } as any : p);
      toast(`Village confirmed as ${place.village}`, 'success');
      setShowUpdateModal(false);
    } catch (e: any) {
      toast(e.message || 'Could not confirm village', 'error');
    } finally {
      setConfirming(false);
    }
  }, [place, center, toast]);

  const confirmSearchedVillage = useCallback(async (villageName: string, result?: any) => {
    if (!villageName.trim()) return;
    setConfirming(true);
    try {
      await geoService.confirmVillage({
        village: villageName.trim(),
        city: result?.city ?? place?.city ?? undefined,
        region: result?.region ?? place?.region ?? undefined,
        country: result?.country ?? place?.country ?? undefined,
        lat: result?.lat ?? center?.lat,
        lng: result?.lng ?? center?.lng,
      });
      setPlace((p) => ({
        ...(p ?? {} as any),
        village: villageName.trim(),
        city: result?.city ?? p?.city ?? null,
        region: result?.region ?? p?.region ?? null,
        country: result?.country ?? p?.country ?? null,
        label: villageName.trim(),
        shortLabel: villageName.trim(),
        displayLabel: villageName.trim(),
        isUserConfirmed: true,
        villageConfirmed: true,
        villageSource: 'user_confirmed',
      } as any));
      toast(`Village set to ${villageName.trim()}`, 'success');
      setShowUpdateModal(false);
      setVillageSearch('');
      setVillageResults([]);
    } catch (e: any) {
      toast(e.message || 'Could not set village', 'error');
    } finally {
      setConfirming(false);
    }
  }, [place, center, toast]);

  const stats = useMemo(() => ({
    shown: sellers.length,
    open: sellers.filter((s) => s.isOpen).length,
    delivering: sellers.filter((s) => s.withinServiceRadius).length,
  }), [sellers]);

  const isLowAccuracy = accuracyM !== null && accuracyM > GOOD_ACCURACY_M;
  const isVeryLowAccuracy = accuracyM !== null && accuracyM > 1000;
  const showUncertainWarning = place?.isUncertain || place?.requiresConfirmation;

  return (
    <>
      <PageHeader
        title="Stores near you"
        sub="GPS for distance — village confirmed separately. Sellers sharing live location update in real time."
        actions={
          usingGps ? (
            <Btn variant="danger" icon={<LocateOff size={15} />} onClick={stopTracking}>Stop tracking</Btn>
          ) : (
            <Btn variant="primary" icon={<LocateFixed size={15} />} onClick={startTracking}>Follow my location</Btn>
          )
        }
      />

      {/* Where you are — fixed logic */}
      <div className="card card-pad mb-16 place-banner" style={{ borderColor: showUncertainWarning ? 'var(--warning)' : undefined }}>
        <span className="place-ico"><MapPin size={18} /></span>
        <div className="grow" style={{ minWidth: 0 }}>
          <div className="tiny muted-2 semi row" style={{ gap: 6 }}>
            Your location
            {place?.isUserConfirmed && <Badge tone="green"><CheckCircle2 size={10} /> Confirmed</Badge>}
            {place?.source && <Badge tone={place.source === 'google' ? 'violet' : 'default'}>{place.source}</Badge>}
          </div>

          {locating ? (
            <strong className="place-name">Detecting your location…</strong>
          ) : place ? (
            <>
              <strong className="place-name" data-testid="place-label">
                {place.displayLabel || place.label}
                {place.isUncertain && !place.isUserConfirmed && <span className="muted-2" style={{ fontWeight: 400 }}> — confirm your village</span>}
              </strong>

              {/* Confidence and accuracy warnings */}
              <div className="col mt-8" style={{ gap: 6 }}>
                {isLowAccuracy && (
                  <div className="row" style={{ gap: 6, color: isVeryLowAccuracy ? 'var(--danger)' : 'var(--warning)', fontSize: 12 }}>
                    <AlertCircle size={14} />
                    <span>
                      {isVeryLowAccuracy
                        ? `Low GPS accuracy (±${accuracyM >= 1000 ? `${(accuracyM/1000).toFixed(1)} km` : `${accuracyM} m`}) — move outdoors for better precision`
                        : `GPS accuracy is low (±${accuracyM} m) — village may be approximate`}
                    </span>
                  </div>
                )}

                {showUncertainWarning && !place.isUserConfirmed && (
                  <div className="row" style={{ gap: 6, color: 'var(--warning)', fontSize: 12 }}>
                    <Info size={14} />
                    <span>
                      {place.village
                        ? `Uncertain between nearby villages — showing ${place.village}. Please confirm your actual village.`
                        : `Location detected — confirm your village to avoid wrong locality`}
                    </span>
                  </div>
                )}

                <div className="tiny muted mt-4 place-parts row wrap" style={{ gap: 8 }}>
                  {accuracyM !== null && (
                    <span data-testid="gps-accuracy">
                      <span className="muted-2">GPS:</span> ±{accuracyM} m
                      {accuracyM <= GOOD_ACCURACY_M ? ' • precise' : ' • approximate'}
                    </span>
                  )}
                  {place.village && <span><span className="muted-2">Village:</span> {place.village}{place.isUserConfirmed && ' ✓'}</span>}
                  {place.suburb && place.suburb !== place.village && <span><span className="muted-2">Suburb:</span> {place.suburb}</span>}
                  {place.neighbourhood && place.neighbourhood !== place.village && <span><span className="muted-2">Neighbourhood:</span> {place.neighbourhood}</span>}
                  {place.city && <span><span className="muted-2">City:</span> {place.city}</span>}
                  {place.district && <span><span className="muted-2">District:</span> {place.district}</span>}
                  {place.region && <span><span className="muted-2">Region:</span> {place.region}</span>}
                  {place.country && <span><span className="muted-2">Country:</span> {place.country}</span>}
                  {place.confidence !== undefined && <span><span className="muted-2">Confidence:</span> {Math.round((place.confidence ?? 0)*100)}%</span>}
                </div>

                {place.alternatives && place.alternatives.length > 0 && !place.isUserConfirmed && (
                  <div className="tiny mt-8">
                    <span className="muted-2">Nearby localities:</span>{' '}
                    {place.alternatives.map((a, idx) => (
                      <span key={idx} style={{ marginRight: 8 }}>
                        {a.name} {a.distanceKm ? `(${a.distanceKm} km)` : ''}
                        {idx < (place.alternatives?.length ?? 0)-1 ? ',' : ''}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            </>
          ) : (
            <strong className="place-name">Location unavailable</strong>
          )}
        </div>

        <div className="row" style={{ gap: 8 }}>
          {usingGps && <Badge tone="green" live>Live GPS</Badge>}
          {!locating && (
            <Btn variant={showUncertainWarning ? 'primary' : 'ghost'} icon={<Edit3 size={14} />} onClick={() => setShowUpdateModal(true)}>
              Update
            </Btn>
          )}
        </div>
      </div>

      {geoDenied && (
        <div className="card card-pad mb-16 row" style={{ gap: 10, borderColor: 'var(--warning)' }}>
          <AlertCircle size={18} className="t-warning" />
          <div className="grow">
            <strong>We could not detect your location</strong>
            <div className="tiny muted">Allow location access, then try again — GPS is used for distance, village is confirmed separately.</div>
          </div>
          <Btn variant="primary" onClick={retryLocate}>Try again</Btn>
        </div>
      )}

      {/* Filters */}
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

      {/* Live status strip */}
      <div className="row wrap mb-16" style={{ gap: 9 }}>
        <Badge tone="primary"><Globe2 size={11} style={{ verticalAlign: -1, marginRight: 4 }} />{stats.shown} of {total} store{total === 1 ? '' : 's'}</Badge>
        {liveCount > 0 && <Badge tone="green" live>{liveCount} sharing live</Badge>}
        <Badge tone="cyan">{stats.open} open now</Badge>
        <Badge tone="violet">{stats.delivering} deliver to you</Badge>
        {updatedAt && <span className="tiny muted-2"><Clock size={11} style={{ verticalAlign: -1 }} /> Updated {updatedAt.toLocaleTimeString()}</span>}
        {moved && <span className="tiny t-primary semi">You moved — refreshing…</span>}
        {center && <span className="tiny muted-2"><Crosshair size={11} style={{ verticalAlign: -1 }} /> GPS: {center.lat.toFixed(5)}, {center.lng.toFixed(5)}</span>}
      </div>

      {/* Results — distances from GPS, NOT village */}
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

      {/* Update modal — 3 options */}
      <Modal open={showUpdateModal} title="Update your location" onClose={() => setShowUpdateModal(false)}>
        <div className="col" style={{ gap: 16 }}>
          <p className="tiny muted">
            GPS is used for distance. Village name is separate and can be confirmed.
            Your confirmed village will never be overwritten by automatic detection.
          </p>

          <div className="card card-pad" style={{ background: 'var(--surface-2)' }}>
            <strong className="row" style={{ gap: 6 }}><Crosshair size={14} /> Use my current GPS location</strong>
            <p className="tiny muted mt-4">Re-acquire high-accuracy GPS. Distance to stores will update, village will be re-detected but confirmed village is preserved.</p>
            <Btn variant="primary" size="sm" icon={<LocateFixed size={14} />} onClick={() => { setShowUpdateModal(false); retryLocate(); }} className="mt-8">
              Use GPS now
            </Btn>
            {center && <div className="tiny muted-2 mt-4">Current GPS: {center.lat.toFixed(6)}, {center.lng.toFixed(6)} ±{accuracyM ?? '?'} m</div>}
          </div>

          {place?.village && !place.isUserConfirmed && (
            <div className="card card-pad" style={{ background: 'var(--surface-2)', borderColor: 'var(--success)' }}>
              <strong className="row" style={{ gap: 6 }}><CheckCircle2 size={14} /> Confirm detected village</strong>
              <p className="tiny muted mt-4">Detected as <strong>{place.village}</strong>{place.city ? `, ${place.city}` : ''}. Confirm this is your actual village — it will be saved as user-confirmed and never overwritten.</p>
              {place.alternatives && place.alternatives.length > 0 && (
                <p className="tiny muted-2 mt-4">Alternatives: {place.alternatives.map(a => a.name).join(', ')}</p>
              )}
              <Btn variant="primary" size="sm" loading={confirming} icon={<ShieldCheck size={14} />} onClick={confirmDetectedVillage} className="mt-8">
                Confirm {place.village}
              </Btn>
            </div>
          )}

          <div className="card card-pad" style={{ background: 'var(--surface-2)' }}>
            <strong className="row" style={{ gap: 6 }}><Search size={14} /> Search/select your actual village</strong>
            <p className="tiny muted mt-4">If detected village is wrong (e.g., showing Kisaasi but you are in Kigoowa), search and select correct one.</p>
            <Field label="Village name">
              <div className="row" style={{ gap: 8 }}>
                <Input value={villageSearch} onChange={(e) => setVillageSearch(e.target.value)} placeholder="e.g., Kigoowa, Kabalagala…" />
                <Btn variant="ghost" size="sm" icon={<Search size={14} />} onClick={() => searchVillage(villageSearch)}>Search</Btn>
              </div>
            </Field>

            {searchingVillage && <div className="tiny muted">Searching…</div>}

            {villageResults.length > 0 && (
              <div className="col mt-8" style={{ gap: 6, maxHeight: 200, overflowY: 'auto' }}>
                {villageResults.map((r, idx) => (
                  <button key={idx} className="card card-pad row-between" style={{ textAlign: 'left', cursor: 'pointer' }} onClick={() => confirmSearchedVillage(r.name, r)}>
                    <span><strong>{r.name}</strong><span className="tiny muted"> — {r.label}</span></span>
                    <Badge tone="default">{r.type}</Badge>
                  </button>
                ))}
              </div>
            )}

            <Field label="Or enter manually" hint="If not found in search, type exact village">
              <div className="row" style={{ gap: 8 }}>
                <Input value={villageSearch} onChange={(e) => setVillageSearch(e.target.value)} placeholder="Your village" />
                <Btn variant="primary" size="sm" loading={confirming} disabled={!villageSearch.trim()} onClick={() => confirmSearchedVillage(villageSearch)}>
                  Confirm
                </Btn>
              </div>
            </Field>
          </div>

          <div className="tiny muted-2" style={{ borderTop: '1px solid var(--border)', paddingTop: 12 }}>
            <strong>How it works:</strong><br/>
            • GPS (lat/lng) always used for distance to stores<br/>
            • Village name is human label — can be uncertain near borders<br/>
            • If uncertain, we show "Location near X" not confidently wrong<br/>
            • Once you confirm, village_source=user_confirmed, never overwritten<br/>
            • Refresh keeps confirmed village, moving updates GPS but preserves confirmation until you change it
          </div>
        </div>
      </Modal>
    </>
  );
}
