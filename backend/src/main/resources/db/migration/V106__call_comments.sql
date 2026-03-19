CREATE TABLE IF NOT EXISTS call_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    call_id UUID NOT NULL REFERENCES call_sessions (id) ON DELETE CASCADE,
    chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    author_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    content VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_call_comments_call_created
    ON call_comments (call_id, created_at DESC);
