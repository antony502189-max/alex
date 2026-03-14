ALTER TABLE channel_monetization_owner_reminder_digest_subscriptions
    ADD COLUMN failure_state VARCHAR(16) NOT NULL DEFAULT 'NONE',
    ADD COLUMN next_retry_at TIMESTAMPTZ,
    ADD COLUMN auto_paused_at TIMESTAMPTZ;

CREATE INDEX idx_channel_monetization_owner_reminder_digest_subscriptions_retry
    ON channel_monetization_owner_reminder_digest_subscriptions(status, next_retry_at, updated_at);
