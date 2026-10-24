import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { api } from '../../api/client';
import { useAssistant, useAssistantPage } from '../../assistant/AssistantContext';
import { useVisibleTimer } from '../../assistant/proactive';
import { formatMoney, formatPct } from '../../api/format';
import { ApplicationsList } from '../../components/ApplicationsList';
import type { Recommendation } from '../../api/types';
import { ErrorBox, Loader } from '../../components/ui';

export function NeedPage() {
  const navigate = useNavigate();
  const assistant = useAssistant();
  const [search, setSearch] = useSearchParams();
  const purposes = useQuery({ queryKey: ['purposes'], queryFn: api.purposes });
  const [purpose, setPurpose] = useState('WORKING_CAPITAL');
  const [amount, setAmount] = useState('1200000');
  const [term, setTerm] = useState('9');
  const recommend = useMutation({
    mutationFn: (need: { purpose: string; amount: string; term: string }) =>
      api.recommend(need.purpose, Number(need.amount), Number(need.term)),
  });
  useAssistantPage({ purpose, amount: Number(amount) || undefined, termMonths: Number(term) || undefined });

  // Помощник подставляет значения через адрес страницы и сразу запускает подбор
  useEffect(() => {
    const p = search.get('purpose');
    const a = search.get('amount');
    const t = search.get('term');
    if (!p || !a || !t) return;
    setPurpose(p);
    setAmount(a);
    setTerm(t);
    if (search.get('auto') === '1') recommend.mutate({ purpose: p, amount: a, term: t });
    setSearch({}, { replace: true });
  }, [search]);

  // Признаки затруднения: бездействие до подбора, частая смена цели, суммы и срока, долгий выбор между продуктами
  const config = assistant.config;
  const purposeChanges = useRef<number[]>([]);
  const valueChanges = useRef(0);
  const focusValue = useRef('');
  useVisibleTimer(config?.idleSeconds, !recommend.data && !recommend.isPending, () => assistant.offer('IDLE_NEED'), {
    resetOnActivity: true,
  });
  const availableCount = recommend.data?.filter((r) => r.available).length ?? 0;
  useVisibleTimer(config?.recommendationsSeconds, availableCount > 1, () => assistant.offer('COMPARE_DOUBT'), {
    resetKey: recommend.data,
  });

  function changePurpose(value: string) {
    setPurpose(value);
    const now = Date.now();
    purposeChanges.current = [...purposeChanges.current.filter((t) => now - t < 60_000), now];
    if (config && purposeChanges.current.length >= config.purposeChanges) assistant.offer('PURPOSE_CHANGES');
  }

  /** Изменение суммы или срока засчитывается, когда клиент уходит из поля с другим значением. */
  function commitValue(value: string) {
    if (value === focusValue.current) return;
    valueChanges.current += 1;
    if (config && valueChanges.current >= config.valueChanges) assistant.offer('TERM_DOUBT');
  }

  function submit(e: FormEvent) {
    e.preventDefault();
    valueChanges.current = 0;
    recommend.mutate({ purpose, amount, term });
  }

  function apply(productCode: string) {
    const params = new URLSearchParams({ product: productCode, purpose, amount, term });
    navigate(`/applications/new?${params.toString()}`);
  }

  return (
    <div className="stack">
      <div className="card">
        <h1>На что нужны деньги?</h1>
        <p className="muted">
          Укажите цель, сумму и срок. Подберём продукты, объясним, какой подходит, и покажем ориентировочную ставку.
        </p>
        {purposes.isLoading && <Loader />}
        {purposes.error && <ErrorBox error={purposes.error} />}
        <form className="need-form" onSubmit={submit}>
          <label className="field" data-assist="need-purpose">
            <span>Цель</span>
            <select value={purpose} onChange={(e) => changePurpose(e.target.value)}>
              {purposes.data?.map((p) => (
                <option key={p.code} value={p.code}>
                  {p.label}
                </option>
              ))}
            </select>
          </label>
          <label className="field" data-assist="need-amount">
            <span>Сумма, ₽</span>
            <input
              type="number"
              min={1}
              step={1}
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              onFocus={(e) => (focusValue.current = e.target.value)}
              onBlur={(e) => commitValue(e.target.value)}
              required
            />
          </label>
          <label className="field" data-assist="need-term">
            <span>Срок, месяцев</span>
            <input
              type="number"
              min={1}
              max={120}
              value={term}
              onChange={(e) => setTerm(e.target.value)}
              onFocus={(e) => (focusValue.current = e.target.value)}
              onBlur={(e) => commitValue(e.target.value)}
              required
            />
          </label>
          <button type="submit" className="btn btn-primary" disabled={recommend.isPending}>
            {recommend.isPending ? 'Подбираем…' : 'Подобрать'}
          </button>
        </form>
        {assistant.enabled && (
          <div className="need-help">
            <span className="muted small">Сомневаетесь?</span>
            <button type="button" className="link small" onClick={() => assistant.openPanel('purpose', 'INLINE_PURPOSE')}>
              Не знаю цель
            </button>
            <button type="button" className="link small" onClick={() => assistant.openPanel('term', 'INLINE_TERM')}>
              Как выбрать срок?
            </button>
          </div>
        )}
      </div>

      {recommend.error && <ErrorBox error={recommend.error} />}
      {recommend.data && (
        <Recommendations
          items={recommend.data}
          amount={String(recommend.variables?.amount ?? amount)}
          onApply={apply}
          onCompare={assistant.enabled ? () => assistant.openPanel('compare', 'INLINE_COMPARE') : undefined}
        />
      )}
      <ApplicationsList title="Мои заявки" />
    </div>
  );
}

