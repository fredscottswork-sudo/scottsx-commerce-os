/** ScottsTechX web — domain types (mirror the backend models). */

export interface Seller {
  id: string;
  name: string;
  rating: number;
  location: string;
  verified: boolean;
  logoUrl?: string | null;
}

export interface Product {
  id: string;
  title: string;
  description: string;
  priceMinor: number;
  oldPriceMinor: number | null;
  currency: string;
  stockQuantity: number;
  imageUrl: string;
  category: string;
  brand: string;
  seller: Seller;
  rating: number;
  ratingCount: number;
  isFlashDeal: boolean;
  discountPercent: number;
  location: string;
  status?: ProductStatus;
  rejectionReason?: string;
  viewCount?: number;
  createdAt?: string;
}

export type ProductStatus = 'draft' | 'pending' | 'approved' | 'rejected' | 'suspended';

export interface Paged2<T> {
  products: T[];
  total: number;
  page: number;
  pageSize: number;
}

export interface Facets {
  categories: { name: string; count: number }[];
  brands: { name: string; count: number }[];
  priceRange: { minPrice: number; maxPrice: number };
}

export interface CartItem {
  productId: string;
  quantity: number;
  title: string;
  priceMinor: number;
  stockQuantity: number;
  imageUrl: string;
  status: string;
  sellerId: string;
  sellerName: string;
  lineTotalMinor: number;
}

export interface Cart {
  items: CartItem[];
  subtotalMinor: number;
  itemCount: number;
  currency: string;
}

export interface FavoriteSeller {
  id: string;
  storeName: string;
  city: string;
  rating: number;
  verified: boolean;
  logoUrl: string;
  followedAt: string;
  productCount: number;
  newThisWeek: number;
}

export interface AiAgent {
  id: string;
  name: string;
  tagline: string;
  audience: 'buyer' | 'seller' | 'both';
  icon: string;
  starters: string[];
}

export interface AiAnswer {
  text: string;
  provider: string;
  model: string;
  screen: string;
  agent: { id: string; name: string; tagline: string };
  products: Product[];
  grounded: boolean;
}

export interface AiSearchResult {
  query: string;
  explanation: string;
  products: Product[];
  detected?: string;
  transcript?: string;
  filters: {
    category: string | null;
    maxPriceMinor: number | null;
    minPriceMinor: number | null;
    city: string | null;
    flashOnly: boolean;
    sort: string;
  };
}

export interface SupportThread {
  id: string;
  subject: string;
  message: string;
  status: 'open' | 'answered' | 'closed';
  mode: 'admin' | 'ai';
  createdAt: string;
  updatedAt: string;
  replyCount?: number;
  lastReply?: string;
  userEmail?: string;
  userName?: string;
  userRole?: string;
}

export interface SupportReply {
  id: string;
  body: string;
  authorRole: 'user' | 'admin' | 'ai';
  authorName: string;
  createdAt: string;
}

export interface ProductRating {
  id: string;
  stars: number;
  comment: string;
  authorName: string;
  createdAt: string;
}

export interface NearbySeller {
  id: string;
  name: string;
  storeName: string;
  description: string;
  city: string;
  address: string;
  verified: boolean;
  rating: number;
  logoUrl: string | null;
  lat: number;
  lng: number;
  /** true = following a live GPS fix; false = last-known / fixed address. */
  live: boolean;
  locationSharing: boolean;
  locationUpdatedAt: string | null;
  locationAgeMinutes: number | null;
  isOpen: boolean;
  productCount: number;
  newThisWeek: number;
  distanceKm: number;
  etaMinutes: number;
  serviceRadiusKm: number;
  deliveryFeeUgx: number;
  freeAboveUgx: number;
  codEnabled: boolean;
  withinServiceRadius: boolean;
  /** Human place for the pin: "Kireka, Central Region". */
  placeLabel: string;
}

