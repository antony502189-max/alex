ALTER TABLE users
    ADD COLUMN photo_storage_provider VARCHAR(32),
    ADD COLUMN photo_bucket_name VARCHAR(255),
    ADD COLUMN photo_object_key VARCHAR(512),
    ADD COLUMN photo_content_type VARCHAR(255),
    ADD COLUMN photo_updated_at TIMESTAMPTZ;

ALTER TABLE chats
    ADD COLUMN photo_storage_provider VARCHAR(32),
    ADD COLUMN photo_bucket_name VARCHAR(255),
    ADD COLUMN photo_object_key VARCHAR(512),
    ADD COLUMN photo_content_type VARCHAR(255),
    ADD COLUMN photo_updated_at TIMESTAMPTZ;
