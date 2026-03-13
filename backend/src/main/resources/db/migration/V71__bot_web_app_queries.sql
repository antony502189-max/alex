ALTER TABLE bot_updates
    ALTER COLUMN message_id DROP NOT NULL;

CREATE TABLE IF NOT EXISTS bot_web_app_queries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bot_user_id UUID NOT NULL REFERENCES bot_accounts (bot_user_id) ON DELETE CASCADE,
    chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    from_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    start_parameter VARCHAR(128),
    platform VARCHAR(32) NOT NULL,
    query_text TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    answered_at TIMESTAMPTZ,
    result_message_id UUID
);

CREATE INDEX IF NOT EXISTS idx_bot_web_app_queries_bot_created
    ON bot_web_app_queries (bot_user_id, created_at DESC);

ALTER TABLE bot_updates
    ADD COLUMN IF NOT EXISTS web_app_query_id UUID REFERENCES bot_web_app_queries (id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_bot_updates_web_app_query
    ON bot_updates (web_app_query_id);
