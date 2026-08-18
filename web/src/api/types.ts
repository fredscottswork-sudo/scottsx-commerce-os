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
}

export interface NearbySeller {
  id: string;
  name: string;
  storeName: string;
  description: string;
  city: string;
  verified: boolean;
  rating: number;
  productCount: number;
  distanceKm: number;
  serviceRadiusKm: number;
}

export interface Conversation {
  id: string;
  otherParty: { id: string; name: string; role: string };
  lastMessage: string;
  lastTime: string;
  unread: number;
  productTitle?: string | null;
  mySide: string;
}

export interface ChatMessage {
  id: string;
  senderId: string;
  text: string;
  imageUrl?: string | null;
  createdAt: string;
}

export interface Order {
  id: string;
  sellerId?: string;
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
    users: { total: number; buyers: number; sellers: number; admins: number; verified: number };
    products: { total: number; flash_deals: number; low_stock: number };
    orders: { total: number; paid: number; revenue_ugx: number };
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
  category: string;
  priceMinor: number;
  stockQuantity: number;
  imageUrl: string;
  isFlashDeal: boolean;
  createdAt: string;
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
