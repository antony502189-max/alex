ALTER TABLE chats
    ADD COLUMN IF NOT EXISTS direct_messages_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS channel_direct_message_chats (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    channel_chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    direct_chat_id UUID NOT NULL UNIQUE REFERENCES chats(id) ON DELETE CASCADE,
    participant_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_channel_direct_message_participant UNIQUE (channel_chat_id, participant_user_id)
);

CREATE INDEX IF NOT EXISTS idx_channel_direct_message_chats_channel_updated
    ON channel_direct_message_chats (channel_chat_id, updated_at DESC, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_channel_direct_message_chats_participant
    ON channel_direct_message_chats (participant_user_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS channel_direct_message_topics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    channel_chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    direct_chat_id UUID NOT NULL UNIQUE REFERENCES chats(id) ON DELETE CASCADE,
    participant_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    last_message_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_channel_direct_message_topic_participant UNIQUE (channel_chat_id, participant_user_id)
);

CREATE INDEX IF NOT EXISTS idx_channel_direct_message_topics_channel_last_message
    ON channel_direct_message_topics (channel_chat_id, last_message_at DESC, created_at DESC);
