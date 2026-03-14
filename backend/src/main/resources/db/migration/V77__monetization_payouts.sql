ALTER TABLE sponsored_messages
    ADD COLUMN IF NOT EXISTS settled_units BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS channel_monetization_payouts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    channel_chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    recipient_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    triggered_by_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    trigger_mode VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'COMPLETED',
    total_units BIGINT NOT NULL DEFAULT 0,
    sponsored_message_count INTEGER NOT NULL DEFAULT 0,
    period_started_at TIMESTAMPTZ,
    period_ended_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_channel_monetization_payouts_channel_created
    ON channel_monetization_payouts (channel_chat_id, created_at DESC);

CREATE TABLE IF NOT EXISTS channel_monetization_payout_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payout_id UUID NOT NULL REFERENCES channel_monetization_payouts (id) ON DELETE CASCADE,
    sponsored_message_id UUID NOT NULL REFERENCES sponsored_messages (id) ON DELETE CASCADE,
    settled_units BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (payout_id, sponsored_message_id)
);

CREATE INDEX IF NOT EXISTS idx_channel_monetization_payout_items_payout
    ON channel_monetization_payout_items (payout_id, created_at ASC);

CREATE INDEX IF NOT EXISTS idx_channel_monetization_payout_items_message
    ON channel_monetization_payout_items (sponsored_message_id);
