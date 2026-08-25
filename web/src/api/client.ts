/**
 * ScottsTechX web — API client.
 * One fetch wrapper, one token source, normalized errors. Every page uses this.
 */

<<<<<<< HEAD
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

=======
const BASE = (import.meta.env.VITE_API_URL as string | undefined) || '';
export const API_ROOT = `${BASE}/api/v1`;

>>>>>>> origin/master
const TOKEN_KEY = 'stx_token';
const USER_KEY = 'stx_user';

export class ApiError extends Error {
  status: number;
  issues?: unknown[];
<<<<<<< HEAD
  /** Seconds to wait, when the server sent a 429 with Retry-After. */
  retryAfterSec?: number;
  constructor(status: number, message: string, issues?: unknown[], retryAfterSec?: number) {
    super(message);
    this.status = status;
    this.issues = issues;
    this.retryAfterSec = retryAfterSec;
=======
  constructor(status: number, message: string, issues?: unknown[]) {
    super(message);
    this.status = status;
    this.issues = issues;
>>>>>>> origin/master
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

<<<<<<< HEAD
/**
 * Global handler for the backend's EMAIL_NOT_VERIFIED refusal (wired in
 * App.tsx). Distinct from onUnauthorized because the session is still good —
 * discarding it would strand the user, since they need it to verify.
 */
export const onEmailUnverified: { current: (() => void) | null } = { current: null };

=======
>>>>>>> origin/master
interface RequestOptions {
  method?: string;
  body?: unknown;
  auth?: boolean;
  rawBody?: boolean;
}

<<<<<<< HEAD
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

/** Recursively rewrite the media fields of a parsed API payload. */
const MEDIA_KEYS = new Set(['imageUrl', 'profilePhotoUrl', 'logoUrl', 'bannerUrl', 'coverUrl']);
function rewriteMedia(node: unknown, depth = 0): void {
  if (depth > 6 || node === null || typeof node !== 'object') return;
  if (Array.isArray(node)) {
    for (const item of node) rewriteMedia(item, depth + 1);
    return;
  }
  const obj = node as Record<string, unknown>;
  for (const [key, value] of Object.entries(obj)) {
    if (typeof value === 'string') {
      if (MEDIA_KEYS.has(key)) obj[key] = resolveMediaUrl(value);
    } else if (key === 'mediaUrls' && Array.isArray(value)) {
      obj[key] = value.map((v) => (typeof v === 'string' ? resolveMediaUrl(v) : v));
    } else {
      rewriteMedia(value, depth + 1);
    }
  }
}

=======
>>>>>>> origin/master
export async function api<T = unknown>(path: string, opts: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, auth = true, rawBody = false } = opts;
  const headers: Record<string, string> = {};
  if (!rawBody && body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth) {
    const token = tokenStore.get();
    if (token) headers['Authorization'] = `Bearer ${token}`;
  }

  let res: Response;
  try {
    res = await fetch(`${API_ROOT}${path}`, {
      method,
      headers,
      body: rawBody ? (body as BodyInit) : body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError(0, 'Network error — check your connection and try again.');
  }

<<<<<<< HEAD
  // A JSON API must answer with JSON. If the static host's SPA catch-all (or a
  // captive portal, or a proxy error page) answers instead, the body is HTML
  // with a 200. Treating that as "empty body" is what turned a misconfigured
  // API URL into buttons that silently did nothing, so name it explicitly.
  const contentType = res.headers.get('content-type') || '';
  const looksLikeJson = contentType.includes('json');

=======
>>>>>>> origin/master
  let data: any = null;
  try {
    data = await res.json();
  } catch {
<<<<<<< HEAD
    /* empty or non-JSON body — handled below */
  }

  if (res.ok && !looksLikeJson && data === null) {
    throw new ApiError(
      502,
      'The server did not return data. The app is not connected to its API — ' +
        'please try again shortly.'
    );
=======
    /* empty body */
>>>>>>> origin/master
  }

  if (!res.ok) {
    if (res.status === 401 && auth) onUnauthorized.current?.();
<<<<<<< HEAD
    // The account is real but has not proven its address. Keep the session and
    // send the user to the gate instead of showing a bare error.
    if (res.status === 403 && data?.code === 'EMAIL_NOT_VERIFIED') {
      onEmailUnverified.current?.();
    }
=======
>>>>>>> origin/master
    const message =
      (data && (data.error as string)) ||
      (data && data.message as string) ||
      `Request failed (${res.status})`;
<<<<<<< HEAD
    throw new ApiError(
      res.status,
      message,
      data?.issues,
      typeof data?.retryAfterSec === 'number' ? data.retryAfterSec : undefined
    );
  }
  rewriteMedia(data);
=======
    throw new ApiError(res.status, message, data?.issues);
  }
>>>>>>> origin/master
  return data as T;
}

export function multipart(path: string, form: FormData): Promise<unknown> {
  const headers: Record<string, string> = {};
  const token = tokenStore.get();
  if (token) headers['Authorization'] = `Bearer ${token}`;
  return fetch(`${API_ROOT}${path}`, { method: 'POST', headers, body: form }).then(async (res) => {
    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
      if (res.status === 401) onUnauthorized.current?.();
<<<<<<< HEAD
      if (res.status === 403 && (data as any)?.code === 'EMAIL_NOT_VERIFIED') {
        onEmailUnverified.current?.();
      }
=======
>>>>>>> origin/master
      throw new ApiError(res.status, (data as any)?.error || `Upload failed (${res.status})`);
    }
    return data;
  });
}
