ALTER TABLE bot_payment_invoices
    ADD COLUMN IF NOT EXISTS provider_token VARCHAR(128),
    ADD COLUMN IF NOT EXISTS provider_data_json TEXT NOT NULL DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS need_name BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS need_phone_number BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS need_email BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS need_shipping_address BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS flexible BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS max_tip_amount_units BIGINT,
    ADD COLUMN IF NOT EXISTS suggested_tip_amounts_json TEXT NOT NULL DEFAULT '[]',
    ADD COLUMN IF NOT EXISTS shipping_options_json TEXT NOT NULL DEFAULT '[]',
    ADD COLUMN IF NOT EXISTS successful_payment_message_id UUID;

ALTER TABLE bot_pre_checkout_queries
    ADD COLUMN IF NOT EXISTS requested_name VARCHAR(120),
    ADD COLUMN IF NOT EXISTS requested_phone_number VARCHAR(64),
    ADD COLUMN IF NOT EXISTS requested_email VARCHAR(120),
    ADD COLUMN IF NOT EXISTS shipping_address_json TEXT,
    ADD COLUMN IF NOT EXISTS shipping_option_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS shipping_option_title VARCHAR(120),
    ADD COLUMN IF NOT EXISTS shipping_option_amount_units BIGINT,
    ADD COLUMN IF NOT EXISTS tip_amount_units BIGINT,
    ADD COLUMN IF NOT EXISTS total_amount_units BIGINT,
    ADD COLUMN IF NOT EXISTS receipt_id UUID;

CREATE TABLE IF NOT EXISTS bot_payment_receipts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_invoice_id UUID NOT NULL REFERENCES bot_payment_invoices (payment_invoice_id) ON DELETE CASCADE,
    payment_intent_id UUID NOT NULL REFERENCES payment_intents (id) ON DELETE CASCADE,
    pre_checkout_query_id UUID NOT NULL REFERENCES bot_pre_checkout_queries (id) ON DELETE CASCADE,
    bot_user_id UUID NOT NULL REFERENCES bot_accounts (bot_user_id) ON DELETE CASCADE,
    chat_id UUID NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    invoice_message_id UUID NOT NULL,
    service_message_id UUID,
    payer_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    invoice_payload VARCHAR(255) NOT NULL,
    currency_code VARCHAR(16) NOT NULL DEFAULT 'XTR',
    base_amount_units BIGINT NOT NULL,
    shipping_amount_units BIGINT NOT NULL DEFAULT 0,
    tip_amount_units BIGINT NOT NULL DEFAULT 0,
    total_amount_units BIGINT NOT NULL,
    provider_token VARCHAR(128),
    provider_data_json TEXT NOT NULL DEFAULT '{}',
    payer_name VARCHAR(120),
    phone_number VARCHAR(64),
    email VARCHAR(120),
    shipping_address_json TEXT,
    shipping_option_id VARCHAR(64),
    shipping_option_title VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    refunded_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_bot_payment_receipts_intent
    ON bot_payment_receipts (payment_intent_id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_bot_payment_receipts_query
    ON bot_payment_receipts (pre_checkout_query_id);

CREATE INDEX IF NOT EXISTS idx_bot_payment_receipts_payer_created
    ON bot_payment_receipts (payer_user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_bot_payment_receipts_message
    ON bot_payment_receipts (invoice_message_id, service_message_id);
