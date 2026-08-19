import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { rememberDevCode } from '../lib/devCode';
import { authService } from '../api/services';
import { forgetGoogleSession } from '../lib/google';
import { tokenStore, userStore, onUnauthorized, type StoredUser } from '../api/client';

interface AuthState {
  user: StoredUser | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  loginWithGoogle: (idToken: string) => Promise<StoredUser>;
  loginWithFirebase: (
    idToken: string,
    profile?: { displayName?: string; phone?: string; role?: string; storeName?: string }
  ) => Promise<StoredUser>;
  register: (body: { email: string; password: string; displayName: string; phone?: string; role?: string })
    => Promise<{ required: boolean; sent: boolean; devCode?: string } | undefined>;
  logout: () => void;
  refresh: () => Promise<void>;
  setUser: (u: StoredUser) => void;
}

const AuthContext = createContext<AuthState | null>(null);

function toStoredUser(u: any): StoredUser {
  return {
    id: u.id,
    email: u.email,
    displayName: u.displayName ?? u.display_name ?? '',
    phone: u.phone ?? '',
    role: u.role ?? 'buyer',
    emailVerified: !!u.emailVerified,
    profilePhotoUrl: u.profilePhotoUrl ?? u.profile_photo_url ?? null,
    city: u.city ?? '',
    createdAt: u.createdAt ?? u.created_at ?? undefined,
  };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUserState] = useState<StoredUser | null>(() => userStore.get());
  const [loading, setLoading] = useState(false);

  const setUser = useCallback((u: StoredUser) => {
    setUserState(u);
    userStore.set(u);
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    setLoading(true);
    try {
      const res = await authService.login(email, password);
      if (!res?.token) throw new Error('The server did not return a session. Please try again.');
      tokenStore.set(res.token);
      setUser(toStoredUser(res.user));
    } finally {
      setLoading(false);
    }
  }, [setUser]);

  /**
   * Exchange a Firebase ID token for our own session.
   *
   * Used by every Firebase-backed path: Google popup, email sign-in, and the
   * re-check after a user clicks the verification link.
   */
  const loginWithFirebase = useCallback(
    async (
      idToken: string,
      profile?: { displayName?: string; phone?: string; role?: string; storeName?: string }
    ) => {
      setLoading(true);
      try {
        const res = await authService.firebase(idToken, profile);
        if (!res?.token) throw new Error('The server did not return a session. Please try again.');
        tokenStore.set(res.token);
        const stored = toStoredUser(res.user);
        setUser(stored);
        return stored;
      } finally {
        setLoading(false);
      }
    },
    [setUser]
  );

  const loginWithGoogle = useCallback(
    async (idToken: string) => {
      setLoading(true);
      try {
        const res = await authService.google(idToken);
        if (!res?.token) throw new Error('The server did not return a session. Please try again.');
        tokenStore.set(res.token);
        const stored = toStoredUser(res.user);
        setUser(stored);
        return stored;
      } finally {
        setLoading(false);
      }
    },
    [setUser]
  );

  const register = useCallback(
    async (body: { email: string; password: string; displayName: string; phone?: string; role?: string }) => {
      setLoading(true);
      try {
        const res = await authService.register(body);
        if (!res?.token) throw new Error('The server did not return a session. Please try again.');
        tokenStore.set(res.token);
        setUser(toStoredUser(res.user));
        // With no SMTP configured the API hands back the code so the flow is
        // still completable; the banner reads it from sessionStorage.
        rememberDevCode(res.verification?.devCode);
        return res.verification;
      } finally {
        setLoading(false);
      }
    },
    [setUser]
  );

  const logout = useCallback(() => {
    forgetGoogleSession();
    tokenStore.clear();
    userStore.set(null);
    setUserState(null);
  }, []);

  const refresh = useCallback(async () => {
    try {
      const res = await authService.me();
      setUser(toStoredUser(res.user));
    } catch {
      /* 401 handled globally */
    }
  }, [setUser]);

  const value = useMemo(
    () => ({ user, loading, login, loginWithGoogle, loginWithFirebase, register, logout, refresh, setUser }),
    [user, loading, login, loginWithGoogle, loginWithFirebase, register, logout, refresh, setUser]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}

// Wire the global 401 handler: clear session.
onUnauthorized.current = () => {
  tokenStore.clear();
  userStore.set(null);
  window.dispatchEvent(new CustomEvent('stx:unauthorized'));
};
