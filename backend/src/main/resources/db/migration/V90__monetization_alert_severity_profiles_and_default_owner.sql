ALTER TABLE channel_monetization_alert_policies
    ADD COLUMN default_owner_user_id UUID,
    ADD COLUMN high_severity_acknowledge_sla_minutes INTEGER NOT NULL DEFAULT 15,
    ADD COLUMN high_severity_resolve_sla_minutes INTEGER NOT NULL DEFAULT 60,
    ADD COLUMN high_severity_reminder_interval_minutes INTEGER NOT NULL DEFAULT 15;
