-- Лимит продукта как доля среднего месячного оборота (для овердрафта 0,5). Пусто: ограничения нет
alter table product add column max_turnover_share numeric(4, 2);
