# Описание API

> Статус: сверено с контроллерами. Полная спецификация — [`openapi.yaml`](openapi.yaml); при запущенном приложении её можно выгрузить заново командой из README.

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
| 429 | слишком много неудачных попыток входа; заголовок `Retry-After` содержит секунды до следующей попытки |

Частые коды в поле `code`: `VALIDATION_ERROR`, `BAD_CREDENTIALS`, `INVALID_STATUS_TRANSITION`, `INVALID_STATUS`, `CONSENTS_REQUIRED`, `AMOUNT_OUT_OF_RANGE`, `TERM_OUT_OF_RANGE`, `CHECKS_INCOMPLETE`, `NEGATIVE_CHECK`, `CHECK_NEEDS_INFO`, `DECLINE_REASON_REQUIRED`, `TERMS_NOT_ACCEPTED`, `FILE_CONTENT_MISMATCH`, `TOO_MANY_ATTEMPTS`, `TASK_ALREADY_IN_PROGRESS`, `QUEUE_EMPTY`, `CONCURRENT_MODIFICATION`.

Чужая заявка возвращает `404`, а не `403`, чтобы не раскрывать факт её существования.

## Эндпоинты

### Общие

| Метод и путь | Назначение |
|---|---|
| `POST /api/auth/login` | вход, возвращает токен и профиль |
| `GET /api/auth/me` | текущий пользователь и роль |
| `GET /api/products` | каталог продуктов |
| `GET /api/products/purposes` | справочник целей финансирования |

### Клиент (`CLIENT`)

| Метод и путь | Назначение |
|---|---|
| `GET /api/client/profile` | профиль компании и финансовый снимок |
| `POST /api/products/recommend` | подбор продуктов по цели, сумме и сроку, с объяснением |
| `POST /api/applications` | создать черновик заявки |
| `PUT /api/applications/{id}` | обновить черновик (автосохранение) |
| `POST /api/applications/{id}/submit` | отправить заявку, ответ `202`, обработка асинхронная |
| `GET /api/applications` | мои заявки |
| `GET /api/applications/{id}` | заявка: статус, следующий шаг, история с нейтральными комментариями, текущее предложение и что в нём изменилось. Баллов, грейда, факторов и причин решения в ответе клиенту нет |
| `POST /api/applications/{id}/documents` | загрузить документ (multipart) |
| `GET /api/applications/{id}/documents/{docId}` | скачать документ |
| `POST /api/applications/{id}/info-requests/{rid}/answer` | ответить на запрос банка |
| `POST /api/applications/{id}/offer/accept` | принять предложение |
| `POST /api/applications/{id}/offer/decline` | отклонить предложение |
| `POST /api/applications/{id}/sign` | подписать договор: тело `{"termsAccepted": true}`, выполняется симуляция выдачи, сумма зачисляется на счёт |
| `GET /api/client/profile` | данные компании, маска и остаток расчётного счёта |
| `GET /api/client/activity` | история операций: зачисления кредитов и события по заявкам, новые сверху |
| `GET /api/feed` | лента «Для вас»: непрочитанные события и новости с датой |
| `POST /api/feed/{id}/read` | убрать карточку из ленты: `event-N` (событие) или `news-N` (новость, подсказка) |

### Банк (`UNDERWRITER`)

| Метод и путь | Назначение |
|---|---|
| `GET /api/bank/tasks?filter=` | очередь задач; `active` (по умолчанию), `mine`, `new`, `postponed`, `done` |
| `POST /api/bank/tasks/take-next` | взять следующую задачу по правилам очереди |
| `POST /api/bank/tasks/{id}/take` | взять конкретную задачу |
| `GET /api/bank/tasks/{id}` | карточка: данные, документы, скоринг с разбором, проверки, история |
| `PUT /api/bank/tasks/{id}/checks/{code}` | результат проверки |
| `POST /api/bank/tasks/{id}/recalculate` | пересчёт скоринга с изменёнными оборотом, суммой или сроком |
| `POST /api/bank/tasks/{id}/decision` | решение: `APPROVE`, `APPROVE_WITH_CHANGES`, `REQUEST_INFO`, `DECLINE`, `POSTPONE` |
| `GET /api/bank/applications/{id}/documents/{docId}` | скачать документ заявки |

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

Запрос данных у клиента:

```http
POST /api/bank/tasks/42/decision
Authorization: Bearer <token>

{ "result": "REQUEST_INFO", "requestText": "Приложите выписку по счёту за 12 месяцев" }
```

Подписание договора (без `termsAccepted: true` ответ `422 TERMS_NOT_ACCEPTED`, повторное подписание `409`):

```http
POST /api/applications/17/sign
Authorization: Bearer <token>

{ "termsAccepted": true }
```

## Коллекция запросов

В каталоге `postman/` лежит коллекция и environment. Коллекция сначала сбрасывает демо-данные, токены и идентификаторы подставляются скриптами. Покрыты вход и роли, S1 целиком (подписание, остаток счёта, история операций, лента и «Прочитано»), S2, S5 и путь андеррайтера в S4. В ответах клиенту проверяется отсутствие полей внутренней оценки. Прогон из корня репозитория: `npx newman run postman/collection.json -e postman/environment.json --working-dir .`
