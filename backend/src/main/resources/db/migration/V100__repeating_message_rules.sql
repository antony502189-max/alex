CREATE TABLE IF NOT EXISTS repeating_message_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_rule_id UUID,
    topic_id UUID,
    thread_root_message_id UUID,
    discussion_chat_id UUID,
    discussion_root_message_id UUID,
    ciphertext TEXT NOT NULL,
    nonce TEXT NOT NULL,
    key_version INTEGER NOT NULL,
    reply_to_message_id UUID,
    sticker_id UUID,
    attachment_ids TEXT NOT NULL DEFAULT '',
    interval_minutes INTEGER NOT NULL,
    max_occurrences INTEGER,
    emitted_occurrences INTEGER NOT NULL DEFAULT 0,
    last_scheduled_at TIMESTAMPTZ,
    next_scheduled_at TIMESTAMPTZ,
    last_scheduled_message_id UUID,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_repeating_message_rules_sender_client_rule
    ON repeating_message_rules (sender_id, client_rule_id)
    WHERE client_rule_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_repeating_message_rules_sender_status
    ON repeating_message_rules (sender_id, status, created_at DESC);

ALTER TABLE scheduled_messages
    ADD COLUMN IF NOT EXISTS repeating_rule_id UUID REFERENCES repeating_message_rules(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS repeating_occurrence INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_scheduled_messages_repeating_rule
    ON scheduled_messages (repeating_rule_id, repeating_occurrence);
