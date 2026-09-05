/**
 * AdminLocationsMap — every user who has shared a position (buyers and
 * sellers, new and old), on a Leaflet/OpenStreetMap map with filters, a
 * ranked list and per-user ping history.
 *
 * Positions come from /me/location (the Nearby screen asks for it on open)
 * and live store tracking; history from location_pings.
 */
import { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { Clock, Crosshair, MapPin, RefreshCw, Store, User as UserIcon } from 'lucide-react';
import { adminService } from '../api/services';
import type { AdminLocatedUser } from '../api/types';
import { Badge, Btn } from './ui';

const KAMPALA: [number, number] = [0.3476, 32.5825];

const ago = (iso?: string | null) => {
  if (!iso) return 'never';
  const s = (Date.now() - new Date(iso).getTime()) / 1000;
  if (s < 60) return 'just now';
  if (s < 3600) return `${Math.floor(s / 60)} min ago`;
  if (s < 86400) return `${Math.floor(s / 3600)} h ago`;
  if (s < 86400 * 30) return `${Math.floor(s / 86400)} d ago`;
  return new Date(iso).toLocaleDateString();
};

function dot(color: string, ring: boolean) {
  return L.divIcon({
    className: 'adm-pin',
    html: `<span class="adm-pin-dot${ring ? ' adm-pin-dot--live' : ''}" style="--c:${color}"></span>`,
    iconSize: [18, 18],
    iconAnchor: [9, 9],
  });
}

export function AdminLocationsMap() {
  const [users, setUsers] = useState<AdminLocatedUser[]>([]);
  const [summary, setSummary] = useState<{ located: number; total: number; buyers: number; sellers: number; activeToday: number } | null>(null);
  const [role, setRole] = useState<'all' | 'buyer' | 'seller'>('all');
  const [since, setSince] = useState<'all' | '24h' | '7d' | '30d'>('all');
  const [q, setQ] = useState('');
  const [selected, setSelected] = useState<AdminLocatedUser | null>(null);
  const [history, setHistory] = useState<{ lat: number; lng: number; at: string; accuracyM: number | null; village: string | null; city: string | null }[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const mapEl = useRef<HTMLDivElement>(null);
  const map = useRef<L.Map | null>(null);
  const layer = useRef<L.LayerGroup | null>(null);
  const trail = useRef<L.LayerGroup | null>(null);
  const markers = useRef<Map<string, L.Marker>>(new Map());

  const load = async () => {
    setLoading(true);
    try {
      const r = await adminService.locations({ role: role === 'all' ? undefined : role, since: since === 'all' ? undefined : since });
      setUsers(r.users);
      setSummary(r.summary);
      setError('');
    } catch (e: any) {
      setError(e?.message || 'Could not load locations');
    } finally {
      setLoading(false);
    }
  };
  useEffect(() => { void load(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [role, since]);

  // Map init (once).
  useEffect(() => {
    if (!mapEl.current || map.current) return;
    const m = L.map(mapEl.current, { center: KAMPALA, zoom: 11, zoomControl: true, attributionControl: true });
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
    }).addTo(m);
    layer.current = L.layerGroup().addTo(m);
    trail.current = L.layerGroup().addTo(m);
    map.current = m;
    setTimeout(() => m.invalidateSize(), 50);
    return () => { m.remove(); map.current = null; };
  }, []);

  const filtered = useMemo(() => {
    const t = q.trim().toLowerCase();
    if (!t) return users;
    return users.filter((u) =>
      [u.name, u.email, u.storeName, u.village, u.city, u.region, u.placeLabel].some((v) => v && v.toLowerCase().includes(t))
    );
  }, [users, q]);

  // Markers.
  useEffect(() => {
    const m = map.current, g = layer.current;
    if (!m || !g) return;
    g.clearLayers();
    markers.current.clear();
    const pts: [number, number][] = [];
    for (const u of filtered) {
      if (!Number.isFinite(u.lat) || !Number.isFinite(u.lng)) continue;
      const live = !!u.locationAt && Date.now() - new Date(u.locationAt).getTime() < 15 * 60_000;
      const color = u.role === 'seller' ? '#0ea5e9' : u.role === 'admin' ? '#a855f7' : '#f59e0b';
      const mk = L.marker([u.lat, u.lng], { icon: dot(color, live), title: u.storeName || u.name || u.email });
      mk.bindTooltip(`<strong>${u.storeName || u.name || u.email}</strong><br>${u.role} · ${u.village || u.city || '—'}<br><small>${ago(u.locationAt)}</small>`, { direction: 'top', offset: [0, -8] });
      mk.on('click', () => setSelected(u));
      mk.addTo(g);
      markers.current.set(u.id, mk);
      pts.push([u.lat, u.lng]);
    }
    if (pts.length && !selected) {
      m.fitBounds(L.latLngBounds(pts).pad(0.2), { maxZoom: 14 });
    }
  }, [filtered, selected]);

  // Selected user: fly + history trail.
  useEffect(() => {
    const m = map.current, t = trail.current;
    if (!m || !t) return;
    t.clearLayers();
    setHistory([]);
    if (!selected) return;
    m.flyTo([selected.lat, selected.lng], Math.max(m.getZoom(), 15), { duration: 0.6 });
    markers.current.get(selected.id)?.openTooltip();
    if (selected.accuracyM) L.circle([selected.lat, selected.lng], { radius: selected.accuracyM, color: '#22c55e', weight: 1, fillOpacity: 0.08 }).addTo(t);
    adminService.locationHistory(selected.id).then((r) => {
      setHistory(r.history);
      const pts = r.history.map((h) => [h.lat, h.lng] as [number, number]);
      if (pts.length > 1) L.polyline(pts, { color: '#22c55e', weight: 2, dashArray: '4 6', opacity: 0.8 }).addTo(t);
      pts.slice(1, 40).forEach((p) => L.circleMarker(p, { radius: 3, color: '#22c55e', fillOpacity: 0.9, weight: 1 }).addTo(t));
    }).catch(() => undefined);
  }, [selected]);

  return (
    <section className="card adm-map-card" data-testid="admin-locations">
      <div className="card-head">
        <h2 className="card-title"><MapPin size={17} /> User locations</h2>
        <div className="row" style={{ gap: 6 }}>
          {summary && <Badge tone="default">{summary.located} of {summary.total} users located</Badge>}
          <Btn size="sm" variant="ghost" icon={<RefreshCw size={13} className={loading ? 'anim-spin' : ''} />} onClick={() => void load()}>Refresh</Btn>
        </div>
      </div>

      <div className="adm-map-kpis">
        <span><strong>{summary?.buyers ?? 0}</strong> buyers</span>
        <span><strong>{summary?.sellers ?? 0}</strong> sellers</span>
        <span><strong>{summary?.activeToday ?? 0}</strong> active today</span>
        <span className="muted-2">Buyers <i className="adm-key" style={{ background: '#f59e0b' }} /> · Sellers <i className="adm-key" style={{ background: '#0ea5e9' }} /> · pulsing = seen in the last 15 min</span>
      </div>

      <div className="adm-map-toolbar">
        <div className="chip-row">
          {(['all', 'buyer', 'seller'] as const).map((r) => (
            <button key={r} className={`chip${role === r ? ' active' : ''}`} onClick={() => { setRole(r); setSelected(null); }}>
              {r === 'all' ? 'Everyone' : r === 'buyer' ? 'Buyers' : 'Sellers'}
            </button>
          ))}
        </div>
        <div className="chip-row">
          {(['all', '24h', '7d', '30d'] as const).map((s) => (
            <button key={s} className={`chip${since === s ? ' active' : ''}`} onClick={() => { setSince(s); setSelected(null); }}>
              {s === 'all' ? 'All time' : s === '24h' ? 'Last 24 h' : s === '7d' ? 'Last 7 days' : 'Last 30 days'}
            </button>
          ))}
        </div>
        <input className="input adm-map-search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search name, email, village…" aria-label="Search located users" />
      </div>

      <div className="adm-map-layout">
        <div className="adm-map" ref={mapEl} aria-label="Map of user locations" />
        <aside className="adm-map-side">
          {error && <p className="err-text tiny">{error}</p>}
          {selected ? (
            <div className="adm-map-detail">
              <div className="row-between">
                <strong className="ellipsis">{selected.storeName || selected.name || selected.email}</strong>
                <button className="btn btn-sm btn-ghost" onClick={() => setSelected(null)}>Back</button>
              </div>
              <div className="row" style={{ gap: 6, flexWrap: 'wrap' }}>
                <Badge tone={selected.role === 'seller' ? 'cyan' : selected.role === 'admin' ? 'violet' : 'default'}>{selected.role}</Badge>
                {selected.storeVerified && <Badge tone="green">verified store</Badge>}
                {selected.liveTracking && <Badge tone="amber">live tracking</Badge>}
                {!selected.verified && <Badge tone="red">email unverified</Badge>}
              </div>
              <p className="tiny muted" style={{ margin: 0 }}>{selected.email}</p>
              <dl className="adm-map-dl">
                <dt><MapPin size={12} /> Place</dt><dd>{selected.placeLabel || [selected.village, selected.city, selected.region].filter(Boolean).join(', ') || '—'}</dd>
                <dt><Crosshair size={12} /> Coordinates</dt><dd>{selected.lat.toFixed(5)}, {selected.lng.toFixed(5)}{selected.accuracyM ? ` · ±${selected.accuracyM} m` : ''}</dd>
                <dt><Clock size={12} /> Last seen</dt><dd>{ago(selected.locationAt)}</dd>
                <dt><UserIcon size={12} /> Joined</dt><dd>{new Date(selected.joinedAt).toLocaleDateString()} · {selected.pingCount} ping{selected.pingCount === 1 ? '' : 's'}</dd>
              </dl>
              <div className="row" style={{ gap: 6 }}>
                <a className="btn btn-sm" href={`https://www.openstreetmap.org/?mlat=${selected.lat}&mlon=${selected.lng}#map=17/${selected.lat}/${selected.lng}`} target="_blank" rel="noreferrer">Open in OSM</a>
                {selected.role === 'seller' && <Link className="btn btn-sm" to={`/seller/${selected.id}`}>Storefront</Link>}
                <Link className="btn btn-sm" to={`/admin/users?search=${encodeURIComponent(selected.email)}`}>Manage</Link>
              </div>
              {history.length > 0 && (
                <div className="adm-map-hist">
                  <strong className="tiny">Recent positions</strong>
                  <ul>
                    {history.slice(0, 12).map((h, i) => (
                      <li key={i}>
                        <span>{h.village || h.city || `${h.lat.toFixed(4)}, ${h.lng.toFixed(4)}`}</span>
                        <small>{ago(h.at)}{h.accuracyM ? ` · ±${h.accuracyM} m` : ''}</small>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          ) : (
            <ul className="adm-map-list">
              {filtered.length === 0 && !loading && <li className="muted tiny" style={{ padding: 12 }}>No one has shared a location yet{role !== 'all' || since !== 'all' ? ' for this filter' : ''}.</li>}
              {filtered.slice(0, 200).map((u) => (
                <li key={u.id}>
                  <button type="button" onClick={() => setSelected(u)}>
                    <span className={`adm-map-role adm-map-role--${u.role}`}>{u.role === 'seller' ? <Store size={13} /> : <UserIcon size={13} />}</span>
                    <span className="grow" style={{ minWidth: 0 }}>
                      <span className="ellipsis semi">{u.storeName || u.name || u.email}</span>
                      <span className="ellipsis tiny muted">{u.village || u.city || u.placeLabel || '—'}</span>
                    </span>
                    <small className="muted-2">{ago(u.locationAt)}</small>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </aside>
      </div>
    </section>
  );
}
