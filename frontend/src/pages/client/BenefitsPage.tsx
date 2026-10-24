import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';
import { CopyIcon } from '../../components/icons';

const OFFERS: Array<{ title: string; text: string; tone: string }> = [
  {
    title: 'Онлайн-касса на 3 месяца без абонентской платы',
    text: 'Для тех, кто принимает оплату картой в магазине или на выезде',
    tone: 'mint',
  },
  {
    title: 'Бухгалтерия для ИП на УСН: первый квартал в подарок',
    text: 'Отчётность и налоги готовятся по выписке из вашего счёта',
    tone: 'sky',
  },
  {
    title: 'Кэшбэк 5% на топливо по бизнес-карте',
    text: 'Возвращается на счёт в начале следующего месяца',
    tone: 'sand',
  },
  {
    title: 'Проверка контрагента бесплатно до конца года',
    text: 'Покажем, нет ли у партнёра долгов и признаков ликвидации, до того как вы отгрузите товар',
    tone: 'lilac',
  },
];

/** Раздел «Выгода»: приглашение партнёров и предложения для бизнеса. Условия в прототипе условные. */
export function BenefitsPage() {
  const profile = useQuery({ queryKey: ['profile'], queryFn: api.profile });
  const [copied, setCopied] = useState(false);
  const code = profile.data ? `BIZ-${profile.data.inn.slice(-4)}${profile.data.id}` : '';
  const link = `${window.location.origin}/invite/${code}`;

  async function copy() {
    try {
      await navigator.clipboard.writeText(link);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2500);
    } catch {
      setCopied(false);
    }
  }

  return (
    <div className="stack">
      <h1>Выгода</h1>

      <section className="referral">
        <div className="referral-copy">
          <h2>5 000 ₽ за каждого приглашённого предпринимателя</h2>
          <p>
            Поделитесь ссылкой с коллегами по бизнесу. Когда партнёр откроет счёт и проведёт первый платёж, деньги придут
            на ваш расчётный счёт.
          </p>
          {code && (
            <div className="invite">
              <code className="invite-link">{link}</code>
              <button type="button" className="btn btn-primary" onClick={copy}>
                <CopyIcon />
                {copied ? 'Ссылка скопирована' : 'Скопировать ссылку'}
              </button>
            </div>
          )}
        </div>
        <ol className="referral-steps">
          <li>
            <strong>Отправьте ссылку</strong>
            <span>Любым удобным способом: в чате, письмом, на визитке</span>
          </li>
          <li>
            <strong>Партнёр открывает счёт</strong>
            <span>Онлайн, по ссылке он сразу попадает в нужную форму</span>
          </li>
          <li>
            <strong>Вы получаете 5 000 ₽</strong>
            <span>После первого платежа партнёра, без ограничений по числу приглашений</span>
          </li>
        </ol>
      </section>

      <section>
        <h2 className="section-title">Предложения для вашего бизнеса</h2>
        <div className="offers">
          {OFFERS.map((o) => (
            <article key={o.title} className={`offer-tile tone-${o.tone}`}>
              <h3>{o.title}</h3>
              <p>{o.text}</p>
              <button type="button" className="link small" disabled title="В прототипе подключение не реализовано">
                Подключить
              </button>
            </article>
          ))}
        </div>
        <p className="muted small">В прототипе программы условные: выплаты и подключение не выполняются</p>
      </section>
    </div>
  );
}
