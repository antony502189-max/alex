create table if not exists auth_security_events (
    id uuid primary key,
    user_id uuid not null,
    session_id uuid,
    event_type varchar(64) not null,
    severity varchar(16) not null,
    ip_address varchar(64),
    user_agent varchar(255),
    device_name varchar(120),
    platform varchar(32),
    app_version varchar(32),
    details varchar(500),
    created_at timestamptz not null default now()
);

create index if not exists idx_auth_security_events_user_created
    on auth_security_events (user_id, created_at desc);
