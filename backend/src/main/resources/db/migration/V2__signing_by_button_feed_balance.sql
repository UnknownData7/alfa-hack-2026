-- Версия 2: подписание без кода подтверждения, баланс расчётного счёта, даты новостей, скрытие карточек ленты.

-- Договор подтверждается кнопкой в кабинете, сессии с кодом больше не нужны
drop table signing_session;

-- Синтетический остаток на расчётном счёте; выдача кредита зачисляет на него сумму
alter table company add column account_balance numeric(15, 2) not null default 0;

-- Дата публикации новости или подсказки показывается в ленте
alter table news_catalog add column published_at timestamptz not null default now();

-- Новости и подсказки, которые клиент убрал из ленты кнопкой «Прочитано»
create table feed_dismissal (
    company_id    bigint      not null references company (id),
    news_id       bigint      not null references news_catalog (id),
    dismissed_at  timestamptz not null default now(),
    primary key (company_id, news_id)
);

-- Запас по длине для типа участника в журнале аудита
alter table audit_event alter column actor_type type varchar(20);
