ALTER TABLE channel_monetization_artifact_subscriptions
    ADD COLUMN IF NOT EXISTS consecutive_failure_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_failure_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_failure_reason VARCHAR(255),
    ADD COLUMN IF NOT EXISTS escalation_status VARCHAR(16) NOT NULL DEFAULT 'NONE';

CREATE TABLE IF NOT EXISTS channel_monetization_artifact_subscription_failures (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id UUID NOT NULL REFERENCES channel_monetization_artifact_subscriptions (id) ON DELETE CASCADE,
    channel_chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    target_chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    artifact_type VARCHAR(32) NOT NULL,
    attempt_number INTEGER NOT NULL DEFAULT 1,
    failure_reason VARCHAR(255) NOT NULL,
    alert_created BOOLEAN NOT NULL DEFAULT FALSE,
    failed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_channel_monetization_artifact_subscription_failures_subscription_failed
    ON channel_monetization_artifact_subscription_failures (subscription_id, failed_at DESC);

CREATE TABLE IF NOT EXISTS channel_monetization_artifact_subscription_alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id UUID NOT NULL REFERENCES channel_monetization_artifact_subscriptions (id) ON DELETE CASCADE,
    channel_chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    target_chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    severity VARCHAR(16) NOT NULL,
    failure_count INTEGER NOT NULL DEFAULT 0,
    last_failure_reason VARCHAR(255),
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    published_message_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_channel_monetization_artifact_subscription_alerts_subscription_created
    ON channel_monetization_artifact_subscription_alerts (subscription_id, created_at DESC);
