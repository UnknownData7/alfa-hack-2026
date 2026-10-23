-- Синтетические демо-данные (профиль demo). Все компании, люди, ИНН и счета вымышлены.
-- Даты задаются относительно текущего дня, чтобы возраст бизнеса не «устаревал».
-- Пароль всех учёток: Demo#2026

insert into product (code, name, description, purposes, min_amount, max_amount, min_term_months, max_term_months,
                     spread_pct, requires_collateral, routing, auto_limit_amount, min_avg_monthly_turnover, sort_order)
values ('WORKING_EXPRESS', 'Оборотный экспресс',
        'Быстрое пополнение оборотных средств: закупка товара, кассовый разрыв, текущие расходы. Без залога.',
        'WORKING_CAPITAL,CASH_GAP,PURCHASE', 100000, 3000000, 3, 12, 3.00, false, 'AUTO_ALLOWED', 1500000, null, 1),
       ('BUSINESS_OVERDRAFT', 'Бизнес-овердрафт',
        'Деньги на счёте сверх остатка, чтобы закрывать кассовые разрывы. Доступен при среднем месячном обороте от 300 тыс. ₽.',
        'CASH_GAP,WORKING_CAPITAL', 100000, 2000000, 1, 12, 2.50, false, 'AUTO_ALLOWED', 1000000, 300000, 2),
       ('EQUIPMENT_GROWTH', 'Оборудование и развитие',
        'Крупные вложения: оборудование, транспорт, запуск нового направления. Нужен залог, заявку рассматривает специалист банка.',
        'EQUIPMENT,TRANSPORT,NEW_DIRECTION', 500000, 15000000, 12, 60, 4.00, true, 'ALWAYS_MANUAL', null, null, 3);

insert into company (inn, name, legal_form, ogrn, okved_code, okved_name, industry_risk, registration_date,
                     employees_count, tax_system, account_masked, months_with_bank)
values ('7701000001', 'ООО «Ромашка Логистик»', 'OOO', '1027700000001', '52.29', 'Транспортная обработка грузов', 'LOW',
        current_date - interval '62 months', 38, 'ОСН', '40702 •••• 0001', 48),
       ('770100000002', 'ИП Соколов Д. А.', 'IP', '318770000000002', '47.19', 'Розничная торговля в магазинах', 'MEDIUM',
        current_date - interval '30 months', 6, 'УСН', '40802 •••• 0002', 24),
       ('7701000003', 'ООО «Север-Строй»', 'OOO', '1217700000003', '43.99', 'Специализированные строительные работы', 'MEDIUM',
        current_date - interval '40 months', 25, 'ОСН', '40702 •••• 0003', 36),
       ('770100000004', 'ИП Ким А. В.', 'IP', '326770000000004', '56.10', 'Деятельность ресторанов и доставка еды', 'HIGH',
        current_date - interval '9 months', 4, 'Патент', '40802 •••• 0004', 8),
       ('7701000005', 'ООО «Вектор-Опт»', 'OOO', '1207700000005', '46.90', 'Оптовая торговля', 'MEDIUM',
        current_date - interval '50 months', 14, 'ОСН', '40702 •••• 0005', 40),
       ('7701000006', 'ООО «Заря»', 'OOO', '1197700000006', '68.20', 'Аренда недвижимости', 'MEDIUM',
        current_date - interval '70 months', 3, 'УСН', '40702 •••• 0006', 50),
       ('770100000007', 'ИП Орлова М. С.', 'IP', '324770000000007', '74.10', 'Дизайн и печать', 'LOW',
        current_date - interval '20 months', 2, 'УСН', '40802 •••• 0007', 18),
       ('770100000008', 'ИП Белова Е. Н.', 'IP', '325770000000008', '96.02', 'Салон красоты', 'MEDIUM',
        current_date - interval '14 months', 5, 'УСН', '40802 •••• 0008', 12),
       ('7701000009', 'ООО «Ладога Трейд»', 'OOO', '1237700000009', '46.39', 'Оптовая торговля продуктами', 'MEDIUM',
        current_date - interval '28 months', 9, 'УСН', '40702 •••• 0009', 20);

