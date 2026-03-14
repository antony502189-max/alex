CREATE TABLE IF NOT EXISTS channel_monetization_provider_sync_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    channel_chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    triggered_by_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    trigger_mode VARCHAR(16) NOT NULL,
    payload_size INTEGER NOT NULL DEFAULT 0,
    applied_count INTEGER NOT NULL DEFAULT 0,
    ignored_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    artifact_id UUID REFERENCES channel_monetization_export_artifacts (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_channel_monetization_provider_sync_runs_channel_created
    ON channel_monetization_provider_sync_runs (channel_chat_id, created_at DESC);

CREATE TABLE IF NOT EXISTS channel_monetization_artifact_publications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    artifact_id UUID NOT NULL REFERENCES channel_monetization_export_artifacts (id) ON DELETE CASCADE,
    channel_chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    target_chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    published_by_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    delivery_mode VARCHAR(32) NOT NULL,
    note VARCHAR(255),
    published_message_id UUID,
    published_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_channel_monetization_artifact_publications_artifact_published
    ON channel_monetization_artifact_publications (artifact_id, published_at DESC);
