CREATE TABLE channel_monetization_owner_reminder_digest_subscriptions (
    id UUID PRIMARY KEY,
    channel_chat_id UUID NOT NULL,
    owner_user_id UUID NOT NULL,
    target_chat_id UUID,
    created_by_user_id UUID NOT NULL,
    severity VARCHAR(16),
    breached_only BOOLEAN NOT NULL DEFAULT FALSE,
    note VARCHAR(255),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    min_interval_minutes INTEGER NOT NULL DEFAULT 60,
    last_delivered_artifact_id UUID,
    last_delivered_at TIMESTAMPTZ,
    last_processed_at TIMESTAMPTZ,
    consecutive_failure_count INTEGER NOT NULL DEFAULT 0,
    last_failure_at TIMESTAMPTZ,
    last_failure_reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_channel_monetization_owner_reminder_digest_subscriptions_channel_owner
    ON channel_monetization_owner_reminder_digest_subscriptions(channel_chat_id, owner_user_id, created_at DESC);

CREATE INDEX idx_channel_monetization_owner_reminder_digest_subscriptions_status
    ON channel_monetization_owner_reminder_digest_subscriptions(status, updated_at);