insert into company_financials (company_id, annual_turnover_12m, avg_monthly_turnover, turnover_growth_pct,
                                existing_monthly_obligations, as_of)
select c.id, f.turnover, f.turnover / 12, f.growth, f.obligations, current_date - 1
from (values ('7701000001', 48000000, 14, 120000),
             ('770100000002', 9600000, 6, 90000),
             ('7701000003', 30000000, 3, 200000),
             ('770100000004', 4200000, 25, 0),
             ('7701000005', 20000000, -12, 400000),
             ('7701000006', 6000000, -2, 50000),
             ('770100000007', 7200000, 0, 0),
             ('770100000008', 3600000, 4, 40000),
             ('7701000009', 12000000, 8, 150000)) as f(inn, turnover, growth, obligations)
         join company c on c.inn = f.inn;

insert into app_user (login, password_hash, role, full_name, company_id)
select u.login, '$2a$10$5sT14g1RnWEpiEClctd3ZOpr8/z8u1eefoVIA0xccpFZh6FnTdi6i', 'CLIENT', u.full_name, c.id
from (values ('romashka', 'Иванова Ольга Петровна', '7701000001'),
             ('sokolov', 'Соколов Дмитрий Андреевич', '770100000002'),
             ('sever-stroy', 'Медведев Артём Игоревич', '7701000003'),
             ('kim', 'Ким Алексей Викторович', '770100000004'),
             ('vector-opt', 'Лебедева Наталья Сергеевна', '7701000005'),
             ('zarya', 'Фомин Игорь Олегович', '7701000006'),
             ('orlova', 'Орлова Мария Сергеевна', '770100000007')) as u(login, full_name, inn)
         join company c on c.inn = u.inn;

insert into app_user (login, password_hash, role, full_name, company_id)
values ('uw1', '$2a$10$5sT14g1RnWEpiEClctd3ZOpr8/z8u1eefoVIA0xccpFZh6FnTdi6i', 'UNDERWRITER', 'Андеррайтер Петров П. П.', null),
       ('uw2', '$2a$10$5sT14g1RnWEpiEClctd3ZOpr8/z8u1eefoVIA0xccpFZh6FnTdi6i', 'UNDERWRITER', 'Андеррайтер Сидорова А. А.', null);

-- Исторические заявки: выданный кредит «Ромашки» и две заявки других клиентов в очереди банка

insert into loan_application (number, company_id, product_code, purpose, requested_amount, requested_term_months,
                              monthly_obligations_declared, consent_data_processing, consent_credit_bureau, status,
                              decision_reason_code, decision_summary, created_at, updated_at, submitted_at)
select 'A-2026-000901', id, 'WORKING_EXPRESS', 'PURCHASE', 900000, 6, 40000, true, true, 'ISSUED',
       'AUTO_APPROVE', 'Одобрено автоматически на запрошенных условиях',
       now() - interval '200 days', now() - interval '199 days', now() - interval '200 days'
from company where inn = '7701000001';

insert into loan_application (number, company_id, product_code, purpose, requested_amount, requested_term_months,
                              monthly_obligations_declared, consent_data_processing, consent_credit_bureau, status,
                              decision_reason_code, decision_summary, created_at, updated_at, submitted_at)
select a.number, c.id, 'WORKING_EXPRESS', a.purpose, a.amount, a.term, a.obligations, true, true, 'MANUAL_REVIEW',
       'GRADE_C', 'Средняя оценка риска, заявка передана специалисту банка',
       now() - a.age, now() - a.age, now() - a.age
