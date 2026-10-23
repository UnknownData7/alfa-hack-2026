import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { api, downloadFile } from '../../api/client';
import { docTypeLabel, formatDateTime, formatMoney, formatMonths, formatPct, formatSize } from '../../api/format';
import type { ApplicationDetail, InfoRequest } from '../../api/types';
import { StatusTracker } from '../../components/StatusTracker';
import { ErrorBox, FactorTable, KeyValue, Loader, StatusBadge, Term } from '../../components/ui';

const ACTIVE_STATUSES = new Set(['SUBMITTED', 'SCORING', 'MANUAL_REVIEW', 'NEED_INFO', 'OFFER_MADE', 'SIGNING']);

export function ApplicationPage() {
  const id = Number(useParams().id);
  const queryClient = useQueryClient();
  const query = useQuery({
    queryKey: ['application', id],
    queryFn: () => api.application(id),
    // Статус меняется асинхронно и действиями банка, поэтому страница опрашивает сервер
    refetchInterval: (q) => (q.state.data && !ACTIVE_STATUSES.has(q.state.data.status) ? false : 3000),
  });

  function refresh(updated?: ApplicationDetail) {
    if (updated) {
      queryClient.setQueryData(['application', id], updated);
    }
    queryClient.invalidateQueries({ queryKey: ['application', id] });
    queryClient.invalidateQueries({ queryKey: ['applications'] });
    queryClient.invalidateQueries({ queryKey: ['feed'] });
  }

  if (query.isLoading) return <Loader />;
  if (query.error || !query.data) return <ErrorBox error={query.error} onRetry={() => query.refetch()} />;
  const a = query.data;
  const openRequests = a.infoRequests.filter((r) => r.status === 'OPEN');

  return (
    <div className="stack">
      <div className="card">
        <div className="app-head">
          <div>
            <div className="muted small">
              Заявка {a.number} · {a.productName}
            </div>
            <h1>
              {formatMoney(a.requestedAmount)} на {formatMonths(a.requestedTermMonths)}
            </h1>
            <div className="muted small">{a.purposeLabel}</div>
          </div>
          <StatusBadge status={a.status} label={a.statusLabel} />
        </div>
        <StatusTracker status={a.status} history={a.history} />
        <div className={`next-step next-${a.nextStep.action.toLowerCase()}`}>
          <strong>Что дальше: {a.nextStep.title}</strong>
          <p>{a.nextStep.text}</p>
          {a.nextStep.action === 'WAIT' && <span className="spinner small" aria-hidden />}
          {a.nextStep.action === 'NEW_APPLICATION' && (
            <Link to={`/applications/new?from=${a.id}`} className="btn btn-ghost btn-sm">
              Подать новую заявку на основе этой
            </Link>
          )}
          {a.nextStep.action === 'CONTINUE_DRAFT' && (
            <Link to={`/applications/${a.id}/edit`} className="btn btn-primary btn-sm">
              Продолжить заполнение
            </Link>
          )}
        </div>
      </div>

      {openRequests.map((r) => (
        <AnswerForm key={r.id} application={a} request={r} onDone={refresh} />
      ))}

      {a.offer && (a.status === 'OFFER_MADE' || a.status === 'SIGNING' || a.status === 'ISSUED' || a.status === 'CLOSED') && (
        <OfferBlock application={a} onDone={refresh} />
      )}

      {a.status === 'SIGNING' && <SigningBlock application={a} onDone={refresh} />}

      {a.status === 'ISSUED' && a.disbursement && (
        <div className="card success">
          <h2>Кредит выдан</h2>
          <KeyValue
            items={[
              ['Сумма', formatMoney(a.disbursement.amount)],
              ['Зачислено на счёт', a.disbursement.accountMasked],
              ['Дата', formatDateTime(a.disbursement.executedAt)],
            ]}
          />
          <p className="muted small">
            Выдача смоделирована: денежных операций не было. В промышленной версии здесь будет проводка в банковской
            системе.
          </p>
        </div>
      )}

      {a.decision && <DecisionBlock application={a} />}

      <div className="grid-2">
        <div className="card">
          <h2>История</h2>
          <ul className="timeline">
            {a.history
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
        </div>
        <div className="card">
          <h2>Документы</h2>
          {a.documents.length === 0 && <p className="muted">Документов нет</p>}
          <ul className="doc-list">
            {a.documents.map((d) => (
              <li key={d.id}>
                <button
                  type="button"
                  className="link"
                  onClick={() => downloadFile(`/api/applications/${a.id}/documents/${d.id}`, d.fileName)}
                >
                  {d.fileName}
                </button>
                <span className="muted small">
                  {' '}
                  · {docTypeLabel[d.docType] ?? d.docType} · {formatSize(d.sizeBytes)}
                </span>
              </li>
            ))}
          </ul>
          {a.infoRequests.some((r) => r.status === 'ANSWERED') && (
            <>
              <h3>Ответы банку</h3>
              <ul className="plain">
                {a.infoRequests
                  .filter((r) => r.status === 'ANSWERED')
                  .map((r) => (
                    <li key={r.id} className="small">
                      <span className="muted">Запрос: {r.text}</span>
                      <br />
                      Ответ: {r.clientAnswer}
                    </li>
                  ))}
              </ul>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

function AnswerForm({
  application,
  request,
  onDone,
}: {
  application: ApplicationDetail;
  request: InfoRequest;
  onDone: (a?: ApplicationDetail) => void;
}) {
  const [answer, setAnswer] = useState('');
  const [collateral, setCollateral] = useState(application.collateralDescription ?? '');
  const [docType, setDocType] = useState('BANK_STATEMENT');
  const [uploaded, setUploaded] = useState<string[]>([]);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const needsCollateral = application.requiresCollateral && request.text.includes('залог');

  async function upload(file?: File) {
    if (!file) return;
    setBusy(true);
    setError(null);
    try {
      await api.uploadDocument(application.id, file, docType);
      setUploaded((u) => [...u, file.name]);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }

  async function send() {
    setBusy(true);
    setError(null);
    try {
      const updated = await api.answer(application.id, request.id, answer, needsCollateral ? collateral : undefined);
      onDone(updated);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="card attention">
      <h2>{request.source === 'BANK' ? 'Вопрос от специалиста банка' : 'Нужно дополнить заявку'}</h2>
      <p className="request-text">{request.text}</p>
      <label className="field">
        <span>Ваш ответ</span>
        <textarea rows={3} value={answer} onChange={(e) => setAnswer(e.target.value)} placeholder="Напишите ответ" />
      </label>
      {needsCollateral && (
        <label className="field">
          <span>Описание залога</span>
          <textarea rows={2} value={collateral} onChange={(e) => setCollateral(e.target.value)} />
        </label>
      )}
      <div className="upload-row">
        <select value={docType} onChange={(e) => setDocType(e.target.value)}>
          {Object.entries(docTypeLabel).map(([k, v]) => (
            <option key={k} value={k}>
              {v}
            </option>
          ))}
        </select>
        <label className="btn btn-ghost btn-sm">
          Приложить документ
          <input
            type="file"
            hidden
            accept=".pdf,.jpg,.jpeg,.png,.docx,.xlsx,.csv,.txt"
            onChange={(e) => {
              upload(e.target.files?.[0]);
              e.target.value = '';
            }}
          />
        </label>
        {uploaded.length > 0 && <span className="small">Загружено: {uploaded.join(', ')}</span>}
      </div>
      {error != null && <ErrorBox error={error} />}
      <button type="button" className="btn btn-primary" onClick={send} disabled={busy || answer.trim() === ''}>
        {busy ? 'Отправляем…' : 'Отправить ответ'}
      </button>
    </div>
  );
}

export function OfferBlock({ application, onDone }: { application: ApplicationDetail; onDone: (a?: ApplicationDetail) => void }) {
  const offer = application.offer!;
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const actionable = application.status === 'OFFER_MADE';

  async function act(kind: 'accept' | 'decline') {
    setBusy(true);
    setError(null);
    try {
      onDone(kind === 'accept' ? await api.acceptOffer(application.id) : await api.declineOffer(application.id));
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="card offer">
      <div className="offer-head">
        <h2>{offer.kind === 'COUNTER' ? 'Встречное предложение банка' : 'Предложение банка'}</h2>
        {offer.kind === 'COUNTER' && <span className="badge badge-warn">условия изменены</span>}
      </div>
      <div className="offer-figures">
        <div>
          <span className="muted small">Сумма</span>
          <strong>{formatMoney(offer.amount)}</strong>
        </div>
        <div>
          <span className="muted small">Срок</span>
          <strong>{formatMonths(offer.termMonths)}</strong>
        </div>
        <div>
          <span className="muted small">Ставка</span>
          <strong>{formatPct(offer.ratePct)} годовых</strong>
        </div>
        <div>
          <span className="muted small">
            <Term hint="Одинаковый платёж каждый месяц: часть идёт на проценты, часть на погашение долга">Ежемесячный платёж</Term>
          </span>
          <strong>{formatMoney(offer.monthlyPayment)}</strong>
        </div>
        <div>
          <span className="muted small">
            <Term hint="Сколько вы заплатите банку сверх суммы кредита за весь срок">Переплата</Term>
          </span>
          <strong>{formatMoney(offer.overpayment)}</strong>
        </div>
      </div>
      {offer.changes.length > 0 && (
        <div className="changes">
          <strong>Что изменилось по сравнению с заявкой</strong>
          <ul>
            {offer.changes.map((c) => (
              <li key={c}>{c}</li>
            ))}
          </ul>
        </div>
      )}
      <div className="why">
        <strong>Почему такие условия</strong>
        <p className="small">{offer.summary}</p>
        <ul className="small">
          {offer.reasons.map((r) => (
            <li key={r}>{r}</li>
          ))}
        </ul>
        <p className="muted small">
          Ставка складывается из ключевой ставки, надбавки продукта и надбавки за риск: чем лучше оценка заявки, тем ниже
          надбавка.
        </p>
      </div>
      {error != null && <ErrorBox error={error} />}
      {actionable && (
        <div className="row-actions">
          <button type="button" className="btn btn-primary" onClick={() => act('accept')} disabled={busy}>
            Принять и перейти к подписанию
          </button>
          <button type="button" className="btn btn-ghost" onClick={() => act('decline')} disabled={busy}>
            Отклонить
          </button>
        </div>
      )}
    </div>
  );
}

function SigningBlock({ application, onDone }: { application: ApplicationDetail; onDone: (a?: ApplicationDetail) => void }) {
  const [demoCode, setDemoCode] = useState<string | null>(null);
  const [hint, setHint] = useState('');
  const [code, setCode] = useState('');
  const [agree, setAgree] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const signing = application.signing;

  async function requestCode() {
    setBusy(true);
    setError(null);
    try {
      const r = await api.sendCode(application.id);
      setDemoCode(r.demoCode ?? null);
      setHint(r.hint);
      setCode('');
      onDone();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }

  async function confirm() {
    setBusy(true);
    setError(null);
    try {
      onDone(await api.confirmCode(application.id, code));
    } catch (e) {
      setError(e);
      onDone();
    } finally {
      setBusy(false);
    }
  }

  const blocked = signing?.blocked;
  const codeActive = signing?.codeRequested && !blocked && !signing.confirmed;

  return (
    <div className="card">
      <h2>Подписание договора</h2>
      <ol className="sign-steps">
        <li>
          <label className="check">
            <input type="checkbox" checked={agree} onChange={(e) => setAgree(e.target.checked)} />
            Ознакомился с условиями кредитного договора и даю согласие на зачисление средств на расчётный счёт
          </label>
        </li>
        <li>
          <button type="button" className="btn btn-ghost" onClick={requestCode} disabled={busy || !agree}>
            {signing?.codeRequested ? 'Получить новый код' : 'Получить код'}
          </button>
          {demoCode && (
            <div className="demo-code">
              Демо-режим, ваш код: <code>{demoCode}</code>
              <div className="muted small">{hint}</div>
            </div>
          )}
        </li>
        {codeActive && (
          <li>
            <div className="code-row">
              <input
                inputMode="numeric"
                maxLength={6}
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
                placeholder="Код из 6 цифр"
                aria-label="Код подтверждения"
              />
              <button type="button" className="btn btn-primary" onClick={confirm} disabled={busy || code.length !== 6}>
                Подписать
              </button>
            </div>
            <div className="muted small">Осталось попыток: {signing?.attemptsLeft}</div>
          </li>
        )}
      </ol>
      {blocked && (
        <div className="alert alert-error">Код введён неверно три раза. Запросите новый код, чтобы продолжить.</div>
      )}
      {error != null && <ErrorBox error={error} />}
    </div>
  );
}

function DecisionBlock({ application }: { application: ApplicationDetail }) {
  const d = application.decision!;
  const declined = application.status === 'DECLINED';
  return (
    <div className={`card ${declined ? 'declined' : ''}`}>
      <h2>Почему так</h2>
      <p>{d.summary}</p>
      {d.hardStopReason && (
        <div className="alert alert-error">
          Причина: {d.hardStopReason}. Это обязательное условие, при котором банк не может выдать кредит.
        </div>
      )}
      {d.grade && (
        <p className="small">
          <Term hint="Итоговая оценка риска по заявке: A — низкий риск, D — высокий">Оценка заявки</Term>: грейд{' '}
          <strong>{d.grade}</strong>, {d.score} баллов из 100. Ниже показано, что повлияло на оценку.
        </p>
      )}
      <FactorTable factors={d.factors} />
    </div>
  );
}
