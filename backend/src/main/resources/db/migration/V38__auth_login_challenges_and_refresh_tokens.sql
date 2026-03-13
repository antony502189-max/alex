CREATE TABLE IF NOT EXISTS auth_login_code_challenges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number VARCHAR(32) NOT NULL,
    display_name VARCHAR(120),
    device_name VARCHAR(120),
    platform VARCHAR(32),
    app_version VARCHAR(32),
    code_hash VARCHAR(128) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 5,
    requested_by_ip VARCHAR(64),
    requested_by_user_agent VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_auth_login_code_challenges_phone_created
    ON auth_login_code_challenges (phone_number, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_auth_login_code_challenges_expires
    ON auth_login_code_challenges (expires_at, consumed_at);

ALTER TABLE user_sessions
    ADD COLUMN IF NOT EXISTS auth_method VARCHAR(32) NOT NULL DEFAULT 'LEGACY_LOGIN',
    ADD COLUMN IF NOT EXISTS refresh_token_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS refresh_token_expires_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_refreshed_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_user_sessions_refresh_token_hash
    ON user_sessions (refresh_token_hash);
