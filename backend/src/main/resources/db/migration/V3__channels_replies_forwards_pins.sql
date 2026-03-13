ALTER TABLE chats
    ADD COLUMN IF NOT EXISTS pinned_message_id UUID;
