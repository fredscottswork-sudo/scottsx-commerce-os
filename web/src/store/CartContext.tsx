import {
  createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode,
} from 'react';
import { socialService, buyerService } from '../api/services';
import type { Cart, Product } from '../api/types';
import { useAuth } from './AuthContext';
import { useToast } from './ToastContext';

interface CartState {
  cart: Cart;
  loading: boolean;
  /**
   * Set when the cart could not be loaded (backend asleep, network dropped).
   * Distinct from "loaded successfully and it is empty" — conflating the two
   * tells a buyer their cart was wiped when it is really still on the server.
   */
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
const CartContext = createContext<CartState | null>(null);

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
    if (!isBuyer) { setCart(EMPTY); setFavoriteSellerIds(new Set()); setSavedIds(new Set()); setLoadError(null); return; }
    setLoading(true);
    try {
      const [c, f, b] = await Promise.allSettled([
        socialService.cart(),
        socialService.favorites(),
        buyerService.bookmarks(),
      ]);
      // The cart is the one call whose failure must be visible: falling back to
      // an empty cart silently would read as "we deleted your items".
      if (c.status === 'fulfilled') { setCart(c.value); setLoadError(null); }
      else setLoadError((c.reason as Error)?.message || 'Could not load your cart');
      if (f.status === 'fulfilled') setFavoriteSellerIds(new Set(f.value.sellers.map((s) => s.id)));
      if (b.status === 'fulfilled') setSavedIds(new Set(b.value.products.map((p) => p.id)));
    } finally {
      setLoading(false);
    }
  }, [isBuyer]);

  useEffect(() => { void refresh(); }, [refresh]);

  const add = useCallback(async (product: Product, quantity = 1) => {
    if (!isBuyer) { toast('Sign in as a buyer to use the cart', 'warning'); return; }
    // Optimistic bump so the header badge reacts instantly.
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
  }, [refresh, toast]);

  const remove = useCallback(async (productId: string) => {
    try {
      setCart(await socialService.removeFromCart(productId)); setLoadError(null);
    } catch (e: any) {
      await refresh();
      toast(e?.message || 'Could not remove item', 'error');
    }
  }, [refresh, toast]);

  const clear = useCallback(async () => {
    try {
      setCart(await socialService.clearCart()); setLoadError(null);
    } catch { await refresh(); }
  }, [refresh]);

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
