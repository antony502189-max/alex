CREATE TABLE IF NOT EXISTS channel_monetization_withdrawals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    channel_chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    recipient_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    requested_by_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    amount_units BIGINT NOT NULL,
    currency_code VARCHAR(16) NOT NULL DEFAULT 'XTR',
    destination_type VARCHAR(32) NOT NULL,
    destination_label VARCHAR(255) NOT NULL,
    note VARCHAR(255),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    provider_reference VARCHAR(128),
    failure_reason VARCHAR(255),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processing_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    canceled_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_channel_monetization_withdrawals_channel_requested
    ON channel_monetization_withdrawals (channel_chat_id, requested_at DESC);

CREATE INDEX IF NOT EXISTS idx_channel_monetization_withdrawals_status_requested
    ON channel_monetization_withdrawals (status, requested_at ASC);
