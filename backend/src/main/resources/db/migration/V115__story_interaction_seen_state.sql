alter table story_interactions
    add column if not exists seen_at timestamptz;
