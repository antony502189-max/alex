ALTER TABLE users
    ADD COLUMN IF NOT EXISTS preferred_language VARCHAR(16),
    ADD COLUMN IF NOT EXISTS translation_target_language VARCHAR(16);

CREATE TABLE IF NOT EXISTS message_translation_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL,
    provider VARCHAR(512) NOT NULL,
    source_language VARCHAR(16) NOT NULL,
    target_language VARCHAR(16) NOT NULL,
    original_text TEXT,
    translated_text TEXT,
    original_caption TEXT,
    translated_caption TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (message_id, target_language)
);

CREATE INDEX IF NOT EXISTS idx_message_translation_cache_message
    ON message_translation_cache (message_id, updated_at DESC);
