ALTER TABLE attachments
    ADD COLUMN preview_bucket_name VARCHAR(255),
    ADD COLUMN preview_object_key VARCHAR(512),
    ADD COLUMN processing_status VARCHAR(16) NOT NULL DEFAULT 'NOT_REQUIRED';

ALTER TABLE stories
    ADD COLUMN media_preview_bucket_name VARCHAR(255),
    ADD COLUMN media_preview_object_key VARCHAR(512),
    ADD COLUMN media_processing_status VARCHAR(16) NOT NULL DEFAULT 'NOT_REQUIRED';

CREATE TABLE media_processing_jobs (
    id UUID PRIMARY KEY,
    owner_type VARCHAR(16) NOT NULL,
    owner_id UUID NOT NULL,
    job_type VARCHAR(32) NOT NULL,
    source_bucket_name VARCHAR(255) NOT NULL,
    source_object_key VARCHAR(512) NOT NULL,
    derivative_bucket_name VARCHAR(255),
    derivative_object_key VARCHAR(512),
    status VARCHAR(16) NOT NULL,
    error_message TEXT,
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_media_processing_jobs_owner_job UNIQUE (owner_type, owner_id, job_type)
);

CREATE INDEX idx_media_processing_jobs_status_created
    ON media_processing_jobs (status, created_at ASC);
