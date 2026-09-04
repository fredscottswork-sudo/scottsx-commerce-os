import {
  createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode,
} from 'react';
import { buyerService } from '../api/services';
import { tokenStore } from '../api/client';

export type ThemeMode = 'light' | 'dark' | 'system';

interface ThemeState {
  mode: ThemeMode;
  resolved: 'light' | 'dark';
  setMode: (m: ThemeMode) => void;
  toggle: () => void;
}

const ThemeContext = createContext<ThemeState | null>(null);
const KEY = 'stx_theme';

function systemPrefersDark(): boolean {
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false;
}

function readStored(): ThemeMode {
  const saved = localStorage.getItem(KEY);
  return saved === 'light' || saved === 'dark' || saved === 'system' ? saved : 'dark';
}

function resolve(mode: ThemeMode): 'light' | 'dark' {
  return mode === 'system' ? (systemPrefersDark() ? 'dark' : 'light') : mode;
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  // Dark (deep blue + black) is the signature default; light flips black→white.
  const [mode, setModeState] = useState<ThemeMode>(readStored);
  const [resolved, setResolved] = useState<'light' | 'dark'>(() => resolve(readStored()));

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', resolved);
    // Keep the browser UI (address bar / form controls) in sync.
    const meta = document.querySelector('meta[name="theme-color"]');
    const color = resolved === 'dark' ? '#0e1420' : '#f4f6fb';
    if (meta) meta.setAttribute('content', color);
  }, [resolved]);

  useEffect(() => {
    const mq = window.matchMedia?.('(prefers-color-scheme: dark)');
    if (!mq) return;
    const onSystem = () => { if (mode === 'system') setResolved(systemPrefersDark() ? 'dark' : 'light'); };
    mq.addEventListener('change', onSystem);
    return () => mq.removeEventListener('change', onSystem);
  }, [mode]);

  const setMode = useCallback((m: ThemeMode) => {
    setModeState(m);
    localStorage.setItem(KEY, m);
    setResolved(resolve(m));
    // Persist to the backend so the phone and the web app agree.
    if (tokenStore.get()) buyerService.savePreferences({ theme: m }).catch(() => undefined);
  }, []);

  const toggle = useCallback(() => {
    setMode(resolve(readStored()) === 'dark' ? 'light' : 'dark');
  }, [setMode]);

  const value = useMemo(() => ({ mode, resolved, setMode, toggle }), [mode, resolved, setMode, toggle]);
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeState {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useTheme must be used within ThemeProvider');
  return ctx;
}
