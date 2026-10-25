-- База знаний помощника: фрагменты Markdown-файлов и карточки продуктов, полнотекстовый поиск с русской морфологией
create table kb_chunk (
    id          varchar(80)   primary key,
    source      varchar(30)   not null,
    title       varchar(200)  not null,
    body        text          not null,
    keywords    text,
    -- Связь фрагмента с кодом предметной области, например цель финансирования для раздела purposes
    tag         varchar(40),
    version     varchar(16)   not null,
    indexed_at  timestamptz   not null default now(),
    tsv         tsvector generated always as (
                    setweight(to_tsvector('russian'::regconfig, title), 'A')
                    || setweight(to_tsvector('russian'::regconfig, coalesce(keywords, '')), 'A')
                    || setweight(to_tsvector('russian'::regconfig, body), 'B')) stored
);
create index idx_kb_chunk_tsv on kb_chunk using gin (tsv);
create index idx_kb_chunk_source on kb_chunk (source);
