ALTER TABLE channel_monetization_alert_policies
    ADD COLUMN IF NOT EXISTS acknowledge_sla_minutes INTEGER NOT NULL DEFAULT 60,
    ADD COLUMN IF NOT EXISTS resolve_sla_minutes INTEGER NOT NULL DEFAULT 240,
    ADD COLUMN IF NOT EXISTS reminder_interval_minutes INTEGER NOT NULL DEFAULT 60;

ALTER TABLE channel_monetization_artifact_subscription_alerts
    ADD COLUMN IF NOT EXISTS acknowledge_by_due_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS resolve_by_due_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_reminder_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reminder_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_reminder_message_id UUID;

CREATE INDEX IF NOT EXISTS idx_channel_monetization_subscription_alerts_due
    ON channel_monetization_artifact_subscription_alerts (status, acknowledge_by_due_at, resolve_by_due_at);
