CREATE TABLE IF NOT EXISTS sponsored_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    channel_chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    sponsor_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_by_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title VARCHAR(120) NOT NULL,
    message_text VARCHAR(1000) NOT NULL,
    call_to_action_label VARCHAR(64),
    call_to_action_url VARCHAR(512),
    budget_units BIGINT NOT NULL,
    spent_units BIGINT NOT NULL DEFAULT 0,
    cost_per_impression_units BIGINT NOT NULL DEFAULT 1,
    cost_per_click_units BIGINT NOT NULL DEFAULT 5,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    delivered_message_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ,
    active_until TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_sponsored_messages_channel_created
    ON sponsored_messages (channel_chat_id, created_at DESC);

CREATE TABLE IF NOT EXISTS sponsored_message_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sponsored_message_id UUID NOT NULL REFERENCES sponsored_messages (id) ON DELETE CASCADE,
    viewer_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    event_type VARCHAR(16) NOT NULL,
    cost_units BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_sponsored_message_events_unique
    ON sponsored_message_events (sponsored_message_id, viewer_user_id, event_type);

CREATE INDEX IF NOT EXISTS idx_sponsored_message_events_message_created
    ON sponsored_message_events (sponsored_message_id, created_at DESC);
