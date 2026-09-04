import {
  createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode,
} from 'react';
import { socialService, buyerService } from '../api/services';
import type { Cart, CartItem, Product } from '../api/types';
import { useAuth } from './AuthContext';
import { useToast } from './ToastContext';

interface CartState {
  cart: Cart;
  loading: boolean;
  /** True when the cart is held in localStorage (guest / not a buyer). */
  isGuestCart: boolean;
  favoriteSellerIds: Set<string>;
  savedIds: Set<string>;
  refresh: () => Promise<void>;
  add: (product: Product, quantity?: number) => Promise<void>;
  setQty: (productId: string, quantity: number) => Promise<void>;
  remove: (productId: string) => Promise<void>;
  clear: () => Promise<void>;
  toggleFavoriteSeller: (sellerId: string, sellerName?: string) => Promise<void>;
  toggleSaved: (productId: string) => Promise<void>;
}

const EMPTY: Cart = { items: [], subtotalMinor: 0, itemCount: 0, currency: 'UGX' };
const GUEST_KEY = 'guest_cart_v1';
const CartContext = createContext<CartState | null>(null);

/* ── Guest cart storage ──────────────────────────────────────────────────────
 * Browsing is free: a visitor (or a seller browsing the catalogue) can add
 * items offline in localStorage. Sign-in is only demanded at the moment an
 * action genuinely needs an account — checkout, orders, messages, following. */
function loadGuestCart(): Cart {
  try {
    const raw = localStorage.getItem(GUEST_KEY);
    if (!raw) return EMPTY;
    const parsed = JSON.parse(raw) as Cart;
    if (!Array.isArray(parsed.items)) return EMPTY;
    return {
      items: parsed.items,
      subtotalMinor: parsed.subtotalMinor || 0,
      itemCount: parsed.itemCount || parsed.items.length,
      currency: 'UGX',
    };
  } catch {
    return EMPTY;
  }
}

function saveGuestCart(cart: Cart): void {
  try { localStorage.setItem(GUEST_KEY, JSON.stringify(cart)); } catch { /* private mode */ }
}

function clearGuestCart(): void {
  try { localStorage.removeItem(GUEST_KEY); } catch { /* ignore */ }
}

function recalc(items: CartItem[]): Cart {
  return {
    items,
    subtotalMinor: items.reduce((s, i) => s + i.lineTotalMinor, 0),
    itemCount: items.reduce((s, i) => s + i.quantity, 0),
    currency: 'UGX',
  };
}

function guestItem(product: Product, quantity: number): CartItem {
  return {
    productId: product.id,
    quantity,
    title: product.title,
    priceMinor: product.priceMinor,
    stockQuantity: product.stockQuantity,
    imageUrl: product.imageUrl || '',
    status: product.status ?? 'approved',
    sellerId: product.seller?.id ?? '',
    sellerName: product.seller?.name ?? 'Seller',
    lineTotalMinor: product.priceMinor * quantity,
  };
}

/** Merge a guest cart into the buyer's backend cart, best-effort. */
async function mergeGuestCart(onDone: () => void): Promise<void> {
  const guest = loadGuestCart();
  if (guest.items.length === 0) { onDone(); return; }
  const results = await Promise.allSettled(
    guest.items.map((i) => socialService.addToCart(i.productId, i.quantity))
  );
  if (results.every((r) => r.status === 'fulfilled')) clearGuestCart();
  onDone();
}

