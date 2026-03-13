CREATE TABLE IF NOT EXISTS chat_pin_events (
    id UUID PRIMARY KEY,
    chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    message_id UUID NOT NULL,
    pinned_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    pinned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    active BOOLEAN NOT NULL DEFAULT FALSE,
    unpinned_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_chat_pin_events_chat_pinned_at
    ON chat_pin_events (chat_id, pinned_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS idx_chat_pin_events_chat_active
    ON chat_pin_events (chat_id)
    WHERE active;
