/**
 * ScottsTechX web — typed service layer. Every backend call lives here so UI
 * components never touch fetch directly.
 */
import { api, multipart } from './client';
import type {
  Address,
  AdminProductRow,
  AdminStats,
  AdminUserRow,
  AppNotification,
  ChatMessage,
  CheckoutResult,
  CmsPage,
  Conversation,
  Faq,
  NearbySeller,
  Order,
  Paged,
  PaymentMethod,
  Product,
  Refund,
  SellerDashboardStats,
  StoreSettings,
  SupportTicket,
  UserSettings,
  Paged2,
  Facets,
  Cart,
  FavoriteSeller,
  AiAgent,
  AiAnswer,
  AiSearchResult,
  SupportThread,
  SupportReply,
  ProductRating,
  AdminQueueItem,
  SellerDashboard,
  InboxCounts,
  InboxFilter,
  OfferStatus,
  QuickReply,
} from './types';

// ── Auth ────────────────────────────────────────────────────────────────────
export const authService = {
  login: (email: string, password: string) =>
    api<{ token: string; user: any }>('/auth/login', { method: 'POST', auth: false, body: { email, password } }),
  register: (body: { email: string; password: string; displayName: string; phone?: string; role?: string }) =>
    api<{ token: string; user: any }>('/auth/register', { method: 'POST', auth: false, body }),
  me: () => api<{ user: any }>('/auth/me'),
  updateMe: (body: { displayName?: string; phone?: string; profilePhotoUrl?: string | null; city?: string }) =>
    api<{ user: any }>('/auth/me', { method: 'PATCH', body }),
  upgradeToSeller: () => api<{ token: string; user: any }>('/auth/upgrade-to-seller', { method: 'POST' }),
  uploadPhoto: (file: File) => {
    const form = new FormData();
    form.append('photo', file);
    return multipart('/me/photo', form) as Promise<{ profilePhotoUrl: string }>;
  },
};

// ── Products (public) ───────────────────────────────────────────────────────
export interface CatalogQuery {
  q?: string;
  category?: string;
  brand?: string;
  minPrice?: number;
  maxPrice?: number;
  minRating?: number;
  verifiedOnly?: boolean;
  inStock?: boolean;
  flashOnly?: boolean;
  sellerId?: string;
  sort?: 'relevance' | 'newest' | 'price_asc' | 'price_desc' | 'rating' | 'popular';
  page?: number;
  pageSize?: number;
}

function qs(params: Record<string, unknown>): string {
  const q = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === '' || v === false) continue;
    q.set(k, String(v));
  }
  const s = q.toString();
  return s ? `?${s}` : '';
}

export const productService = {
  list: (params: CatalogQuery = {}) =>
    api<Paged2<Product>>(`/products${qs(params as Record<string, unknown>)}`, { auth: false }),
  search: (params: CatalogQuery) =>
    api<Paged2<Product>>(`/products/search${qs(params as Record<string, unknown>)}`, { auth: false }),
  facets: () => api<Facets>('/products/facets', { auth: false }),
  suggest: (q: string) =>
    api<{ suggestions: { label: string; kind: string }[] }>(`/products/suggest?q=${encodeURIComponent(q)}`, {
      auth: false,
    }),
  byId: (id: string) => api<{ product: Product }>(`/products/${id}`, { auth: false }),
  related: (id: string) => api<{ products: Product[] }>(`/products/${id}/related`, { auth: false }),
  ratings: (id: string) =>
    api<{ ratings: ProductRating[]; summary: { average: number; count: number } }>(
      `/products/${id}/ratings`, { auth: false }
    ),
  rate: (id: string, stars: number, comment = '') =>
    api<{ ok: boolean; rating: number; ratingCount: number }>(`/products/${id}/ratings`, {
      method: 'POST',
      body: { stars, comment },
    }),
  nearby: (params: {
    lat: number; lng: number; radiusKm?: number; category?: string; q?: string;
    verifiedOnly?: boolean; openOnly?: boolean; sort?: 'distance' | 'rating' | 'products' | 'newest';
  }) =>
    api<{
      sellers: NearbySeller[]; count: number; liveCount: number;
      center: { lat: number; lng: number; radiusKm: number }; generatedAt: string;
    }>(`/sellers/nearby${qs(params as Record<string, unknown>)}`, { auth: false }),
  sellerPublic: (id: string) => api<{ seller: any; products: Product[] }>(`/sellers/${id}`, { auth: false }),
};

