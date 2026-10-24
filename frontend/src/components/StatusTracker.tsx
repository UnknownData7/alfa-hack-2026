import type { ApplicationStatus, HistoryEntry } from '../api/types';

interface Step {
  key: string;
  label: string;
  statuses: ApplicationStatus[];
}

const BASE_STEPS: Step[] = [
  { key: 'draft', label: 'Черновик', statuses: ['DRAFT'] },
  { key: 'submitted', label: 'Подана', statuses: ['SUBMITTED', 'NEED_INFO'] },
  { key: 'scoring', label: 'Проверка', statuses: ['SCORING'] },
  { key: 'review', label: 'Рассмотрение', statuses: ['MANUAL_REVIEW'] },
  { key: 'offer', label: 'Предложение', statuses: ['OFFER_MADE'] },
  { key: 'signing', label: 'Подписание', statuses: ['SIGNING'] },
  { key: 'issued', label: 'Выдан', statuses: ['ISSUED'] },
];

/**
 * Шаги заявки с подсветкой текущего. Ручная проверка показывается, только если заявка через неё проходила;
 * для отказа и закрытия последний шаг заменяется итогом.
 */
export function StatusTracker({ status, history }: { status: ApplicationStatus; history: HistoryEntry[] }) {
  const visited = new Set<ApplicationStatus>(history.map((h) => h.toStatus));
  visited.add(status);
  let steps = BASE_STEPS.filter((s) => s.key !== 'review' || visited.has('MANUAL_REVIEW'));
  if (status === 'DECLINED' || status === 'CLOSED') {
    const lastReached = steps.reduce((idx, s, i) => (s.statuses.some((x) => visited.has(x)) ? i : idx), 0);
    steps = [
      ...steps.slice(0, lastReached + 1),
      { key: 'final', label: status === 'DECLINED' ? 'Отказ' : 'Закрыта', statuses: [status] },
    ];
  }
  const currentIndex = steps.findIndex((s) => s.statuses.includes(status));
  const needInfo = status === 'NEED_INFO';

  return (
    <ol className="tracker" aria-label="Этапы заявки">
      {steps.map((s, i) => {
        const state = i < currentIndex ? 'done' : i === currentIndex ? 'current' : 'todo';
        const tone = s.key === 'final' && status === 'DECLINED' ? ' danger' : '';
        return (
          <li key={s.key} className={`tracker-step ${state}${tone}`} aria-current={state === 'current' ? 'step' : undefined}>
            <span className="tracker-dot" aria-hidden>
              {state === 'done' ? '✓' : i + 1}
            </span>
            <span className="tracker-label">
              {s.label}
              {state === 'current' && needInfo && s.key === 'submitted' && <em>нужны данные</em>}
            </span>
          </li>
        );
      })}
    </ol>
  );
}
