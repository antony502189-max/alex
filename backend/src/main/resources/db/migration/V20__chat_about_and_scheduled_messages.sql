ALTER TABLE chats
ADD COLUMN IF NOT EXISTS about VARCHAR(500);

CREATE TABLE IF NOT EXISTS scheduled_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    ciphertext TEXT NOT NULL,
    nonce TEXT NOT NULL,
    key_version INTEGER NOT NULL,
    reply_to_message_id UUID,
    sticker_id UUID,
    attachment_ids TEXT NOT NULL DEFAULT '',
    scheduled_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    delivered_message_id UUID,
    error_message VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_scheduled_messages_due
ON scheduled_messages (status, scheduled_at);

CREATE INDEX IF NOT EXISTS idx_scheduled_messages_sender_chat
ON scheduled_messages (sender_id, chat_id, status, scheduled_at);
