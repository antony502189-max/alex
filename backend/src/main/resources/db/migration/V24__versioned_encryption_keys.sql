ALTER TABLE encryption_keys
    ADD COLUMN IF NOT EXISTS id UUID;

UPDATE encryption_keys
SET id = gen_random_uuid()
WHERE id IS NULL;

ALTER TABLE encryption_keys
    ALTER COLUMN id SET NOT NULL;

ALTER TABLE encryption_keys
    ADD COLUMN IF NOT EXISTS active BOOLEAN;

UPDATE encryption_keys
SET active = TRUE
WHERE active IS NULL;

ALTER TABLE encryption_keys
    ALTER COLUMN active SET NOT NULL;

ALTER TABLE encryption_keys
    ALTER COLUMN active SET DEFAULT TRUE;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'encryption_keys_pkey'
          AND conrelid = 'encryption_keys'::regclass
    ) THEN
        ALTER TABLE encryption_keys DROP CONSTRAINT encryption_keys_pkey;
    END IF;
END $$;

ALTER TABLE encryption_keys
    ADD CONSTRAINT encryption_keys_pkey PRIMARY KEY (id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uq_encryption_keys_chat_version'
          AND conrelid = 'encryption_keys'::regclass
    ) THEN
        ALTER TABLE encryption_keys
            ADD CONSTRAINT uq_encryption_keys_chat_version UNIQUE (chat_id, key_version);
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_encryption_keys_active_chat
    ON encryption_keys (chat_id)
    WHERE active = TRUE;
