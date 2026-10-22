# Описание API

> Статус: проектное описание. Источник истины — `openapi.yaml`, который экспортируется из работающего приложения (команда экспорта — в README). После реализации сверить пути и поля.

Базовый путь: `/api`. Формат: JSON, кодировка UTF-8. Интерактивная документация доступна в Swagger UI (адрес — в README).

## Аутентификация

Вход по логину и паролю возвращает JWT с коротким временем жизни. Токен передаётся в заголовке `Authorization: Bearer <token>`. Роли: `CLIENT` и `UNDERWRITER`. Права проверяются на сервере для каждого запроса.

## Формат ошибок

Все ошибки возвращаются в формате problem+json (RFC 7807):

```json
{
  "type": "about:blank",
  "title": "Недопустимый переход статуса",
  "status": 409,
  "detail": "Заявка в статусе DECLINED не может быть отправлена",
  "code": "INVALID_STATUS_TRANSITION",
  "traceId": "c1f0..."
}
```

| Код | Когда |
|---|---|
| 400 | ошибка валидации входных данных |
| 401 | нет или просрочен токен |
| 403 | недостаточно прав (чужая заявка, не та роль) |
| 404 | объект не найден |
| 409 | недопустимый переход статуса или конфликт версий |
| 422 | нарушено бизнес-правило (например, не заполнены проверки) |

## Эндпоинты

### Общие

| Метод и путь | Назначение |
|---|---|
| `POST /api/auth/login` | вход, возвращает токен и профиль |
| `GET /api/auth/me` | текущий пользователь и роль |
| `GET /api/products` | каталог продуктов |

### Клиент (`CLIENT`)

| Метод и путь | Назначение |
|---|---|
| `GET /api/client/profile` | профиль компании и финансовый снимок |
| `POST /api/products/recommend` | подбор продуктов по цели, сумме и сроку, с объяснением |
| `POST /api/applications` | создать черновик заявки |
| `PUT /api/applications/{id}` | обновить черновик (автосохранение) |
| `POST /api/applications/{id}/submit` | отправить заявку, ответ `202`, обработка асинхронная |
| `GET /api/applications` | мои заявки |
| `GET /api/applications/{id}` | заявка: статус, история, пояснение решения, текущее предложение |
| `POST /api/applications/{id}/documents` | загрузить документ (multipart) |
| `GET /api/applications/{id}/documents/{docId}` | скачать документ |
| `POST /api/applications/{id}/info-requests/{rid}/answer` | ответить на запрос банка |
| `POST /api/applications/{id}/offer/accept` | принять предложение |
| `POST /api/applications/{id}/offer/decline` | отклонить предложение |
| `POST /api/applications/{id}/signing/send-code` | получить код подтверждения (в демо-режиме код возвращается для показа в интерфейсе) |
| `POST /api/applications/{id}/signing/confirm` | подтвердить код, запускается симуляция выдачи |
| `GET /api/feed` | лента «Для вас» |
| `POST /api/feed/{id}/read` | отметить прочитанным |

### Банк (`UNDERWRITER`)

| Метод и путь | Назначение |
|---|---|
| `GET /api/bank/tasks` | очередь задач, фильтры по статусу и «мои» |
| `POST /api/bank/tasks/take-next` | взять следующую задачу по правилам очереди |
| `POST /api/bank/tasks/{id}/take` | взять конкретную задачу |
| `GET /api/bank/tasks/{id}` | карточка: данные, документы, скоринг с разбором, проверки, история |
| `PUT /api/bank/tasks/{id}/checks/{code}` | результат проверки |
| `POST /api/bank/tasks/{id}/recalculate` | пересчёт скоринга с изменёнными оборотом, суммой или сроком |
| `POST /api/bank/tasks/{id}/decision` | решение: одобрить, одобрить с изменениями, запросить данные, отказать, отложить |

### Служебные

| Метод и путь | Назначение |
|---|---|
| `GET /actuator/health` | состояние сервиса |
| `POST /api/dev/reset-demo` | сброс демо-данных (только профиль `demo`) |

## Примеры

Вход:

```http
POST /api/auth/login
Content-Type: application/json

{ "login": "romashka", "password": "Demo#2026" }
```

Подбор продукта:

```http
POST /api/products/recommend
Authorization: Bearer <token>

{ "purpose": "WORKING_CAPITAL", "amount": 1200000, "termMonths": 9 }
```

Решение андеррайтера (встречное предложение):

```http
POST /api/bank/tasks/42/decision
Authorization: Bearer <token>

{
  "result": "APPROVE_WITH_CHANGES",
  "comment": "Сумма снижена из-за платёжной нагрузки",
  "counterOffer": { "amount": 900000, "termMonths": 9 }
}
```

## Коллекция запросов

В каталоге `postman/` лежит коллекция и environment. Токен подставляется скриптом после запроса входа. Прогон из командной строки: `newman run postman/collection.json -e postman/environment.json` (точная команда — в README).
