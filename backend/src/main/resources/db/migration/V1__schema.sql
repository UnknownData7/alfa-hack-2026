-- Схема кредитного конвейера. Деньги хранятся в numeric, статусы в varchar с проверкой допустимых значений.

create table company (
    id                 bigint generated always as identity primary key,
    inn                varchar(12)  not null unique,
    name               varchar(200) not null,
    legal_form         varchar(3)   not null check (legal_form in ('IP', 'OOO')),
    ogrn               varchar(15)  not null,
    okved_code         varchar(10)  not null,
    okved_name         varchar(200) not null,
    industry_risk      varchar(6)   not null check (industry_risk in ('LOW', 'MEDIUM', 'HIGH')),
    registration_date  date         not null,
    employees_count    int          not null,
    tax_system         varchar(20)  not null,
    account_masked     varchar(30)  not null,
    months_with_bank   int          not null
);

create table company_financials (
    company_id                    bigint primary key references company (id),
    annual_turnover_12m           numeric(15, 2) not null,
    avg_monthly_turnover          numeric(15, 2) not null,
    turnover_growth_pct           numeric(6, 2)  not null,
    existing_monthly_obligations  numeric(15, 2) not null,
    as_of                         date           not null
);

create table app_user (
    id             bigint generated always as identity primary key,
    login          varchar(50)  not null unique,
    password_hash  varchar(100) not null,
    role           varchar(20)  not null check (role in ('CLIENT', 'UNDERWRITER')),
    full_name      varchar(200) not null,
    company_id     bigint references company (id),
    check ((role = 'CLIENT') = (company_id is not null))
);

create table product (
    code                      varchar(30) primary key,
    name                      varchar(100)   not null,
    description               varchar(500)   not null,
    purposes                  varchar(200)   not null,
    min_amount                numeric(15, 2) not null,
    max_amount                numeric(15, 2) not null,
    min_term_months           int            not null,
    max_term_months           int            not null,
    spread_pct                numeric(5, 2)  not null,
    requires_collateral       boolean        not null,
    routing                   varchar(20)    not null check (routing in ('AUTO_ALLOWED', 'ALWAYS_MANUAL')),
    auto_limit_amount         numeric(15, 2),
    min_avg_monthly_turnover  numeric(15, 2),
    sort_order                int            not null
);

create sequence application_number_seq start with 1000;

create table loan_application (
    id                            bigint generated always as identity primary key,
    number                        varchar(20)    not null unique,
    company_id                    bigint         not null references company (id),
    product_code                  varchar(30)    not null references product (code),
    purpose                       varchar(30)    not null,
    requested_amount              numeric(15, 2) not null,
    requested_term_months         int            not null,
    monthly_obligations_declared  numeric(15, 2),
    collateral_description        varchar(1000),
    consent_data_processing       boolean        not null default false,
    consent_credit_bureau         boolean        not null default false,
    status                        varchar(20)    not null check (status in
        ('DRAFT', 'SUBMITTED', 'NEED_INFO', 'SCORING', 'MANUAL_REVIEW', 'OFFER_MADE', 'SIGNING', 'ISSUED', 'DECLINED', 'CLOSED')),
    decision_reason_code          varchar(50),
    decision_summary              varchar(1000),
    current_offer_id              bigint,
    parent_application_id         bigint references loan_application (id),
    version                       int            not null default 0,
    created_at                    timestamptz    not null default now(),
    updated_at                    timestamptz    not null default now(),
    submitted_at                  timestamptz
);
create index idx_application_company on loan_application (company_id);
create index idx_application_status on loan_application (status);

create table application_document (
    id                bigint generated always as identity primary key,
    application_id    bigint       not null references loan_application (id),
    doc_type          varchar(30)  not null,
    file_name         varchar(255) not null,
    content_type      varchar(100) not null,
    storage_path      varchar(500) not null,
    size_bytes        bigint       not null,
    uploaded_by_role  varchar(20)  not null,
    uploaded_at       timestamptz  not null default now(),
    is_new_for_bank   boolean      not null default false
);
create index idx_document_application on application_document (application_id);

create table external_check_result (
    id              bigint generated always as identity primary key,
    application_id  bigint      not null references loan_application (id),
    source          varchar(20) not null check (source in ('REGISTRY', 'BUREAU', 'TAX')),
    status          varchar(20) not null check (status in ('OK', 'UNAVAILABLE')),
    payload         jsonb       not null,
    received_at     timestamptz not null default now()
);
create index idx_check_application on external_check_result (application_id);

create table scoring_result (
    id                bigint generated always as identity primary key,
    application_id    bigint       not null references loan_application (id),
    run_no            int          not null,
    total_score       int          not null,
    grade             varchar(1)   not null check (grade in ('A', 'B', 'C', 'D')),
    hard_stop         boolean      not null,
    hard_stop_reason  varchar(200),
    payment_load      numeric(6, 3) not null,
    factors           jsonb        not null,
    inputs_snapshot   jsonb        not null,
    trigger           varchar(10)  not null check (trigger in ('INITIAL', 'RECALC')),
    created_by        varchar(50)  not null,
    created_at        timestamptz  not null default now(),
    unique (application_id, run_no)
);

