import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { formatMoney, formatMonths } from '../api/format';
import { PlusIcon } from './icons';
import { Money } from './ui';

const IN_PROGRESS = new Set(['SUBMITTED', 'SCORING', 'NEED_INFO', 'MANUAL_REVIEW', 'OFFER_MADE', 'SIGNING']);

/** Левая колонка «Мои продукты»: расчётный счёт, выданные кредиты и заявки в работе. */
export function ProductsColumn() {
  const profile = useQuery({ queryKey: ['profile'], queryFn: api.profile, refetchInterval: 10000 });
  const applications = useQuery({ queryKey: ['applications'], queryFn: api.applications, refetchInterval: 5000 });
  const issued = applications.data?.filter((a) => a.status === 'ISSUED') ?? [];
  const inProgress = applications.data?.filter((a) => IN_PROGRESS.has(a.status)) ?? [];

  return (
    <aside className="products" aria-label="Мои продукты">
      <h2 className="products-title">Мои продукты</h2>
      {profile.data && (
        <>
          <div className="company-chip">
            <strong>{profile.data.name}</strong>
            <span className="muted small">ИНН {profile.data.inn}</span>
          </div>
          <Link to="/history" className="account">
            <span className="account-label">Расчётный счёт</span>
            <Money value={profile.data.accountBalance} sensitive className="account-balance" />
            <span className="account-number">{profile.data.accountMasked}</span>
          </Link>
        </>
      )}
      {profile.isLoading && <div className="account skeleton" aria-hidden />}

      <div className="products-group">
        <h3>Кредиты</h3>
        {issued.length === 0 && <p className="muted small">Действующих кредитов нет</p>}
        {issued.map((a) => (
          <Link key={a.id} to={`/applications/${a.id}`} className="product-line">
            <span>
              {a.productName}
              <span className="muted small">на {formatMonths(a.requestedTermMonths)}</span>
            </span>
            <Money value={a.requestedAmount} sensitive />
          </Link>
        ))}
      </div>

      {inProgress.length > 0 && (
        <div className="products-group">
          <h3>Заявки в работе</h3>
          {inProgress.map((a) => (
            <Link key={a.id} to={`/applications/${a.id}`} className="product-line">
              <span>
                {a.productName}
                <span className={`muted small ${a.status === 'NEED_INFO' || a.status === 'OFFER_MADE' || a.status === 'SIGNING' ? 'needs-you' : ''}`}>
                  {a.statusLabel}
                </span>
              </span>
              <span className="small">{formatMoney(a.requestedAmount)}</span>
            </Link>
          ))}
        </div>
      )}

      <Link to="/need" className="add-product">
        <PlusIcon />
        Получить деньги на бизнес
      </Link>
    </aside>
  );
}
