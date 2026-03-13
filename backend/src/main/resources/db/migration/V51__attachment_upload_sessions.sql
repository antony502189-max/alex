CREATE TABLE attachment_upload_sessions (
    id UUID PRIMARY KEY,
    chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    uploader_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    original_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    total_size_bytes BIGINT NOT NULL,
    uploaded_bytes BIGINT NOT NULL DEFAULT 0,
    duration_ms BIGINT,
    width INTEGER,
    height INTEGER,
    waveform TEXT,
    storage_path VARCHAR(1024) NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL,
    completed_attachment_id UUID REFERENCES attachments(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_chunk_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_attachment_upload_sessions_owner_status
    ON attachment_upload_sessions (uploader_user_id, status, expires_at DESC);

CREATE INDEX idx_attachment_upload_sessions_chat_created
    ON attachment_upload_sessions (chat_id, created_at DESC);
