CREATE TABLE IF NOT EXISTS channel_monetization_alert_policies (
    channel_chat_id UUID PRIMARY KEY REFERENCES chats (id) ON DELETE CASCADE,
    configured_by_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    alert_threshold INTEGER NOT NULL DEFAULT 3,
    high_severity_threshold INTEGER NOT NULL DEFAULT 5,
    alert_suppression_minutes INTEGER NOT NULL DEFAULT 180,
    auto_digest_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    alert_target_chat_id UUID REFERENCES chats (id) ON DELETE SET NULL,
    digest_target_chat_id UUID REFERENCES chats (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
