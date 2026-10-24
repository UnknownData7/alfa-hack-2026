import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';

const DEMO_ACCOUNTS: Array<[string, string]> = [
  ['romashka', 'ООО «Ромашка Логистик»: автоодобрение и путь до выдачи'],
  ['sokolov', 'ИП Соколов: встречное предложение'],
  ['sever-stroy', 'ООО «Север-Строй»: ручная проверка с залогом'],
  ['kim', 'ИП Ким: запрос данных от банка'],
  ['vector-opt', 'ООО «Вектор-Опт»: автоотказ'],
  ['zarya', 'ООО «Заря»: компания в процессе ликвидации'],
  ['orlova', 'ИП Орлова: овердрафт'],
  ['uw1', 'Андеррайтер банка'],
  ['uw2', 'Второй андеррайтер'],
];

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [loginName, setLoginName] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const user = await login(loginName.trim(), password);
      navigate(user.role === 'UNDERWRITER' ? '/bank' : '/', { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Не удалось войти');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="login-wrap">
      <form className="card login-card" onSubmit={submit}>
        <h1>Вход</h1>
        <p className="muted">Кабинет для предпринимателей и рабочее место сотрудника банка</p>
        <label className="field">
          <span>Логин</span>
          <input value={loginName} onChange={(e) => setLoginName(e.target.value)} autoComplete="username" required />
        </label>
        <label className="field">
          <span>Пароль</span>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
        </label>
        {error && (
          <div className="alert alert-error" role="alert">
            {error}
          </div>
        )}
        <button type="submit" className="btn btn-primary btn-block" disabled={busy}>
          {busy ? 'Входим…' : 'Войти'}
        </button>
      </form>
      <aside className="card demo-accounts">
        <h2>Демо-учётки</h2>
        <p className="muted">
          Пароль для всех: <code>Demo#2026</code>. Нажмите на логин, чтобы подставить его.
        </p>
        <ul>
          {DEMO_ACCOUNTS.map(([name, description]) => (
            <li key={name}>
              <button
                type="button"
                className="link"
                onClick={() => {
                  setLoginName(name);
                  setPassword('Demo#2026');
                }}
              >
                {name}
              </button>
              <span className="muted">{description}</span>
            </li>
          ))}
        </ul>
      </aside>
    </div>
  );
}
