CREATE TABLE IF NOT EXISTS message_live_locations (
    message_id UUID PRIMARY KEY,
    chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    sender_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    title VARCHAR(120),
    address VARCHAR(240),
    expires_at TIMESTAMPTZ NOT NULL,
    last_updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    stopped_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_message_live_locations_chat_id
    ON message_live_locations (chat_id);

CREATE INDEX IF NOT EXISTS idx_message_live_locations_sender_user_id
    ON message_live_locations (sender_user_id);
