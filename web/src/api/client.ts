/**
 * ScottsTechX web — API client.
 * One fetch wrapper, one token source, normalized errors. Every page uses this.
 */

// Trailing slashes are stripped: a VITE_API_URL of "https://api.example.com/"
// would otherwise build "https://api.example.com//api/v1", which the backend
// answers with 404 on every single request. Easy to type, painful to diagnose.
const BASE = ((import.meta.env.VITE_API_URL as string | undefined) || '').replace(/\/+$/, '');
export const API_ROOT = `${BASE}/api/v1`;

const TOKEN_KEY = 'stx_token';
const USER_KEY = 'stx_user';

export class ApiError extends Error {
  status: number;
  issues?: unknown[];
  constructor(status: number, message: string, issues?: unknown[]) {
    super(message);
    this.status = status;
    this.issues = issues;
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

interface RequestOptions {
  method?: string;
  body?: unknown;
  auth?: boolean;
  rawBody?: boolean;
}

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

  let data: any = null;
  try {
    data = await res.json();
  } catch {
    /* empty body */
  }

  if (!res.ok) {
    if (res.status === 401 && auth) onUnauthorized.current?.();
    const message =
      (data && (data.error as string)) ||
      (data && data.message as string) ||
      `Request failed (${res.status})`;
    throw new ApiError(res.status, message, data?.issues);
  }
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
      throw new ApiError(res.status, (data as any)?.error || `Upload failed (${res.status})`);
    }
    return data;
  });
}
