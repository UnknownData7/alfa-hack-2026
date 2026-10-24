import type { ReactNode } from 'react';
import { ApiError } from '../api/client';
import { formatMoney } from '../api/format';
import type { ApplicationStatus, Factor } from '../api/types';
import { useSettings } from '../settings/SettingsContext';

export function Loader({ text = 'Загружаем…' }: { text?: string }) {
  return (
    <div className="loader" role="status">
      <span className="spinner" aria-hidden />
      {text}
    </div>
  );
}

export function ErrorBox({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const message = error instanceof ApiError ? error.message : 'Что-то пошло не так. Повторите попытку';
  return (
    <div className="alert alert-error" role="alert">
      <span>{message}</span>
      {onRetry && (
        <button type="button" className="btn btn-ghost btn-sm" onClick={onRetry}>
          Повторить
        </button>
      )}
    </div>
  );
}

export function Empty({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="empty">
      <strong>{title}</strong>
      {children && <div>{children}</div>}
    </div>
  );
}

const statusTone: Record<ApplicationStatus, string> = {
  DRAFT: 'neutral',
  SUBMITTED: 'info',
  SCORING: 'info',
  NEED_INFO: 'warn',
  MANUAL_REVIEW: 'info',
  OFFER_MADE: 'accent',
  SIGNING: 'accent',
  ISSUED: 'ok',
  DECLINED: 'danger',
  CLOSED: 'neutral',
};

export function StatusBadge({ status, label }: { status: ApplicationStatus; label: string }) {
  return <span className={`badge badge-${statusTone[status]}`}>{label}</span>;
}

/** Термин с подсказкой простыми словами. */
export function Term({ children, hint }: { children: ReactNode; hint: string }) {
  return (
    <span className="term" title={hint} tabIndex={0}>
      {children}
    </span>
  );
}

export function FactorTable({ factors, compact = false }: { factors: Factor[]; compact?: boolean }) {
  if (factors.length === 0) {
    return null;
  }
  return (
    <table className="table factors">
      <thead>
        <tr>
          <th>Что учитывали</th>
          <th>Значение</th>
          <th className="num">Влияние</th>
          {!compact && <th>Пояснение</th>}
        </tr>
      </thead>
      <tbody>
        {factors.map((f) => (
          <tr key={f.code}>
            <td>{f.title}</td>
            <td>{f.value}</td>
            <td className={`num points ${f.points > 0 ? 'pos' : f.points < 0 ? 'neg' : ''}`}>
              {f.points > 0 ? `+${f.points}` : f.points}
            </td>
            {!compact && <td className="muted">{f.comment}</td>}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export function KeyValue({ items }: { items: Array<[string, ReactNode]> }) {
  return (
    <dl className="kv">
      {items.map(([k, v]) => (
        <div key={k}>
          <dt>{k}</dt>
          <dd>{v}</dd>
        </div>
      ))}
    </dl>
  );
}

/** Сумма в рублях. Для остатков (sensitive) учитывается настройка «Скрывать остатки». */
export function Money({ value, sensitive = false, className }: { value?: number | null; sensitive?: boolean; className?: string }) {
  const { hideBalance } = useSettings();
  const hidden = sensitive && hideBalance;
  return (
    <span className={`money ${className ?? ''}`} aria-label={hidden ? 'Сумма скрыта' : undefined}>
      {hidden ? '••• ₽' : formatMoney(value)}
    </span>
  );
}
