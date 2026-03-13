CREATE TABLE IF NOT EXISTS premium_entitlements (
    user_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    tier VARCHAR(32) NOT NULL DEFAULT 'PREMIUM',
    active_until TIMESTAMPTZ,
    custom_emoji_status_id UUID,
    custom_emoji_status_emoji VARCHAR(16),
    custom_emoji_status_label VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS premium_custom_emojis (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    short_code VARCHAR(64) NOT NULL UNIQUE,
    emoji VARCHAR(16) NOT NULL,
    label VARCHAR(64) NOT NULL,
    premium_required BOOLEAN NOT NULL DEFAULT TRUE,
    position INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_premium_custom_emojis_position
    ON premium_custom_emojis (position ASC, created_at ASC);

CREATE TABLE IF NOT EXISTS premium_gifts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    recipient_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    custom_emoji_id UUID REFERENCES premium_custom_emojis (id) ON DELETE SET NULL,
    message VARCHAR(255),
    premium_days_granted INTEGER NOT NULL DEFAULT 30,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_premium_gifts_recipient_created
    ON premium_gifts (recipient_user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_premium_gifts_sender_created
    ON premium_gifts (sender_user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS channel_boosts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    channel_chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    boosted_by_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    boost_count INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (channel_chat_id, boosted_by_user_id)
);

CREATE INDEX IF NOT EXISTS idx_channel_boosts_channel_updated
    ON channel_boosts (channel_chat_id, updated_at DESC);

INSERT INTO premium_custom_emojis (short_code, emoji, label, premium_required, position)
VALUES
    ('premium_crown', '👑', 'Premium Crown', TRUE, 0),
    ('ruby_heart', '❤️', 'Ruby Heart', TRUE, 1),
    ('rocket_glow', '🚀', 'Rocket Glow', TRUE, 2),
    ('sparkles_gold', '✨', 'Golden Sparkles', TRUE, 3)
ON CONFLICT (short_code) DO NOTHING;
