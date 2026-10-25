import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '../../api/client';
import type { Activity } from '../../api/types';
import { Empty, ErrorBox, Loader, Money } from '../../components/ui';

const dayFormat = new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long', year: 'numeric' });
const timeFormat = new Intl.DateTimeFormat('ru-RU', { hour: '2-digit', minute: '2-digit' });

function dayLabel(iso: string) {
  const d = new Date(iso);
  const today = new Date();
  const yesterday = new Date();
  yesterday.setDate(today.getDate() - 1);
  if (d.toDateString() === today.toDateString()) return 'Сегодня';
  if (d.toDateString() === yesterday.toDateString()) return 'Вчера';
  return dayFormat.format(d);
}

export function groupByDay(items: Activity[]) {
  const groups: Array<{ day: string; items: Activity[] }> = [];
  for (const item of items) {
    const day = dayLabel(item.createdAt);
    const last = groups[groups.length - 1];
    if (last && last.day === day) {
      last.items.push(item);
    } else {
      groups.push({ day, items: [item] });
    }
  }
  return groups;
}

export function HistoryPage() {
  const activity = useQuery({ queryKey: ['activity'], queryFn: api.activity, refetchInterval: 10000 });

  return (
    <div className="stack">
      <h1>История</h1>
      {activity.isLoading && <Loader />}
      {activity.error && <ErrorBox error={activity.error} onRetry={() => activity.refetch()} />}
      {activity.data && activity.data.length === 0 && (
        <div className="card">
          <Empty title="Операций пока нет">Здесь появятся зачисления кредитов и события по заявкам</Empty>
        </div>
      )}
      {activity.data &&
        groupByDay(activity.data).map((group) => (
          <section key={group.day} className="card history-day">
            <h2>{group.day}</h2>
            <ul className="ops">
              {group.items.map((op, i) => (
                <li key={`${op.applicationId}-${op.createdAt}-${i}`} className={`op op-${op.type.toLowerCase()}`}>
                  <span className="op-icon" aria-hidden>
                    {op.type === 'CREDIT' ? '+' : ''}
                  </span>
                  <Link to={`/applications/${op.applicationId}`} className="op-main">
                    <strong>{op.title}</strong>
                    <span className="muted small">{op.description}</span>
                  </Link>
                  <span className="op-side">
                    {op.amount != null && <Money value={op.amount} sensitive className="op-amount" />}
                    <time dateTime={op.createdAt} className="muted small">
                      {timeFormat.format(new Date(op.createdAt))}
                    </time>
                  </span>
                </li>
              ))}
            </ul>
          </section>
        ))}
    </div>
  );
}
