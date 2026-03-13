ALTER TABLE business_profiles
    ADD COLUMN IF NOT EXISTS time_zone VARCHAR(64) NOT NULL DEFAULT 'UTC';

CREATE TABLE IF NOT EXISTS business_chat_automation_state (
    owner_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    first_customer_message_at TIMESTAMPTZ,
    last_customer_message_at TIMESTAMPTZ,
    last_greeting_sent_at TIMESTAMPTZ,
    last_away_sent_at TIMESTAMPTZ,
    last_auto_response_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (owner_user_id, chat_id)
);

CREATE INDEX IF NOT EXISTS idx_business_chat_automation_state_owner_updated
    ON business_chat_automation_state (owner_user_id, updated_at DESC);
