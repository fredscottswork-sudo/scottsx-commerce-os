import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { buyerService } from '../api/services';
import { tokenStore } from '../api/client';

type ThemeMode = 'light' | 'dark' | 'system';

interface ThemeState {
  mode: ThemeMode;
  resolved: 'light' | 'dark';
  setMode: (m: ThemeMode) => void;
}

const ThemeContext = createContext<ThemeState | null>(null);
const KEY = 'stx_theme';

function systemPrefersDark(): boolean {
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false;
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [mode, setModeState] = useState<ThemeMode>(() => {
    const saved = localStorage.getItem(KEY);
    return saved === 'light' || saved === 'dark' || saved === 'system' ? (saved as ThemeMode) : 'system';
  });
  const [resolved, setResolved] = useState<'light' | 'dark'>(
    mode === 'system' ? (systemPrefersDark() ? 'dark' : 'light') : mode
  );

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', resolved);
  }, [resolved]);

  useEffect(() => {
    const onSystem = () => {
      if (mode === 'system') setResolved(systemPrefersDark() ? 'dark' : 'light');
    };
    window.matchMedia?.('(prefers-color-scheme: dark)').addEventListener('change', onSystem);
    return () => window.matchMedia?.('(prefers-color-scheme: dark)').removeEventListener('change', onSystem);
  }, [mode]);

  const setMode = useCallback((m: ThemeMode) => {
    setModeState(m);
    localStorage.setItem(KEY, m);
    setResolved(m === 'system' ? (systemPrefersDark() ? 'dark' : 'light') : m);
    // Persist to the backend when signed in (shared with mobile).
    if (tokenStore.get()) {
      buyerService.savePreferences({ theme: m }).catch(() => undefined);
    }
  }, []);

  const value = useMemo(() => ({ mode, resolved, setMode }), [mode, resolved, setMode]);
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeState {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useTheme must be used within ThemeProvider');
  return ctx;
}