// ── Favourites, cart, devices ───────────────────────────────────────────────
export const socialService = {
  favorites: () => api<{ sellers: FavoriteSeller[] }>('/me/favorites'),
  followSeller: (sellerId: string) =>
    api<{ ok: boolean; following: boolean }>(`/me/favorites/${sellerId}`, { method: 'POST' }),
  unfollowSeller: (sellerId: string) =>
    api<{ ok: boolean; following: boolean }>(`/me/favorites/${sellerId}`, { method: 'DELETE' }),
  favoritesFeed: (limit = 20) => api<{ products: Product[] }>(`/me/favorites/feed?limit=${limit}`),

  // Every cart mutation returns the recomputed cart — no follow-up GET needed.
  cart: () => api<Cart>('/me/cart'),
  addToCart: (productId: string, quantity = 1) =>
    api<Cart>('/me/cart', { method: 'POST', body: { productId, quantity } }),
  updateCartItem: (productId: string, quantity: number) =>
    api<Cart>(`/me/cart/${productId}`, { method: 'PATCH', body: { quantity } }),
  removeFromCart: (productId: string) => api<Cart>(`/me/cart/${productId}`, { method: 'DELETE' }),
  clearCart: () => api<Cart>('/me/cart', { method: 'DELETE' }),
  checkout: (payload: { addressId?: string; phone?: string; note?: string } = {}) =>
    api<{
      orders: Order[]; orderCount: number; totalMinor: number; currency: string;
      paymentMode: string; message: string;
    }>('/me/cart/checkout', { method: 'POST', body: payload }),

  registerDevice: (token: string, platform: 'android' | 'ios' | 'web' = 'web') =>
    api<{ ok: boolean }>('/me/devices', { method: 'POST', body: { token, platform } }),

  searchHistory: () =>
    api<{ history: { id: string; query: string; mode: string; results: number; createdAt: string }[] }>(
      '/me/search-history'
    ),
  clearSearchHistory: () => api<{ ok: boolean }>('/me/search-history', { method: 'DELETE' }),
};

// ── Buyer account ───────────────────────────────────────────────────────────
export const buyerService = {
  orders: () => api<{ orders: Order[] }>('/me/orders'),
  order: (id: string) => api<{ order: Order }>(`/me/orders/${id}`),
  bookmarks: () => api<{ products: Product[] }>('/me/bookmarks'),
  toggleBookmark: (productId: string) =>
    api<{ bookmarked: boolean }>('/me/bookmarks/toggle', { method: 'POST', body: { productId } }),
  addresses: () => api<{ addresses: Address[] }>('/me/addresses'),
  createAddress: (a: Omit<Address, 'id'>) => api<{ address: Address }>('/me/addresses', { method: 'POST', body: a }),
  deleteAddress: (id: string) => api<{ ok: boolean }>(`/me/addresses/${id}`, { method: 'DELETE' }),
  paymentMethods: () => api<{ paymentMethods: PaymentMethod[] }>('/me/payment-methods'),
  createPaymentMethod: (p: Omit<PaymentMethod, 'id'>) =>
    api<{ paymentMethod: PaymentMethod }>('/me/payment-methods', { method: 'POST', body: p }),
  deletePaymentMethod: (id: string) => api<{ ok: boolean }>(`/me/payment-methods/${id}`, { method: 'DELETE' }),
  refunds: () => api<{ refunds: Refund[] }>('/me/refunds'),
  createRefund: (orderId: string, reason: string) =>
    api<{ refund: Refund }>('/me/refunds', { method: 'POST', body: { orderId, reason } }),
  tickets: () => api<{ tickets: SupportTicket[] }>('/me/support/tickets'),
  createTicket: (subject: string, message: string) =>
    api<{ ticket: SupportTicket }>('/me/support/tickets', { method: 'POST', body: { subject, message } }),
  faqs: () => api<{ faqs: Faq[] }>('/me/faqs', { auth: false }),
  notifications: () => api<{ notifications: AppNotification[]; unread: number }>('/me/notifications'),
  unreadCount: () => api<{ unread: number }>('/me/notifications/unread-count'),
  markNotificationRead: (id: string) => api<{ ok: boolean }>(`/me/notifications/${id}/read`, { method: 'PATCH' }),
  markAllNotificationsRead: () => api<{ ok: boolean }>('/me/notifications/read-all', { method: 'POST' }),
  preferences: () => api<{ preferences: UserSettings }>('/me/preferences'),
  savePreferences: (p: Partial<UserSettings>) =>
    api<{ preferences: UserSettings }>('/me/preferences', { method: 'PATCH', body: p }),
  changePassword: (oldPassword: string, newPassword: string) =>
    api<{ ok: boolean }>('/me/change-password', { method: 'POST', body: { oldPassword, newPassword } }),
};

