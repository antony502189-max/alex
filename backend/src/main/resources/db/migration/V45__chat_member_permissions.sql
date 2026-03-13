ALTER TABLE chat_members
    ADD COLUMN IF NOT EXISTS can_manage_members BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS can_manage_invite_links BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS can_manage_messages BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS can_pin_messages BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS can_approve_join_requests BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS can_post_messages BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE chat_members
SET can_manage_members = CASE WHEN role IN ('OWNER', 'ADMIN') THEN TRUE ELSE FALSE END,
    can_manage_invite_links = CASE WHEN role IN ('OWNER', 'ADMIN') THEN TRUE ELSE FALSE END,
    can_manage_messages = CASE WHEN role IN ('OWNER', 'ADMIN') THEN TRUE ELSE FALSE END,
    can_pin_messages = CASE WHEN role IN ('OWNER', 'ADMIN') THEN TRUE ELSE FALSE END,
    can_approve_join_requests = CASE WHEN role IN ('OWNER', 'ADMIN') THEN TRUE ELSE FALSE END;

UPDATE chat_members cm
SET can_post_messages = CASE
    WHEN c.chat_type = 'CHANNEL' AND cm.role NOT IN ('OWNER', 'ADMIN') THEN FALSE
    ELSE TRUE
END
FROM chats c
WHERE c.id = cm.chat_id;
