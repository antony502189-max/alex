CREATE TABLE IF NOT EXISTS story_albums (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title VARCHAR(120) NOT NULL,
    cover_story_id UUID REFERENCES stories (id) ON DELETE SET NULL,
    position INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_story_albums_owner_position
    ON story_albums (owner_user_id, position ASC, created_at ASC);

CREATE TABLE IF NOT EXISTS story_album_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    album_id UUID NOT NULL REFERENCES story_albums (id) ON DELETE CASCADE,
    story_id UUID NOT NULL REFERENCES stories (id) ON DELETE CASCADE,
    position INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (album_id, story_id)
);

CREATE INDEX IF NOT EXISTS idx_story_album_items_album_position
    ON story_album_items (album_id, position ASC, created_at ASC);

CREATE TABLE IF NOT EXISTS story_live_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    story_id UUID NOT NULL REFERENCES stories (id) ON DELETE CASCADE,
    owner_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    donations_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    donation_provider VARCHAR(32),
    donation_currency VARCHAR(8),
    donation_event_hook_url VARCHAR(500),
    donation_events_count BIGINT NOT NULL DEFAULT 0,
    donations_total_minor BIGINT NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_story_live_sessions_story_status_started
    ON story_live_sessions (story_id, status, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_story_live_sessions_owner_status
    ON story_live_sessions (owner_user_id, status, started_at DESC);

CREATE TABLE IF NOT EXISTS story_live_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    story_id UUID NOT NULL REFERENCES stories (id) ON DELETE CASCADE,
    live_session_id UUID NOT NULL REFERENCES story_live_sessions (id) ON DELETE CASCADE,
    author_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    message_text VARCHAR(500),
    donation_amount_minor BIGINT,
    donation_currency VARCHAR(8),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_story_live_comments_session_created
    ON story_live_comments (live_session_id, created_at ASC);

CREATE INDEX IF NOT EXISTS idx_story_live_comments_story_created
    ON story_live_comments (story_id, created_at ASC);
