import { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Flame, Clock, Zap, TrendingUp, ShoppingCart, Eye, Sparkles } from 'lucide-react';
import type { Product } from '../api/types';
import { formatUgx } from '../api/types';

function useCountdown(targetMs: number) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);
  const diff = Math.max(0, targetMs - now);
  const h = Math.floor(diff / 3600000);
  const m = Math.floor((diff % 3600000) / 60000);
  const s = Math.floor((diff % 60000) / 1000);
  return { diff, h, m, s, done: diff <= 0 };
}

export default function ExtraDealDisplay({ deals }: { deals: Product[] }) {
  const [active, setActive] = useState(0);
  const timerRef = useRef<number | null>(null);
  const items = useMemo(() => deals.slice(0, 6), [deals]);
  const featured = items[active] || null;

  // Auto-rotate every 5.5s
  useEffect(() => {
    if (items.length <= 1) return;
    timerRef.current = window.setInterval(() => {
      setActive((i) => (i + 1) % items.length);
    }, 5500);
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [items.length]);

  // Reset timer on manual select
  const select = (i: number) => {
    setActive(i);
    if (timerRef.current) {
      clearInterval(timerRef.current);
      timerRef.current = window.setInterval(() => {
        setActive((v) => (v + 1) % items.length);
      }, 5500);
    }
  };

  // 4-hour countdown from mount, resets when featured changes
  const target = useMemo(() => Date.now() + 4 * 3600 * 1000 + 23 * 60 * 1000 + 42 * 1000, [featured?.id]);
  const cd = useCountdown(target);

  if (!featured) return null;

  const discount = featured.discountPercent || (featured.oldPriceMinor ? Math.round((1 - featured.priceMinor / featured.oldPriceMinor) * 100) : 0);
  const stockPct = Math.max(8, Math.min(92, 100 - (featured.stockQuantity % 92)));
  const sold = Math.max(3, 100 - featured.stockQuantity);

  return (
    <section className="edeal" aria-label="Extraordinary deals">
      {/* bg */}
      <div className="edeal-bg" aria-hidden="true">
        <div className="edeal-orb edeal-orb-1" />
        <div className="edeal-orb edeal-orb-2" />
        <div className="edeal-orb edeal-orb-3" />
        <div className="edeal-grid" />
        <div className="edeal-noise" />
      </div>

      <div className="edeal-head">
        <div className="edeal-title-wrap">
          <span className="edeal-icon">
            <Flame size={16} />
          </span>
          <h2 className="edeal-title">Extraordinary Deals</h2>
          <span className="edeal-live">
            <span className="edeal-live-dot" /> LIVE
          </span>
        </div>
        <div className="edeal-timer" title="Ends in">
          <Clock size={14} />
          <span className="edeal-timer-label hide-sm">Ends in</span>
          <span className="edeal-timer-digits">
            <b>{String(cd.h).padStart(2, '0')}</b>:<b>{String(cd.m).padStart(2, '0')}</b>:<b>{String(cd.s).padStart(2, '0')}</b>
          </span>
        </div>
      </div>

      <div className="edeal-main">
        {/* featured */}
        <Link to={`/product/${featured.id}`} className="edeal-featured" style={{ '--i': 0 } as any}>
          <div className="edeal-featured-media">
            <img src={featured.imageUrl} alt={featured.title} loading="lazy" />
            <div className="edeal-badge">
              <Zap size={12} /> {discount > 0 ? `${discount}% OFF` : 'FLASH'}
            </div>
            <div className="edeal-media-glow" aria-hidden="true" />
          </div>
          <div className="edeal-featured-body">
            <div className="edeal-meta">
              <span className="edeal-brand">{featured.brand || featured.category}</span>
              <span className="edeal-stock"><TrendingUp size={12} /> {sold} sold</span>
            </div>
            <h3 className="edeal-name">{featured.title}</h3>
            <div className="edeal-prices">
              <span className="edeal-price">{formatUgx(featured.priceMinor)}</span>
              {featured.oldPriceMinor ? <span className="edeal-old">{formatUgx(featured.oldPriceMinor)}</span> : null}
            </div>
            <div className="edeal-progress" aria-label="Stock left">
              <div className="edeal-progress-bar" style={{ width: `${stockPct}%` } as any} />
            </div>
            <div className="edeal-progress-label">
              <span>{stockPct}% claimed</span>
              <span>{featured.stockQuantity} left</span>
            </div>
            <div className="edeal-cta-row">
              <span className="btn btn-primary btn-sm edeal-cta">
                <ShoppingCart size={14} /> Grab deal
              </span>
              <span className="edeal-views">
                <Eye size={12} /> {featured.viewCount ?? Math.floor(Math.random() * 800 + 120)} watching
              </span>
            </div>
          </div>
          <div className="edeal-shine" aria-hidden="true" />
        </Link>

        {/* list */}
        <div className="edeal-list">
          {items.map((p, i) => {
            const d = p.discountPercent || (p.oldPriceMinor ? Math.round((1 - p.priceMinor / p.oldPriceMinor) * 100) : 0);
            const isActive = i === active;
            return (
              <button
                key={p.id}
                className={`edeal-item ${isActive ? 'active' : ''}`}
                onClick={() => select(i)}
                style={{ '--i': i } as any}
                aria-label={`Show ${p.title}`}
              >
                <span className="edeal-item-media">
                  <img src={p.imageUrl} alt="" loading="lazy" />
                  {d > 0 ? <span className="edeal-item-disc">-{d}%</span> : null}
                </span>
                <span className="edeal-item-body">
                  <span className="edeal-item-name">{p.title}</span>
                  <span className="edeal-item-price">{formatUgx(p.priceMinor)}</span>
                </span>
                <span className="edeal-item-arrow">→</span>
              </button>
            );
          })}
          <Link to="/search?flash=1" className="edeal-more">
            <Sparkles size={14} /> View all flash deals →
          </Link>
        </div>
      </div>

      <div className="edeal-foot">
        <span className="edeal-foot-dot" />
        <span>Extraordinary prices • Verified sellers • Same-day in Kampala</span>
      </div>
    </section>
  );
}
