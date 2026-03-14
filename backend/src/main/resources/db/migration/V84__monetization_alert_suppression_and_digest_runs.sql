ALTER TABLE channel_monetization_artifact_subscriptions
    ADD COLUMN IF NOT EXISTS alert_suppression_minutes INTEGER NOT NULL DEFAULT 180,
    ADD COLUMN IF NOT EXISTS last_alerted_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS channel_monetization_alert_digest_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    channel_chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    generated_by_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    trigger_mode VARCHAR(16) NOT NULL,
    open_alert_count INTEGER NOT NULL DEFAULT 0,
    affected_subscription_count INTEGER NOT NULL DEFAULT 0,
    artifact_id UUID REFERENCES channel_monetization_export_artifacts (id) ON DELETE SET NULL,
    published_message_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_channel_monetization_alert_digest_runs_channel_created
    ON channel_monetization_alert_digest_runs (channel_chat_id, created_at DESC);
