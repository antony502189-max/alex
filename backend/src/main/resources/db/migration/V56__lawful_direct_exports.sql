CREATE TABLE IF NOT EXISTS lawful_direct_exports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    target_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    operator_id VARCHAR(120) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    from_inclusive TIMESTAMPTZ,
    to_exclusive TIMESTAMPTZ,
    include_attachments_metadata BOOLEAN NOT NULL DEFAULT FALSE,
    message_count INTEGER NOT NULL DEFAULT 0,
    artifact_checksum VARCHAR(128) NOT NULL,
    artifact_location VARCHAR(512),
    exported_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_lawful_direct_export_window
        CHECK (to_exclusive IS NULL OR from_inclusive IS NULL OR to_exclusive > from_inclusive)
);

CREATE INDEX IF NOT EXISTS idx_lawful_direct_exports_target
    ON lawful_direct_exports (target_user_id, exported_at DESC);

CREATE INDEX IF NOT EXISTS idx_lawful_direct_exports_operator
    ON lawful_direct_exports (operator_id, exported_at DESC);
