import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { authService } from '../api/services';
import { tokenStore, userStore, onUnauthorized, type StoredUser } from '../api/client';

interface AuthState {
  user: StoredUser | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (body: { email: string; password: string; displayName: string; phone?: string; role?: string }) => Promise<void>;
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
      tokenStore.set(res.token);
      setUser(toStoredUser(res.user));
    } finally {
      setLoading(false);
    }
  }, [setUser]);

  const register = useCallback(
    async (body: { email: string; password: string; displayName: string; phone?: string; role?: string }) => {
      setLoading(true);
      try {
        const res = await authService.register(body);
        tokenStore.set(res.token);
        setUser(toStoredUser(res.user));
      } finally {
        setLoading(false);
      }
    },
    [setUser]
  );

  const logout = useCallback(() => {
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
    () => ({ user, loading, login, register, logout, refresh, setUser }),
    [user, loading, login, register, logout, refresh, setUser]
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
