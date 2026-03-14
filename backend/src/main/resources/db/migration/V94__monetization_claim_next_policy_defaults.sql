ALTER TABLE channel_monetization_alert_policies
    ADD COLUMN claim_next_strategy VARCHAR(32) NOT NULL DEFAULT 'DEFAULT',
    ADD COLUMN claim_next_triage_only_default BOOLEAN NOT NULL DEFAULT FALSE;
