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
  loadError: string | null;
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
const GUEST_CART_KEY = 'guest_cart_v1';
const CartContext = createContext<CartState | null>(null);

function loadGuestCart(): Cart {
  try {
    const raw = localStorage.getItem(GUEST_CART_KEY);
    if (!raw) return EMPTY;
    const parsed = JSON.parse(raw) as Cart;
    if (!parsed || !Array.isArray(parsed.items)) return EMPTY;
    return parsed;
  } catch {
    return EMPTY;
  }
}
function saveGuestCart(c: Cart) {
  try { localStorage.setItem(GUEST_CART_KEY, JSON.stringify(c)); } catch {}
}
function buildGuestCartItem(p: Product, qty: number) {
  return {
    productId: p.id,
    quantity: qty,
    title: p.title,
    priceMinor: p.priceMinor,
    stockQuantity: p.stockQuantity ?? 999,
    imageUrl: p.imageUrl || '',
    status: p.status || 'approved',
    sellerId: p.seller?.id || '',
    sellerName: p.seller?.name || '',
    lineTotalMinor: p.priceMinor * qty,
  };
}
function recalcGuestCart(items: any[]): Cart {
  let subtotal = 0;
  let count = 0;
  for (const it of items) {
    subtotal += (it.priceMinor || 0) * (it.quantity || 0);
    count += it.quantity || 0;
    it.lineTotalMinor = (it.priceMinor || 0) * (it.quantity || 0);
  }
  return { items, subtotalMinor: subtotal, itemCount: count, currency: 'UGX' };
}

export function CartProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const { toast } = useToast();
  const [cart, setCart] = useState<Cart>(EMPTY);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [favoriteSellerIds, setFavoriteSellerIds] = useState<Set<string>>(new Set());
  const [savedIds, setSavedIds] = useState<Set<string>>(new Set());

  const isBuyer = !!user && user.role === 'buyer';

  const refresh = useCallback(async () => {
    if (!isBuyer) {
      const g = loadGuestCart();
      setCart(g);
      setFavoriteSellerIds(new Set());
      setSavedIds(new Set());
      setLoadError(null);
      return;
    }
    setLoading(true);
    try {
      const [c, f, b] = await Promise.allSettled([
        socialService.cart(),
        socialService.favorites(),
        buyerService.bookmarks(),
      ]);
      if (c.status === 'fulfilled') {
        let nextCart = c.value as Cart;
        try {
          const guest = loadGuestCart();
          if (guest.items.length > 0) {
            for (const it of guest.items) {
              try { await socialService.addToCart(it.productId, it.quantity); } catch {}
            }
            localStorage.removeItem(GUEST_CART_KEY);
            try {
              const merged = await socialService.cart();
              nextCart = merged;
            } catch {}
          }
        } catch {}
        setCart(nextCart);
        setLoadError(null);
      } else setLoadError((c.reason as Error)?.message || 'Could not load your cart');
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
      const current = loadGuestCart();
      const existing = current.items.find((i: any) => i.productId === product.id);
      let nextItems: any[];
      if (existing) {
        nextItems = current.items.map((i: any) => i.productId === product.id ? { ...i, quantity: i.quantity + quantity } : i);
      } else {
        nextItems = [...current.items, buildGuestCartItem(product, quantity)];
      }
      const nextCart = recalcGuestCart(nextItems);
      saveGuestCart(nextCart);
      setCart(nextCart);
      toast(`${product.title} added to cart — sign in to checkout`, 'success');
      return;
    }
    setCart((c) => ({ ...c, itemCount: c.itemCount + quantity }));
    try {
      const next = await socialService.addToCart(product.id, quantity);
      setCart(next); setLoadError(null);
      toast(`${product.title} added to cart`, 'success');
    } catch (e: any) {
      await refresh();
      toast(e?.message || 'Could not add to cart', 'error');
    }
  }, [isBuyer, refresh, toast]);

  const setQty = useCallback(async (productId: string, quantity: number) => {
    if (!isBuyer) {
      const current = loadGuestCart();
      let nextItems: any[];
      if (quantity <= 0) {
        nextItems = current.items.filter((i: any) => i.productId !== productId);
      } else {
        nextItems = current.items.map((i: any) => i.productId === productId ? { ...i, quantity } : i);
      }
      const nextCart = recalcGuestCart(nextItems);
      saveGuestCart(nextCart);
      setCart(nextCart);
      return;
    }
    if (quantity <= 0) {
      try {
        setCart(await socialService.removeFromCart(productId)); setLoadError(null);
      } catch (e: any) {
        await refresh();
        toast(e?.message || 'Could not remove item', 'error');
      }
      return;
    }
    try {
      setCart(await socialService.updateCartItem(productId, quantity)); setLoadError(null);
    } catch (e: any) {
      await refresh();
      toast(e?.message || 'Could not update quantity', 'error');
    }
  }, [isBuyer, refresh, toast]);

  const remove = useCallback(async (productId: string) => {
    if (!isBuyer) {
      const current = loadGuestCart();
      const nextCart = recalcGuestCart(current.items.filter((i: any) => i.productId !== productId));
      saveGuestCart(nextCart);
      setCart(nextCart);
      return;
    }
    try {
      setCart(await socialService.removeFromCart(productId)); setLoadError(null);
    } catch (e: any) {
      await refresh();
      toast(e?.message || 'Could not remove item', 'error');
    }
  }, [isBuyer, refresh, toast]);

  const clear = useCallback(async () => {
    if (!isBuyer) {
      saveGuestCart(EMPTY);
      setCart(EMPTY);
      return;
    }
    try {
      setCart(await socialService.clearCart()); setLoadError(null);
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
    () => ({ cart, loading, loadError, favoriteSellerIds, savedIds, refresh, add, setQty, remove, clear, toggleFavoriteSeller, toggleSaved }),
    [cart, loading, loadError, favoriteSellerIds, savedIds, refresh, add, setQty, remove, clear, toggleFavoriteSeller, toggleSaved]
  );

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart(): CartState {
  const ctx = useContext(CartContext);
  if (!ctx) throw new Error('useCart must be used within CartProvider');
  return ctx;
}
