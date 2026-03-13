CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number VARCHAR(32) NOT NULL UNIQUE,
    display_name VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS chats (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_type VARCHAR(16) NOT NULL DEFAULT 'DIRECT',
    participant_low_id UUID NOT NULL REFERENCES users (id),
    participant_high_id UUID NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_message_at TIMESTAMPTZ,
    CONSTRAINT uq_direct_chat_pair UNIQUE (participant_low_id, participant_high_id),
    CONSTRAINT chk_distinct_participants CHECK (participant_low_id <> participant_high_id)
);

CREATE INDEX IF NOT EXISTS idx_chats_low ON chats (participant_low_id);
CREATE INDEX IF NOT EXISTS idx_chats_high ON chats (participant_high_id);

CREATE TABLE IF NOT EXISTS encryption_keys (
    chat_id UUID PRIMARY KEY REFERENCES chats (id) ON DELETE CASCADE,
    algorithm VARCHAR(32) NOT NULL,
    key_version INTEGER NOT NULL DEFAULT 1,
    key_material TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    rotated_at TIMESTAMPTZ
);
