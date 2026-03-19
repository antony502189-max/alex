ALTER TABLE users
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS account_self_destruct_days INTEGER NOT NULL DEFAULT 365;

CREATE INDEX IF NOT EXISTS idx_users_deleted_at
    ON users (deleted_at);

CREATE TABLE IF NOT EXISTS passkey_credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    credential_id VARCHAR(255) NOT NULL,
    public_key TEXT NOT NULL,
    transports VARCHAR(255),
    label VARCHAR(120),
    sign_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_passkey_credentials_credential_id
    ON passkey_credentials (credential_id);

CREATE INDEX IF NOT EXISTS idx_passkey_credentials_user_created
    ON passkey_credentials (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS passkey_challenges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    flow_type VARCHAR(16) NOT NULL,
    challenge_hash VARCHAR(128) NOT NULL,
    requested_phone_number VARCHAR(32),
    device_name VARCHAR(120),
    platform VARCHAR(32),
    app_version VARCHAR(32),
    requested_by_ip VARCHAR(64),
    requested_by_user_agent VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_passkey_challenges_user_created
    ON passkey_challenges (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_passkey_challenges_expires
    ON passkey_challenges (expires_at, consumed_at);

CREATE TABLE IF NOT EXISTS phone_change_challenges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_id UUID NOT NULL REFERENCES user_sessions(id) ON DELETE CASCADE,
    new_phone_number VARCHAR(32) NOT NULL,
    code_hash VARCHAR(128) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 5,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_phone_change_challenges_user_created
    ON phone_change_challenges (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_phone_change_challenges_expires
    ON phone_change_challenges (expires_at, consumed_at);

CREATE TABLE IF NOT EXISTS account_export_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    requested_by_session_id UUID REFERENCES user_sessions(id) ON DELETE SET NULL,
    status VARCHAR(32) NOT NULL,
    export_format VARCHAR(16) NOT NULL,
    include_attachments_metadata BOOLEAN NOT NULL DEFAULT FALSE,
    message_count INTEGER NOT NULL DEFAULT 0,
    artifact_checksum VARCHAR(128),
    artifact_location VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_account_export_jobs_user_created
    ON account_export_jobs (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS account_deletion_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    requested_by_session_id UUID REFERENCES user_sessions(id) ON DELETE SET NULL,
    trigger_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    reason VARCHAR(255),
    scheduled_for TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    executed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_account_deletion_jobs_user_created
    ON account_deletion_jobs (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_account_deletion_jobs_due
    ON account_deletion_jobs (status, scheduled_for);

CREATE TABLE IF NOT EXISTS user_privacy_exceptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    privacy_type VARCHAR(16) NOT NULL,
    access_mode VARCHAR(8) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_user_privacy_exceptions_owner_target_type
    ON user_privacy_exceptions (owner_user_id, target_user_id, privacy_type);

CREATE INDEX IF NOT EXISTS idx_user_privacy_exceptions_owner_type
    ON user_privacy_exceptions (owner_user_id, privacy_type, created_at DESC);

CREATE TABLE IF NOT EXISTS user_close_friends (
    owner_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    friend_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (owner_user_id, friend_user_id)
);

CREATE INDEX IF NOT EXISTS idx_user_close_friends_friend
    ON user_close_friends (friend_user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS contact_notes (
    owner_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    contact_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    note VARCHAR(500),
    birthday DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (owner_user_id, contact_user_id)
);

CREATE INDEX IF NOT EXISTS idx_contact_notes_birthday
    ON contact_notes (owner_user_id, birthday);
