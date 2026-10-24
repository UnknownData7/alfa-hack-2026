# Сторонние компоненты

Перечень сторонних компонентов, моделей, данных и сервисов, используемых в решении. Все компоненты бесплатные и распространяются под открытыми лицензиями.

## Backend

Версии транзитивных зависимостей управляются `spring-boot-starter-parent` 3.5.6.

| Компонент | Версия | Лицензия | Назначение |
|---|---|---|---|
| Spring Boot (web, validation, security, data-jpa, actuator, oauth2-resource-server) | 3.5.6 | Apache License 2.0 | каркас приложения, REST, безопасность, доступ к данным |
| Spring Security OAuth2 JOSE (Nimbus JOSE + JWT) | по BOM Spring Boot 3.5.6 | Apache License 2.0 | выпуск и проверка JWT |
| Hibernate ORM | по BOM Spring Boot 3.5.6 | Apache License 2.0 | JPA |
| Flyway (core, database-postgresql) | по BOM Spring Boot 3.5.6 | Apache License 2.0 | миграции схемы |
| PostgreSQL JDBC Driver | по BOM Spring Boot 3.5.6 | BSD 2-Clause | драйвер БД |
| springdoc-openapi-starter-webmvc-ui | 2.8.9 | Apache License 2.0 | OpenAPI и Swagger UI |
| Jackson | по BOM Spring Boot 3.5.6 | Apache License 2.0 | JSON |
| Micrometer Registry Prometheus | по BOM Spring Boot 3.5.6 | Apache License 2.0 | метрики в формате Prometheus |

Тестовые зависимости:

| Компонент | Версия | Лицензия |
|---|---|---|
| spring-boot-starter-test (JUnit 5, Mockito, AssertJ) | 3.5.6 | Apache License 2.0, EPL 2.0, MIT |
| spring-security-test | по BOM Spring Boot 3.5.6 | Apache License 2.0 |
| spring-boot-testcontainers, Testcontainers (junit-jupiter, postgresql) | по BOM Spring Boot 3.5.6 | Apache License 2.0, MIT |

## Frontend

Точные версии всех пакетов, включая транзитивные, зафиксированы в `frontend/package-lock.json`.

| Компонент | Версия | Лицензия |
|---|---|---|
| React, React DOM | 18.3.1 | MIT |
| React Router DOM | 6.30.6 | MIT |
| TanStack Query (React) | 5.104.5 | MIT |
| Vite | 6.4.5 | MIT |
| @vitejs/plugin-react | 4.7.0 | MIT |
| TypeScript | 5.8.3 | Apache License 2.0 |
| Vitest | 3.2.7 | MIT |
| Testing Library (react, dom, jest-dom) | 16.3.3, 10.4.2, 6.9.1 | MIT |
| jsdom | 26.1.0 | MIT |
| @types/react, @types/react-dom | 18.3.31, 18.3.7 | MIT |
| @fontsource-variable/golos-text (шрифт Golos Text) | 5.3.0 | SIL Open Font License 1.1 |

Шрифт Golos Text входит в сборку интерфейса и отдаётся с того же сервера. Иконки нарисованы в коде, внешние шрифты, иконки и CDN не используются.

## Образы Docker

| Образ | Тег | Лицензия |
|---|---|---|
| postgres | 16.10-alpine | PostgreSQL License |
| eclipse-temurin (запуск backend) | 21.0.8_9-jre-alpine | GPL v2 with Classpath Exception |
| maven (этап сборки) | 3.9.11-eclipse-temurin-21 | Apache License 2.0 |
| node (этап сборки) | 22.20.0-alpine | MIT |
| nginx | 1.28.0-alpine | BSD 2-Clause |

## Инструменты проверки (не входят в поставку)

| Инструмент | Лицензия | Назначение |
|---|---|---|
| Newman (Postman CLI) | Apache License 2.0 | прогон коллекции `postman/collection.json` |
| Maven Wrapper | Apache License 2.0 | запуск Maven 3.9.11 без предустановки (`backend/mvnw`) |
| GitHub Actions: checkout, setup-java, setup-node, upload-artifact (v5) | MIT | CI: тесты, сборка, смоук-тест |
| Dependabot | сервис GitHub | предложения обновлений зависимостей |

## Смоделированные интеграции

Реальные внешние системы не вызываются. Вместо них используются локальные моки за интерфейсами (подробнее в `docs/ASSUMPTIONS.md`):

| Интеграция | Интерфейс | Мок |
|---|---|---|
| Реестр юрлиц и ИП (аналог ЕГРЮЛ/ЕГРИП) | `CompanyRegistryClient` | `MockCompanyRegistryClient` |
| Бюро кредитных историй | `CreditBureauClient` | `MockCreditBureauClient` |
| Сведения ФНС о налоговой задолженности | `TaxServiceClient` | `MockTaxServiceClient` |
| Выдача денежных средств | `DisbursementProvider` | `MockDisbursementProvider` (только запись о симуляции) |

## Данные

Все данные синтетические и созданы командой: компании, люди, ИНН, обороты, кредитные истории, новости. Внешние датасеты не используются.

## Модели ИИ

Не используются. Скоринг построен на прозрачных правилах.
