CREATE TABLE IF NOT EXISTS bot_web_app_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bot_user_id UUID NOT NULL REFERENCES bot_accounts (bot_user_id) ON DELETE CASCADE,
    chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    message_id UUID NOT NULL,
    from_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    start_parameter VARCHAR(128),
    platform VARCHAR(32) NOT NULL,
    button_text VARCHAR(64),
    payload_data TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_bot_web_app_events_bot_created
    ON bot_web_app_events (bot_user_id, created_at DESC);

ALTER TABLE bot_updates
    ADD COLUMN IF NOT EXISTS web_app_event_id UUID REFERENCES bot_web_app_events (id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_bot_updates_web_app_event
    ON bot_updates (web_app_event_id);
