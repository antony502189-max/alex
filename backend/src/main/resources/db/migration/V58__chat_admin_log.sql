CREATE TABLE IF NOT EXISTS chat_admin_log_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    actor_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    subject_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    event_type VARCHAR(64) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    message_id UUID,
    invite_link_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_admin_log_chat_created
    ON chat_admin_log_events (chat_id, created_at DESC);
