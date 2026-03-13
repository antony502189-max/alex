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
