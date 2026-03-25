alter table auth_login_code_challenges
    add column if not exists request_fingerprint_hash varchar(128);

create index if not exists idx_auth_login_code_challenges_requested_by_ip_created
    on auth_login_code_challenges (requested_by_ip, created_at desc);

create index if not exists idx_auth_login_code_challenges_request_fingerprint_created
    on auth_login_code_challenges (request_fingerprint_hash, created_at desc);
