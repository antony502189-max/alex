CREATE TABLE IF NOT EXISTS bot_inline_result_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bot_user_id UUID NOT NULL REFERENCES bot_accounts (bot_user_id) ON DELETE CASCADE,
    query_text VARCHAR(512) NOT NULL,
    result_id VARCHAR(64) NOT NULL,
    title VARCHAR(120) NOT NULL,
    description VARCHAR(255),
    text VARCHAR(4000) NOT NULL,
    cached_until TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_bot_inline_result_cache_lookup
    ON bot_inline_result_cache (bot_user_id, query_text, cached_until DESC, created_at DESC);
