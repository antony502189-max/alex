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

CREATE INDEX IF NOT EXISTS idx_stories_owner_user_id
    ON stories (owner_user_id);

CREATE INDEX IF NOT EXISTS idx_stories_expires_at
    ON stories (expires_at);

CREATE TABLE IF NOT EXISTS story_views (
    story_id UUID NOT NULL REFERENCES stories (id) ON DELETE CASCADE,
    viewer_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    viewed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (story_id, viewer_user_id)
);

CREATE INDEX IF NOT EXISTS idx_story_views_viewer_user_id
    ON story_views (viewer_user_id);
