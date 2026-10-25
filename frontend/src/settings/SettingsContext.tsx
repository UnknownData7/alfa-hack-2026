import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

export type ThemeChoice = 'light' | 'dark' | 'system';

interface Settings {
  theme: ThemeChoice;
  hideBalance: boolean;
  /** Помощник сам предлагает подсказку, если заметит затруднение. */
  proactiveHelp: boolean;
  setTheme: (theme: ThemeChoice) => void;
  setHideBalance: (hide: boolean) => void;
  setProactiveHelp: (on: boolean) => void;
}

const STORAGE_KEY = 'credit.settings';

interface Stored {
  theme?: ThemeChoice;
  hideBalance?: boolean;
  proactiveHelp?: boolean;
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

/** Личные настройки интерфейса: тема, скрытие остатков, подсказки помощника. Хранятся только в браузере. */
export function SettingsProvider({ children }: { children: ReactNode }) {
  const initial = read();
  const [theme, setTheme] = useState<ThemeChoice>(initial.theme ?? 'system');
  const [hideBalance, setHideBalance] = useState(initial.hideBalance ?? false);
  const [proactiveHelp, setProactiveHelp] = useState(initial.proactiveHelp ?? true);

  useEffect(() => {
    const root = document.documentElement;
    if (theme === 'system') {
      root.removeAttribute('data-theme');
    } else {
      root.setAttribute('data-theme', theme);
    }
    write({ theme, hideBalance, proactiveHelp });
  }, [theme, hideBalance, proactiveHelp]);

  const value = useMemo(
    () => ({ theme, hideBalance, proactiveHelp, setTheme, setHideBalance, setProactiveHelp }),
    [theme, hideBalance, proactiveHelp],
  );
  return <SettingsContext.Provider value={value}>{children}</SettingsContext.Provider>;
}

export function useSettings(): Settings {
  const ctx = useContext(SettingsContext);
  if (!ctx) {
    throw new Error('useSettings вне SettingsProvider');
  }
  return ctx;
}
