ALTER TABLE bot_accounts
    ADD COLUMN IF NOT EXISTS webhook_secret_value VARCHAR(255);

CREATE TABLE IF NOT EXISTS bot_updates (
    id BIGSERIAL PRIMARY KEY,
    bot_user_id UUID NOT NULL REFERENCES bot_accounts (bot_user_id) ON DELETE CASCADE,
    chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    message_id UUID NOT NULL,
    update_type VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    delivered_at TIMESTAMPTZ,
    last_delivery_attempt_at TIMESTAMPTZ,
    delivery_attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_bot_updates_polling
    ON bot_updates (bot_user_id, delivered_at, id);

CREATE INDEX IF NOT EXISTS idx_bot_updates_webhook
    ON bot_updates (delivered_at, id);
