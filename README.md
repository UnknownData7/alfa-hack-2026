# Умный кредитный кабинет предпринимателя

Прототип цифрового кредитного конвейера для малого и микробизнеса. В одном продукте соединены клиентский путь (подбор продукта, заявка, предложение, подписание, выдача) и обработка заявки банком (проверки, скоринг, кредитное решение, ручная проверка андеррайтером). Решение подготовлено для хакатона «Альфа Будущее».

> Статус: каркас (этап 0). Разделы ниже дополняются по мере реализации этапов, журнал работ в `docs/DEV_LOG.md`.

## Архитектура

Модульный монолит на Spring Boot, PostgreSQL, веб-интерфейс на React за nginx. Всё запускается в Docker Compose. Подробное описание в [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md), диаграммы процесса и состояний в [`docs/DIAGRAMS.md`](docs/DIAGRAMS.md).

| Сервис | Назначение | Образ |
|---|---|---|
| `frontend` | статика React и прокси `/api` к backend | `nginx:1.28.0-alpine` |
| `backend` | REST API и кредитный конвейер | `eclipse-temurin:21.0.8_9-jre-alpine` |
| `db` | хранилище данных | `postgres:16.10-alpine` |

## Требования к окружению

- Docker Engine 24+ и Docker Compose v2 (или Docker Desktop)
- около 2 ГБ свободной оперативной памяти для контейнеров, интернет нужен только для первой сборки образов
- свободные порты 3000 и 8081 (меняются в `.env`)

## Запуск

```bash
cp .env.example .env
docker compose up --build
```

Первая сборка занимает несколько минут: Maven и npm скачивают зависимости.

| Адрес | Что там |
|---|---|
| http://localhost:3000 | веб-интерфейс |
| http://localhost:3000/actuator/health | состояние backend через nginx |
| http://localhost:8081/actuator/health | состояние backend напрямую |

Проверка, что всё поднялось: `docker compose ps` показывает у сервисов статус `healthy`.

Остановка: `docker compose down`. Полный сброс вместе с данными: `docker compose down -v`.

## Переменные окружения

| Переменная | Назначение | По умолчанию |
|---|---|---|
| `FRONTEND_PORT` | порт веб-интерфейса на хосте | `3000` |
| `BACKEND_PORT` | порт backend на хосте | `8081` |
| `POSTGRES_DB`, `POSTGRES_USER` | имя базы и пользователя | `credit` |
| `POSTGRES_PASSWORD` | пароль базы | задаётся в `.env` |
| `SPRING_PROFILES_ACTIVE` | профиль; `demo` засевает синтетические данные | `demo` |
| `JWT_SECRET` | секрет подписи токенов, не короче 32 символов | задаётся в `.env` |
| `JWT_TTL_MINUTES` | время жизни токена | `60` |
| `KEY_RATE_PERCENT` | ключевая ставка для расчёта цены, % | `15.0` |
| `PIPELINE_STEP_DELAY_MS` | пауза между шагами конвейера | `800` |
| `MOCK_LATENCY_MS` | имитация задержки внешних источников | `200` |
| `JAVA_OPTS` | параметры памяти JVM | `-Xms128m -Xmx512m` |

## Разработка без Docker

```bash
# backend (нужны JDK 21 и Maven)
cd backend && mvn spring-boot:run

# frontend (нужен Node 22)
cd frontend && npm ci && npm run dev
```

Vite проксирует `/api` и `/actuator` на `localhost:8080`.

## Тесты

```bash
cd backend && mvn test
cd frontend && npm test
```

## Структура репозитория

```
backend/     Spring Boot, Maven, Dockerfile
frontend/    React + Vite, Dockerfile, nginx.conf
docs/        архитектура, модель данных, API, процесс, сценарии, допущения
```

## Данные

Все данные синтетические. Реальных компаний, людей, ИНН и счетов в проекте нет.
