import { useState, type FormEvent } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { api } from '../../api/client';
import { formatMoney, formatPct } from '../../api/format';
import { ApplicationsList } from '../../components/ApplicationsList';
import type { Recommendation } from '../../api/types';
import { ErrorBox, Loader } from '../../components/ui';

export function NeedPage() {
  const navigate = useNavigate();
  const purposes = useQuery({ queryKey: ['purposes'], queryFn: api.purposes });
  const [purpose, setPurpose] = useState('WORKING_CAPITAL');
  const [amount, setAmount] = useState('1200000');
  const [term, setTerm] = useState('9');
  const recommend = useMutation({
    mutationFn: () => api.recommend(purpose, Number(amount), Number(term)),
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    recommend.mutate();
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
          <label className="field">
            <span>Цель</span>
            <select value={purpose} onChange={(e) => setPurpose(e.target.value)}>
              {purposes.data?.map((p) => (
                <option key={p.code} value={p.code}>
                  {p.label}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Сумма, ₽</span>
            <input type="number" min={1} step={1} value={amount} onChange={(e) => setAmount(e.target.value)} required />
          </label>
          <label className="field">
            <span>Срок, месяцев</span>
            <input type="number" min={1} max={120} value={term} onChange={(e) => setTerm(e.target.value)} required />
          </label>
          <button type="submit" className="btn btn-primary" disabled={recommend.isPending}>
            {recommend.isPending ? 'Подбираем…' : 'Подобрать'}
          </button>
        </form>
      </div>

      {recommend.error && <ErrorBox error={recommend.error} />}
      {recommend.data && <Recommendations items={recommend.data} amount={amount} onApply={apply} />}
      <ApplicationsList title="Мои заявки" />
    </div>
  );
}

/** Подходящие продукты. Неподходящие клиенту не показываются: вместо них подсказка, что изменить в запросе. */
export function Recommendations({
  items,
  amount,
  onApply,
}: {
  items: Recommendation[];
  amount: string;
  onApply: (code: string) => void;
}) {
  const available = items.filter((r) => r.available);
  if (available.length === 0) {
    // Подсказки берём у продуктов, которые подходят по цели: им не хватает только суммы, срока или оборота
    const hints = Array.from(new Set(items.filter((r) => r.fits.some((f) => f.startsWith('подходит для цели'))).flatMap((r) => r.limits)));
    return (
      <div className="card no-match">
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
    <div className="recommendations">
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
