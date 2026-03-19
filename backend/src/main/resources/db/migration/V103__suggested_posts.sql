CREATE TABLE IF NOT EXISTS suggested_posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    submitted_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    ciphertext TEXT NOT NULL,
    nonce TEXT NOT NULL,
    key_version INTEGER NOT NULL,
    sticker_id UUID,
    attachment_ids TEXT NOT NULL DEFAULT '',
    status VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED',
    payment_amount_units BIGINT,
    payment_currency_code VARCHAR(16),
    reviewed_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    published_message_id UUID,
    approved_at TIMESTAMPTZ,
    declined_at TIMESTAMPTZ,
    decline_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_suggested_posts_chat_status_created
    ON suggested_posts (chat_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_suggested_posts_chat_submitter_created
    ON suggested_posts (chat_id, submitted_by_user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS suggested_post_payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    suggested_post_id UUID NOT NULL UNIQUE REFERENCES suggested_posts(id) ON DELETE CASCADE,
    invoice_id UUID NOT NULL UNIQUE REFERENCES payment_invoices(id) ON DELETE CASCADE,
    payment_intent_id UUID REFERENCES payment_intents(id) ON DELETE SET NULL,
    payer_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recipient_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount_units BIGINT NOT NULL,
    currency_code VARCHAR(16) NOT NULL DEFAULT 'XTR',
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_suggested_post_payments_payer_status
    ON suggested_post_payments (payer_user_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_suggested_post_payments_recipient_status
    ON suggested_post_payments (recipient_user_id, status, created_at DESC);
