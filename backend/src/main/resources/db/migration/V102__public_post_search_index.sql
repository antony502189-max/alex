CREATE TABLE IF NOT EXISTS public_post_search_index (
    message_id UUID PRIMARY KEY,
    chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    topic_id UUID,
    discussion_chat_id UUID,
    discussion_root_message_id UUID,
    excerpt VARCHAR(600),
    search_corpus TEXT NOT NULL,
    message_type VARCHAR(32) NOT NULL,
    attachment_count INTEGER NOT NULL DEFAULT 0,
    has_media BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_public_post_search_chat_created
    ON public_post_search_index (chat_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_public_post_search_created
    ON public_post_search_index (created_at DESC);
