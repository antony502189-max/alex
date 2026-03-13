ALTER TABLE call_participants
    ADD COLUMN IF NOT EXISTS audio_publishing_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS video_publishing_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS screen_share_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS screen_sharing BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS moderated_by_user_id UUID REFERENCES users (id),
    ADD COLUMN IF NOT EXISTS moderated_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS call_join_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    created_by_user_id UUID NOT NULL REFERENCES users (id),
    kind VARCHAR(16) NOT NULL,
    label VARCHAR(120),
    token VARCHAR(64) NOT NULL UNIQUE,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    usage_count INTEGER NOT NULL DEFAULT 0,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_used_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_call_join_links_chat_created
    ON call_join_links (chat_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_call_join_links_token
    ON call_join_links (token);
