import { useEffect, useMemo, useRef, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { api, ApiError } from '../../api/client';
import { docTypeLabel, formatMoney, formatMonths, formatSize } from '../../api/format';
import type { ApplicationDetail, DraftRequest, Product } from '../../api/types';
import { useAssistant, useAssistantPage } from '../../assistant/AssistantContext';
import { ErrorBox, KeyValue, Loader, Term } from '../../components/ui';

const STEPS = ['Условия', 'Данные', 'Документы', 'Проверка и отправка'];

interface Form {
  productCode: string;
  purpose: string;
  amount: string;
  termMonths: string;
  monthlyObligationsDeclared: string;
  collateralDescription: string;
  consentDataProcessing: boolean;
  consentCreditBureau: boolean;
}

function toRequest(f: Form, parentApplicationId?: number | null): DraftRequest {
  return {
    productCode: f.productCode,
    purpose: f.purpose,
    amount: Number(f.amount),
    termMonths: Number(f.termMonths),
    monthlyObligationsDeclared: f.monthlyObligationsDeclared === '' ? null : Number(f.monthlyObligationsDeclared),
    collateralDescription: f.collateralDescription.trim() === '' ? null : f.collateralDescription,
    consentDataProcessing: f.consentDataProcessing,
    consentCreditBureau: f.consentCreditBureau,
    parentApplicationId: parentApplicationId ?? null,
  };
}

/** Проверка условий по границам продукта до отправки на сервер. */
export function validateConditions(f: Form, product?: Product): string | null {
  if (!product) return 'Выберите продукт';
  const amount = Number(f.amount);
  const term = Number(f.termMonths);
  if (!amount || amount < product.minAmount || amount > product.maxAmount) {
    return `Сумма для продукта «${product.name}» от ${formatMoney(product.minAmount)} до ${formatMoney(product.maxAmount)}`;
  }
  if (!term || term < product.minTermMonths || term > product.maxTermMonths) {
    return `Срок для продукта «${product.name}» от ${product.minTermMonths} до ${product.maxTermMonths} мес.`;
  }
  if (!product.purposes.some((p) => p.code === f.purpose)) {
    return 'Этот продукт не подходит для выбранной цели';
  }
  return null;
}

export function WizardPage() {
  const params = useParams();
  const [search] = useSearchParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const products = useQuery({ queryKey: ['products'], queryFn: api.products });
  const profile = useQuery({ queryKey: ['profile'], queryFn: api.profile });

  const fromId = search.get('from') ? Number(search.get('from')) : null;
  const [draftId, setDraftId] = useState<number | null>(params.id ? Number(params.id) : null);
  const [draft, setDraft] = useState<ApplicationDetail | null>(null);
  const [step, setStep] = useState(0);
  const [form, setForm] = useState<Form>({
    productCode: search.get('product') ?? 'WORKING_EXPRESS',
    purpose: search.get('purpose') ?? 'WORKING_CAPITAL',
    amount: search.get('amount') ?? '',
    termMonths: search.get('term') ?? '',
    monthlyObligationsDeclared: '',
    collateralDescription: '',
    consentDataProcessing: false,
    consentCreditBureau: false,
  });
  const [error, setError] = useState<unknown>(null);
  const [saving, setSaving] = useState<'idle' | 'saving' | 'saved'>('idle');
  const [busy, setBusy] = useState(false);
  const [docType, setDocType] = useState('BANK_STATEMENT');
  const loaded = useRef(false);
  const assistant = useAssistant();
  useAssistantPage({
    wizardStep: step,
    purpose: form.purpose,
    amount: Number(form.amount) || undefined,
    termMonths: Number(form.termMonths) || undefined,
    productCode: form.productCode,
  });

  // Загрузка существующего черновика или предзаполнение из прошлой заявки
  useEffect(() => {
    const sourceId = draftId ?? fromId;
    if (!sourceId || loaded.current) return;
    loaded.current = true;
    api
      .application(sourceId)
      .then((a) => {
        if (draftId && a.status !== 'DRAFT') {
          navigate(`/applications/${a.id}`, { replace: true });
          return;
        }
        setForm((f) => ({
          ...f,
          productCode: a.productCode,
          purpose: a.purpose,
          amount: String(a.requestedAmount),
          termMonths: String(a.requestedTermMonths),
          monthlyObligationsDeclared: a.monthlyObligationsDeclared == null ? '' : String(a.monthlyObligationsDeclared),
          collateralDescription: a.collateralDescription ?? '',
          consentDataProcessing: draftId ? a.consentDataProcessing : false,
          consentCreditBureau: draftId ? a.consentCreditBureau : false,
        }));
        if (draftId) setDraft(a);
      })
      .catch(setError);
  }, [draftId, fromId, navigate]);

  const product = useMemo(() => products.data?.find((p) => p.code === form.productCode), [products.data, form.productCode]);
  const conditionsError = validateConditions(form, product);

  // Автосохранение черновика
  useEffect(() => {
    if (!draftId || conditionsError) return;
    setSaving('saving');
    const timer = setTimeout(() => {
      api
        .updateDraft(draftId, toRequest(form, fromId))
        .then((a) => {
          setDraft(a);
          setSaving('saved');
        })
        .catch((e) => {
          setSaving('idle');
          setError(e);
        });
    }, 800);
    return () => clearTimeout(timer);
  }, [form, draftId, conditionsError, fromId]);

  function update<K extends keyof Form>(key: K, value: Form[K]) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  async function next() {
    setError(null);
    if (step === 0) {
      if (conditionsError) {
        setError(new ApiError(400, conditionsError));
        return;
      }
      if (!draftId) {
        setBusy(true);
        try {
          const created = await api.createDraft(toRequest(form, fromId));
          setDraftId(created.id);
          setDraft(created);
          loaded.current = true;
          // Меняем адрес без перехода: новый маршрут пересоздал бы мастер и сбросил шаг
          window.history.replaceState(window.history.state, '', `/applications/${created.id}/edit`);
        } catch (e) {
          setError(e);
          return;
        } finally {
          setBusy(false);
        }
      }
    }
    if (step === 1 && product?.requiresCollateral && form.collateralDescription.trim() === '') {
      setError(new ApiError(400, 'Опишите залог: что это за имущество и его примерная стоимость'));
      return;
    }
    setStep((s) => Math.min(s + 1, STEPS.length - 1));
  }

  async function upload(file: File | undefined) {
    if (!file || !draftId) return;
    setError(null);
    setBusy(true);
    try {
      await api.uploadDocument(draftId, file, docType);
      setDraft(await api.application(draftId));
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }

  async function submit() {
    if (!draftId) return;
    setError(null);
    setBusy(true);
    try {
      await api.updateDraft(draftId, toRequest(form, fromId));
      await api.submit(draftId);
      await queryClient.invalidateQueries({ queryKey: ['applications'] });
      navigate(`/applications/${draftId}`);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }

  if (products.isLoading || profile.isLoading) return <Loader />;
  if (products.error) return <ErrorBox error={products.error} onRetry={() => products.refetch()} />;

  const obligationsHint = profile.data
    ? `По данным банка ваши текущие платежи по кредитам около ${formatMoney(profile.data.existingMonthlyObligations)} в месяц`
    : '';

  return (
    <div className="card wizard">
      <div className="wizard-head">
        <h1>{draft ? `Заявка ${draft.number}` : 'Новая заявка'}</h1>
        <span className="muted small">
          {saving === 'saving' ? 'Сохраняем черновик…' : saving === 'saved' ? 'Черновик сохранён' : ''}
        </span>
      </div>
      <ol className="steps">
        {STEPS.map((s, i) => (
          <li key={s} className={i === step ? 'current' : i < step ? 'done' : ''}>
            <span>{i + 1}</span>
            {s}
          </li>
        ))}
      </ol>
      {assistant.enabled && (
        <p className="wizard-help">
          <button type="button" className="link small" onClick={() => assistant.openPanel('wizard', 'INLINE_WIZARD')}>
            Что нужно на этом шаге?
          </button>
        </p>
      )}

      {step === 0 && (
        <div className="form-grid">
          <label className="field">
            <span>Продукт</span>
            <select value={form.productCode} onChange={(e) => update('productCode', e.target.value)}>
              {products.data?.map((p) => (
                <option key={p.code} value={p.code}>
                  {p.name}
                </option>
              ))}
            </select>
            {product && <small className="muted">{product.description}</small>}
          </label>
          <label className="field">
            <span>Цель</span>
            <select value={form.purpose} onChange={(e) => update('purpose', e.target.value)}>
              {product?.purposes.map((p) => (
                <option key={p.code} value={p.code}>
                  {p.label}
                </option>
              ))}
            </select>
          </label>
          <label className="field" data-assist="wizard-amount">
            <span>Сумма, ₽</span>
            <input type="number" value={form.amount} onChange={(e) => update('amount', e.target.value)} />
            {product && (
              <small className="muted">
                от {formatMoney(product.minAmount)} до {formatMoney(product.maxAmount)}
              </small>
            )}
          </label>
          <label className="field" data-assist="wizard-term">
            <span>Срок, месяцев</span>
            <input type="number" value={form.termMonths} onChange={(e) => update('termMonths', e.target.value)} />
            {product && (
              <small className="muted">
                от {product.minTermMonths} до {product.maxTermMonths} мес.
              </small>
            )}
          </label>
        </div>
      )}

      {step === 1 && (
        <div className="form-grid">
          <label className="field" data-assist="wizard-obligations">
            <span>
              <Term hint="Сколько в месяц компания уже платит по другим кредитам и лизингу">Ежемесячные платежи по кредитам</Term>, ₽
            </span>
            <input
              type="number"
              min={0}
              value={form.monthlyObligationsDeclared}
              onChange={(e) => update('monthlyObligationsDeclared', e.target.value)}
              placeholder={profile.data ? String(profile.data.existingMonthlyObligations) : ''}
            />
            <small className="muted">{obligationsHint}. Если сумма другая, укажите её.</small>
          </label>
          {product?.requiresCollateral && (
            <label className="field wide" data-assist="wizard-collateral">
              <span>
                <Term hint="Имущество, которое обеспечивает кредит: например, покупаемое оборудование или транспорт">Залог</Term>
              </span>
              <textarea
                rows={3}
                value={form.collateralDescription}
                onChange={(e) => update('collateralDescription', e.target.value)}
                placeholder="Например: приобретаемый грузовой автомобиль, стоимость около 7 млн ₽"
              />
            </label>
          )}
          {!product?.requiresCollateral && <p className="muted">Для этого продукта залог не нужен</p>}
        </div>
      )}

      {step === 2 && (
        <div className="stack">
          <p>
            Приложите выписку по расчётному счёту за последние 12 месяцев или другой документ, подтверждающий обороты. Так
            банк быстрее примет решение.
          </p>
          <div className="upload-row" data-assist="wizard-documents">
            <select value={docType} onChange={(e) => setDocType(e.target.value)}>
              {Object.entries(docTypeLabel).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
            <label className="btn btn-ghost">
              Выбрать файл
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
            <span className="muted small">PDF, JPG, PNG, DOCX, XLSX, CSV или TXT до 10 МБ</span>
          </div>
          {draft && draft.documents.length > 0 ? (
            <ul className="doc-list">
              {draft.documents.map((d) => (
                <li key={d.id}>
                  {d.fileName} <span className="muted small">{docTypeLabel[d.docType] ?? d.docType}, {formatSize(d.sizeBytes)}</span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="muted small">Документы пока не загружены. Без них банк запросит их после отправки.</p>
          )}
        </div>
      )}

      {step === 3 && (
        <div className="stack">
          <KeyValue
            items={[
              ['Продукт', product?.name ?? form.productCode],
              ['Цель', product?.purposes.find((p) => p.code === form.purpose)?.label ?? form.purpose],
              ['Сумма', formatMoney(Number(form.amount))],
              ['Срок', formatMonths(Number(form.termMonths))],
              [
                'Платежи по кредитам',
                form.monthlyObligationsDeclared === ''
                  ? `по данным банка, ${formatMoney(profile.data?.existingMonthlyObligations)}`
                  : formatMoney(Number(form.monthlyObligationsDeclared)),
              ],
              ...(product?.requiresCollateral ? [['Залог', form.collateralDescription || '—'] as [string, string]] : []),
              ['Документы', `${draft?.documents.length ?? 0} шт.`],
            ]}
          />
          <div className="stack" data-assist="wizard-consents">
          <label className="check">
            <input
              type="checkbox"
              checked={form.consentDataProcessing}
              onChange={(e) => update('consentDataProcessing', e.target.checked)}
            />
            Согласен на обработку данных компании и представителя для рассмотрения заявки
          </label>
          <label className="check">
            <input
              type="checkbox"
              checked={form.consentCreditBureau}
              onChange={(e) => update('consentCreditBureau', e.target.checked)}
            />
            Согласен на запрос кредитной истории в бюро кредитных историй
          </label>
          </div>
        </div>
      )}

      {error != null && <ErrorBox error={error} />}

      <div className="wizard-actions">
        {step > 0 && (
          <button type="button" className="btn btn-ghost" onClick={() => setStep((s) => s - 1)} disabled={busy}>
            Назад
          </button>
        )}
        {step < STEPS.length - 1 && (
          <button type="button" className="btn btn-primary" onClick={next} disabled={busy}>
            {busy ? 'Сохраняем…' : 'Далее'}
          </button>
        )}
        {step === STEPS.length - 1 && (
          <button
            type="button"
            className="btn btn-primary"
            onClick={submit}
            disabled={busy || !form.consentDataProcessing || !form.consentCreditBureau}
          >
            {busy ? 'Отправляем…' : 'Отправить заявку'}
          </button>
        )}
      </div>
    </div>
  );
}
