import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  ShoppingCart, Trash2, Minus, Plus, ShieldCheck, Truck, MapPin, CheckCircle2, Store,
} from 'lucide-react';
import { socialService, buyerService } from '../../api/services';
import type { Address, Order } from '../../api/types';
import { formatUgx } from '../../api/types';
import { useCart } from '../../store/CartContext';
import { useToast } from '../../store/ToastContext';
import { useAuth } from '../../store/AuthContext';
import {
  Btn, Empty, ErrorBox, Field, Input, Select, TextArea, Modal, PageHeader, ConfirmModal, SkeletonRows,
} from '../../components/ui';

export default function Cart() {
  const { cart, loading, loadError, setQty, remove, clear, refresh } = useCart();
  const { toast } = useToast();
  const { user } = useAuth();
  const navigate = useNavigate();

  const [addresses, setAddresses] = useState<Address[]>([]);
  const [addressId, setAddressId] = useState('');
  const [phone, setPhone] = useState(user?.phone || '');
  const [note, setNote] = useState('');
  const [placing, setPlacing] = useState(false);
  const [confirmClear, setConfirmClear] = useState(false);
  const [placed, setPlaced] = useState<{ orders: Order[]; message: string; totalMinor: number } | null>(null);
  const [busyId, setBusyId] = useState('');

  useEffect(() => {
    buyerService.addresses()
      .then((r) => {
        setAddresses(r.addresses);
        setAddressId(r.addresses.find((a) => a.isDefault)?.id ?? r.addresses[0]?.id ?? '');
      })
      .catch(() => undefined);
  }, []);

  // Group lines by seller — buyers think in stores, not rows.
  const bySeller = useMemo(() => {
    const map = new Map<string, { sellerName: string; items: typeof cart.items }>();
    for (const it of cart.items) {
      const g = map.get(it.sellerId) ?? { sellerName: it.sellerName, items: [] };
      g.items.push(it);
      map.set(it.sellerId, g);
    }
    return [...map.entries()];
  }, [cart.items]);

  const unavailable = cart.items.filter((i) => i.status !== 'approved' || i.stockQuantity < i.quantity);

  const changeQty = async (productId: string, qty: number) => {
    setBusyId(productId);
    await setQty(productId, qty);
    setBusyId('');
  };

  const placeOrder = async () => {
    if (unavailable.length > 0) {
      toast('Remove the unavailable items first', 'warning');
      return;
    }
    setPlacing(true);
    try {
      const r = await socialService.checkout({ addressId: addressId || undefined, phone, note });
      setPlaced({ orders: r.orders, message: r.message, totalMinor: r.totalMinor });
      await refresh();
      window.dispatchEvent(new CustomEvent('stx:refresh-badges'));
    } catch (e: any) {
      toast(e?.message || 'Could not place the order', 'error');
      await refresh();
    } finally {
      setPlacing(false);
    }
  };

  if (loading && cart.items.length === 0) {
    return (
      <>
        <PageHeader title="Your cart" />
        <SkeletonRows rows={4} height={82} />
      </>
    );
  }

  // A failed load must never masquerade as an empty cart: the items are still
  // on the server, and telling the buyer otherwise invites them to re-add
  // everything (or abandon the purchase).
  if (loadError && cart.items.length === 0 && !placed) {
    return (
      <>
        <PageHeader title="Your cart" />
        <ErrorBox message={`${loadError} — your items are safe, this device just could not reach the server.`} onRetry={() => { void refresh(); }} />
      </>
    );
  }

  if (cart.items.length === 0 && !placed) {
    return (
      <>
        <PageHeader title="Your cart" />
        <Empty
          icon={<ShoppingCart size={28} />}
          title="Your cart is empty"
          subtitle="Add products from the marketplace and they'll show up here, grouped by seller."
          action={<Btn variant="primary" onClick={() => navigate('/search')}>Start shopping</Btn>}
        />
      </>
    );
  }

  return (
    <>
      <PageHeader
        title="Your cart"
        sub={`${cart.itemCount} item${cart.itemCount === 1 ? '' : 's'} from ${bySeller.length} seller${bySeller.length === 1 ? '' : 's'}`}
        actions={
          cart.items.length > 0 ? (
            <Btn variant="ghost" icon={<Trash2 size={15} />} onClick={() => setConfirmClear(true)}>Empty cart</Btn>
          ) : undefined
        }
      />

      <div className="checkout-layout">
        {/* ── Lines grouped by seller ─────────────────────────────────── */}
        <div className="col-lg">
          {unavailable.length > 0 && (
            <div className="card banner-warning">
              <strong>{unavailable.length} item{unavailable.length > 1 ? 's' : ''} need attention</strong>
              <p className="tiny muted mt-4">
                These are out of stock or no longer published. Remove them to continue.
              </p>
            </div>
          )}

          {bySeller.map(([sellerId, group], gi) => (
            <section key={sellerId} className="card stagger-item" style={{ '--i': gi } as React.CSSProperties}>
              <div className="card-head">
                <Link to={`/seller/${sellerId}`} className="card-title" style={{ textDecoration: 'none' }}>
                  <Store size={16} /> {group.sellerName}
                </Link>
                <span className="tiny muted">{group.items.length} item{group.items.length > 1 ? 's' : ''}</span>
              </div>

              <div className="col">
                {group.items.map((it) => {
                  const bad = it.status !== 'approved' || it.stockQuantity < it.quantity;
                  return (
                    <div key={it.productId} className={`cart-line ${bad ? 'cart-line-bad' : ''}`}>
                      <Link to={`/product/${it.productId}`}>
                        <img src={it.imageUrl} alt={it.title} className="cart-thumb" loading="lazy" />
                      </Link>

                      <div className="grow" style={{ minWidth: 0 }}>
                        <Link to={`/product/${it.productId}`} className="semi ellipsis" style={{ color: 'var(--text)', textDecoration: 'none' }}>
                          {it.title}
                        </Link>
                        <div className="tiny muted mt-4">{formatUgx(it.priceMinor)} each</div>
                        {bad && (
                          <div className="tiny t-danger mt-4">
                            {it.status !== 'approved' ? 'No longer available' : `Only ${it.stockQuantity} left`}
                          </div>
                        )}
                      </div>

                      <div className="qty-stepper">
                        <button aria-label="Decrease quantity" disabled={busyId === it.productId}
                          onClick={() => void changeQty(it.productId, it.quantity - 1)}>
                          <Minus size={14} />
                        </button>
                        <span>{it.quantity}</span>
                        <button aria-label="Increase quantity"
                          disabled={busyId === it.productId || it.quantity >= it.stockQuantity}
                          onClick={() => void changeQty(it.productId, it.quantity + 1)}>
                          <Plus size={14} />
                        </button>
                      </div>

                      <div className="cart-line-total">
                        <div className="semi">{formatUgx(it.lineTotalMinor)}</div>
                        <button className="btn btn-ghost btn-icon" aria-label={`Remove ${it.title}`}
                          onClick={() => void remove(it.productId)}>
                          <Trash2 size={15} />
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            </section>
          ))}
        </div>

        {/* ── Summary / checkout ──────────────────────────────────────── */}
        <aside className="card checkout-summary">
          <h3 className="card-title mb-12">Order summary</h3>

          <div className="sum-row"><span className="muted">Subtotal</span><span className="semi">{formatUgx(cart.subtotalMinor)}</span></div>
          <div className="sum-row"><span className="muted">Delivery</span><span className="tiny muted">Quoted by each seller</span></div>
          <div className="sum-divider" />
          <div className="sum-row">
            <span className="semi">Total due</span>
            <span className="sum-total">{formatUgx(cart.subtotalMinor)}</span>
          </div>

          <div className="col mt-16">
            {addresses.length > 0 ? (
              <Field label="Deliver to">
                <Select value={addressId} onChange={(e) => setAddressId(e.target.value)}>
                  {addresses.map((a) => (
                    <option key={a.id} value={a.id}>{a.label} — {a.line1}, {a.city}</option>
                  ))}
                </Select>
              </Field>
            ) : (
              <div className="tiny muted">
                <MapPin size={13} style={{ verticalAlign: -2 }} /> No saved address.{' '}
                <Link to="/buyer/addresses">Add one</Link> so sellers know where to deliver.
              </div>
            )}

            <Field label="Phone for delivery" hint="The seller calls this number to arrange handover.">
              <Input value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="07xx xxx xxx" />
            </Field>

            <Field label="Note for the seller (optional)">
              <TextArea rows={2} value={note} onChange={(e) => setNote(e.target.value)}
                placeholder="e.g. call when you reach the gate" />
            </Field>
          </div>

          <Btn variant="primary" size="lg" className="w-full mt-12" loading={placing}
            disabled={unavailable.length > 0} onClick={placeOrder}>
            Place order · {formatUgx(cart.subtotalMinor)}
          </Btn>

          <ul className="trust-list">
            <li><ShieldCheck size={14} /> Buyer protection on every order</li>
            <li><Truck size={14} /> Pay on delivery — no card needed</li>
            <li><CheckCircle2 size={14} /> Sellers are notified instantly</li>
          </ul>
        </aside>
      </div>

      <ConfirmModal
        open={confirmClear}
        title="Empty your cart?"
        message="This removes every item. You can always add them again."
        confirmLabel="Empty cart"
        danger
        onCancel={() => setConfirmClear(false)}
        onConfirm={async () => { await clear(); setConfirmClear(false); toast('Cart emptied', 'success'); }}
      />

      {/* ── Success ─────────────────────────────────────────────────── */}
      <Modal
        open={!!placed}
        onClose={() => { setPlaced(null); navigate('/buyer/orders'); }}
        title="Order placed 🎉"
        footer={
          <>
            <Btn onClick={() => { setPlaced(null); navigate('/search'); }}>Keep shopping</Btn>
            <Btn variant="primary" onClick={() => { setPlaced(null); navigate('/buyer/orders'); }}>Track my orders</Btn>
          </>
        }
      >
        <div className="center mb-16">
          <div className="success-ring"><CheckCircle2 size={34} /></div>
        </div>
        <p className="center semi">{placed?.message}</p>
        <p className="center muted tiny mt-4">
          Total {formatUgx(placed?.totalMinor ?? 0)} · pay on delivery
        </p>
        <div className="col mt-16">
          {placed?.orders.map((o) => (
            <div key={o.id} className="row-between sum-row">
              <span className="ellipsis">{o.title} × {o.quantity}</span>
              <span className="semi">{formatUgx(o.amount * o.quantity)}</span>
            </div>
          ))}
        </div>
      </Modal>
    </>
  );
}
