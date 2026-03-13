CREATE TABLE IF NOT EXISTS chat_invite_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    created_by UUID NOT NULL REFERENCES users (id),
    token VARCHAR(64) NOT NULL UNIQUE,
    label VARCHAR(120),
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMPTZ,
    usage_limit INTEGER,
    usage_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_used_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_chat_invite_links_chat ON chat_invite_links (chat_id, created_at DESC);
