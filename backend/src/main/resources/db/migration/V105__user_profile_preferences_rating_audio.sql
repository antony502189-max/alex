CREATE TABLE IF NOT EXISTS user_profile_preferences (
    user_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    default_profile_tab VARCHAR(32) NOT NULL DEFAULT 'MEDIA',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS user_profile_ratings (
    user_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    rating_score BIGINT NOT NULL DEFAULT 0,
    rating_level VARCHAR(32) NOT NULL DEFAULT 'NEW',
    received_gift_count BIGINT NOT NULL DEFAULT 0,
    sent_gift_count BIGINT NOT NULL DEFAULT 0,
    received_gift_premium_days BIGINT NOT NULL DEFAULT 0,
    sent_gift_premium_days BIGINT NOT NULL DEFAULT 0,
    stars_received_units BIGINT NOT NULL DEFAULT 0,
    stars_spent_units BIGINT NOT NULL DEFAULT 0,
    successful_transaction_count BIGINT NOT NULL DEFAULT 0,
    last_recomputed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS profile_audios (
    user_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    attachment_id UUID NOT NULL UNIQUE REFERENCES attachments (id) ON DELETE CASCADE,
    title VARCHAR(120),
    performer VARCHAR(120),
    caption VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
