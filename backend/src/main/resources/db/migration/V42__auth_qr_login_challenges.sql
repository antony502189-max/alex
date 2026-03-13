CREATE TABLE auth_qr_login_challenges (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_by_session_id UUID NOT NULL REFERENCES user_sessions(id) ON DELETE CASCADE,
    qr_token_hash VARCHAR(128) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL DEFAULT 'NEW',
    bound_device_name VARCHAR(120),
    bound_platform VARCHAR(32),
    bound_app_version VARCHAR(32),
    bound_ip_address VARCHAR(64),
    bound_user_agent VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    bound_at TIMESTAMPTZ,
    approved_at TIMESTAMPTZ,
    approved_by_session_id UUID REFERENCES user_sessions(id) ON DELETE SET NULL,
    declined_at TIMESTAMPTZ,
    consumed_at TIMESTAMPTZ
);

CREATE INDEX idx_auth_qr_login_challenges_user_created_at
    ON auth_qr_login_challenges (user_id, created_at DESC);

CREATE INDEX idx_auth_qr_login_challenges_status_expires_at
    ON auth_qr_login_challenges (status, expires_at);
