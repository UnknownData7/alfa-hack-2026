import type { ReactNode } from 'react';
import { Link, NavLink } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export function Layout({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth();
  const isBank = user?.role === 'UNDERWRITER';
  return (
    <div className="app">
      <header className="topbar">
        <div className="topbar-inner">
          <Link to="/" className="brand">
            <span className="brand-mark" aria-hidden>
              ₽
            </span>
            {isBank ? 'Рабочее место андеррайтера' : 'Кредитный кабинет'}
          </Link>
          {user && (
            <nav className="nav">
              {isBank ? (
                <NavLink to="/bank" end>
                  Очередь задач
                </NavLink>
              ) : (
                <>
                  <NavLink to="/" end>
                    Главная
                  </NavLink>
                  <NavLink to="/need">Нужно финансирование</NavLink>
                </>
              )}
              <span className="nav-user">{user.fullName}</span>
              <button type="button" className="btn btn-ghost btn-sm" onClick={logout}>
                Выйти
              </button>
            </nav>
          )}
        </div>
      </header>
      <main className="container">{children}</main>
      <footer className="footer">
        Прототип для хакатона. Все данные синтетические, подписание и выдача смоделированы.
      </footer>
    </div>
  );
}
