CREATE TABLE IF NOT EXISTS business_profiles (
    user_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    greeting_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    greeting_message VARCHAR(1000),
    away_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    away_message VARCHAR(1000),
    business_hours_json TEXT NOT NULL DEFAULT '[]',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS business_quick_replies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    shortcut VARCHAR(64) NOT NULL,
    message_text VARCHAR(1000) NOT NULL,
    position INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, shortcut)
);

CREATE INDEX IF NOT EXISTS idx_business_quick_replies_user_position
    ON business_quick_replies (user_id, position ASC, created_at ASC);

CREATE TABLE IF NOT EXISTS business_chat_tags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    tag_name VARCHAR(64) NOT NULL,
    color VARCHAR(16),
    position INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (owner_user_id, chat_id, tag_name)
);

CREATE INDEX IF NOT EXISTS idx_business_chat_tags_owner_chat
    ON business_chat_tags (owner_user_id, chat_id, position ASC, created_at ASC);

CREATE TABLE IF NOT EXISTS business_operator_assignments (
    owner_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    operator_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    note VARCHAR(255),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (owner_user_id, chat_id)
);

CREATE INDEX IF NOT EXISTS idx_business_operator_assignments_operator
    ON business_operator_assignments (operator_user_id, updated_at DESC);
