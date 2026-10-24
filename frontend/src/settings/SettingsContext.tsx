import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

export type ThemeChoice = 'light' | 'dark' | 'system';

interface Settings {
  theme: ThemeChoice;
  hideBalance: boolean;
  setTheme: (theme: ThemeChoice) => void;
  setHideBalance: (hide: boolean) => void;
}

const STORAGE_KEY = 'credit.settings';

interface Stored {
  theme?: ThemeChoice;
  hideBalance?: boolean;
}

function read(): Stored {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as Stored) : {};
  } catch {
    return {};
  }
}

function write(value: Stored) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(value));
  } catch {
    // хранилище недоступно: настройки действуют до перезагрузки
  }
}

const SettingsContext = createContext<Settings | null>(null);

/** Личные настройки интерфейса: тема и скрытие остатков. Хранятся только в браузере. */
export function SettingsProvider({ children }: { children: ReactNode }) {
  const initial = read();
  const [theme, setTheme] = useState<ThemeChoice>(initial.theme ?? 'system');
  const [hideBalance, setHideBalance] = useState(initial.hideBalance ?? false);

  useEffect(() => {
    const root = document.documentElement;
    if (theme === 'system') {
      root.removeAttribute('data-theme');
    } else {
      root.setAttribute('data-theme', theme);
    }
    write({ theme, hideBalance });
  }, [theme, hideBalance]);

  const value = useMemo(() => ({ theme, hideBalance, setTheme, setHideBalance }), [theme, hideBalance]);
  return <SettingsContext.Provider value={value}>{children}</SettingsContext.Provider>;
}

export function useSettings(): Settings {
  const ctx = useContext(SettingsContext);
  if (!ctx) {
    throw new Error('useSettings вне SettingsProvider');
  }
  return ctx;
}
