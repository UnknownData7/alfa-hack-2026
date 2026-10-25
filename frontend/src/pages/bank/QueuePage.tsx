import { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { api } from '../../api/client';
import { formatDateTime, formatMoney, formatMonths, statusLabel } from '../../api/format';
import { Empty, ErrorBox, Loader, StatusBadge } from '../../components/ui';

const FILTERS: Array<[string, string]> = [
  ['active', 'Все активные'],
  ['mine', 'Мои'],
  ['new', 'Новые'],
  ['postponed', 'Отложенные'],
  ['done', 'Завершённые'],
];

export function QueuePage() {
  const navigate = useNavigate();
  const [filter, setFilter] = useState('active');
  const tasks = useQuery({ queryKey: ['tasks', filter], queryFn: () => api.tasks(filter), refetchInterval: 3000 });
  const takeNext = useMutation({
    mutationFn: api.takeNext,
    onSuccess: (card) => navigate(`/bank/tasks/${card.task.id}`),
  });

  return (
    <div className="card">
      <div className="queue-head">
        <h1>Очередь задач</h1>
        <button type="button" className="btn btn-primary" onClick={() => takeNext.mutate()} disabled={takeNext.isPending}>
          {takeNext.isPending ? 'Берём…' : 'Взять следующую задачу'}
        </button>
      </div>
      <p className="muted small">
        Задачи выдаются по приоритету (1 означает самый высокий), затем по времени создания. В работе может быть только одна
        задача; повторные задачи после ответа клиента возвращаются тому же сотруднику.
      </p>
      {takeNext.error && <ErrorBox error={takeNext.error} />}
      <div className="tabs" role="tablist">
        {FILTERS.map(([key, label]) => (
          <button
            key={key}
            type="button"
            role="tab"
            aria-selected={filter === key}
            className={filter === key ? 'tab active' : 'tab'}
            onClick={() => setFilter(key)}
          >
            {label}
          </button>
        ))}
      </div>
      {tasks.isLoading && <Loader />}
      {tasks.error && <ErrorBox error={tasks.error} onRetry={() => tasks.refetch()} />}
      {tasks.data && tasks.data.length === 0 && <Empty title="Задач нет" />}
      {tasks.data && tasks.data.length > 0 && (
        <table className="table queue">
          <thead>
            <tr>
              <th>Заявка</th>
              <th>Компания</th>
              <th>Продукт</th>
              <th className="num">Сумма</th>
              <th className="num">Приоритет</th>
              <th>SLA</th>
              <th>Статус</th>
              <th>Исполнитель</th>
            </tr>
          </thead>
          <tbody>
            {tasks.data.map((t) => (
              <tr key={t.id} className="clickable" onClick={() => navigate(`/bank/tasks/${t.id}`)}>
                <td>
                  <strong>{t.applicationNumber}</strong>
                  {t.repeat && <span className="badge badge-warn small-badge">повторная</span>}
                  <div className="meta muted small">
                    <span>Грейд {t.grade ?? 'нет'}</span>
                    <StatusBadge status={t.applicationStatus} label={statusLabel[t.applicationStatus]} />
                  </div>
                </td>
                <td>{t.companyName}</td>
                <td>{t.productName}</td>
                <td className="num">
                  {formatMoney(t.amount)}
                  <div className="muted small">{formatMonths(t.termMonths)}</div>
                </td>
                <td className="num">
                  <span className={`prio prio-${t.priority <= 2 ? 'high' : t.priority <= 5 ? 'mid' : 'low'}`}>{t.priority}</span>
                </td>
                <td className={t.overdue ? 'overdue' : ''}>
                  {formatDateTime(t.slaDueAt)}
                  {t.overdue && <div className="small">просрочено</div>}
                </td>
                <td>{t.statusLabel}</td>
                <td>{t.mine ? 'Вы' : t.assigneeName ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
