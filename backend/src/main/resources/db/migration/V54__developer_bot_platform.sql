CREATE TABLE IF NOT EXISTS bot_accounts (
    bot_user_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    owner_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    api_token_hash VARCHAR(64) NOT NULL UNIQUE,
    api_token_prefix VARCHAR(24) NOT NULL,
    token_rotated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    webhook_url VARCHAR(512),
    webhook_secret_hash VARCHAR(64),
    webhook_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    last_webhook_delivery_at TIMESTAMPTZ,
    last_webhook_error VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_bot_accounts_owner_created
    ON bot_accounts (owner_user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_bot_accounts_api_token_hash
    ON bot_accounts (api_token_hash);
