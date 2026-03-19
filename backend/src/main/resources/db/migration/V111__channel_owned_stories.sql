ALTER TABLE stories
    ADD COLUMN IF NOT EXISTS owner_chat_id UUID REFERENCES chats (id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_stories_owner_chat_id
    ON stories (owner_chat_id);

ALTER TABLE story_albums
    ADD COLUMN IF NOT EXISTS owner_chat_id UUID REFERENCES chats (id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_story_albums_owner_chat_position
    ON story_albums (owner_chat_id, position ASC, created_at ASC);
