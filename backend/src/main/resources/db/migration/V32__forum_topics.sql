ALTER TABLE chats
    ADD COLUMN IF NOT EXISTS forum_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS forum_topics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    icon_emoji VARCHAR(32),
    created_by UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    general_topic BOOLEAN NOT NULL DEFAULT FALSE,
    closed BOOLEAN NOT NULL DEFAULT FALSE,
    hidden BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_message_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_forum_topics_chat_updated
    ON forum_topics (chat_id, hidden, general_topic DESC, COALESCE(last_message_at, created_at) DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_forum_topics_chat_general
    ON forum_topics (chat_id)
    WHERE general_topic = TRUE;

ALTER TABLE scheduled_messages
    ADD COLUMN IF NOT EXISTS topic_id UUID REFERENCES forum_topics (id) ON DELETE SET NULL;
