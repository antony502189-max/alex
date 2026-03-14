ALTER TABLE channel_monetization_artifact_subscription_alerts
    ADD COLUMN IF NOT EXISTS owner_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS assigned_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS channel_monetization_artifact_alert_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id UUID NOT NULL REFERENCES channel_monetization_artifact_subscription_alerts (id) ON DELETE CASCADE,
    subscription_id UUID NOT NULL,
    channel_chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    author_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    body VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS channel_monetization_artifact_alert_audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id UUID NOT NULL REFERENCES channel_monetization_artifact_subscription_alerts (id) ON DELETE CASCADE,
    subscription_id UUID NOT NULL,
    channel_chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    action_type VARCHAR(32) NOT NULL,
    actor_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    owner_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    from_status VARCHAR(16),
    to_status VARCHAR(16),
    note VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_channel_monetization_alert_comments_alert_created
    ON channel_monetization_artifact_alert_comments (alert_id, created_at ASC);

CREATE INDEX IF NOT EXISTS idx_channel_monetization_alert_audit_events_alert_created
    ON channel_monetization_artifact_alert_audit_events (alert_id, created_at ASC);