// ── Seller ──────────────────────────────────────────────────────────────────
export const sellerService = {
  dashboard: () => api<SellerDashboard>('/seller/dashboard/stats'),
  /** @deprecated use dashboard() — kept so older call sites keep compiling. */
  dashboardStats: () => api<{ stats: SellerDashboardStats }>('/seller/dashboard/stats'),
  orders: () => api<{ orders: Order[] }>('/seller/orders'),
  inventory: (status?: string) =>
    api<{ products: Product[]; counts: Record<string, number> }>(
      `/seller/products${status && status !== 'all' ? `?status=${status}` : ''}`
    ),
  createProduct: (p: any) =>
    api<{ product: Product; message: string }>('/seller/products', { method: 'POST', body: p }),
  updateProduct: (id: string, p: any) =>
    api<{ product: Product }>(`/seller/products/${id}`, { method: 'PATCH', body: p }),
  submitForReview: (id: string) =>
    api<{ product: Product }>(`/seller/products/${id}/submit`, { method: 'POST' }),
  deleteProduct: (id: string) => api<{ ok: boolean }>(`/seller/products/${id}`, { method: 'DELETE' }),
  storeSettings: () => api<{ settings: StoreSettings }>('/seller/store-settings'),
  saveStoreSettings: (s: Partial<StoreSettings>) =>
    api<{ settings: StoreSettings }>('/seller/store-settings', { method: 'PATCH', body: s }),
  profile: () => api<{ seller: any }>('/seller/profile'),

  // Live location — powers the buyer's Nearby screen.
  location: () =>
    api<{ location: { lat: number; lng: number; sharing: boolean; updatedAt: string; isOpen: boolean } | null }>(
      '/seller/location'
    ),
  publishLocation: (lat: number, lng: number, city?: string) =>
    api<{ location: { lat: number; lng: number; sharing: boolean; updatedAt: string } }>('/seller/location', {
      method: 'POST',
      body: { lat, lng, sharing: true, city },
    }),
  stopSharingLocation: () =>
    api<{ location: { sharing: boolean }; message: string }>('/seller/location', { method: 'DELETE' }),
  setOpenState: (isOpen: boolean) =>
    api<{ isOpen: boolean }>('/seller/open-state', { method: 'PATCH', body: { isOpen } }),

  generateProduct: (imageUrl: string, hint: string) =>
    api<{
      title: string; description: string; category: string; brand: string;
      suggestedPriceMinor: number; comparables: Product[]; provider: string;
    }>('/ai/v2/generate-product', { method: 'POST', body: { imageUrl, hint } }),
};

// ── Checkout / payments ─────────────────────────────────────────────────────
export const paymentService = {
  checkout: (productId: string, quantity = 1, buyerPhone = '') =>
    api<CheckoutResult>('/orders/checkout', { method: 'POST', body: { productId, quantity, buyerPhone } }),
  paymentStatus: (orderId: string) =>
    api<{ order: { status: string; paymentReference?: string; paymentLink?: string }; nylonStatus?: string }>(
      `/orders/${orderId}/payment-status`
    ),
};

// ── Chat ────────────────────────────────────────────────────────────────────
export interface SendMessageInput {
  text?: string;
  imageUrl?: string;
  attachmentName?: string;
  kind?: 'text' | 'image' | 'offer';
  productId?: string;
  offerMinor?: number;
  offerQuantity?: number;
  replyToId?: string;
}

