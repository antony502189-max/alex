CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number VARCHAR(32) NOT NULL UNIQUE,
    display_name VARCHAR(120) NOT NULL,
    username VARCHAR(64) UNIQUE,
    about VARCHAR(255),
    phone_privacy VARCHAR(16) NOT NULL DEFAULT 'EVERYBODY',
    last_seen_privacy VARCHAR(16) NOT NULL DEFAULT 'EVERYBODY',
    story_privacy VARCHAR(16) NOT NULL DEFAULT 'EVERYBODY',
    last_seen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS chats (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_type VARCHAR(16) NOT NULL DEFAULT 'DIRECT',
    title VARCHAR(255),
    public_username VARCHAR(64),
    about VARCHAR(500),
    auto_delete_seconds INTEGER,
    created_by UUID REFERENCES users (id),
    pinned_message_id UUID,
    participant_low_id UUID REFERENCES users (id),
    participant_high_id UUID REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_message_at TIMESTAMPTZ,
    CONSTRAINT uq_direct_chat_pair UNIQUE (participant_low_id, participant_high_id),
    CONSTRAINT chk_distinct_participants CHECK (
        participant_low_id IS NULL OR participant_high_id IS NULL OR participant_low_id <> participant_high_id
    )
);

CREATE INDEX IF NOT EXISTS idx_chats_low ON chats (participant_low_id);
CREATE INDEX IF NOT EXISTS idx_chats_high ON chats (participant_high_id);
CREATE INDEX IF NOT EXISTS idx_chats_created_by ON chats (created_by);
CREATE UNIQUE INDEX IF NOT EXISTS uq_chats_public_username ON chats (lower(public_username)) WHERE public_username IS NOT NULL;

CREATE TABLE IF NOT EXISTS scheduled_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    ciphertext TEXT NOT NULL,
    nonce TEXT NOT NULL,
    key_version INTEGER NOT NULL,
    reply_to_message_id UUID,
    sticker_id UUID,
    attachment_ids TEXT NOT NULL DEFAULT '',
    scheduled_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    delivered_message_id UUID,
    error_message VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_scheduled_messages_due ON scheduled_messages (status, scheduled_at);
CREATE INDEX IF NOT EXISTS idx_scheduled_messages_sender_chat ON scheduled_messages (sender_id, chat_id, status, scheduled_at);

