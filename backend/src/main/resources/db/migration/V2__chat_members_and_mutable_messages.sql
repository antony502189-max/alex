ALTER TABLE chats
    ADD COLUMN IF NOT EXISTS title VARCHAR(255);

ALTER TABLE chats
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users (id);

ALTER TABLE chats
    ALTER COLUMN participant_low_id DROP NOT NULL;

ALTER TABLE chats
    ALTER COLUMN participant_high_id DROP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_chats_created_by ON chats (created_by);

CREATE TABLE IF NOT EXISTS chat_members (
    chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_read_message_id UUID,
    last_read_at TIMESTAMPTZ,
    PRIMARY KEY (chat_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_members_user_id ON chat_members (user_id);

UPDATE chats
SET created_by = participant_low_id
WHERE created_by IS NULL
  AND participant_low_id IS NOT NULL;

INSERT INTO chat_members (chat_id, user_id, role, joined_at)
SELECT id, participant_low_id, 'OWNER', created_at
FROM chats
WHERE participant_low_id IS NOT NULL
ON CONFLICT (chat_id, user_id) DO NOTHING;

INSERT INTO chat_members (chat_id, user_id, role, joined_at)
SELECT id, participant_high_id, 'MEMBER', created_at
FROM chats
WHERE participant_high_id IS NOT NULL
ON CONFLICT (chat_id, user_id) DO NOTHING;
