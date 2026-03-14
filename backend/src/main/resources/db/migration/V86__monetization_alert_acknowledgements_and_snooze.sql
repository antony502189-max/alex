ALTER TABLE channel_monetization_artifact_subscription_alerts
    ADD COLUMN IF NOT EXISTS acknowledged_by_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS acknowledged_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS snoozed_until TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_channel_monetization_subscription_alerts_channel_status_created
    ON channel_monetization_artifact_subscription_alerts (channel_chat_id, status, created_at DESC);