export function CartProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const { toast } = useToast();
  const [cart, setCart] = useState<Cart>(EMPTY);
  const [loading, setLoading] = useState(false);
  const [favoriteSellerIds, setFavoriteSellerIds] = useState<Set<string>>(new Set());
  const [savedIds, setSavedIds] = useState<Set<string>>(new Set());

  const isBuyer = !!user && user.role === 'buyer';

  const refresh = useCallback(async () => {
    if (!isBuyer) {
      // Guests (and non-buyers) read the local cart — no redirect, no 401.
      setCart(loadGuestCart());
      setFavoriteSellerIds(new Set());
      setSavedIds(new Set());
      return;
    }
    setLoading(true);
    try {
      const [c, f, b] = await Promise.allSettled([
        socialService.cart(),
        socialService.favorites(),
        buyerService.bookmarks(),
      ]);
      if (c.status === 'fulfilled') setCart(c.value);
      if (f.status === 'fulfilled') setFavoriteSellerIds(new Set(f.value.sellers.map((s) => s.id)));
      if (b.status === 'fulfilled') setSavedIds(new Set(b.value.products.map((p) => p.id)));
    } finally {
      setLoading(false);
    }
  }, [isBuyer]);

  // First render / role change: on becoming a buyer, carry the guest cart over.
  useEffect(() => {
    if (isBuyer) {
      void mergeGuestCart(() => { void refresh(); });
    } else {
      void refresh();
    }
  }, [isBuyer, refresh]);

  const add = useCallback(async (product: Product, quantity = 1) => {
    if (!isBuyer) {
      setCart((c) => {
        const existing = c.items.find((i) => i.productId === product.id);
        const qty = Math.min(quantity, Math.max(1, product.stockQuantity ?? 99));
        const items = existing
          ? c.items.map((i) =>
              i.productId === product.id
                ? { ...i, quantity: Math.min(i.quantity + qty, i.stockQuantity || qty), lineTotalMinor: i.priceMinor * Math.min(i.quantity + qty, i.stockQuantity || qty) }
                : i
            )
          : [...c.items, guestItem(product, qty)];
        const next = recalc(items);
        saveGuestCart(next);
        return next;
      });
      toast(`${product.title} added — sign in when you're ready to check out`, 'info');
      return;
    }
    // Optimistic bump so the header badge reacts instantly.
    setCart((c) => ({ ...c, itemCount: c.itemCount + quantity }));
    try {
      const next = await socialService.addToCart(product.id, quantity);
      setCart(next);
      toast(`${product.title} added to cart`, 'success');
    } catch (e: any) {
      await refresh();
      toast(e?.message || 'Could not add to cart', 'error');
    }
  }, [isBuyer, refresh, toast]);

  const setQty = useCallback(async (productId: string, quantity: number) => {
    if (!isBuyer) {
      setCart((c) => {
        const items = c.items.map((i) => {
          if (i.productId !== productId) return i;
          const q = Math.max(0, Math.min(quantity, i.stockQuantity || 99));
          return { ...i, quantity: q, lineTotalMinor: i.priceMinor * q };
        }).filter((i) => i.quantity > 0);
        const next = recalc(items);
        saveGuestCart(next);
        return next;
      });
      return;
    }
    try {
      setCart(await socialService.updateCartItem(productId, quantity));
    } catch (e: any) {
      await refresh();
      toast(e?.message || 'Could not update quantity', 'error');
    }
  }, [isBuyer, refresh, toast]);

  const remove = useCallback(async (productId: string) => {
    if (!isBuyer) {
      setCart((c) => {
        const next = recalc(c.items.filter((i) => i.productId !== productId));
        saveGuestCart(next);
        return next;
      });
      return;
    }
    try {
      setCart(await socialService.removeFromCart(productId));
    } catch (e: any) {
      await refresh();
      toast(e?.message || 'Could not remove item', 'error');
    }
  }, [isBuyer, refresh, toast]);

  const clear = useCallback(async () => {
    if (!isBuyer) {
      clearGuestCart();
      setCart(EMPTY);
      return;
    }
    try {
      setCart(await socialService.clearCart());
    } catch { await refresh(); }
  }, [isBuyer, refresh]);

  const toggleFavoriteSeller = useCallback(async (sellerId: string, sellerName?: string) => {
    if (!isBuyer) { toast('Sign in as a buyer to follow sellers', 'warning'); return; }
    const following = favoriteSellerIds.has(sellerId);
    setFavoriteSellerIds((s) => {
      const next = new Set(s);
      following ? next.delete(sellerId) : next.add(sellerId);
      return next;
    });
    try {
      if (following) await socialService.unfollowSeller(sellerId);
      else await socialService.followSeller(sellerId);
      toast(
        following
          ? `Unfollowed ${sellerName || 'seller'}`
          : `Following ${sellerName || 'seller'} — you'll get a push when they post new products`,
        following ? 'info' : 'success'
      );
    } catch (e: any) {
      setFavoriteSellerIds((s) => {
        const next = new Set(s);
        following ? next.add(sellerId) : next.delete(sellerId);
        return next;
      });
      toast(e?.message || 'Could not update follow', 'error');
    }
  }, [favoriteSellerIds, isBuyer, toast]);

  const toggleSaved = useCallback(async (productId: string) => {
    if (!user) { toast('Sign in to save products', 'warning'); return; }
    try {
      const { bookmarked } = await buyerService.toggleBookmark(productId);
      setSavedIds((s) => {
        const next = new Set(s);
        bookmarked ? next.add(productId) : next.delete(productId);
        return next;
      });
      toast(bookmarked ? 'Saved to your wishlist' : 'Removed from wishlist', 'success');
    } catch (e: any) {
      toast(e?.message || 'Could not save', 'error');
    }
  }, [toast, user]);

  const value = useMemo(
    () => ({
      cart,
      loading,
      isGuestCart: !isBuyer,
      favoriteSellerIds,
      savedIds,
      refresh,
      add,
      setQty,
      remove,
      clear,
      toggleFavoriteSeller,
      toggleSaved,
    }),
    [cart, loading, isBuyer, favoriteSellerIds, savedIds, refresh, add, setQty, remove, clear, toggleFavoriteSeller, toggleSaved]
  );

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart(): CartState {
  const ctx = useContext(CartContext);
  if (!ctx) throw new Error('useCart must be used within CartProvider');
  return ctx;
}
