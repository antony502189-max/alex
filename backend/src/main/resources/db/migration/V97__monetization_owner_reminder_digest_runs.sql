CREATE TABLE channel_monetization_owner_reminder_digest_runs (
    id UUID PRIMARY KEY,
    subscription_id UUID NOT NULL,
    channel_chat_id UUID NOT NULL,
    owner_user_id UUID NOT NULL,
    processed_by_user_id UUID,
    trigger_mode VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    target_chat_id UUID,
    severity VARCHAR(16),
    breached_only BOOLEAN NOT NULL DEFAULT FALSE,
    due_alert_count INTEGER NOT NULL DEFAULT 0,
    breached_due_alert_count INTEGER NOT NULL DEFAULT 0,
    artifact_id UUID,
    publication_id UUID,
    published_message_id UUID,
    failure_reason VARCHAR(255),
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_channel_monetization_owner_reminder_digest_runs_subscription
    ON channel_monetization_owner_reminder_digest_runs(subscription_id, processed_at DESC);
