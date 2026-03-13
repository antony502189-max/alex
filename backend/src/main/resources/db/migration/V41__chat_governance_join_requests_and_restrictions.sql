ALTER TABLE chats
    ADD COLUMN IF NOT EXISTS join_requires_approval BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE chat_members
    ADD COLUMN IF NOT EXISTS can_send_messages BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS restricted_until TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS restriction_reason VARCHAR(255),
    ADD COLUMN IF NOT EXISTS restricted_by_user_id UUID;

CREATE TABLE IF NOT EXISTS chat_join_requests (
    chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    source VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    invite_link_id UUID REFERENCES chat_invite_links(id) ON DELETE SET NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    decided_at TIMESTAMPTZ,
    decided_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    PRIMARY KEY (chat_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_join_requests_chat_status_requested
    ON chat_join_requests (chat_id, status, requested_at DESC);