create table offer (
    id               bigint generated always as identity primary key,
    application_id   bigint         not null references loan_application (id),
    kind             varchar(10)    not null check (kind in ('INITIAL', 'COUNTER')),
    amount           numeric(15, 2) not null,
    term_months      int            not null,
    rate_pct         numeric(5, 2)  not null,
    monthly_payment  numeric(15, 2) not null,
    total_payment    numeric(15, 2) not null,
    overpayment      numeric(15, 2) not null,
    status           varchar(12)    not null check (status in ('ACTIVE', 'ACCEPTED', 'DECLINED', 'SUPERSEDED')),
    explanation      jsonb          not null,
    created_by       varchar(50)    not null,
    created_at       timestamptz    not null default now()
);
create index idx_offer_application on offer (application_id);

create table bank_task (
    id                bigint generated always as identity primary key,
    application_id    bigint       not null references loan_application (id),
    queue             varchar(30)  not null default 'UNDERWRITING',
    priority          int          not null check (priority between 1 and 10),
    status            varchar(12)  not null check (status in ('NEW', 'IN_PROGRESS', 'POSTPONED', 'DONE')),
    assignee_user_id  bigint references app_user (id),
    reason_codes      varchar(300) not null,
    is_repeat         boolean      not null default false,
    sla_due_at        timestamptz  not null,
    created_at        timestamptz  not null default now(),
    taken_at          timestamptz,
    finished_at       timestamptz,
    version           int          not null default 0
);
create index idx_task_queue on bank_task (status, priority, created_at);
-- У андеррайтера не больше одной задачи в работе
create unique index uq_task_one_in_progress on bank_task (assignee_user_id) where status = 'IN_PROGRESS';

create table task_check (
    id          bigint generated always as identity primary key,
    task_id     bigint       not null references bank_task (id),
    check_code  varchar(30)  not null,
    title       varchar(100) not null,
    goal        varchar(300) not null,
    status      varchar(12)  not null check (status in ('PENDING', 'OK', 'NEGATIVE', 'NEEDS_INFO')),
    comment     varchar(1000),
    updated_at  timestamptz  not null default now(),
    unique (task_id, check_code)
);

create table bank_decision (
    id             bigint generated always as identity primary key,
    task_id        bigint       not null references bank_task (id),
    result         varchar(25)  not null check (result in ('APPROVE', 'APPROVE_WITH_CHANGES', 'REQUEST_INFO', 'DECLINE', 'POSTPONE')),
    comment        varchar(1000),
    counter_offer  jsonb,
    decided_by     bigint       not null references app_user (id),
    created_at     timestamptz  not null default now()
);

create table info_request (
    id              bigint generated always as identity primary key,
    application_id  bigint        not null references loan_application (id),
    task_id         bigint references bank_task (id),
    source          varchar(10)   not null check (source in ('SYSTEM', 'BANK')),
    text            varchar(1000) not null,
    status          varchar(10)   not null check (status in ('OPEN', 'ANSWERED')),
    client_answer   varchar(2000),
    created_at      timestamptz   not null default now(),
    answered_at     timestamptz
);
create index idx_info_request_application on info_request (application_id);

create table signing_session (
    id              bigint generated always as identity primary key,
    application_id  bigint      not null references loan_application (id),
    code_hash       varchar(64) not null,
    attempts_left   int         not null,
    status          varchar(10) not null check (status in ('ACTIVE', 'CONFIRMED', 'BLOCKED', 'REPLACED')),
    created_at      timestamptz not null default now()
);
create index idx_signing_application on signing_session (application_id);

create table disbursement (
    id              bigint generated always as identity primary key,
    application_id  bigint         not null unique references loan_application (id),
    amount          numeric(15, 2) not null,
    account_masked  varchar(30)    not null,
    executed_at     timestamptz    not null default now(),
    simulated       boolean        not null default true
);

create table status_history (
    id              bigint generated always as identity primary key,
    application_id  bigint        not null references loan_application (id),
    from_status     varchar(20),
    to_status       varchar(20)   not null,
    actor_type      varchar(10)   not null check (actor_type in ('CLIENT', 'BANK', 'SYSTEM')),
    actor_id        bigint,
    comment         varchar(1000),
    created_at      timestamptz   not null default now()
);
create index idx_history_application on status_history (application_id, id);

create table audit_event (
    id              bigint generated always as identity primary key,
    application_id  bigint,
    actor_type      varchar(10)   not null,
    actor_id        bigint,
    event_type      varchar(50)   not null,
    details         varchar(2000),
    created_at      timestamptz   not null default now()
);
create index idx_audit_application on audit_event (application_id);

create table news_catalog (
    id                    bigint generated always as identity primary key,
    type                  varchar(10)   not null check (type in ('NEWS', 'TIP')),
    title                 varchar(200)  not null,
    body                  varchar(1000) not null,
    target_legal_form     varchar(3),
    target_industry_risk  varchar(6),
    target_product_code   varchar(30),
    target_status         varchar(20),
    weight                int           not null default 0
);

create table feed_item (
    id                      bigint generated always as identity primary key,
    company_id              bigint        not null references company (id),
    type                    varchar(10)   not null check (type in ('NEWS', 'TIP', 'STATUS')),
    title                   varchar(200)  not null,
    body                    varchar(1000) not null,
    reason                  varchar(300)  not null,
    related_application_id  bigint references loan_application (id),
    created_at              timestamptz   not null default now(),
    is_read                 boolean       not null default false
);
create index idx_feed_company on feed_item (company_id, created_at desc);
