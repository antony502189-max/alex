ALTER TABLE scheduled_messages
    ADD COLUMN IF NOT EXISTS client_message_id UUID;

CREATE UNIQUE INDEX IF NOT EXISTS uq_scheduled_messages_sender_client_message
    ON scheduled_messages (sender_id, client_message_id)
    WHERE client_message_id IS NOT NULL;
