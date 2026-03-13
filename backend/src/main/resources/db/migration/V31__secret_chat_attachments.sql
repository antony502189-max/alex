CREATE TABLE IF NOT EXISTS secret_chat_attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    secret_chat_id UUID NOT NULL REFERENCES secret_chats (id) ON DELETE CASCADE,
    secret_message_id UUID REFERENCES secret_chat_messages (id) ON DELETE SET NULL,
    uploader_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind VARCHAR(16) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    encrypted_file_size_bytes BIGINT NOT NULL,
    storage_path VARCHAR(512) NOT NULL UNIQUE,
    storage_provider VARCHAR(32) NOT NULL,
    bucket_name VARCHAR(255),
    object_key VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_secret_chat_attachments_chat_created
    ON secret_chat_attachments (secret_chat_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_secret_chat_attachments_message
    ON secret_chat_attachments (secret_message_id);
