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
} from './types';

// ── Auth ────────────────────────────────────────────────────────────────────
export const authService = {
  login: (email: string, password: string) =>
    api<{ token: string; user: any }>('/auth/login', { method: 'POST', auth: false, body: { email, password } }),
  register: (body: { email: string; password: string; displayName: string; phone?: string; role?: string }) =>
    api<{ token: string; user: any }>('/auth/register', { method: 'POST', auth: false, body }),
  me: () => api<{ user: any }>('/auth/me'),
  updateMe: (body: { displayName?: string; phone?: string }) =>
    api<{ user: any }>('/auth/me', { method: 'PATCH', body }),
  upgradeToSeller: () => api<{ token: string; user: any }>('/auth/upgrade-to-seller', { method: 'POST' }),
  uploadPhoto: (file: File) => {
    const form = new FormData();
    form.append('photo', file);
    return multipart('/me/photo', form) as Promise<{ profilePhotoUrl: string }>;
  },
};

// ── Products (public) ───────────────────────────────────────────────────────
export const productService = {
  list: () => api<{ products: Product[] }>('/products', { auth: false }),
  byId: (id: string) => api<{ product: Product }>(`/products/${id}`, { auth: false }),
  nearby: (lat: number, lng: number, radiusKm = 50) =>
    api<{ sellers: NearbySeller[]; count: number }>(
      `/sellers/nearby?lat=${lat}&lng=${lng}&radiusKm=${radiusKm}`,
      { auth: false }
    ),
  sellerPublic: (id: string) => api<{ seller: any; products: Product[] }>(`/sellers/${id}`, { auth: false }),
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
  notifications: () => api<{ notifications: AppNotification[] }>('/me/notifications'),
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
  dashboardStats: () => api<{ stats: SellerDashboardStats }>('/seller/dashboard/stats'),
  orders: () => api<{ orders: Order[] }>('/seller/orders'),
  inventory: () => api<{ products: Product[] }>('/seller/products'),
  createProduct: (p: any) => api<{ product: Product }>('/seller/products', { method: 'POST', body: p }),
  deleteProduct: (id: string) => api<{ ok: boolean }>(`/seller/products/${id}`, { method: 'DELETE' }),
  storeSettings: () => api<{ settings: StoreSettings }>('/seller/store-settings'),
  saveStoreSettings: (s: Partial<StoreSettings>) =>
    api<{ settings: StoreSettings }>('/seller/store-settings', { method: 'PATCH', body: s }),
  profile: () => api<{ seller: any }>('/seller/profile'),
  generateProduct: (imageUrl: string, hint: string) =>
    api<{ title: string; description: string; category: string; suggestedPriceMinor: number }>(
      '/ai/v2/generate-product',
      { method: 'POST', auth: false, body: { imageUrl, hint } }
    ),
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
export const chatService = {
  conversations: () => api<{ conversations: Conversation[] }>('/conversations'),
  open: (sellerId: string, productId?: string) =>
    api<{ conversation: { id: string; existing: boolean } }>('/conversations', {
      method: 'POST',
      body: { sellerId, productId },
    }),
  messages: (conversationId: string) =>
    api<{ messages: ChatMessage[] }>(`/conversations/${conversationId}/messages`),
  send: (conversationId: string, text: string) =>
    api<{ message: ChatMessage }>(`/conversations/${conversationId}/messages`, { method: 'POST', body: { text } }),
  markRead: (conversationId: string) =>
    api<{ ok: boolean }>(`/conversations/${conversationId}/read`, { method: 'POST' }),
};

// ── AI ──────────────────────────────────────────────────────────────────────
export const aiService = {
  ask: (prompt: string, screen = 'web') =>
    api<{ text: string; provider: string; model: string }>('/ai/v2/ask', {
      method: 'POST',
      auth: false,
      body: { prompt, screen },
    }),
  status: () => api<{ configured: boolean; provider: string; model: string }>('/ai/status', { auth: false }),
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
    return api<Paged<AdminUserRow>>(`/admin/users?${q}`);
  },
  setRole: (id: string, role: string) =>
    api<{ user: AdminUserRow }>(`/admin/users/${id}/role`, { method: 'PATCH', body: { role } }),
  products: (params: { search?: string; page?: number; pageSize?: number }) => {
    const q = new URLSearchParams();
    if (params.search) q.set('search', params.search);
    q.set('page', String(params.page || 1));
    q.set('pageSize', String(params.pageSize || 25));
    return api<Paged<AdminProductRow>>(`/admin/products?${q}`);
  },
  deleteProduct: (id: string) => api<{ ok: boolean }>(`/admin/products/${id}`, { method: 'DELETE' }),
};
