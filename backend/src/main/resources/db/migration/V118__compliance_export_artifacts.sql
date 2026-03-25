create table if not exists compliance_case_export_artifacts (
    id uuid primary key,
    case_id uuid not null,
    exported_by_operator_id varchar(120) not null,
    exported_at timestamptz not null,
    message_count integer not null,
    artifact_checksum varchar(128) not null,
    storage_path varchar(512) not null,
    encryption_iv varchar(64) not null,
    content_type varchar(120) not null,
    expires_at timestamptz not null,
    download_count integer not null default 0,
    last_downloaded_at timestamptz,
    last_downloaded_by_operator_id varchar(120),
    deleted_at timestamptz,
    created_at timestamptz not null default now()
);

create index if not exists idx_compliance_export_artifacts_case
    on compliance_case_export_artifacts (case_id, exported_at desc, created_at desc);

create index if not exists idx_compliance_export_artifacts_expiry
    on compliance_case_export_artifacts (expires_at)
    where deleted_at is null;

create table if not exists compliance_case_export_download_audits (
    id uuid primary key,
    artifact_id uuid not null,
    case_id uuid not null,
    operator_id varchar(120) not null,
    downloaded_at timestamptz not null,
    checksum_verified boolean not null,
    created_at timestamptz not null default now()
);

create index if not exists idx_compliance_export_download_audits_artifact
    on compliance_case_export_download_audits (artifact_id, downloaded_at desc);