from (values ('A-2026-000902', '770100000008', 'CASH_GAP', 300000, 6, 40000, interval '5 hours'),
             ('A-2026-000903', '7701000009', 'PURCHASE', 450000, 9, 150000, interval '90 minutes'))
         as a(number, inn, purpose, amount, term, obligations, age)
         join company c on c.inn = a.inn;

insert into offer (application_id, kind, amount, term_months, rate_pct, monthly_payment, total_payment, overpayment,
                   status, explanation, created_by, created_at)
select id, 'INITIAL', 900000, 6, 19.00, 158421, 950526, 50526, 'ACCEPTED',
       '{"summary":"Условия на запрошенную сумму и срок","reasons":["Грейд A: стабильный оборот и чистая кредитная история"],"changes":[]}',
       'SYSTEM', now() - interval '200 days'
from loan_application where number = 'A-2026-000901';

update loan_application a set current_offer_id = o.id
from offer o where o.application_id = a.id and a.number = 'A-2026-000901';

insert into disbursement (application_id, amount, account_masked, executed_at)
select id, 900000, '40702 •••• 0001', now() - interval '199 days'
from loan_application where number = 'A-2026-000901';

insert into status_history (application_id, from_status, to_status, actor_type, comment, created_at)
select a.id, h.from_status, h.to_status, h.actor_type, h.comment, a.created_at + h.shift
from loan_application a
         join (values ('A-2026-000901', null, 'DRAFT', 'CLIENT', 'Создан черновик', interval '0'),
                      ('A-2026-000901', 'DRAFT', 'SUBMITTED', 'CLIENT', 'Заявка отправлена', interval '10 minutes'),
                      ('A-2026-000901', 'SUBMITTED', 'SCORING', 'SYSTEM', 'Данные приняты', interval '11 minutes'),
                      ('A-2026-000901', 'SCORING', 'OFFER_MADE', 'SYSTEM', 'Автоматическое одобрение', interval '12 minutes'),
                      ('A-2026-000901', 'OFFER_MADE', 'SIGNING', 'CLIENT', 'Предложение принято', interval '1 hour'),
                      ('A-2026-000901', 'SIGNING', 'ISSUED', 'SYSTEM', 'Договор подписан, средства зачислены (симуляция)', interval '1 day'),
                      ('A-2026-000902', null, 'DRAFT', 'CLIENT', 'Создан черновик', interval '0'),
                      ('A-2026-000902', 'DRAFT', 'SUBMITTED', 'CLIENT', 'Заявка отправлена', interval '1 minute'),
                      ('A-2026-000902', 'SUBMITTED', 'SCORING', 'SYSTEM', 'Данные приняты', interval '2 minutes'),
                      ('A-2026-000902', 'SCORING', 'MANUAL_REVIEW', 'SYSTEM', 'Средняя оценка риска, нужна проверка специалиста', interval '3 minutes'),
                      ('A-2026-000903', null, 'DRAFT', 'CLIENT', 'Создан черновик', interval '0'),
                      ('A-2026-000903', 'DRAFT', 'SUBMITTED', 'CLIENT', 'Заявка отправлена', interval '1 minute'),
                      ('A-2026-000903', 'SUBMITTED', 'SCORING', 'SYSTEM', 'Данные приняты', interval '2 minutes'),
                      ('A-2026-000903', 'SCORING', 'MANUAL_REVIEW', 'SYSTEM', 'Средняя оценка риска, нужна проверка специалиста', interval '3 minutes'))
    as h(number, from_status, to_status, actor_type, comment, shift) on h.number = a.number;

insert into scoring_result (application_id, run_no, total_score, grade, hard_stop, payment_load, factors,
                            inputs_snapshot, trigger, created_by, created_at)
select a.id, 1, s.score, s.grade, false, s.load, s.factors::jsonb, s.inputs::jsonb, 'INITIAL', 'SYSTEM',
       a.created_at + interval '3 minutes'
