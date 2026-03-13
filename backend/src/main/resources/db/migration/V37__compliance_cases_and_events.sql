CREATE TABLE IF NOT EXISTS compliance_cases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    target_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    case_reference VARCHAR(120) NOT NULL,
    legal_basis VARCHAR(255) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    from_inclusive TIMESTAMPTZ,
    to_exclusive TIMESTAMPTZ,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_APPROVAL',
    requested_by_operator_id VARCHAR(120) NOT NULL,
    approved_by_operator_id VARCHAR(120),
    last_exported_by_operator_id VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    approved_at TIMESTAMPTZ,
    last_exported_at TIMESTAMPTZ,
    export_count INTEGER NOT NULL DEFAULT 0,
    latest_artifact_checksum VARCHAR(128),
    CONSTRAINT chk_compliance_case_window
        CHECK (to_exclusive IS NULL OR from_inclusive IS NULL OR to_exclusive > from_inclusive)
);

CREATE INDEX IF NOT EXISTS idx_compliance_cases_target_user
    ON compliance_cases (target_user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_compliance_cases_status
    ON compliance_cases (status, created_at DESC);

CREATE TABLE IF NOT EXISTS compliance_case_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id UUID NOT NULL REFERENCES compliance_cases (id) ON DELETE CASCADE,
    actor_operator_id VARCHAR(120) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_compliance_case_events_case
    ON compliance_case_events (case_id, created_at ASC);
