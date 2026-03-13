ALTER TABLE attachments
    ADD COLUMN IF NOT EXISTS storage_provider VARCHAR(32) NOT NULL DEFAULT 'LOCAL_FS';

ALTER TABLE attachments
    ADD COLUMN IF NOT EXISTS bucket_name VARCHAR(255);

ALTER TABLE attachments
    ADD COLUMN IF NOT EXISTS object_key VARCHAR(512);

ALTER TABLE attachments
    ALTER COLUMN nonce DROP NOT NULL;

ALTER TABLE attachments
    ALTER COLUMN key_version DROP NOT NULL;

UPDATE attachments
SET storage_provider = 'LOCAL_FS'
WHERE storage_provider IS NULL;

CREATE INDEX IF NOT EXISTS idx_attachments_storage_provider
    ON attachments (storage_provider);
