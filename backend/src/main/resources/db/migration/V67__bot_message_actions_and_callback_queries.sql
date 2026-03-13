CREATE TABLE IF NOT EXISTS bot_message_actions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bot_user_id UUID NOT NULL REFERENCES bot_accounts (bot_user_id) ON DELETE CASCADE,
    message_id UUID NOT NULL,
    action_type VARCHAR(16) NOT NULL,
    button_text VARCHAR(64) NOT NULL,
    callback_data VARCHAR(255),
    target_url VARCHAR(512),
    web_app_start_parameter VARCHAR(128),
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_bot_message_actions_message_order
    ON bot_message_actions (message_id, sort_order);

CREATE TABLE IF NOT EXISTS bot_callback_queries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bot_user_id UUID NOT NULL REFERENCES bot_accounts (bot_user_id) ON DELETE CASCADE,
    chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    message_id UUID NOT NULL,
    from_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    action_id UUID REFERENCES bot_message_actions (id) ON DELETE SET NULL,
    callback_data VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    answered_at TIMESTAMPTZ,
    answer_text VARCHAR(255),
    show_alert BOOLEAN NOT NULL DEFAULT FALSE,
    redirect_url VARCHAR(512)
);

CREATE INDEX IF NOT EXISTS idx_bot_callback_queries_bot_created
    ON bot_callback_queries (bot_user_id, created_at DESC);

ALTER TABLE bot_updates
    ADD COLUMN IF NOT EXISTS callback_query_id UUID REFERENCES bot_callback_queries (id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_bot_updates_callback_query
    ON bot_updates (callback_query_id);
