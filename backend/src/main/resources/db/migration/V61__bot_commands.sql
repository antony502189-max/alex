CREATE TABLE IF NOT EXISTS bot_commands (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bot_user_id UUID NOT NULL REFERENCES bot_accounts (bot_user_id) ON DELETE CASCADE,
    command VARCHAR(32) NOT NULL,
    description VARCHAR(255) NOT NULL,
    position INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (bot_user_id, command)
);

CREATE INDEX IF NOT EXISTS idx_bot_commands_bot_position
    ON bot_commands (bot_user_id, position ASC, created_at ASC);
