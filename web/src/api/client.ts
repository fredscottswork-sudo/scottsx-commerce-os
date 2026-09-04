/**
 * ScottsTechX web — API client.
 * One fetch wrapper, one token source, normalized errors. Every page uses this.
 */

// Trailing slashes are stripped: a VITE_API_URL of "https://api.example.com/"
// would otherwise build "https://api.example.com//api/v1", which the backend
// answers with 404 on every single request. Easy to type, painful to diagnose.
const CONFIGURED = ((import.meta.env.VITE_API_URL as string | undefined) || '').replace(/\/+$/, '');

/**
 * Where the API lives when VITE_API_URL was not supplied at build time.
 *
 * This is not belt-and-braces, it is a real bug fix. The static host serves
 * `/*  /index.html  200` (see public/_redirects) so that deep links work. With
 * an empty BASE every API call resolves same-origin, hits that catch-all, and
 * comes back as **200 with the SPA's HTML**. `res.ok` is true, `res.json()`
 * throws, the catch swallows it as "empty body", and `api()` returns null — so
 * `loginWithGoogle` did `null.token` and threw a TypeError. GoogleButton only
 * catches ApiError, so the user saw the Google popup succeed and then simply
 * nothing happen. Same silent nothing for email login and registration.
 *
 * Deploying without the env var is therefore a total outage that looks like a
 * broken button, so we ship a working default instead of trusting a dashboard
 * field to have been filled in. Override it any time with VITE_API_URL.
 */
const FALLBACK_API = 'https://scottstechx-api.onrender.com';

/** Hosts that legitimately serve the API from their own origin (dev proxy). */
function isSameOriginApiHost(hostname: string): boolean {
  return (
    hostname === 'localhost' ||
    hostname === '127.0.0.1' ||
    hostname === '0.0.0.0' ||
    hostname === '[::1]' ||
    hostname.endsWith('.local')
  );
}

function resolveBase(): string {
  if (CONFIGURED) return CONFIGURED;
  if (typeof window === 'undefined' || !window.location) return '';
  // Vite dev server and the test harness proxy /api themselves.
  if (isSameOriginApiHost(window.location.hostname)) return '';
  return FALLBACK_API;
}

const BASE = resolveBase();
export const API_ROOT = `${BASE}/api/v1`;

/** True when the deployment is relying on the fallback rather than config. */
export const API_URL_IS_FALLBACK = !CONFIGURED && BASE === FALLBACK_API;

const TOKEN_KEY = 'stx_token';
const USER_KEY = 'stx_user';

export class ApiError extends Error {
  status: number;
  issues?: unknown[];
  /** Seconds to wait, when the server sent a 429 with Retry-After. */
  retryAfterSec?: number;
  constructor(status: number, message: string, issues?: unknown[], retryAfterSec?: number) {
    super(message);
    this.status = status;
    this.issues = issues;
    this.retryAfterSec = retryAfterSec;
  }
}

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (t: string) => localStorage.setItem(TOKEN_KEY, t),
  clear: () => localStorage.removeItem(TOKEN_KEY),
};

export const userStore = {
  get: (): StoredUser | null => {
    const raw = localStorage.getItem(USER_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw);
    } catch {
      return null;
    }
  },
  set: (u: StoredUser | null) => {
    if (u) localStorage.setItem(USER_KEY, JSON.stringify(u));
    else localStorage.removeItem(USER_KEY);
  },
};

export interface StoredUser {
  id: string;
  email: string;
  displayName: string;
  phone: string;
  role: 'buyer' | 'seller' | 'admin';
  emailVerified: boolean;
  profilePhotoUrl: string | null;
  city: string;
  createdAt?: string;
}

/** Global handler so any 401 can redirect (wired in App.tsx). */
export const onUnauthorized: { current: (() => void) | null } = { current: null };

/**
 * Global handler for the backend's EMAIL_NOT_VERIFIED refusal (wired in
 * App.tsx). Distinct from onUnauthorized because the session is still good —
 * discarding it would strand the user, since they need it to verify.
 */
export const onEmailUnverified: { current: (() => void) | null } = { current: null };

