-- Условия продуктов по рыночным ориентирам (октябрь 2026, ключевая ставка 14%)
update product
set spread_pct = 9.00,
    max_turnover_share = 0.50,
    description = 'Деньги на счёте сверх остатка, чтобы закрывать кассовые разрывы. Лимит до половины среднего месячного оборота, каждый транш гасится за 60 дней. Доступен при обороте от 300 тыс. ₽ в месяц.'
where code = 'BUSINESS_OVERDRAFT';

update product
set max_term_months = 84
where code = 'EQUIPMENT_GROWTH';

-- Короткие однофразовые тексты ленты показываются без точки в конце, как остальные подписи интерфейса
update news_catalog
set body = rtrim(body, '.')
where body like '%.' and body not like '%. %';
