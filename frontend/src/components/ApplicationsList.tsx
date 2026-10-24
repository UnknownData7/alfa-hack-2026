import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { formatDateTime, formatMoney, formatMonths } from '../api/format';
import { Empty, ErrorBox, Loader, StatusBadge } from './ui';

/** Список заявок клиента со статусом и следующим шагом. */
export function ApplicationsList({ title, limit }: { title: string; limit?: number }) {
  const applications = useQuery({ queryKey: ['applications'], queryFn: api.applications, refetchInterval: 5000 });
  const items = limit ? applications.data?.slice(0, limit) : applications.data;

  return (
    <section className="card">
      <div className="section-head">
        <h2>{title}</h2>
        {limit && applications.data && applications.data.length > limit && (
          <Link to="/need" className="link small">
            Все заявки
          </Link>
        )}
      </div>
      {applications.isLoading && <Loader />}
      {applications.error && <ErrorBox error={applications.error} onRetry={() => applications.refetch()} />}
      {items && items.length === 0 && (
        <Empty title="Заявок пока нет">Подберите продукт в разделе «Кредиты», это займёт пару минут.</Empty>
      )}
      {items && items.length > 0 && (
        <ul className="app-list">
          {items.map((a) => (
            <li key={a.id}>
              <Link to={a.status === 'DRAFT' ? `/applications/${a.id}/edit` : `/applications/${a.id}`}>
                <div className="app-row">
                  <div>
                    <strong>{a.productName}</strong>
                    <div className="muted small">
                      {formatMoney(a.requestedAmount)} на {formatMonths(a.requestedTermMonths)}, заявка {a.number}
                    </div>
                  </div>
                  <StatusBadge status={a.status} label={a.statusLabel} />
                </div>
                <div className="next-hint small">
                  <span>{a.nextStep.title}</span>
                  <time dateTime={a.updatedAt} className="muted">
                    {formatDateTime(a.updatedAt)}
                  </time>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