/** Offline reverse-geocoding result: where a coordinate actually is. */
export interface Place {
  village: string | null;
  city: string | null;
  region: string | null;
  country: string | null;
  countryCode: string | null;
  accuracyKm: number;
  /** "Kabalagala, Kampala, Central Region, Uganda" */
  label: string;
  /** "Kabalagala, Central Region" */
  shortLabel: string;
  source: 'offline-gazetteer';
}

export interface ChatParty {
  id: string;
  name: string;
  role: string;
  photoUrl?: string | null;
  verified?: boolean;
  location?: string | null;
}

export interface Conversation {
  id: string;
  otherParty: ChatParty;
  lastMessage: string;
  lastTime: string;
  createdAt?: string;
  unread: number;
  mySide: string;
  messageCount?: number;
  lastSenderId?: string | null;
  productId?: string | null;
  productTitle?: string | null;
  productImageUrl?: string | null;
  productPriceMinor?: number | null;
  pinned?: boolean;
  archived?: boolean;
  muted?: boolean;
  readByMe?: boolean;
  pendingOffers?: number;
  /** Only present on GET /conversations/:id. */
  otherLastReadAt?: string | null;
  otherTyping?: boolean;
}

export type MessageKind = 'text' | 'image' | 'offer' | 'system';
export type OfferStatus = 'pending' | 'accepted' | 'declined' | 'countered' | 'withdrawn';

export interface ChatMessage {
  id: string;
  conversationId?: string;
  senderId: string;
  text: string;
  imageUrl?: string | null;
  attachmentName?: string | null;
  kind: MessageKind;
  productId?: string | null;
  productTitle?: string | null;
  productImageUrl?: string | null;
  productPriceMinor?: number | null;
  offerMinor?: number | null;
  offerStatus?: OfferStatus | null;
  offerQuantity?: number;
  replyToId?: string | null;
  deletedAt?: string | null;
  readByOther?: boolean;
  createdAt: string;
}

export interface InboxCounts {
  all: number;
  unread: number;
  pinned: number;
  archived: number;
  offers: number;
}

export type InboxFilter = 'all' | 'unread' | 'pinned' | 'archived' | 'offers';

export interface QuickReply {
  id: string;
  text: string;
  sortOrder: number;
}

export interface Order {
  id: string;
  sellerId?: string;
  buyerId?: string;
  productId?: string;
  title: string;
  amount: number;
  quantity: number;
  status: string;
  createdAt: string;
  imageUrl?: string;
  storeName?: string;
  buyerName?: string;
}

export interface Address {
  id: string;
  label: string;
  line1: string;
  city: string;
  country: string;
  isDefault: boolean;
}

export interface PaymentMethod {
  id: string;
  type: string;
  label: string;
  last4: string;
  phone: string;
  isDefault: boolean;
}

export interface Refund {
  id: string;
  orderId: string;
  reason: string;
  status: string;
  createdAt: string;
}

export interface SupportTicket {
  id: string;
  subject: string;
  message: string;
  status: string;
  createdAt: string;
}

export interface Faq {
  id: string;
  question: string;
  answer: string;
  category: string;
}

export interface AppNotification {
  id: string;
  title: string;
  body: string;
  type: string;
  read: boolean;
  createdAt: string;
  /** Deep-link payload, e.g. { screen: 'product', id: '<uuid>' }. */
  data?: Record<string, string>;
  imageUrl?: string;
}

export interface UserSettings {
  theme: string;
  language: string;
  currency: string;
  notifyOrderUpdates: boolean;
  notifyMessages: boolean;
  notifyMarketing: boolean;
}

