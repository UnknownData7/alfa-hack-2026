import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Link, NavLink } from 'react-router-dom';
import { AssistantWidget } from '../assistant/AssistantWidget';
import { useAuth } from '../auth/AuthContext';
import { useSettings, type ThemeChoice } from '../settings/SettingsContext';
import { CloseIcon, EyeIcon, EyeOffIcon, GearIcon } from './icons';
import { ProductsColumn } from './ProductsColumn';

const CLIENT_NAV: Array<[string, string, boolean]> = [
  ['/', 'Главная', true],
  ['/need', 'Кредиты', false],
  ['/history', 'История', false],
  ['/benefits', 'Выгода', false],
];

function initials(name: string) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0])
    .join('')
    .toUpperCase();
}

export function Layout({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const { hideBalance, setHideBalance } = useSettings();
  const [settingsOpen, setSettingsOpen] = useState(false);
  const isBank = user?.role === 'UNDERWRITER';
  const isClient = user?.role === 'CLIENT';

  return (
    <div className="app">
      <a href="#main" className="skip-link">
        К содержимому
      </a>
      <header className="topbar">
        <div className="topbar-inner">
          <Link to={isBank ? '/bank' : '/'} className="brand">
            <span className="brand-mark" aria-hidden>
              ₽
            </span>
            <span className="brand-text">
              {isBank ? 'Кредитный конвейер' : 'Кредитный кабинет'}
              <small>{isBank ? 'рабочее место андеррайтера' : 'для малого бизнеса'}</small>
            </span>
          </Link>
          {user && (
            <nav className="pills" aria-label="Разделы">
              {isBank ? (
                <NavLink to="/bank" end>
                  Очередь задач
                </NavLink>
              ) : (
                CLIENT_NAV.map(([to, label, end]) => (
                  <NavLink key={to} to={to} end={end}>
                    {label}
                  </NavLink>
                ))
              )}
            </nav>
          )}
          {user && (
            <div className="top-actions">
              {isClient && (
                <button
                  type="button"
                  className="icon-btn"
                  onClick={() => setHideBalance(!hideBalance)}
                  aria-pressed={hideBalance}
                  title={hideBalance ? 'Показать остатки' : 'Скрыть остатки'}
                >
                  {hideBalance ? <EyeOffIcon /> : <EyeIcon />}
                  <span className="sr-only">{hideBalance ? 'Показать остатки' : 'Скрыть остатки'}</span>
                </button>
              )}
              <button type="button" className="icon-btn" onClick={() => setSettingsOpen(true)} title="Настройки">
                <GearIcon />
                <span className="sr-only">Настройки</span>
              </button>
              <button type="button" className="avatar" onClick={() => setSettingsOpen(true)} title={user.fullName}>
                {initials(user.fullName)}
              </button>
            </div>
          )}
        </div>
      </header>
      {isClient ? (
        <div className="shell">
          <ProductsColumn />
          <main id="main" className="shell-main">
            {children}
          </main>
        </div>
      ) : (
        <main id="main" className={user ? 'container' : 'container narrow'}>
          {children}
        </main>
      )}
      <footer className="footer">
        Прототип для хакатона. Все компании, счета и суммы вымышленные, подписание и зачисление смоделированы.
      </footer>
      {settingsOpen && <SettingsDrawer onClose={() => setSettingsOpen(false)} />}
      {isClient && <AssistantWidget />}
    </div>
  );
}

const THEMES: Array<[ThemeChoice, string]> = [
  ['light', 'Светлая'],
  ['dark', 'Тёмная'],
  ['system', 'Как в системе'],
];

function SettingsDrawer({ onClose }: { onClose: () => void }) {
  const { user, logout } = useAuth();
  const { theme, setTheme, hideBalance, setHideBalance } = useSettings();
  const panel = useRef<HTMLDivElement>(null);

  useEffect(() => {
    panel.current?.focus();
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  return (
    <div className="drawer-backdrop" onClick={onClose}>
      <div
        className="drawer"
        role="dialog"
        aria-modal="true"
        aria-labelledby="settings-title"
        tabIndex={-1}
        ref={panel}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="drawer-head">
          <h2 id="settings-title">Настройки</h2>
          <button type="button" className="icon-btn" onClick={onClose}>
            <CloseIcon />
            <span className="sr-only">Закрыть</span>
          </button>
        </div>
        {user && (
          <div className="drawer-user">
            <span className="avatar static">{initials(user.fullName)}</span>
            <div>
              <strong>{user.fullName}</strong>
              <div className="muted small">{user.role === 'UNDERWRITER' ? 'Сотрудник банка' : 'Владелец бизнеса'}</div>
            </div>
          </div>
        )}
        <fieldset className="setting">
          <legend>Тема оформления</legend>
          <div className="segmented">
            {THEMES.map(([value, label]) => (
              <label key={value} className={theme === value ? 'on' : ''}>
                <input type="radio" name="theme" value={value} checked={theme === value} onChange={() => setTheme(value)} />
                {label}
              </label>
            ))}
          </div>
        </fieldset>
        {user?.role === 'CLIENT' && (
          <label className="setting toggle-row">
            <span>
              <strong>Скрывать остатки</strong>
              <span className="muted small">Суммы на счетах заменяются точками, например при показе экрана</span>
            </span>
            <input
              type="checkbox"
              role="switch"
              className="switch"
              checked={hideBalance}
              onChange={(e) => setHideBalance(e.target.checked)}
            />
          </label>
        )}
        <p className="muted small">Настройки сохраняются только в этом браузере</p>
        <button type="button" className="btn btn-ghost btn-block" onClick={logout}>
          Выйти
        </button>
      </div>
    </div>
  );
}
