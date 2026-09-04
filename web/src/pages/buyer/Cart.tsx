import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  ShoppingCart, Trash2, Minus, Plus, ShieldCheck, MessageCircle, Store, Send, LogIn,
} from 'lucide-react';
import { socialService, chatService } from '../../api/services';
import type { Order } from '../../api/types';
import { formatUgx } from '../../api/types';
import { useCart } from '../../store/CartContext';
import { useToast } from '../../store/ToastContext';
import { useAuth } from '../../store/AuthContext';
import {
  Btn, Empty, ErrorBox, Field, Input, Select, TextArea, Modal, PageHeader, ConfirmModal, SkeletonRows,
} from '../../components/ui';

/**
 * Inquiry cart — no online payment anywhere.
 *
 * Items are grouped by seller; the primary action is talking. Buyers can send
 * an inquiry (an order the seller answers in chat) or open a chat per seller.
 * Guests can browse, add and edit for free; sign-in is only needed to send an
 * inquiry or open a conversation.
 */
export default function Cart() {
  const { cart, loading, loadError, setQty, remove, clear, refresh } = useCart();
  const { toast } = useToast();
  const { user } = useAuth();
  const navigate = useNavigate();

  const isBuyer = !!user && user.role === 'buyer';

  const [sending, setSending] = useState(false);
  const [confirmClear, setConfirmClear] = useState(false);
  const [placed, setPlaced] = useState<{ orders: Order[]; message: string } | null>(null);
  const [busyId, setBusyId] = useState('');

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
    if (qty <= 0) {
      setBusyId(productId);
      await remove(productId);
      setBusyId('');
      return;
    }
    setBusyId(productId);
    await setQty(productId, qty);
    setBusyId('');
  };

  const isGuest = !user || user.role !== 'buyer';

  const placeOrder = async () => {
    if (isGuest) {
      toast('Please sign in to place your order — your cart is saved', 'warning');
      navigate('/login', { state: { from: '/cart' } });
      return;
    }
    if (unavailable.length > 0) {
      toast('Remove the unavailable items first', 'warning');
      return;
    }
    setSending(true);
    try {
      const r = await socialService.checkout({});
      setPlaced({ orders: r.orders, message: r.message });
      await refresh();
      window.dispatchEvent(new CustomEvent('stx:refresh-badges'));
    } catch (e: any) {
      toast(e?.message || 'Could not send the inquiry', 'error');
      await refresh();
    } finally {
      setSending(false);
    }
  };

  if (loading && cart.items.length === 0) {
    return (
      <>
        <PageHeader title="Your inquiry cart" />
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
        <PageHeader title="Your inquiry cart" />
        <Empty
          icon={<ShoppingCart size={28} />}
          title="Your cart is empty"
          subtitle="Add products from the marketplace and they'll show up here, grouped by seller — then message sellers directly."
          action={<Btn variant="primary" onClick={() => navigate('/search')}>Start shopping</Btn>}
        />
      </>
    );
  }

  return (
    <>
      <PageHeader
        title="Your inquiry cart"
        sub={`${cart.itemCount} item${cart.itemCount === 1 ? '' : 's'} from ${bySeller.length} seller${bySeller.length === 1 ? '' : 's'} — chat to agree, no online payment`}
        actions={
          cart.items.length > 0 ? (
            <Btn variant="ghost" icon={<Trash2 size={15} />} onClick={() => setConfirmClear(true)}>Empty cart</Btn>
          ) : undefined
        }
      />

      {!isBuyer && (
        <div className="card banner-info row wrap" style={{ gap: 12, alignItems: 'center' }}>
          <div className="grow" style={{ minWidth: 220 }}>
            <strong>Browsing as a guest — everything you add stays here.</strong>
            <p className="tiny muted mt-4">
              Sign in when you're ready to send an inquiry or chat with sellers. No account is
              needed to explore, search or build your list.
            </p>
          </div>
          <div className="row" style={{ gap: 8 }}>
            <Btn size="sm" icon={<LogIn size={14} />} onClick={() => navigate('/login', { state: { from: '/cart' } })}>
              Sign in
            </Btn>
            <Btn size="sm" variant="primary" onClick={() => navigate('/register')}>Create account</Btn>
          </div>
        </div>
      )}

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
                <div className="row" style={{ gap: 8 }}>
                  <span className="tiny muted">{group.items.length} item{group.items.length > 1 ? 's' : ''}</span>
                  <Btn size="sm" variant="ghost" icon={<MessageCircle size={14} />}
                    onClick={() => void openChat(sellerId, group.sellerName)}>
                    Message
                  </Btn>
                </div>
              </div>

              <div className="col">
                {group.items.map((it) => {
                  const bad = it.status !== 'approved' || it.stockQuantity < it.quantity;
                  return (
                    <div key={it.productId} className={`cart-line ${bad ? 'cart-line-bad' : ''}`}>
                      <Link to={`/product/${it.productId}`}>
                        {it.imageUrl ? (
                          <img src={it.imageUrl} alt={it.title} className="cart-thumb" loading="lazy" />
                        ) : (
                          <span className="cart-thumb center" style={{ fontWeight: 800, background: "var(--surface-3)", color: "var(--text-3)" }}>
                            {it.title?.[0]?.toUpperCase() || "•"}
                          </span>
                        )}
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

        {/* ── Summary / checkout — guest sees login prompt, buyer sees full form */}
        <aside className="card checkout-summary">
          <h3 className="card-title mb-12">Next steps</h3>

          <div className="sum-row"><span className="muted">Items</span><span className="semi">{cart.itemCount}</span></div>
          <div className="sum-row"><span className="muted">Estimated total</span><span className="semi">{formatUgx(cart.subtotalMinor)}</span></div>
          <div className="sum-row"><span className="muted">Delivery</span><span className="tiny muted">Agree it in chat</span></div>
          <div className="sum-divider" />

          {isGuest ? (
            <div className="col mt-16">
              <div className="card" style={{ background: 'var(--primary-soft)', borderColor: 'var(--primary)', padding: 12 }}>
                <strong>Sign in to checkout</strong>
                <p className="tiny muted mt-4">Your cart is saved on this device. Create an account or sign in to place your order and track delivery.</p>
              </div>
              <Btn variant="primary" size="lg" className="w-full mt-12" onClick={() => navigate('/login', { state: { from: '/cart' } })}>
                Sign in to checkout · {formatUgx(cart.subtotalMinor)}
              </Btn>
              <Btn size="lg" className="w-full" onClick={() => navigate('/register')}>Create account</Btn>
              <ul className="trust-list">
                <li><ShieldCheck size={14} /> Buyer protection on every order</li>
                <li><Truck size={14} /> Pay on delivery — no card needed</li>
                <li><CheckCircle2 size={14} /> Sellers are notified instantly</li>
              </ul>
            </div>
          ) : (
            <>
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
            </>
          )}
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
        title="Inquiry sent 🎉"
        footer={
          <>
            <Btn onClick={() => { setPlaced(null); navigate('/search'); }}>Keep shopping</Btn>
            <Btn variant="primary" onClick={() => { setPlaced(null); navigate('/messages'); }}>Chat with sellers</Btn>
          </>
        }
      >
        <div className="center mb-16">
          <div className="success-ring"><Send size={32} /></div>
        </div>
        <p className="center semi">{placed?.message}</p>
        <p className="center muted tiny mt-4">
          The sellers have been notified — open the chat to confirm the price and delivery.
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