/** Подходящие продукты. Неподходящие клиенту не показываются: вместо них подсказка, что изменить в запросе. */
export function Recommendations({
  items,
  amount,
  onApply,
  onCompare,
}: {
  items: Recommendation[];
  amount: string;
  onApply: (code: string) => void;
  onCompare?: () => void;
}) {
  const available = items.filter((r) => r.available);
  if (available.length === 0) {
    // Подсказки берём у продуктов, которые подходят по цели: им не хватает только суммы, срока или оборота
    const hints = Array.from(new Set(items.filter((r) => r.fits.some((f) => f.startsWith('подходит для цели'))).flatMap((r) => r.limits)));
    return (
      <div className="card no-match" data-assist="recommendations">
        <h2>Под такие условия продуктов нет</h2>
        {hints.length > 0 ? (
          <>
            <p className="muted">Попробуйте изменить запрос</p>
            <ul className="reasons no">
              {hints.map((h) => (
                <li key={h}>{h}</li>
              ))}
            </ul>
          </>
        ) : (
          <p className="muted">Для этой цели у банка нет подходящего продукта, попробуйте выбрать другую цель</p>
        )}
      </div>
    );
  }
  return (
    <div className="recommendations" data-assist="recommendations">
      {onCompare && available.length > 1 && (
        <div className="recommendations-head">
          <button type="button" className="link" onClick={onCompare}>
            Чем они отличаются?
          </button>
        </div>
      )}
      {available.map((r, i) => (
        <div key={r.code} className={`card product-card ${i === 0 ? 'best' : ''}`}>
          {i === 0 && available.length > 1 && <span className="best-flag">Лучше всего подходит</span>}
          <h2>{r.name}</h2>
          <div className="rate">
            ставка от <strong>{formatPct(r.indicativeRatePct)}</strong> годовых
          </div>
          <ul className="reasons ok">
            {r.fits.map((f) => (
              <li key={f}>{f}</li>
            ))}
          </ul>
          <button type="button" className="btn btn-primary" onClick={() => onApply(r.code)}>
            Оформить на {formatMoney(Number(amount))}
          </button>
        </div>
      ))}
      <p className="muted small rate-note">
        Ставка ориентировочная. Точные условия банк покажет в предложении после рассмотрения заявки
      </p>
    </div>
  );
}
