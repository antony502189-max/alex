ALTER TABLE chats
    ADD COLUMN linked_discussion_chat_id UUID REFERENCES chats(id);

CREATE INDEX IF NOT EXISTS idx_chats_linked_discussion_chat_id
    ON chats(linked_discussion_chat_id);
