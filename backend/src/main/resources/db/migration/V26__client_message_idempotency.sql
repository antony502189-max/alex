CREATE TABLE IF NOT EXISTS client_message_requests (
    id UUID PRIMARY KEY,
    sender_user_id UUID NOT NULL,
    client_message_id UUID NOT NULL,
    chat_id UUID NOT NULL,
    message_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CONSTRAINT uq_client_message_requests_sender_client
        UNIQUE (sender_user_id, client_message_id)
);

CREATE INDEX IF NOT EXISTS idx_client_message_requests_message_id
    ON client_message_requests (message_id);

CREATE INDEX IF NOT EXISTS idx_client_message_requests_status_updated
    ON client_message_requests (status, updated_at);