CREATE TABLE IF NOT EXISTS chat_members (
    chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_read_message_id UUID,
    last_read_at TIMESTAMPTZ,
    unread_count INTEGER NOT NULL DEFAULT 0,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    muted_until TIMESTAMPTZ,
    PRIMARY KEY (chat_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_members_user_id ON chat_members (user_id);

CREATE TABLE IF NOT EXISTS encryption_keys (
    chat_id UUID PRIMARY KEY REFERENCES chats (id) ON DELETE CASCADE,
    algorithm VARCHAR(32) NOT NULL,
    key_version INTEGER NOT NULL DEFAULT 1,
    key_material TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    rotated_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS message_reactions (
    message_id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    emoji VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (message_id, user_id, emoji)
);

CREATE INDEX IF NOT EXISTS idx_message_reactions_message_id ON message_reactions (message_id);

CREATE TABLE IF NOT EXISTS polls (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    created_by_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    question VARCHAR(255) NOT NULL,
    multiple_choice BOOLEAN NOT NULL DEFAULT FALSE,
    closed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_polls_chat_id ON polls (chat_id);

CREATE TABLE IF NOT EXISTS poll_options (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    poll_id UUID NOT NULL REFERENCES polls (id) ON DELETE CASCADE,
    option_text VARCHAR(160) NOT NULL,
    position INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_poll_options_poll_id ON poll_options (poll_id);

CREATE TABLE IF NOT EXISTS poll_votes (
    poll_id UUID NOT NULL REFERENCES polls (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    option_id UUID NOT NULL REFERENCES poll_options (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (poll_id, user_id, option_id)
);

CREATE INDEX IF NOT EXISTS idx_poll_votes_user_id ON poll_votes (user_id);

CREATE TABLE IF NOT EXISTS attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    uploader_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    original_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    kind VARCHAR(16) NOT NULL DEFAULT 'FILE',
    file_size_bytes BIGINT NOT NULL,
    duration_ms BIGINT,
    storage_path VARCHAR(512) NOT NULL UNIQUE,
    nonce TEXT NOT NULL,
    key_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_attachments_chat_id ON attachments (chat_id);
CREATE INDEX IF NOT EXISTS idx_attachments_uploader_user_id ON attachments (uploader_user_id);

CREATE TABLE IF NOT EXISTS sticker_packs (
    id UUID PRIMARY KEY,
    title VARCHAR(120) NOT NULL,
    slug VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS stickers (
    id UUID PRIMARY KEY,
    pack_id UUID NOT NULL REFERENCES sticker_packs (id) ON DELETE CASCADE,
    emoji VARCHAR(16) NOT NULL,
    label VARCHAR(64) NOT NULL,
    background_from VARCHAR(16) NOT NULL,
    background_to VARCHAR(16) NOT NULL,
    text_color VARCHAR(16) NOT NULL,
    position INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_stickers_pack_id ON stickers (pack_id);

INSERT INTO sticker_packs (id, title, slug)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'Alex Mood', 'alex-mood'),
    ('22222222-2222-2222-2222-222222222222', 'Belarus Vibes', 'belarus-vibes')
ON CONFLICT (id) DO NOTHING;

INSERT INTO stickers (id, pack_id, emoji, label, background_from, background_to, text_color, position)
VALUES
    ('11111111-1111-1111-1111-111111111201', '11111111-1111-1111-1111-111111111111', '🔥', 'On fire', '#f97316', '#ef4444', '#ffffff', 0),
    ('11111111-1111-1111-1111-111111111202', '11111111-1111-1111-1111-111111111111', '😎', 'Cool', '#0ea5e9', '#2563eb', '#ffffff', 1),
    ('11111111-1111-1111-1111-111111111203', '11111111-1111-1111-1111-111111111111', '💤', 'Sleepy', '#a78bfa', '#6366f1', '#ffffff', 2),
    ('11111111-1111-1111-1111-111111111204', '11111111-1111-1111-1111-111111111111', '🎉', 'Party', '#ec4899', '#8b5cf6', '#ffffff', 3),
    ('22222222-2222-2222-2222-222222222201', '22222222-2222-2222-2222-222222222222', '🌤️', 'Good morning', '#facc15', '#fb923c', '#0f172a', 0),
    ('22222222-2222-2222-2222-222222222202', '22222222-2222-2222-2222-222222222222', '☕', 'Coffee time', '#92400e', '#78350f', '#ffffff', 1),
    ('22222222-2222-2222-2222-222222222203', '22222222-2222-2222-2222-222222222222', '🚲', 'City ride', '#14b8a6', '#0f766e', '#ffffff', 2),
    ('22222222-2222-2222-2222-222222222204', '22222222-2222-2222-2222-222222222222', '❄️', 'Winter mode', '#e0f2fe', '#60a5fa', '#0f172a', 3)
ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS contacts (
    owner_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    contact_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    contact_name VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (owner_user_id, contact_user_id)
);

CREATE INDEX IF NOT EXISTS idx_contacts_contact_user_id ON contacts (contact_user_id);

CREATE TABLE IF NOT EXISTS chat_folders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title VARCHAR(64) NOT NULL,
    position INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_folders_owner_user_id ON chat_folders (owner_user_id);

CREATE TABLE IF NOT EXISTS chat_folder_items (
    folder_id UUID NOT NULL REFERENCES chat_folders (id) ON DELETE CASCADE,
    chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    PRIMARY KEY (folder_id, chat_id)
);

CREATE TABLE IF NOT EXISTS chat_drafts (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    draft_text TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, chat_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_drafts_chat_id ON chat_drafts (chat_id);

CREATE TABLE IF NOT EXISTS user_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    device_name VARCHAR(120) NOT NULL,
    platform VARCHAR(32),
    app_version VARCHAR(32),
    user_agent VARCHAR(255),
    ip_address VARCHAR(64),
    push_provider VARCHAR(16),
    push_token VARCHAR(255),
    notifications_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_active_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_user_sessions_user_id ON user_sessions (user_id);
CREATE INDEX IF NOT EXISTS idx_user_sessions_active ON user_sessions (user_id, revoked_at, last_active_at DESC);

CREATE TABLE IF NOT EXISTS stories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    text VARCHAR(500) NOT NULL,
    background_from VARCHAR(16) NOT NULL,
    background_to VARCHAR(16) NOT NULL,
    text_color VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_stories_owner_user_id ON stories (owner_user_id);
CREATE INDEX IF NOT EXISTS idx_stories_expires_at ON stories (expires_at);

CREATE TABLE IF NOT EXISTS story_views (
    story_id UUID NOT NULL REFERENCES stories (id) ON DELETE CASCADE,
    viewer_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    viewed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (story_id, viewer_user_id)
);

CREATE INDEX IF NOT EXISTS idx_story_views_viewer_user_id ON story_views (viewer_user_id);

CREATE TABLE IF NOT EXISTS chat_invite_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    created_by UUID NOT NULL REFERENCES users (id),
    token VARCHAR(64) NOT NULL UNIQUE,
    label VARCHAR(120),
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMPTZ,
    usage_limit INTEGER,
    usage_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_used_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_chat_invite_links_chat ON chat_invite_links (chat_id, created_at DESC);

CREATE TABLE IF NOT EXISTS message_expirations (
    message_id UUID PRIMARY KEY,
    chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_message_expirations_pending
    ON message_expirations (processed_at, expires_at);

CREATE TABLE IF NOT EXISTS call_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    created_by_user_id UUID NOT NULL REFERENCES users (id),
    kind VARCHAR(16) NOT NULL,
    mode VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    answered_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_call_sessions_chat_status
    ON call_sessions (chat_id, status, started_at DESC);

CREATE TABLE IF NOT EXISTS call_participants (
    call_id UUID NOT NULL REFERENCES call_sessions (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    state VARCHAR(16) NOT NULL,
    invited_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    joined_at TIMESTAMPTZ,
    left_at TIMESTAMPTZ,
    PRIMARY KEY (call_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_call_participants_user_state
    ON call_participants (user_id, state, invited_at DESC);
