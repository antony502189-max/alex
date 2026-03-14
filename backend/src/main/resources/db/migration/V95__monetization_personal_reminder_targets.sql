ALTER TABLE channel_monetization_alert_policies
    ADD COLUMN personal_reminder_target_chat_id UUID REFERENCES chats (id) ON DELETE SET NULL,
    ADD COLUMN personal_reminder_digest_target_chat_id UUID REFERENCES chats (id) ON DELETE SET NULL;
