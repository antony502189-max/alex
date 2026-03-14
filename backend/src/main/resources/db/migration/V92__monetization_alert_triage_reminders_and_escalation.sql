ALTER TABLE channel_monetization_alert_policies
    ADD COLUMN triage_reminder_interval_minutes INTEGER NOT NULL DEFAULT 30,
    ADD COLUMN triage_escalation_after_minutes INTEGER NOT NULL DEFAULT 90,
    ADD COLUMN triage_escalation_target_chat_id UUID;

ALTER TABLE channel_monetization_artifact_subscription_alerts
    ADD COLUMN last_triage_reminder_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN triage_reminder_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_triage_reminder_message_id UUID,
    ADD COLUMN last_triage_reminder_target_chat_id UUID,
    ADD COLUMN triage_escalated_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN triage_escalation_message_id UUID,
    ADD COLUMN triage_escalation_target_chat_id UUID;

CREATE INDEX idx_channel_monetization_artifact_subscription_alerts_triage_followup
    ON channel_monetization_artifact_subscription_alerts (owner_user_id, triaged_at, triage_escalated_at);
