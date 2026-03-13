CREATE TABLE IF NOT EXISTS story_interactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    story_id UUID NOT NULL REFERENCES stories (id) ON DELETE CASCADE,
    actor_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    target_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    interaction_type VARCHAR(32) NOT NULL,
    reaction_code VARCHAR(64),
    message_text VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_story_interactions_story_created
    ON story_interactions (story_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_story_interactions_actor_created
    ON story_interactions (actor_user_id, created_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_story_reaction_actor
    ON story_interactions (story_id, actor_user_id, interaction_type)
    WHERE interaction_type = 'REACTION';

CREATE TABLE IF NOT EXISTS story_highlights (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title VARCHAR(120) NOT NULL,
    cover_story_id UUID REFERENCES stories (id) ON DELETE SET NULL,
    position INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_story_highlights_owner_position
    ON story_highlights (owner_user_id, position ASC, created_at ASC);

CREATE TABLE IF NOT EXISTS story_highlight_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    highlight_id UUID NOT NULL REFERENCES story_highlights (id) ON DELETE CASCADE,
    story_id UUID NOT NULL REFERENCES stories (id) ON DELETE CASCADE,
    position INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (highlight_id, story_id)
);

CREATE INDEX IF NOT EXISTS idx_story_highlight_items_highlight_position
    ON story_highlight_items (highlight_id, position ASC, created_at ASC);
