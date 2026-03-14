ALTER TABLE channel_monetization_alert_policies
    ADD COLUMN triage_auto_assign_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN triage_fallback_owner_user_id UUID;
