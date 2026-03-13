ALTER TABLE chats
ADD COLUMN IF NOT EXISTS public_username VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uq_chats_public_username
ON chats (lower(public_username))
WHERE public_username IS NOT NULL;
