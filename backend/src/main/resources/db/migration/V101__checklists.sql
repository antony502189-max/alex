CREATE TABLE IF NOT EXISTS checklists (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    topic_id UUID REFERENCES forum_topics(id) ON DELETE SET NULL,
    title VARCHAR(120) NOT NULL,
    description VARCHAR(1000),
    created_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_checklists_chat_updated
    ON checklists (chat_id, updated_at DESC, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_checklists_chat_topic_updated
    ON checklists (chat_id, topic_id, updated_at DESC, created_at DESC);

CREATE TABLE IF NOT EXISTS checklist_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    checklist_id UUID NOT NULL REFERENCES checklists(id) ON DELETE CASCADE,
    task_text VARCHAR(500) NOT NULL,
    position INTEGER NOT NULL,
    assigned_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMPTZ,
    completed_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    created_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_checklist_tasks_checklist_position
    ON checklist_tasks (checklist_id, position, created_at);

CREATE INDEX IF NOT EXISTS idx_checklist_tasks_checklist_completed
    ON checklist_tasks (checklist_id, completed, position);