from loan_application a
         join (values ('A-2026-000901', 100, 'A', 0.068,
                       '[{"code":"BUSINESS_AGE","title":"Возраст бизнеса","value":"55 мес.","points":15,"comment":"Бизнес работает больше трёх лет"},{"code":"TURNOVER_COVERAGE","title":"Сумма к годовому обороту","value":"0,02","points":20,"comment":"Сумма мала относительно оборота"},{"code":"PAYMENT_LOAD","title":"Платёжная нагрузка","value":"0,07","points":15,"comment":"Платежи занимают малую долю оборота"},{"code":"CREDIT_HISTORY","title":"Кредитная история","value":"без просрочек","points":15,"comment":"Обязательства исполнялись вовремя"},{"code":"TURNOVER_DYNAMICS","title":"Динамика оборота","value":"+12%","points":10,"comment":"Оборот растёт"},{"code":"INDUSTRY_RISK","title":"Риск отрасли","value":"низкий","points":5,"comment":"Отрасль с низким риском"},{"code":"TAX_DEBT","title":"Налоговая задолженность","value":"нет","points":0,"comment":"Задолженности нет"}]',
                       '{"source":"seed"}'),
                      ('A-2026-000902', 57, 'C', 0.293,
                       '[{"code":"BUSINESS_AGE","title":"Возраст бизнеса","value":"14 мес.","points":8,"comment":"Бизнес работает от года до трёх лет"},{"code":"TURNOVER_COVERAGE","title":"Сумма к годовому обороту","value":"0,08","points":20,"comment":"Сумма мала относительно оборота"},{"code":"PAYMENT_LOAD","title":"Платёжная нагрузка","value":"0,29","points":5,"comment":"Нагрузка умеренная"},{"code":"CREDIT_HISTORY","title":"Кредитная история","value":"короткие просрочки","points":3,"comment":"Были просрочки до 30 дней"},{"code":"TURNOVER_DYNAMICS","title":"Динамика оборота","value":"+4%","points":4,"comment":"Оборот стабилен"},{"code":"INDUSTRY_RISK","title":"Риск отрасли","value":"средний","points":0,"comment":"Отрасль со средним риском"},{"code":"TAX_DEBT","title":"Налоговая задолженность","value":"есть","points":-12,"comment":"По данным ФНС есть задолженность"}]',
                       '{"source":"seed"}'),
                      ('A-2026-000903', 56, 'C', 0.248,
                       '[{"code":"BUSINESS_AGE","title":"Возраст бизнеса","value":"28 мес.","points":8,"comment":"Бизнес работает от года до трёх лет"},{"code":"TURNOVER_COVERAGE","title":"Сумма к годовому обороту","value":"0,04","points":20,"comment":"Сумма мала относительно оборота"},{"code":"PAYMENT_LOAD","title":"Платёжная нагрузка","value":"0,25","points":5,"comment":"Нагрузка умеренная"},{"code":"CREDIT_HISTORY","title":"Кредитная история","value":"просрочка 30–90 дней","points":-20,"comment":"Была просрочка от 30 до 90 дней"},{"code":"TURNOVER_DYNAMICS","title":"Динамика оборота","value":"+8%","points":4,"comment":"Оборот стабилен"},{"code":"INDUSTRY_RISK","title":"Риск отрасли","value":"средний","points":0,"comment":"Отрасль со средним риском"},{"code":"TAX_DEBT","title":"Налоговая задолженность","value":"нет","points":0,"comment":"Задолженности нет"}]',
                       '{"source":"seed"}'))
    as s(number, score, grade, load, factors, inputs) on s.number = a.number;

insert into bank_task (application_id, priority, status, reason_codes, sla_due_at, created_at)
select a.id, 7, 'NEW', 'GRADE_C', a.created_at + interval '4 hours 3 minutes', a.created_at + interval '3 minutes'
from loan_application a where a.number in ('A-2026-000902', 'A-2026-000903');

