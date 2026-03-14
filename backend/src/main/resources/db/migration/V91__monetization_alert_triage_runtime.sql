ALTER TABLE channel_monetization_alert_policies
    ADD COLUMN auto_triage_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN triage_target_chat_id UUID,
    ADD COLUMN triage_delay_minutes INTEGER NOT NULL DEFAULT 15;

ALTER TABLE channel_monetization_artifact_subscription_alerts
    ADD COLUMN triaged_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN triage_message_id UUID,
    ADD COLUMN triage_target_chat_id UUID;

CREATE INDEX idx_channel_monetization_artifact_subscription_alerts_triage
    ON channel_monetization_artifact_subscription_alerts (channel_chat_id, severity, owner_user_id, triaged_at);
