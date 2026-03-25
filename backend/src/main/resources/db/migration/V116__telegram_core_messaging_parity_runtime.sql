alter table chat_members
    add column if not exists manually_marked_unread boolean not null default false;

create table if not exists chat_reports (
    id uuid primary key,
    reporter_user_id uuid not null,
    chat_id uuid not null,
    category varchar(32) not null,
    details varchar(1000),
    created_at timestamp not null
);

create index if not exists idx_chat_reports_chat_created_at
    on chat_reports (chat_id, created_at desc);

create index if not exists idx_chat_reports_reporter_created_at
    on chat_reports (reporter_user_id, created_at desc);

create table if not exists message_reports (
    id uuid primary key,
    reporter_user_id uuid not null,
    message_id uuid not null,
    chat_id uuid not null,
    category varchar(32) not null,
    details varchar(1000),
    created_at timestamp not null
);

create index if not exists idx_message_reports_message_created_at
    on message_reports (message_id, created_at desc);

create index if not exists idx_message_reports_chat_created_at
    on message_reports (chat_id, created_at desc);

create table if not exists link_preview_cache (
    normalized_url varchar(1000) primary key,
    canonical_url varchar(1000),
    title varchar(512),
    description varchar(1000),
    site_name varchar(255),
    image_url varchar(1000),
    success boolean not null default true,
    fetched_at timestamp not null,
    expires_at timestamp not null
);

create table if not exists user_recent_stickers (
    user_id uuid not null,
    sticker_id uuid not null,
    used_at timestamp not null,
    usage_count integer not null default 1,
    primary key (user_id, sticker_id)
);

create index if not exists idx_user_recent_stickers_user_used_at
    on user_recent_stickers (user_id, used_at desc);

create table if not exists user_favorite_stickers (
    user_id uuid not null,
    sticker_id uuid not null,
    created_at timestamp not null,
    primary key (user_id, sticker_id)
);

create index if not exists idx_user_favorite_stickers_user_created_at
    on user_favorite_stickers (user_id, created_at desc);

create table if not exists user_recent_gifs (
    user_id uuid not null,
    attachment_id uuid not null,
    used_at timestamp not null,
    usage_count integer not null default 1,
    primary key (user_id, attachment_id)
);

create index if not exists idx_user_recent_gifs_user_used_at
    on user_recent_gifs (user_id, used_at desc);

create table if not exists user_sync_events (
    id bigserial primary key,
    user_id uuid not null,
    event_type varchar(64) not null,
    entity_type varchar(64),
    entity_id uuid,
    chat_id uuid,
    payload_json text not null,
    created_at timestamp not null
);

create index if not exists idx_user_sync_events_user_id_id
    on user_sync_events (user_id, id);
