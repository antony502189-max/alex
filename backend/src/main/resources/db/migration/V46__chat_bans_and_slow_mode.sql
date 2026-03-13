ALTER TABLE chats
    ADD COLUMN IF NOT EXISTS slow_mode_seconds INTEGER;

ALTER TABLE chat_members
    ADD COLUMN IF NOT EXISTS last_sent_message_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS chat_bans (
    chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    banned_until TIMESTAMPTZ,
    reason VARCHAR(255),
    banned_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    banned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (chat_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_bans_chat_banned_at
    ON chat_bans (chat_id, banned_at DESC);