export interface StoreSettings {
  storeName: string;
  storeDescription: string;
  storeLogoUrl: string;
  legalName: string;
  tin: string;
  businessEmail: string;
  businessPhone: string;
  address: string;
  pickupInstructions: string;
  serviceRadiusKm: number;
  deliveryFeeUgx: number;
  freeAboveUgx: number;
  codEnabled: boolean;
  momoNumber: string;
  bankName: string;
  bankAccount: string;
  notifOrderUpdates: boolean;
  notifBuyerMessages: boolean;
  notifMarketing: boolean;
  notifWeeklyDigest: boolean;
  twoFactorEnabled: boolean;
  returnsWindowDays: number;
  refundPolicy: string;
  terms: string;
  contactEmail: string;
  contactPhone: string;
  city: string;
  verified: boolean;
  rating: number;
}

export interface SellerDashboardStats {
  revenueUgx: number;
  orders: number;
  totalProducts: number;
  lowStock: number;
  topProduct: string | null;
}

export interface CmsPage {
  slug: string;
  title: string;
  body: string;
  updatedAt: string;
}

export interface CheckoutResult {
  order: Order & { paymentLink?: string | null; invoiceNumber?: string | null; paymentMode?: string };
  paymentMode: string;
  paymentLink: string | null;
  invoiceNumber: string | null;
  paymentReference: string;
  status: string;
}

export interface AdminStats {
  stats: {
    users: {
      total: number; buyers: number; sellers: number; admins: number;
      verified: number; newThisWeek: number;
    };
    products: {
      total: number; approved: number; pending: number; rejected: number;
      draft: number; suspended: number; flash_deals: number; low_stock: number;
    };
    orders: { total: number; paid: number; pending: number; revenue_ugx: number };
    conversations: number;
  };
  recentUsers: Array<{
    id: string;
    email: string;
    displayName: string;
    role: string;
    emailVerified: boolean;
    createdAt: string;
  }>;
  reviewQueue: Array<{
    id: string; title: string; category: string; priceMinor: number;
    imageUrl: string; submittedAt: string; sellerName: string; sellerEmail: string;
  }>;
  topSellers: Array<{
    id: string; storeName: string; verified: boolean; rating: number;
    productCount: number; revenueUgx: number;
  }>;
  salesSeries: Array<{ date: string; orders: number; revenue: number }>;
}

export interface AdminQueueItem {
  id: string;
  title: string;
  description: string;
  category: string;
  brand: string;
  priceMinor: number;
  stockQuantity: number;
  imageUrl: string;
  submittedAt: string;
  sellerId: string;
  sellerName: string;
  sellerEmail: string;
  sellerVerified: boolean;
  sellerApprovedCount: number;
}

export interface SellerDashboard {
  stats: {
    revenueUgx: number; revenue30Ugx: number; orders: number; orders30: number;
    avgOrderValueUgx: number; totalProducts: number; lowStock: number; outOfStock: number;
    topProduct: string | null; unreadMessages: number; followers: number; totalViews: number;
    productsByStatus: Record<string, number>; pendingApproval: number;
  };
  topProducts: Array<{ title: string; sold: number }>;
  recentOrders: Array<{
    id: string; buyerId: string; productTitle: string; amount: number;
    quantity: number; status: string; createdAt: string; buyerName: string;
  }>;
  salesSeries: Array<{ date: string; orders: number; revenue: number }>;
}

export interface AdminUserRow {
  id: string;
  email: string;
  displayName: string;
  phone: string;
  role: string;
  emailVerified: boolean;
  city: string;
  createdAt: string;
}

export interface AdminProductRow {
  id: string;
  title: string;
  description: string;
  category: string;
  brand: string;
  priceMinor: number;
  stockQuantity: number;
  imageUrl: string;
  isFlashDeal: boolean;
  status: ProductStatus;
  rejectionReason: string;
  submittedAt: string | null;
  reviewedAt: string | null;
  createdAt: string;
  viewCount: number;
  sellerId: string;
  sellerEmail: string;
  sellerName: string;
}

export interface Paged<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
}

export const formatUgx = (amount: number): string => `UGX ${new Intl.NumberFormat('en-UG').format(amount)}`;
