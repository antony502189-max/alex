CREATE TABLE IF NOT EXISTS channel_monetization_reconciliation_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    channel_chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    triggered_by_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    trigger_mode VARCHAR(16) NOT NULL,
    processed_count INTEGER NOT NULL DEFAULT 0,
    pending_count INTEGER NOT NULL DEFAULT 0,
    processing_count INTEGER NOT NULL DEFAULT 0,
    completed_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_channel_monetization_reconciliation_runs_channel_created
    ON channel_monetization_reconciliation_runs (channel_chat_id, created_at DESC);
