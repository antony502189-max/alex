ALTER TABLE user_sessions
    ADD COLUMN IF NOT EXISTS push_provider VARCHAR(16),
    ADD COLUMN IF NOT EXISTS push_token VARCHAR(255),
    ADD COLUMN IF NOT EXISTS notifications_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_user_sessions_push_tokens
    ON user_sessions (user_id, notifications_enabled);