interface RequestOptions {
  method?: string;
  body?: unknown;
  auth?: boolean;
  rawBody?: boolean;
  /** Override the default 30s cap (e.g. 120s for AI chat with a thinking model). */
  timeoutMs?: number;
}

/**
 * Uploaded images are stored as API-relative paths ("/api/v1/uploads/images/…")
 * so the same row works on localhost, a preview host and production without
 * baking an origin into the database.
 *
 * In development the dev server proxies /api to the backend, so the bare path
 * already resolves. In production the API lives on a different origin, so the
 * path has to be prefixed — otherwise every uploaded photo 404s against the
 * static host. Rewriting once here means every <img src={product.imageUrl}>
 * in the app keeps working untouched.
 */
export function resolveMediaUrl(url: string): string {
  if (!url) return url;
  if (/^(https?:|data:|blob:)/i.test(url)) return url;
  if (url.startsWith('/api/v1/')) return `${API_ROOT}${url.slice('/api/v1'.length)}`;
  return url;
}

/** Fast media URL rewrite — only touches known keys, avoids deep recursion on large lists. */
const MEDIA_KEYS = new Set(['imageUrl', 'profilePhotoUrl', 'logoUrl', 'bannerUrl', 'coverUrl']);
const mediaCache = new Map<string, string>();
function cachedResolve(url: string): string {
  if (!url) return url;
  let cached = mediaCache.get(url);
  if (cached) return cached;
  cached = resolveMediaUrl(url);
  if (mediaCache.size > 500) mediaCache.clear();
  mediaCache.set(url, cached);
  return cached;
}
function rewriteMedia(node: unknown): void {
  if (!node || typeof node !== 'object') return;
  // Fast path for paged lists: { products: [...] } etc.
  const obj = node as Record<string, unknown>;
  if (Array.isArray(obj.products)) {
    for (const p of obj.products as any[]) {
      if (!p) continue;
      if (typeof p.imageUrl === 'string') p.imageUrl = cachedResolve(p.imageUrl);
      if (Array.isArray(p.mediaUrls)) p.mediaUrls = p.mediaUrls.map((v: any) => typeof v === 'string' ? cachedResolve(v) : v);
      if (p.seller?.logoUrl) p.seller.logoUrl = cachedResolve(p.seller.logoUrl);
    }
    // also handle sellers array if present
    if (Array.isArray(obj.sellers)) {
      for (const s of obj.sellers as any[]) {
        if (s?.logoUrl) s.logoUrl = cachedResolve(s.logoUrl);
        if (s?.placeLabel) continue;
      }
    }
    return;
  }
  // Generic shallow fallback for single objects
  if (typeof obj.imageUrl === 'string') obj.imageUrl = cachedResolve(obj.imageUrl as string);
  if (Array.isArray(obj.mediaUrls)) obj.mediaUrls = (obj.mediaUrls as string[]).map(cachedResolve);
  // Single-object shapes (e.g. { product: {...} } or { seller: {...} }): the
  // nested seller/person object still needs its media keys rewritten, so walk
  // one level of known wrapper keys instead of returning after the top level.
  for (const [k, v] of Object.entries(obj)) {
    if (Array.isArray(v) && v.length > 0 && typeof v[0] === 'object') {
      for (const item of v as any[]) {
        if (!item) continue;
        if (typeof item.imageUrl === 'string') item.imageUrl = cachedResolve(item.imageUrl);
        if (Array.isArray(item.mediaUrls)) item.mediaUrls = item.mediaUrls.map((x: any) => typeof x === 'string' ? cachedResolve(x) : x);
        if (MEDIA_KEYS.has(k) && typeof item === 'string') {
          // no-op
        }
      }
      continue;
    }
    if (v && typeof v === 'object' && (k === 'product' || k === 'seller' || k === 'otherParty' || k === 'thread')) {
      const inner = v as any;
      if (typeof inner.imageUrl === 'string') inner.imageUrl = cachedResolve(inner.imageUrl);
      if (Array.isArray(inner.mediaUrls)) inner.mediaUrls = (inner.mediaUrls as string[]).map(cachedResolve);
      if (inner.logoUrl) inner.logoUrl = cachedResolve(inner.logoUrl);
      if (inner.photoUrl) inner.photoUrl = cachedResolve(inner.photoUrl);
      if (inner.seller?.logoUrl) inner.seller.logoUrl = cachedResolve(inner.seller.logoUrl);
    }
  }
}

