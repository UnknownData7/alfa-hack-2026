import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { api, downloadFile } from '../../api/client';
import { docTypeLabel, formatDateTime, formatMoney, formatMonths, formatPct, formatSize, riskLabel, formatAge } from '../../api/format';
import type { CheckStatus, DecisionResult, RecalculateResponse, TaskCard } from '../../api/types';
import { ErrorBox, FactorTable, KeyValue, Loader, StatusBadge } from '../../components/ui';

const TABS = ['Сводка', 'Компания и финансы', 'Документы', 'Скоринг', 'Проверки', 'История'] as const;
type Tab = (typeof TABS)[number];

const CHECK_LABEL: Record<CheckStatus, string> = {
  PENDING: 'Не проверено',
  OK: 'ОК',
  NEGATIVE: 'Отрицательно',
  NEEDS_INFO: 'Нужны данные',
};

const RESULT_LABEL: Record<DecisionResult, string> = {
  APPROVE: 'Одобрить',
  APPROVE_WITH_CHANGES: 'Одобрить с изменениями',
  REQUEST_INFO: 'Запросить данные',
  DECLINE: 'Отказать',
  POSTPONE: 'Отложить',
};

export function TaskPage() {
  const id = Number(useParams().id);
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<Tab>('Сводка');
  const query = useQuery({ queryKey: ['task', id], queryFn: () => api.task(id), refetchInterval: 5000 });
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  function apply(card: TaskCard) {
    queryClient.setQueryData(['task', id], card);
    queryClient.invalidateQueries({ queryKey: ['tasks'] });
  }

  async function run<T>(action: () => Promise<T>, after?: (r: T) => void) {
    setBusy(true);
    setError(null);
    try {
      const r = await action();
      after?.(r);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }

  if (query.isLoading) return <Loader />;
  if (query.error || !query.data) return <ErrorBox error={query.error} onRetry={() => query.refetch()} />;
  const card = query.data;
  const t = card.task;
  const app = card.application;
  const canTake =
    (t.status === 'NEW' && (t.assigneeId == null || t.mine)) || (t.status === 'POSTPONED' && t.mine);
  const newDocs = card.documents.filter((d) => d.newForBank).length;

  return (
    <div className="stack">
      <Link to="/bank" className="link small">
        ← К очереди
      </Link>
      <div className="card">
        <div className="app-head">
          <div>
            <div className="meta muted small">
              <span>Задача №{t.id}</span>
              <span>Приоритет {t.priority}</span>
              <span>{t.statusLabel}</span>
              {t.repeat && <span>Повторная после ответа клиента</span>}
            </div>
            <h1>
              {app.number}: {card.company.name}
            </h1>
            <div className="meta muted small">
              <span>{app.productName}</span>
              <span>
                {formatMoney(app.requestedAmount)} на {formatMonths(app.requestedTermMonths)}
              </span>
              <span>
                SLA до <span className={t.overdue ? 'overdue' : ''}>{formatDateTime(t.slaDueAt)}</span>
              </span>
            </div>
          </div>
          <div className="head-actions">
            <StatusBadge status={app.status} label={app.statusLabel} />
            {canTake && (
              <button
                type="button"
                className="btn btn-primary"
                disabled={busy}
                onClick={() => run(() => api.takeTask(t.id), apply)}
              >
                {t.status === 'POSTPONED' ? 'Вернуться к задаче' : 'Взять в работу'}
              </button>
            )}
          </div>
        </div>
        {app.decisionSummary && <p className="small">Почему на ручной проверке: {app.decisionSummary}</p>}
        {!card.canAct && t.status === 'IN_PROGRESS' && !t.mine && (
          <div className="alert">Задача в работе у сотрудника {t.assigneeName}. Карточка доступна только для просмотра.</div>
        )}
        {error != null && <ErrorBox error={error} />}
      </div>

      <div className="tabs" role="tablist">
        {TABS.map((name) => (
          <button
            key={name}
            type="button"
            role="tab"
            aria-selected={tab === name}
            className={tab === name ? 'tab active' : 'tab'}
            onClick={() => setTab(name)}
          >
            {name}
            {name === 'Документы' && newDocs > 0 && <span className="badge badge-accent small-badge">новые: {newDocs}</span>}
            {name === 'Проверки' && card.checks.some((c) => c.status === 'PENDING') && (
              <span className="badge badge-warn small-badge">{card.checks.filter((c) => c.status === 'PENDING').length}</span>
            )}
          </button>
        ))}
      </div>

      <div className="grid-task">
        <div className="card">
          {tab === 'Сводка' && <Summary card={card} />}
          {tab === 'Компания и финансы' && <CompanyTab card={card} />}
          {tab === 'Документы' && <DocumentsTab card={card} />}
          {tab === 'Скоринг' && <ScoringTab card={card} />}
          {tab === 'Проверки' && (
            <ChecksTab card={card} busy={busy} onSave={(code, status, comment) => run(() => api.updateCheck(t.id, code, status, comment), apply)} />
          )}
          {tab === 'История' && <HistoryTab card={card} />}
        </div>
        <ActionPanel card={card} busy={busy} run={run} apply={apply} />
      </div>
    </div>
  );
}

function Summary({ card }: { card: TaskCard }) {
  const app = card.application;
  const last = card.scoringRuns[card.scoringRuns.length - 1];
  return (
    <>
      <h2>Сводка</h2>
      <KeyValue
        items={[
          ['Продукт', app.productName],
          ['Цель', app.purposeLabel],
          ['Сумма и срок', `${formatMoney(app.requestedAmount)} на ${formatMonths(app.requestedTermMonths)}`],
          ['Платежи по кредитам (заявлено)', formatMoney(app.monthlyObligationsDeclared)],
          ['Залог', app.collateralDescription || (app.requiresCollateral ? 'не указан' : 'не требуется')],
          ['Последний скоринг', last ? `${last.totalScore} баллов, грейд ${last.grade}, нагрузка ${formatPct(last.paymentLoad * 100)}` : '—'],
          ['Отправлена', formatDateTime(app.submittedAt)],
        ]}
      />
      {card.externalChecks.length > 0 && (
        <>
          <h3>Внешние источники</h3>
          <ul className="plain small">
            {card.externalChecks.map((e) => (
              <li key={e.source}>
                <strong>{e.sourceLabel}:</strong>{' '}
                {e.status === 'UNAVAILABLE' ? 'не ответил' : describePayload(e.source, e.payload)}
              </li>
            ))}
          </ul>
        </>
      )}
      {card.infoRequests.length > 0 && (
        <>
          <h3>Запросы клиенту</h3>
          <ul className="plain small">
            {card.infoRequests.map((r) => (
              <li key={r.id}>
                <span className="muted">{formatDateTime(r.createdAt)}:</span> {r.text}
                <br />
                {r.status === 'ANSWERED' ? <>Ответ: {r.clientAnswer}</> : <em>ждём ответа</em>}
              </li>
            ))}
          </ul>
        </>
      )}
    </>
  );
}

function describePayload(source: string, p: Record<string, unknown>) {
  if (source === 'REGISTRY') return `статус «${p.statusText}»`;
  if (source === 'BUREAU') return `${p.summary}, действующих кредитов: ${p.activeLoans}`;
  if (source === 'TAX') return p.hasDebt ? `задолженность ${formatMoney(Number(p.debtAmount))}` : 'задолженности нет';
  return JSON.stringify(p);
}

function CompanyTab({ card }: { card: TaskCard }) {
  const c = card.company;
  return (
    <>
      <h2>Компания и финансы</h2>
      <KeyValue
        items={[
          ['Наименование', c.name],
          ['ИНН / ОГРН', `${c.inn} / ${c.ogrn}`],
          ['Вид деятельности', `${c.okvedCode} ${c.okvedName}`],
          ['Риск отрасли', riskLabel[c.industryRisk]],
          ['Возраст бизнеса', formatAge(c.businessAgeMonths)],
          ['Сотрудников', String(c.employeesCount)],
          ['Налоговый режим', c.taxSystem],
          ['Клиент банка', formatAge(c.monthsWithBank)],
          ['Оборот за 12 месяцев', formatMoney(c.annualTurnover)],
          ['Средний месячный оборот', formatMoney(c.avgMonthlyTurnover)],
          ['Динамика оборота', formatPct(c.turnoverGrowthPct)],
          ['Платежи по кредитам (данные банка)', formatMoney(c.existingMonthlyObligations)],
        ]}
      />
    </>
  );
}

function DocumentsTab({ card }: { card: TaskCard }) {
  return (
    <>
      <h2>Документы</h2>
      {card.documents.length === 0 && <p className="muted">Клиент не приложил документы</p>}
      <ul className="doc-list">
        {card.documents.map((d) => (
          <li key={d.id}>
            <button
              type="button"
              className="link"
              onClick={() => downloadFile(`/api/bank/applications/${card.application.id}/documents/${d.id}`, d.fileName)}
            >
              {d.fileName}
            </button>
            {d.newForBank && <span className="badge badge-accent small-badge">новое</span>}
            <span className="meta muted small">
              <span>{docTypeLabel[d.docType] ?? d.docType}</span>
              <span>{formatSize(d.sizeBytes)}</span>
              <span>{formatDateTime(d.uploadedAt)}</span>
            </span>
          </li>
        ))}
      </ul>
    </>
  );
}

function ScoringTab({ card }: { card: TaskCard }) {
  const runs = card.scoringRuns.slice().reverse();
  return (
    <>
      <h2>Скоринг</h2>
      {runs.length === 0 && <p className="muted">Скоринг ещё не проводился</p>}
      {runs.map((r) => (
        <details key={r.runNo} open={r.runNo === runs[0].runNo} className="run">
          <summary>
            Запуск {r.runNo} ({r.trigger === 'RECALC' ? 'пересчёт' : 'первичный'}): <strong>{r.totalScore}</strong> баллов, грейд{' '}
            <strong>{r.grade}</strong>, нагрузка {formatPct(r.paymentLoad * 100)}
            {r.hardStop && <span className="badge badge-danger small-badge">жёсткий стоп</span>}
            <span className="muted small run-date">{formatDateTime(r.createdAt)}</span>
          </summary>
          {r.hardStopReason && <div className="alert alert-error">{r.hardStopReason}</div>}
          <FactorTable factors={r.factors} />
          {r.inputs.turnoverConfirmedByUnderwriter === true && (
            <p className="small muted">Оборот подтверждён специалистом: {formatMoney(Number(r.inputs.annualTurnover))} в год</p>
          )}
        </details>
      ))}
    </>
  );
}

function ChecksTab({
  card,
  busy,
  onSave,
}: {
  card: TaskCard;
  busy: boolean;
  onSave: (code: string, status: CheckStatus, comment: string) => void;
}) {
  const [comments, setComments] = useState<Record<string, string>>({});
  return (
    <>
      <h2>Проверки</h2>
      <p className="muted small">
        Решение можно принять, когда заполнены все проверки. Если есть отрицательная, доступны только отказ или встречное
        предложение. Отложить задачу можно в любой момент.
      </p>
      {card.checks.map((c) => (
        <div key={c.code} className={`check-card check-${c.status.toLowerCase()}`}>
          <div className="check-head">
            <strong>{c.title}</strong>
            <span className="badge badge-neutral">{CHECK_LABEL[c.status]}</span>
          </div>
          <p className="small">Цель: {c.goal}</p>
          {c.comment && <p className="small muted">Комментарий: {c.comment}</p>}
          {card.canAct && (
            <>
              <input
                className="input"
                placeholder="Комментарий (для отрицательного результата обязателен)"
                value={comments[c.code] ?? c.comment ?? ''}
                onChange={(e) => setComments((m) => ({ ...m, [c.code]: e.target.value }))}
              />
              <div className="row-actions">
                {(['OK', 'NEEDS_INFO', 'NEGATIVE'] as CheckStatus[]).map((s) => (
                  <button
                    key={s}
                    type="button"
                    className={`btn btn-sm ${c.status === s ? 'btn-primary' : 'btn-ghost'}`}
                    disabled={busy}
                    onClick={() => onSave(c.code, s, comments[c.code] ?? c.comment ?? '')}
                  >
                    {CHECK_LABEL[s]}
                  </button>
                ))}
              </div>
            </>
          )}
        </div>
      ))}
    </>
  );
}

function HistoryTab({ card }: { card: TaskCard }) {
  return (
    <>
      <h2>История</h2>
      <ul className="timeline">
        {card.history
          .slice()
          .reverse()
          .map((h, i) => (
            <li key={i}>
              <div className="timeline-head">
                <strong>{h.toStatusLabel}</strong>
                <span className="muted small">{formatDateTime(h.createdAt)}</span>
              </div>
              <div className="small">
                {h.actorLabel}
                {h.comment ? `: ${h.comment}` : ''}
              </div>
            </li>
          ))}
      </ul>
      {card.decisions.length > 0 && (
        <>
          <h3>Решения специалистов</h3>
          <ul className="plain small">
            {card.decisions.map((d, i) => (
              <li key={i}>
                <span className="muted">{formatDateTime(d.createdAt)}</span> {d.decidedByName}: <strong>{d.resultLabel}</strong>
                {d.comment ? ` (${d.comment})` : ''}
              </li>
            ))}
          </ul>
        </>
      )}
    </>
  );
}

function ActionPanel({
  card,
  busy,
  run,
  apply,
}: {
  card: TaskCard;
  busy: boolean;
  run: <T>(action: () => Promise<T>, after?: (r: T) => void) => Promise<void>;
  apply: (card: TaskCard) => void;
}) {
  const app = card.application;
  const [turnover, setTurnover] = useState('');
  const [amount, setAmount] = useState('');
  const [term, setTerm] = useState('');
  const [recalc, setRecalc] = useState<RecalculateResponse | null>(null);
  const [result, setResult] = useState<DecisionResult | ''>('');
  const [comment, setComment] = useState('');
  const [requestText, setRequestText] = useState('');
  const [counterAmount, setCounterAmount] = useState('');
  const [counterTerm, setCounterTerm] = useState(String(app.requestedTermMonths));

  if (!card.canAct) {
    return (
      <aside className="card action-panel">
        <h2>Действия</h2>
        <p className="muted small">
          {card.task.status === 'DONE'
            ? 'Задача завершена'
            : 'Возьмите задачу в работу, чтобы заполнить проверки, пересчитать скоринг и принять решение'}
        </p>
      </aside>
    );
  }

  const allResults: DecisionResult[] = ['APPROVE', 'APPROVE_WITH_CHANGES', 'REQUEST_INFO', 'DECLINE', 'POSTPONE'];
  const hint = card.checks.some((c) => c.status === 'PENDING')
    ? 'Заполните все проверки, чтобы стали доступны решения. Отложить можно сейчас.'
    : card.checks.some((c) => c.status === 'NEGATIVE')
      ? 'Есть отрицательная проверка: доступны отказ и встречное предложение'
      : card.checks.some((c) => c.status === 'NEEDS_INFO')
        ? 'Есть проверка «нужны данные»: запросите их у клиента'
        : '';

  return (
    <aside className="card action-panel">
      <h2>Пересчёт</h2>
      <p className="muted small">Укажите подтверждённые данные, система пересчитает скоринг и условия</p>
      <label className="field">
        <span>Подтверждённый годовой оборот, ₽</span>
        <input type="number" value={turnover} onChange={(e) => setTurnover(e.target.value)} placeholder={String(card.company.annualTurnover)} />
      </label>
      <div className="form-row">
        <label className="field">
          <span>Сумма, ₽</span>
          <input type="number" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder={String(app.requestedAmount)} />
        </label>
        <label className="field">
          <span>Срок, мес.</span>
          <input type="number" value={term} onChange={(e) => setTerm(e.target.value)} placeholder={String(app.requestedTermMonths)} />
        </label>
      </div>
      <button
        type="button"
        className="btn btn-ghost btn-block"
        disabled={busy}
        onClick={() =>
          run(
            () =>
              api.recalculate(card.task.id, {
                confirmedAnnualTurnover: turnover ? Number(turnover) : undefined,
                amount: amount ? Number(amount) : undefined,
                termMonths: term ? Number(term) : undefined,
              }),
            (r) => {
              setRecalc(r);
              run(() => api.task(card.task.id), apply);
            },
          )
        }
      >
        Пересчитать
      </button>
      {recalc && (
        <div className="recalc">
          <div>
            Запуск {recalc.run.runNo}: <strong>{recalc.run.totalScore}</strong> баллов, грейд <strong>{recalc.run.grade}</strong>
          </div>
          <div className="small">{recalc.indicative.outcomeLabel}</div>
          {recalc.indicative.amount != null && (
            <div className="small">
              {formatMoney(recalc.indicative.amount)} на {formatMonths(recalc.indicative.termMonths ?? 0)}, ставка{' '}
              {formatPct(recalc.indicative.ratePct)}, платёж {formatMoney(recalc.indicative.monthlyPayment)}
            </div>
          )}
        </div>
      )}

      <h2>Решение</h2>
      {hint && <p className="small hint">{hint}</p>}
      <div className="result-options">
        {allResults.map((r) => (
          <label key={r} className={`radio ${card.allowedResults.includes(r) ? '' : 'disabled'}`}>
            <input
              type="radio"
              name="result"
              value={r}
              checked={result === r}
              disabled={!card.allowedResults.includes(r)}
              onChange={() => setResult(r)}
            />
            {RESULT_LABEL[r]}
          </label>
        ))}
      </div>
      {result === 'APPROVE_WITH_CHANGES' && (
        <div className="form-row">
          <label className="field">
            <span>Сумма, ₽</span>
            <input type="number" value={counterAmount} onChange={(e) => setCounterAmount(e.target.value)} />
          </label>
          <label className="field">
            <span>Срок, мес.</span>
            <input type="number" value={counterTerm} onChange={(e) => setCounterTerm(e.target.value)} />
          </label>
        </div>
      )}
      {result === 'REQUEST_INFO' && (
        <label className="field">
          <span>Что запросить у клиента</span>
          <textarea rows={3} value={requestText} onChange={(e) => setRequestText(e.target.value)} />
        </label>
      )}
      {result && (
        <label className="field">
          <span>{result === 'DECLINE' ? 'Причина отказа (обязательно)' : 'Комментарий'}</span>
          <textarea rows={2} value={comment} onChange={(e) => setComment(e.target.value)} />
        </label>
      )}
      <button
        type="button"
        className="btn btn-primary btn-block"
        disabled={busy || !result}
        onClick={() =>
          result &&
          run(
            () =>
              api.decide(card.task.id, {
                result,
                comment: comment || undefined,
                requestText: requestText || undefined,
                counterOffer:
                  result === 'APPROVE_WITH_CHANGES'
                    ? { amount: Number(counterAmount), termMonths: Number(counterTerm) }
                    : undefined,
              }),
            (c) => {
              apply(c);
              setResult('');
              setComment('');
              setRequestText('');
            },
          )
        }
      >
        Сохранить решение
      </button>
      <p className="muted small">
        Границы продукта: {formatMoney(app.productMinAmount)}–{formatMoney(app.productMaxAmount)}, {app.productMinTerm}–
        {app.productMaxTerm} мес.
      </p>
    </aside>
  );
}
