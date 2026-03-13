# Alex MVP

## Current Status

- The repository is already beyond the original MVP scope: it includes groups, channels, folders, invite links, forum topics, scheduled messages, polls, reactions, stickers, stories, calls, built-in bots, mini apps, sessions, push notifications, media storage, and secret chats.
- The legacy README sections below describe an older subset of the product. Use the codebase as the source of truth until the remaining documentation is refreshed.
- A direct lawful-export endpoint is no longer the supported interception surface. Internal exports now move through a case-based compliance workflow.
- Authentication now supports `request-code`, `verify-code`, and `refresh` endpoints in addition to the legacy `/api/auth/login` fallback.

## Feature Flags

Backend feature flags are available through Spring properties and environment variables:

- `alex.features.stories` / `ALEX_FEATURE_STORIES_ENABLED`
- `alex.features.bots` / `ALEX_FEATURE_BOTS_ENABLED`
- `alex.features.calls` / `ALEX_FEATURE_CALLS_ENABLED`
- `alex.features.secret-chats` / `ALEX_FEATURE_SECRET_CHATS_ENABLED`
- `alex.features.admin-compliance` / `ALEX_FEATURE_ADMIN_COMPLIANCE_ENABLED`
- `alex.features.lawful-direct-export` / `ALEX_FEATURE_LAWFUL_DIRECT_EXPORT_ENABLED`
- `alex.features.group-calls` / `ALEX_FEATURE_GROUP_CALLS_ENABLED`
- `alex.features.story-interactions` / `ALEX_FEATURE_STORY_INTERACTIONS_ENABLED`
- `alex.features.bot-api-full` / `ALEX_FEATURE_BOT_API_FULL_ENABLED`
- `alex.features.business` / `ALEX_FEATURE_BUSINESS_ENABLED`
- `alex.features.payments` / `ALEX_FEATURE_PAYMENTS_ENABLED`
- `alex.features.premium` / `ALEX_FEATURE_PREMIUM_ENABLED`
- `alex.features.translations` / `ALEX_FEATURE_TRANSLATIONS_ENABLED`

Existing core flags default to `true`. Newly added roadmap flags default to `false`.

## Internal Compliance API

Internal endpoints remain protected by `X-System-Token`, and compliance actions additionally require `X-Operator-Id` for attribution.

Supported endpoints:

- `POST /api/internal/compliance/cases`
- `GET /api/internal/compliance/cases/{caseId}`
- `POST /api/internal/compliance/cases/{caseId}/approve`
- `POST /api/internal/compliance/cases/{caseId}/exports`
- `POST /api/internal/lawful/exports/direct`

MVP кроссплатформенного мессенджера с backend на Spring Boot 3 / Java 17 и frontend на React Native / Expo.

## Scope MVP

- моковая авторизация по номеру телефона с выдачей JWT
- список direct-чатов пользователя
- отправка текстового сообщения 1-на-1
- server-side encryption без E2E
- хранение ключей чатов в PostgreSQL
- хранение сообщений в Cassandra
- маршрутизация событий сообщений через Kafka
- доставка сообщения получателю через WebSocket STOMP

## Стек

- Frontend: React Native, Expo, TypeScript, Zustand, STOMP WebSocket
- Backend: Java 17+, Spring Boot 3, Spring Security, Spring WebSocket, Spring Kafka
- PostgreSQL: пользователи, чаты, ключи шифрования
- Cassandra: сообщения
- Kafka + Zookeeper: message bus

## Структура

```text
.
├── docker-compose.yml
├── infra
│   ├── postgres/init.sql
│   └── cassandra/init.cql
├── backend
│   ├── pom.xml
│   └── src/main
│       ├── java/com/alex/messenger
│       └── resources
└── frontend
    ├── package.json
    ├── App.tsx
    └── src
```

## Требования

- Docker Desktop / Docker Compose
- JDK 17+
- Maven 3.9+
- Node.js 20+
- npm 10+

## Запуск

### 1. Инфраструктура

```powershell
cd E:\Alex
docker compose up -d
```

### 2. Backend

```powershell
cd E:\Alex\backend
./mvnw spring-boot:run
```

Backend по умолчанию поднимается на `http://localhost:8080`.

Альтернативно backend можно запустить вместе с инфраструктурой через Docker Compose:

```powershell
docker compose up --build backend
```

### 3. Frontend

```powershell
cd E:\Alex\frontend
npm install
npm start
```

Если нужно явно задать адреса backend и WebSocket:

```powershell
$env:EXPO_PUBLIC_API_BASE_URL="http://localhost:8080/api"
$env:EXPO_PUBLIC_WS_URL="ws://localhost:8080/ws"
npm start
```

Для Android emulator по умолчанию уже используется `10.0.2.2`.

## Основные API

### Login

`POST /api/auth/login`

```json
{
  "phoneNumber": "+375291111111",
  "displayName": "Alex A"
}
```

### Chats

`GET /api/chats`

Header:

```text
Authorization: Bearer <jwt>
```

### Send Message

`POST /api/messages`

```json
{
  "recipientUserId": "c9fe6d42-0d03-4d41-9ac5-31d48adcb7e9",
  "text": "Привет"
}
```

Или в существующий чат:

```json
{
  "chatId": "4c2d1a6d-d39e-4c47-8fcb-b1d2e1ef8bd8",
  "text": "Привет еще раз"
}
```

### History

`GET /api/messages/chat/{chatId}?limit=50`

## Message Flow

1. Клиент вызывает `POST /api/messages`.
2. Backend находит или создает direct-chat.
3. Backend получает chat key из PostgreSQL.
4. Сообщение шифруется на сервере через AES/GCM.
5. Ciphertext сохраняется в Cassandra.
6. Событие сообщения публикуется в Kafka topic `chat-messages`.
7. Kafka listener читает событие.
8. Backend расшифровывает payload на сервере.
9. Получатель получает сообщение по WebSocket STOMP через `/user/queue/messages`.

## Безопасность

- E2E шифрование отсутствует намеренно.
- Используется только server-side encryption.
- Ключи чатов хранятся в PostgreSQL таблице `encryption_keys`.
- JWT используется для REST и STOMP подключения.
- Для lawful interception предусмотрен интерфейс `LawfulInterceptionService`.

## Проверка

Для локального smoke test:

```powershell
cd E:\Alex
powershell -ExecutionPolicy Bypass -File .\smoke-test.ps1
```

Для backend smoke / compile path:

```powershell
cd E:\Alex\backend
./mvnw test
```

## Ограничения MVP

- нет SMS OTP и refresh token flow
- только direct 1-на-1 чаты
- нет вложений, статусов доставки и read receipts
- для WebSocket используется in-memory simple broker
- lawful interception пока только интерфейс-заглушка

## Следующий этап для production

- вынести WebSocket broker relay
- добавить outbox/idempotency для publish в Kafka
- добавить key rotation и KMS/HSM
- ввести pagination и time-window queries для Cassandra
- разделить auth, chat, message, notification сервисы
- добавить observability: metrics, tracing, audit logs
