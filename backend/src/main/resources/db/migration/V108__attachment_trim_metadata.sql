ALTER TABLE attachments
    ADD COLUMN IF NOT EXISTS source_attachment_id UUID REFERENCES attachments (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS trim_start_ms BIGINT,
    ADD COLUMN IF NOT EXISTS trim_end_ms BIGINT;

CREATE INDEX IF NOT EXISTS idx_attachments_source_attachment
    ON attachments (source_attachment_id);
