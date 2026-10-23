import { useEffect, useState } from 'react';

type BackendState = 'loading' | 'up' | 'down';

export default function App() {
  const [backend, setBackend] = useState<BackendState>('loading');

  useEffect(() => {
    fetch('/actuator/health')
      .then((r) => r.json())
      .then((body) => setBackend(body.status === 'UP' ? 'up' : 'down'))
      .catch(() => setBackend('down'));
  }, []);

  const label = {
    loading: 'проверяем…',
    up: 'работает',
    down: 'недоступен',
  }[backend];

  return (
    <main className="stub">
      <h1>Кредитный кабинет предпринимателя</h1>
      <p>Каркас приложения. Интерфейс появится на следующих этапах.</p>
      <p className={`badge badge-${backend}`}>Сервер: {label}</p>
    </main>
  );
}
