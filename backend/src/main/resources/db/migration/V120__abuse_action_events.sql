create table if not exists abuse_action_events (
    id uuid primary key,
    action_type varchar(64) not null,
    actor_user_id uuid not null,
    chat_id uuid,
    created_at timestamptz not null default now()
);

create index if not exists idx_abuse_action_events_actor_action_created
    on abuse_action_events (action_type, actor_user_id, created_at desc);

create index if not exists idx_abuse_action_events_actor_chat_action_created
    on abuse_action_events (action_type, actor_user_id, chat_id, created_at desc);
