ALTER TABLE bot_message_actions
    ADD COLUMN IF NOT EXISTS payment_invoice_id UUID REFERENCES payment_invoices (id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_bot_message_actions_payment_invoice
    ON bot_message_actions (payment_invoice_id);

CREATE TABLE IF NOT EXISTS bot_payment_invoices (
    payment_invoice_id UUID PRIMARY KEY REFERENCES payment_invoices (id) ON DELETE CASCADE,
    bot_user_id UUID NOT NULL REFERENCES bot_accounts (bot_user_id) ON DELETE CASCADE,
    chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    message_id UUID NOT NULL,
    payer_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    invoice_payload VARCHAR(255) NOT NULL,
    pay_button_text VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_bot_payment_invoices_message
    ON bot_payment_invoices (message_id);

CREATE INDEX IF NOT EXISTS idx_bot_payment_invoices_bot_created
    ON bot_payment_invoices (bot_user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_bot_payment_invoices_payer_created
    ON bot_payment_invoices (payer_user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS bot_pre_checkout_queries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bot_user_id UUID NOT NULL REFERENCES bot_accounts (bot_user_id) ON DELETE CASCADE,
    chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    message_id UUID NOT NULL,
    payment_invoice_id UUID NOT NULL REFERENCES bot_payment_invoices (payment_invoice_id) ON DELETE CASCADE,
    from_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    payment_intent_id UUID REFERENCES payment_intents (id) ON DELETE SET NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    answer_text VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    answered_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_bot_pre_checkout_queries_bot_created
    ON bot_pre_checkout_queries (bot_user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_bot_pre_checkout_queries_from_created
    ON bot_pre_checkout_queries (from_user_id, created_at DESC);

ALTER TABLE bot_updates
    ADD COLUMN IF NOT EXISTS pre_checkout_query_id UUID REFERENCES bot_pre_checkout_queries (id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_bot_updates_pre_checkout_query
    ON bot_updates (pre_checkout_query_id);
