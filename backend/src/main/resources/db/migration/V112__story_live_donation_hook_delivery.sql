ALTER TABLE story_live_comments
    ADD COLUMN IF NOT EXISTS hook_delivery_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_hook_delivery_attempt_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS hook_delivered_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_hook_error VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_story_live_comments_hook_pending
    ON story_live_comments (hook_delivered_at, created_at ASC)
    WHERE donation_amount_minor IS NOT NULL;
