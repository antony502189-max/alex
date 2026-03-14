CREATE TABLE IF NOT EXISTS channel_monetization_artifact_subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    channel_chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    target_chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    created_by_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    artifact_type VARCHAR(32) NOT NULL,
    delivery_mode VARCHAR(32) NOT NULL DEFAULT 'CHAT_MESSAGE',
    note VARCHAR(255),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    min_interval_minutes INTEGER NOT NULL DEFAULT 60,
    auto_generate BOOLEAN NOT NULL DEFAULT FALSE,
    last_delivered_artifact_id UUID REFERENCES channel_monetization_export_artifacts (id) ON DELETE SET NULL,
    last_delivered_at TIMESTAMPTZ,
    last_generated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_channel_monetization_artifact_subscriptions_channel_created
    ON channel_monetization_artifact_subscriptions (channel_chat_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_channel_monetization_artifact_subscriptions_status_updated
    ON channel_monetization_artifact_subscriptions (status, updated_at ASC);
