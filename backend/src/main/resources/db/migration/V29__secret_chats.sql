CREATE TABLE IF NOT EXISTS secret_chats (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    initiator_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    initiator_session_id UUID NOT NULL REFERENCES user_sessions (id) ON DELETE CASCADE,
    recipient_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    recipient_session_id UUID REFERENCES user_sessions (id) ON DELETE SET NULL,
    initiator_public_key VARCHAR(255) NOT NULL,
    recipient_public_key VARCHAR(255),
    shared_key_fingerprint VARCHAR(128),
    auto_delete_seconds INTEGER,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    accepted_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    last_message_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_secret_chats_initiator
    ON secret_chats (initiator_user_id, initiator_session_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_secret_chats_recipient
    ON secret_chats (recipient_user_id, recipient_session_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS secret_chat_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    secret_chat_id UUID NOT NULL REFERENCES secret_chats (id) ON DELETE CASCADE,
    sender_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    sender_session_id UUID NOT NULL REFERENCES user_sessions (id) ON DELETE CASCADE,
    message_type VARCHAR(16) NOT NULL,
    ciphertext TEXT NOT NULL,
    nonce VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_secret_chat_messages_chat_created
    ON secret_chat_messages (secret_chat_id, created_at DESC);
