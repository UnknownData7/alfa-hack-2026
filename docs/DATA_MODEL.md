# Модель данных

> Статус: проектное описание. После реализации сверить с миграциями Flyway.

Каждое поле нужно кредитному процессу: либо участвует в скоринге и решении, либо нужно для сопровождения заявки, либо для аудита.

## ER-диаграмма

```mermaid
erDiagram
  COMPANY ||--o{ APP_USER : "сотрудники клиента"
  COMPANY ||--|| COMPANY_FINANCIALS : "финансовый снимок"
  COMPANY ||--o{ LOAN_APPLICATION : "подаёт"
  COMPANY ||--o{ FEED_ITEM : "лента"
  PRODUCT ||--o{ LOAN_APPLICATION : "выбран в"
  LOAN_APPLICATION ||--o{ APPLICATION_DOCUMENT : "документы"
  LOAN_APPLICATION ||--o{ EXTERNAL_CHECK_RESULT : "внешние проверки"
  LOAN_APPLICATION ||--o{ SCORING_RESULT : "запуски скоринга"
  LOAN_APPLICATION ||--o{ OFFER : "предложения"
  LOAN_APPLICATION ||--o{ BANK_TASK : "задачи банка"
  LOAN_APPLICATION ||--o{ INFO_REQUEST : "запросы данных"
  LOAN_APPLICATION ||--o| SIGNING_SESSION : "подписание"
  LOAN_APPLICATION ||--o| DISBURSEMENT : "выдача"
  LOAN_APPLICATION ||--o{ STATUS_HISTORY : "история"
  BANK_TASK ||--o{ TASK_CHECK : "проверки"
  BANK_TASK ||--o{ BANK_DECISION : "решения"
  APP_USER ||--o{ BANK_TASK : "исполнитель"

  COMPANY {
    bigint id PK
    string inn
    string name
    string legal_form
    string okved_code
    string industry_risk
    date registration_date
  }
  LOAN_APPLICATION {
    bigint id PK
    string number
    string status
    string product_code
    decimal requested_amount
    int requested_term_months
    int version
  }
  SCORING_RESULT {
    bigint id PK
    int run_no
    int total_score
    string grade
    boolean hard_stop
    jsonb factors
  }
  OFFER {
    bigint id PK
    string kind
    decimal amount
    int term_months
    decimal rate_pct
    string status
  }
  BANK_TASK {
    bigint id PK
    int priority
    string status
    timestamp sla_due_at
  }
```

Показаны ключевые атрибуты. Полный состав — в миграциях.

## Описание таблиц

| Таблица | Назначение | Ключевые поля |
|---|---|---|
| `app_user` | пользователи и роли | login, password_hash, role, company_id |
| `company` | профиль компании-клиента | inn, legal_form (`IP`/`OOO`), okved_code, industry_risk, registration_date, employees_count, tax_system, account_masked, months_with_bank |
| `company_financials` | синтетический снимок «по данным банка» | annual_turnover_12m, avg_monthly_turnover, turnover_growth_pct, existing_monthly_obligations |
| `product` | каталог продуктов | code, spread_pct, min/max сумма и срок, requires_collateral, routing, auto_limit_amount, min_avg_monthly_turnover |
| `loan_application` | заявка | number, product_code, purpose, requested_amount, requested_term_months, monthly_obligations_declared, collateral_description, согласия, status, version |
| `application_document` | документы заявки | doc_type, file_name, storage_path, is_new_for_bank |
| `external_check_result` | ответы моков (реестр, БКИ, ФНС) | source, payload (JSONB), status |
| `scoring_result` | запуски скоринга | run_no, total_score, grade, hard_stop, factors (JSONB), inputs_snapshot, trigger |
| `offer` | предложения условий | kind (`INITIAL`/`COUNTER`), amount, term_months, rate_pct, monthly_payment, status, explanation |
| `bank_task` | задача андеррайтера | priority 1–10, status, assignee_user_id, sla_due_at, reason_codes |
| `task_check` | проверки по задаче | check_code, status (`PENDING`/`OK`/`NEGATIVE`/`NEEDS_INFO`), comment |
| `bank_decision` | решение андеррайтера | result, comment, counter_offer |
| `info_request` | запрос данных у клиента | text, status, client_answer |
| `signing_session` | подписание кодом | code_hash, attempts_left, status |
| `disbursement` | симуляция выдачи | amount, account_masked, simulated = true |
| `status_history` / `audit_event` | история переходов и событий | from_status, to_status, actor_type, actor_id, comment |
| `feed_item` | элементы ленты клиента | type, title, body, reason, related_application_id, is_read |
| `news_catalog` | каталог новостей с таргетингом | title, body, targeting (JSONB) |

## Принципы

- Решение по заявке воспроизводимо: в `scoring_result` хранится снимок входных данных и разбор по факторам, история запусков не удаляется.
- Любое изменение статуса фиксируется в `status_history` с автором и комментарием.
- Деньги хранятся как `decimal`, а не как число с плавающей точкой.
- Документы лежат в файловом хранилище (том Docker), в БД — только метаданные и путь.
