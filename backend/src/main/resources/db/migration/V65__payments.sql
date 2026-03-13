CREATE TABLE IF NOT EXISTS payment_wallet_accounts (
    user_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    balance_units BIGINT NOT NULL DEFAULT 0,
    currency_code VARCHAR(16) NOT NULL DEFAULT 'XTR',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS payment_invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_by_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    recipient_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    amount_units BIGINT NOT NULL,
    currency_code VARCHAR(16) NOT NULL DEFAULT 'XTR',
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    metadata_json TEXT NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_payment_invoices_creator_created
    ON payment_invoices (created_by_user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_payment_invoices_recipient_created
    ON payment_invoices (recipient_user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS payment_intents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id UUID NOT NULL REFERENCES payment_invoices (id) ON DELETE CASCADE,
    payer_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    recipient_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    amount_units BIGINT NOT NULL,
    currency_code VARCHAR(16) NOT NULL DEFAULT 'XTR',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    canceled_reason VARCHAR(255),
    refunded_reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    confirmed_at TIMESTAMPTZ,
    canceled_at TIMESTAMPTZ,
    refunded_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payment_intents_invoice_created
    ON payment_intents (invoice_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_payment_intents_payer_created
    ON payment_intents (payer_user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS payment_wallet_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_user_id UUID NOT NULL REFERENCES payment_wallet_accounts (user_id) ON DELETE CASCADE,
    counterparty_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    invoice_id UUID REFERENCES payment_invoices (id) ON DELETE SET NULL,
    payment_intent_id UUID REFERENCES payment_intents (id) ON DELETE SET NULL,
    transaction_type VARCHAR(32) NOT NULL,
    direction VARCHAR(16) NOT NULL,
    amount_units BIGINT NOT NULL,
    balance_after_units BIGINT NOT NULL,
    currency_code VARCHAR(16) NOT NULL DEFAULT 'XTR',
    description VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payment_wallet_transactions_wallet_created
    ON payment_wallet_transactions (wallet_user_id, created_at DESC);