export const chatService = {
  /** Inbox. `filter` narrows the list; counts always describe the whole inbox. */
  conversations: (params: { filter?: InboxFilter; q?: string } = {}) => {
    const qs = new URLSearchParams();
    if (params.filter && params.filter !== 'all') qs.set('filter', params.filter);
    if (params.q?.trim()) qs.set('q', params.q.trim());
    const suffix = qs.toString() ? `?${qs}` : '';
    return api<{
      conversations: Conversation[];
      counts: InboxCounts;
      totalUnread: number;
      filter: string;
    }>(`/conversations${suffix}`);
  },

  open: (sellerId: string, productId?: string) =>
    api<{ conversation: { id: string; existing: boolean } }>('/conversations', {
      method: 'POST',
      body: { sellerId, productId },
    }),

  /** Thread header: counterparty, product context, pin/mute state, typing. */
  thread: (conversationId: string) =>
    api<{ conversation: Conversation }>(`/conversations/${conversationId}`),

  messages: (conversationId: string) =>
    api<{ messages: ChatMessage[]; otherLastReadAt: string | null; otherTyping: boolean }>(
      `/conversations/${conversationId}/messages`
    ),

  send: (conversationId: string, input: string | SendMessageInput) =>
    api<{ message: ChatMessage }>(`/conversations/${conversationId}/messages`, {
      method: 'POST',
      body: typeof input === 'string' ? { text: input } : input,
    }),

  markRead: (conversationId: string) =>
    api<{ ok: boolean }>(`/conversations/${conversationId}/read`, { method: 'POST' }),

  /** Heartbeat — the indicator expires server-side after ~6s. */
  typing: (conversationId: string, typing: boolean) =>
    api<{ ok: boolean }>(`/conversations/${conversationId}/typing`, {
      method: 'POST',
      body: { typing },
    }),

  setState: (conversationId: string, patch: { pinned?: boolean; archived?: boolean; muted?: boolean }) =>
    api<{ state: { pinned: boolean; archived: boolean; muted: boolean } }>(
      `/conversations/${conversationId}/state`,
      { method: 'PATCH', body: patch }
    ),

  respondToOffer: (conversationId: string, messageId: string, action: 'accept' | 'decline' | 'withdraw') =>
    api<{ ok: boolean; status: OfferStatus; message: ChatMessage }>(
      `/conversations/${conversationId}/offers/${messageId}`,
      { method: 'POST', body: { action } }
    ),

  retract: (conversationId: string, messageId: string) =>
    api<{ ok: boolean }>(`/conversations/${conversationId}/messages/${messageId}`, {
      method: 'DELETE',
    }),

  quickReplies: () => api<{ quickReplies: QuickReply[] }>('/me/quick-replies'),
  addQuickReply: (text: string) =>
    api<{ quickReply: QuickReply }>('/me/quick-replies', { method: 'POST', body: { text } }),
  deleteQuickReply: (id: string) =>
    api<{ ok: boolean }>(`/me/quick-replies/${id}`, { method: 'DELETE' }),
};

// ── AI ──────────────────────────────────────────────────────────────────────
export const aiService = {
  ask: (
    prompt: string,
    opts: { screen?: string; agent?: string; history?: { role: 'user' | 'assistant'; content: string }[] } = {}
  ) =>
    api<AiAnswer>('/ai/v2/ask', {
      method: 'POST',
      body: { prompt, screen: opts.screen ?? 'web', agent: opts.agent, history: opts.history ?? [] },
    }),
  agents: () => api<{ agents: AiAgent[] }>('/ai/agents', { auth: false }),
  status: () =>
    api<{
      configured: boolean; provider: string; model: string; grounded: boolean;
      capabilities: Record<string, boolean>;
    }>('/ai/status', { auth: false }),
  search: (q: string, limit = 24) =>
    api<AiSearchResult>('/ai/search', { method: 'POST', body: { q, limit } }),
  imageSearch: (payload: { imageUrl?: string; hint?: string; labels?: string[] }) =>
    api<AiSearchResult>('/ai/image-search', { method: 'POST', body: payload }),
  voiceSearch: (transcript: string) =>
    api<AiSearchResult>('/ai/voice-search', { method: 'POST', body: { transcript } }),
  generateProduct: (payload: { imageUrl?: string; hint?: string }) =>
    api<{
      title: string; description: string; category: string; brand: string;
      suggestedPriceMinor: number; comparables: Product[]; provider: string;
    }>('/ai/v2/generate-product', { method: 'POST', body: payload }),
};

