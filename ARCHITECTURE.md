# Alex MVP Architecture

## Current State Note

- This note originally described the earliest MVP slice and no longer captures the full codebase.
- The monolith already contains product slices for calls, stories, stickers, folders, invite links, forum topics, bots, mini apps, push notifications, secret chats, and offline/mobile synchronization helpers.
- Internal message export is now being fronted by a case-based compliance workflow with approval and audit metadata, while the low-level decrypted export service remains an internal building block.

## Feature Gating

The backend now supports runtime feature gates for:

- stories
- bots
- calls
- secret chats
- admin compliance

These gates are intended to support phased rollout and safer roadmap execution without branching the codebase.

## Goal

Build an MVP messenger for Belarus with a backend architecture that can evolve toward 10M+ users without replacing the core data and messaging model.

## Functional Scope

- mock auth by phone number
- chat list for a user
- one-to-one text messaging
- persistence of chat metadata and encrypted message bodies
- Kafka-based inter-instance message routing
- WebSocket delivery to the online recipient
- server-side lawful interception capability

## High-Level Components

### Mobile Client

- React Native / Expo application
- REST for login, chat list, message history, and send message
- WebSocket STOMP for real-time inbound delivery
- no end-to-end encryption on the client

### API / Messaging Service

- Spring Boot monolith for MVP
- JWT auth for REST and STOMP
- owns chat creation, message acceptance, history reads, and WebSocket push
- can later be split into `auth-service`, `chat-service`, `message-service`, and `gateway`

### PostgreSQL

Stores relational and strongly consistent metadata:

- `users`
- `chats`
- `encryption_keys`

Why PostgreSQL:

- strong consistency for auth and chat metadata
- simple transactional semantics
- easy unique constraints for direct-chat pairs

### Cassandra

Stores high-volume message history in `messages_by_chat`.

Why Cassandra:

- append-heavy workload
- horizontal scale for large chat history volumes
- efficient partitioning by `chat_id`

### Kafka

Topic `chat-messages` is the internal event backbone.

Why Kafka:

- decouples write path from delivery path
- enables multiple backend instances to process the same stream safely
- provides a forward path to fanout, notifications, analytics, moderation, and auditing

### WebSocket Layer

- STOMP endpoint `/ws`
- user-scoped destination `/user/queue/messages`
- for MVP, Spring simple broker is enough
- for production scale, move to broker relay or dedicated push gateway tier

## Message Write Flow

1. Client calls `POST /api/messages`.
2. Backend authenticates JWT.
3. Backend resolves existing direct chat or creates it.
4. Backend loads the chat key from PostgreSQL or creates it if absent.
5. Backend encrypts plaintext with AES-GCM on the server.
6. Backend stores ciphertext, nonce, and key version in Cassandra.
7. Backend updates `last_message_at` in PostgreSQL.
8. Backend publishes `MessageEvent` into Kafka.
9. Kafka listener consumes the event.
10. Backend decrypts the payload server-side.
11. Backend pushes plaintext to recipient via WebSocket.

## Encryption Model

- No client-side E2E encryption
- Per-chat symmetric key
- Key lives in PostgreSQL table `encryption_keys`
- Cipher: AES/GCM
- Stored message fields:
  - ciphertext
  - nonce
  - key_version

This model preserves server visibility for moderation, abuse handling, and lawful interception.

## Lawful Interception

MVP includes the `LawfulInterceptionService` interface.

Expected production behavior:

- export all decrypted messages for a target user over a requested time window
- use strict access control and audit trail
- isolate execution path and encrypt exported artifacts at rest

## Scale Path to 10M+ Users

### Phase 1: MVP to Early Production

- run multiple stateless Spring Boot instances
- externalize JWT secret
- keep Kafka as central routing bus
- keep PostgreSQL primary with replicas
- run Cassandra as a multi-node cluster

### Phase 2: Regional Growth

- split WebSocket gateway from REST API nodes
- introduce Redis for presence and ephemeral session routing
- use Kafka partitions keyed by `chatId`
- add outbox pattern for PostgreSQL-to-Kafka consistency
- add consumer groups for delivery, notifications, moderation, and analytics

### Phase 3: 10M+ Users

- separate bounded contexts:
  - auth
  - user profile
  - chat metadata
  - message ingest
  - message history
  - notification / push
  - lawful interception / audit
- deploy dedicated WebSocket gateway fleet
- use service mesh / API gateway
- enable multi-DC Cassandra topology
- partition hot chats and monitor skew
- move encryption keys to KMS or HSM-backed storage

## Operational Concerns

### Reliability

- idempotent producer settings for Kafka
- retries with dead-letter handling for delivery failures
- consumer lag monitoring
- health probes for PostgreSQL, Cassandra, Kafka

### Observability

- request metrics
- Kafka producer/consumer metrics
- Cassandra read/write latency
- WebSocket connection counts
- structured audit logs for auth and message export

### Security

- rotate JWT secret and chat keys
- store secrets outside source control
- audit all lawful interception access
- encrypt backups and exports

## MVP Tradeoffs

- monolith instead of microservices for speed
- simple STOMP broker instead of distributed broker relay
- mock auth instead of SMS OTP
- no attachments, read receipts, or offline notification service

These are deliberate MVP cuts, not dead ends in the architecture.
