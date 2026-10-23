import { useState, type FormEvent } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { api } from '../../api/client';
import { formatMoney, formatPct } from '../../api/format';
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
          Опишите задачу, и мы подберём подходящие продукты. Объясним, почему продукт подходит или нет, и покажем
          ориентировочную ставку.
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
            <input type="number" min={1} step={10000} value={amount} onChange={(e) => setAmount(e.target.value)} required />
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
      {recommend.data && (
        <div className="recommendations">
          {recommend.data.map((r) => (
            <div key={r.code} className={`card product-card ${r.available ? '' : 'unavailable'}`}>
              <div className="product-head">
                <h2>{r.name}</h2>
                <span className={`badge ${r.available ? 'badge-ok' : 'badge-neutral'}`}>
                  {r.available ? 'Подходит' : 'Не подходит'}
                </span>
              </div>
              <div className="rate">
                ставка от <strong>{formatPct(r.indicativeRatePct)}</strong> годовых
              </div>
              {r.fits.length > 0 && (
                <ul className="reasons ok">
                  {r.fits.map((f) => (
                    <li key={f}>{f}</li>
                  ))}
                </ul>
              )}
              {r.limits.length > 0 && (
                <ul className="reasons no">
                  {r.limits.map((f) => (
                    <li key={f}>{f}</li>
                  ))}
                </ul>
              )}
              {r.available && (
                <button type="button" className="btn btn-primary" onClick={() => apply(r.code)}>
                  Оформить заявку на {formatMoney(Number(amount))}
                </button>
              )}
            </div>
          ))}
          <p className="muted small">
            Ставка ориентировочная: итоговые условия зависят от оценки заявки. Ставка складывается из ключевой ставки,
            надбавки продукта и надбавки за риск.
          </p>
        </div>
      )}
    </div>
  );
}