// ── Support desk (AI + admin modes) ─────────────────────────────────────────
export const supportService = {
  threads: () => api<{ threads: SupportThread[] }>('/me/support/threads'),
  thread: (id: string) =>
    api<{ thread: SupportThread; replies: SupportReply[] }>(`/me/support/threads/${id}`),
  create: (subject: string, message: string, mode: 'ai' | 'admin' = 'ai') =>
    api<{ thread: SupportThread; aiReply: string | null }>('/me/support/threads', {
      method: 'POST',
      body: { subject, message, mode },
    }),
  reply: (id: string, body: string) =>
    api<{ ok: boolean; aiReply: string | null }>(`/me/support/threads/${id}/reply`, {
      method: 'POST',
      body: { body },
    }),
  escalate: (id: string) =>
    api<{ ok: boolean; mode: string }>(`/me/support/threads/${id}/escalate`, { method: 'POST' }),
  close: (id: string) => api<{ ok: boolean }>(`/me/support/threads/${id}/close`, { method: 'POST' }),
};

// ── CMS ─────────────────────────────────────────────────────────────────────
export const cmsService = {
  page: (slug: string) => api<{ page: CmsPage }>(`/cms/${slug}`, { auth: false }),
};

// ── Admin ───────────────────────────────────────────────────────────────────
export const adminService = {
  stats: () => api<AdminStats>('/admin/stats'),
  users: (params: { search?: string; role?: string; page?: number; pageSize?: number }) => {
    const q = new URLSearchParams();
    if (params.search) q.set('search', params.search);
    if (params.role) q.set('role', params.role);
    q.set('page', String(params.page || 1));
    q.set('pageSize', String(params.pageSize || 25));
    return api<{ users: AdminUserRow[]; total: number; page: number; pageSize: number }>(`/admin/users?${q}`);
  },
  setRole: (id: string, role: string) =>
    api<{ user: AdminUserRow }>(`/admin/users/${id}/role`, { method: 'PATCH', body: { role } }),
  deleteUser: (id: string) =>
    api<{ ok: boolean; deleted: string }>(`/admin/users/${id}`, { method: 'DELETE' }),
  products: (params: { search?: string; status?: string; page?: number; pageSize?: number }) => {
    const q = new URLSearchParams();
    if (params.search) q.set('search', params.search);
    if (params.status) q.set('status', params.status);
    q.set('page', String(params.page || 1));
    q.set('pageSize', String(params.pageSize || 25));
    return api<{
      products: AdminProductRow[]; total: number; page: number; pageSize: number;
      counts: Record<string, number>;
    }>(`/admin/products?${q}`);
  },
  queue: () => api<{ products: AdminQueueItem[]; total: number }>('/admin/products/queue'),
  approve: (id: string) =>
    api<{ ok: boolean; followersNotified: number }>(`/admin/products/${id}/approve`, { method: 'POST' }),
  reject: (id: string, reason: string) =>
    api<{ ok: boolean }>(`/admin/products/${id}/reject`, { method: 'POST', body: { reason } }),
  suspend: (id: string, reason: string) =>
    api<{ ok: boolean }>(`/admin/products/${id}/suspend`, { method: 'POST', body: { reason } }),
  bulk: (ids: string[], action: 'approve' | 'reject' | 'suspend' | 'delete', reason = '') =>
    api<{ ok: boolean; affected: number; followersNotified: number }>('/admin/products/bulk', {
      method: 'POST',
      body: { ids, action, reason },
    }),
  history: (id: string) =>
    api<{ history: { id: string; action: string; reason: string; adminName: string; createdAt: string }[] }>(
      `/admin/products/${id}/history`
    ),
  verifySeller: (id: string, verified: boolean) =>
    api<{ store: { userId: string; verified: boolean } }>(`/admin/sellers/${id}/verify`, {
      method: 'PATCH',
      body: { verified },
    }),
  tickets: (status?: string) =>
    api<{ tickets: SupportThread[] }>(`/admin/support/tickets${status ? `?status=${status}` : ''}`),
  ticket: (id: string) =>
    api<{ ticket: SupportThread; replies: SupportReply[] }>(`/admin/support/tickets/${id}`),
  replyTicket: (id: string, body: string, close = false) =>
    api<{ reply: SupportReply }>(`/admin/support/tickets/${id}/reply`, {
      method: 'POST',
      body: { body, close },
    }),
  deleteProduct: (id: string) => api<{ ok: boolean }>(`/admin/products/${id}`, { method: 'DELETE' }),
};
