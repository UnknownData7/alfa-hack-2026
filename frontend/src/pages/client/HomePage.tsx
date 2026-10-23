import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '../../api/client';
import { formatAge, formatDateTime, formatMoney, formatMonths } from '../../api/format';
import { Empty, ErrorBox, Loader, StatusBadge } from '../../components/ui';

export function HomePage() {
  const profile = useQuery({ queryKey: ['profile'], queryFn: api.profile });
  const applications = useQuery({ queryKey: ['applications'], queryFn: api.applications, refetchInterval: 5000 });
  const feed = useQuery({ queryKey: ['feed'], queryFn: api.feed, refetchInterval: 5000 });
  const queryClient = useQueryClient();
  const markRead = useMutation({
    mutationFn: (id: number) => api.markFeedRead(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['feed'] }),
  });

  return (
    <div className="grid-home">
      <section className="stack">
        {profile.isLoading && <Loader />}
        {profile.error && <ErrorBox error={profile.error} onRetry={() => profile.refetch()} />}
        {profile.data && (
          <div className="card company-card">
            <div className="company-head">
              <div>
                <div className="muted small">{profile.data.legalFormLabel}</div>
                <h1>{profile.data.name}</h1>
                <div className="muted small">ИНН {profile.data.inn}</div>
              </div>
              <Link to="/need" className="btn btn-primary">
                Нужно финансирование
              </Link>
            </div>
            <div className="stats">
              <div>
                <span className="muted small">Бизнес работает</span>
                <strong>{formatAge(profile.data.businessAgeMonths)}</strong>
              </div>
              <div>
                <span className="muted small">Оборот за 12 месяцев</span>
                <strong>{formatMoney(profile.data.annualTurnover)}</strong>
              </div>
              <div>
                <span className="muted small">В среднем в месяц</span>
                <strong>{formatMoney(profile.data.avgMonthlyTurnover)}</strong>
              </div>
              <div>
                <span className="muted small">Счёт в банке</span>
                <strong>{profile.data.accountMasked}</strong>
              </div>
            </div>
            <p className="muted small">
              Эти данные банк уже знает о вашей компании, вводить их в заявке не нужно.
            </p>
          </div>
        )}

        <div className="card">
          <h2>Мои заявки</h2>
          {applications.isLoading && <Loader />}
          {applications.error && <ErrorBox error={applications.error} onRetry={() => applications.refetch()} />}
          {applications.data && applications.data.length === 0 && (
            <Empty title="Заявок пока нет">Начните с кнопки «Нужно финансирование».</Empty>
          )}
          {applications.data && applications.data.length > 0 && (
            <ul className="app-list">
              {applications.data.map((a) => (
                <li key={a.id}>
                  <Link to={a.status === 'DRAFT' ? `/applications/${a.id}/edit` : `/applications/${a.id}`}>
                    <div className="app-row">
                      <div>
                        <strong>{a.productName}</strong>
                        <div className="muted small">
                          {a.number} · {formatMoney(a.requestedAmount)} на {formatMonths(a.requestedTermMonths)}
                        </div>
                      </div>
                      <StatusBadge status={a.status} label={a.statusLabel} />
                    </div>
                    <div className="small next-hint">
                      {a.nextStep.title} · обновлено {formatDateTime(a.updatedAt)}
                    </div>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </div>
      </section>

      <aside className="card feed">
        <h2>Для вас</h2>
        {feed.isLoading && <Loader />}
        {feed.error && <ErrorBox error={feed.error} onRetry={() => feed.refetch()} />}
        {feed.data && feed.data.length === 0 && <Empty title="Пока ничего нового" />}
        {feed.data?.map((card) => (
          <article key={card.id} className={`feed-card feed-${card.type.toLowerCase()} ${card.read ? 'read' : ''}`}>
            <div className="feed-type">
              {card.type === 'STATUS' ? 'Событие' : card.type === 'TIP' ? 'Подсказка' : 'Новость'}
            </div>
            <h3>{card.title}</h3>
            <p>{card.body}</p>
            <div className="feed-reason">{card.reason}</div>
            <div className="feed-actions">
              {card.relatedApplicationId && (
                <Link to={`/applications/${card.relatedApplicationId}`} className="link small">
                  Открыть заявку
                </Link>
              )}
              {card.type === 'STATUS' && !card.read && (
                <button
                  type="button"
                  className="link small"
                  onClick={() => markRead.mutate(Number(card.id.replace('event-', '')))}
                >
                  Прочитано
                </button>
              )}
            </div>
          </article>
        ))}
      </aside>
    </div>
  );
}
