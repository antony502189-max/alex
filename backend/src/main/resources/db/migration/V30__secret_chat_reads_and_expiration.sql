ALTER TABLE secret_chat_messages
    ADD COLUMN IF NOT EXISTS read_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_secret_chat_messages_expires_at
    ON secret_chat_messages (expires_at);
