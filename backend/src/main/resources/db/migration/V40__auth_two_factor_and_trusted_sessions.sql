ALTER TABLE users
    ADD COLUMN IF NOT EXISTS two_factor_password_hash VARCHAR(255),
    ADD COLUMN IF NOT EXISTS two_factor_password_salt VARCHAR(255),
    ADD COLUMN IF NOT EXISTS two_factor_hint VARCHAR(120),
    ADD COLUMN IF NOT EXISTS two_factor_enabled_at TIMESTAMPTZ;

ALTER TABLE user_sessions
    ADD COLUMN IF NOT EXISTS trusted_session BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS trusted_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS auth_two_factor_challenges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    display_name VARCHAR(120),
    device_name VARCHAR(120),
    platform VARCHAR(32),
    app_version VARCHAR(32),
    requested_by_ip VARCHAR(64),
    requested_by_user_agent VARCHAR(255),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 5,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_auth_two_factor_challenges_user_created
    ON auth_two_factor_challenges (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_auth_two_factor_challenges_expires
    ON auth_two_factor_challenges (expires_at, consumed_at);
