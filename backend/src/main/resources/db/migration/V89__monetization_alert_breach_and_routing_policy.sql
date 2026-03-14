ALTER TABLE channel_monetization_alert_policies
    ADD COLUMN reminder_target_chat_id UUID,
    ADD COLUMN breach_target_chat_id UUID,
    ADD COLUMN severity_upgrade_after_minutes INTEGER NOT NULL DEFAULT 30,
    ADD COLUMN breach_escalation_after_minutes INTEGER NOT NULL DEFAULT 120;

ALTER TABLE channel_monetization_artifact_subscription_alerts
    ADD COLUMN severity_escalated_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN breached_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN breach_message_id UUID,
    ADD COLUMN last_reminder_target_chat_id UUID;

CREATE INDEX idx_channel_monetization_artifact_subscription_alerts_breached
    ON channel_monetization_artifact_subscription_alerts (channel_chat_id, breached_at);
