ALTER TABLE attachments
    ADD COLUMN IF NOT EXISTS album_id UUID,
    ADD COLUMN IF NOT EXISTS album_item_index INTEGER,
    ADD COLUMN IF NOT EXISTS thumbnail_bucket_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS thumbnail_object_key VARCHAR(512),
    ADD COLUMN IF NOT EXISTS moderation_status VARCHAR(16) NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN IF NOT EXISTS moderation_reason VARCHAR(255),
    ADD COLUMN IF NOT EXISTS moderation_sensitive BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS moderation_reviewed_by_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS moderation_reviewed_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_attachments_album
    ON attachments (album_id, album_item_index, created_at);

CREATE INDEX IF NOT EXISTS idx_attachments_moderation_status
    ON attachments (moderation_status, created_at DESC);

ALTER TABLE attachment_upload_sessions
    ADD COLUMN IF NOT EXISTS album_id UUID,
    ADD COLUMN IF NOT EXISTS album_item_index INTEGER;
