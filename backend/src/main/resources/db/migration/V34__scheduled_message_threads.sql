ALTER TABLE scheduled_messages
    ADD COLUMN thread_root_message_id UUID,
    ADD COLUMN discussion_chat_id UUID,
    ADD COLUMN discussion_root_message_id UUID;
