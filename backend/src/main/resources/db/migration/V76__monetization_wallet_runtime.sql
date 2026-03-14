ALTER TABLE sponsored_messages
    ADD COLUMN IF NOT EXISTS earned_units BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS canceled_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_sponsored_messages_channel_status_published
    ON sponsored_messages (channel_chat_id, status, published_at DESC);

ALTER TABLE payment_wallet_transactions
    ADD COLUMN IF NOT EXISTS sponsored_message_id UUID REFERENCES sponsored_messages (id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_payment_wallet_transactions_sponsored_message
    ON payment_wallet_transactions (sponsored_message_id, created_at DESC);