/**
 * Hard cap on any single request. Backend calls are fast (<1s) and the
 * slowest path is image search, which the server caps at ~4s + its own work.
 * A render cold start or a hung proxy can still take much longer — without a
 * client timeout the user just stares at a spinner forever. With it, the UI
 * always gets an answer: results or a clear "took too long" message.
 */
const REQUEST_TIMEOUT_MS = 30_000;

async function fetchWithTimeout(url: string, init: RequestInit, timeoutMs = REQUEST_TIMEOUT_MS): Promise<Response> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...init, signal: controller.signal });
  } catch (err) {
    if (err instanceof DOMException && err.name === 'AbortError') {
      throw new ApiError(
        0,
        'That took too long — please try again (large photos or a slow server can cause this).'
      );
    }
    throw new ApiError(0, 'Network error — check your connection and try again.');
  } finally {
    clearTimeout(timer);
  }
}

export async function api<T = unknown>(path: string, opts: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, auth = true, rawBody = false, timeoutMs } = opts;
  const headers: Record<string, string> = {};
  if (!rawBody && body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth) {
    const token = tokenStore.get();
    if (token) headers['Authorization'] = `Bearer ${token}`;
  }

  let res: Response;
  try {
    res = await fetchWithTimeout(
      `${API_ROOT}${path}`,
      {
        method,
        headers,
        body: rawBody ? (body as BodyInit) : body !== undefined ? JSON.stringify(body) : undefined,
      },
      timeoutMs
    );
  } catch (e) {
    throw e instanceof ApiError ? e : new ApiError(0, 'Network error — check your connection and try again.');
  }

  // A JSON API must answer with JSON. If the static host's SPA catch-all (or a
  // captive portal, or a proxy error page) answers instead, the body is HTML
  // with a 200. Treating that as "empty body" is what turned a misconfigured
  // API URL into buttons that silently did nothing, so name it explicitly.
  const contentType = res.headers.get('content-type') || '';
  const looksLikeJson = contentType.includes('json');

  let data: any = null;
  try {
    data = await res.json();
  } catch {
    /* empty or non-JSON body — handled below */
  }

  if (res.ok && !looksLikeJson && data === null) {
    throw new ApiError(
      502,
      'The server did not return data. The app is not connected to its API — ' +
        'please try again shortly.'
    );
  }

  if (!res.ok) {
    if (res.status === 401 && auth) onUnauthorized.current?.();
    // The account is real but has not proven its address. Keep the session and
    // send the user to the gate instead of showing a bare error.
    if (res.status === 403 && data?.code === 'EMAIL_NOT_VERIFIED') {
      onEmailUnverified.current?.();
    }
    const message =
      (data && (data.error as string)) ||
      (data && data.message as string) ||
      `Request failed (${res.status})`;
    throw new ApiError(
      res.status,
      message,
      data?.issues,
      typeof data?.retryAfterSec === 'number' ? data.retryAfterSec : undefined
    );
  }
  rewriteMedia(data);
  return data as T;
}

export function multipart(path: string, form: FormData): Promise<unknown> {
  const headers: Record<string, string> = {};
  const token = tokenStore.get();
  if (token) headers['Authorization'] = `Bearer ${token}`;
  return fetchWithTimeout(`${API_ROOT}${path}`, { method: 'POST', headers, body: form }).then(async (res) => {
    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
      if (res.status === 401) onUnauthorized.current?.();
      if (res.status === 403 && (data as any)?.code === 'EMAIL_NOT_VERIFIED') {
        onEmailUnverified.current?.();
      }
      throw new ApiError(res.status, (data as any)?.error || `Upload failed (${res.status})`);
    }
    return data;
  });
}