insert into task_check (task_id, check_code, title, goal, status)
select t.id, c.code, c.title, c.goal, 'PENDING'
from bank_task t
         join loan_application a on a.id = t.application_id
         join (values ('A-2026-000902', 'CREDIT_HISTORY', 'Кредитная история', 'Оценить причины просрочек и текущее исполнение обязательств'),
                      ('A-2026-000902', 'TURNOVER_CONFIRMATION', 'Подтверждение оборотов', 'Сверить заявленный оборот с выпиской по счёту'),
                      ('A-2026-000903', 'CREDIT_HISTORY', 'Кредитная история', 'Оценить причины просрочек и текущее исполнение обязательств'))
    as c(number, code, title, goal) on c.number = a.number;

-- Каталог новостей и подсказок с таргетингом

insert into news_catalog (type, title, body, target_legal_form, target_industry_risk, target_product_code, target_status, weight)
values ('NEWS', 'Изменение ключевой ставки', 'Ставки по новым кредитам рассчитываются от актуальной ключевой ставки. Условия по уже выданным кредитам не меняются.', null, null, null, null, 1),
       ('TIP', 'Как подготовить выписку по счёту', 'Выписка за последние 12 месяцев помогает быстрее подтвердить обороты. Её можно выгрузить в интернет-банке и приложить к заявке.', null, null, null, 'DRAFT', 5),
       ('NEWS', 'Упрощённая отчётность для ИП', 'Для индивидуальных предпринимателей на упрощённой системе достаточно налоговой декларации вместо полного пакета отчётности.', 'IP', null, null, null, 3),
       ('NEWS', 'Финансирование закупок для компаний', 'Для ООО с оборотом от 2 млн ₽ в месяц доступны лимиты на закупку товара под короткий срок.', 'OOO', null, null, null, 2),
       ('TIP', 'Сезонность в общепите', 'Если выручка зависит от сезона, расскажите об этом в ответе банку: специалист учтёт это при оценке.', null, 'HIGH', null, null, 4),
       ('TIP', 'Как ответить на запрос банка', 'Ответьте на вопрос специалиста прямо на странице заявки и приложите документы. Заявка сразу вернётся к нему в работу.', null, null, null, 'NEED_INFO', 10),
       ('TIP', 'Что означает встречное предложение', 'Банк готов выдать кредит, но на других условиях. В блоке «Почему такие условия» видно, что изменилось и почему.', null, null, null, 'OFFER_MADE', 9),
       ('TIP', 'Подписание без визита в офис', 'Договор подтверждается кодом прямо в кабинете. После подтверждения деньги поступят на расчётный счёт.', null, null, null, 'SIGNING', 9),
       ('NEWS', 'Залог для крупных вложений', 'Для покупки оборудования и транспорта залогом может стать само приобретаемое имущество.', null, null, 'EQUIPMENT_GROWTH', null, 6),
       ('TIP', 'Пока заявку смотрит специалист', 'Обычно ручная проверка занимает несколько часов. Если понадобятся данные, запрос появится на странице заявки.', null, null, null, 'MANUAL_REVIEW', 8),
       ('TIP', 'Что делать после отказа', 'Причины отказа указаны на странице заявки. Подать новую заявку можно, когда ситуация изменится: данные прошлой заявки подставятся автоматически.', null, null, null, 'DECLINED', 8),
       ('NEWS', 'Овердрафт для кассовых разрывов', 'Овердрафт закрывает временную нехватку денег на счёте: проценты начисляются только на использованную сумму.', null, 'LOW', null, null, 2);

insert into feed_item (company_id, type, title, body, reason, related_application_id, created_at, is_read)
select a.company_id, 'STATUS', 'Кредит выдан', 'По заявке A-2026-000901 средства зачислены на расчётный счёт (симуляция).',
       'Событие по вашей заявке', a.id, a.updated_at, true
from loan_application a where a.number = 'A-2026-000901';
