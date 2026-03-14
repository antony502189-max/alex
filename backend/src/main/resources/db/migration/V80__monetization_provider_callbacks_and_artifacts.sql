ALTER TABLE channel_monetization_withdrawals
    ADD COLUMN IF NOT EXISTS provider_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS provider_updated_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_channel_monetization_withdrawals_provider_reference
    ON channel_monetization_withdrawals (provider_reference);

CREATE TABLE IF NOT EXISTS channel_monetization_withdrawal_callbacks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    withdrawal_id UUID NOT NULL REFERENCES channel_monetization_withdrawals (id) ON DELETE CASCADE,
    channel_chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    provider_reference VARCHAR(128),
    callback_type VARCHAR(32) NOT NULL,
    provider_status VARCHAR(32) NOT NULL,
    failure_reason VARCHAR(255),
    payload_json TEXT,
    applied BOOLEAN NOT NULL DEFAULT FALSE,
    applied_withdrawal_status VARCHAR(16),
    result_message VARCHAR(255),
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_channel_monetization_withdrawal_callbacks_withdrawal_received
    ON channel_monetization_withdrawal_callbacks (withdrawal_id, received_at DESC);

CREATE INDEX IF NOT EXISTS idx_channel_monetization_withdrawal_callbacks_channel_received
    ON channel_monetization_withdrawal_callbacks (channel_chat_id, received_at DESC);

CREATE TABLE IF NOT EXISTS channel_monetization_export_artifacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    channel_chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    generated_by_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    artifact_type VARCHAR(32) NOT NULL,
    format VARCHAR(16) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    row_count INTEGER NOT NULL DEFAULT 0,
    total_units BIGINT NOT NULL DEFAULT 0,
    checksum VARCHAR(128) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_channel_monetization_export_artifacts_channel_created
    ON channel_monetization_export_artifacts (channel_chat_id, created_at DESC);
