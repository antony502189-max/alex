ALTER TABLE bot_payment_receipts
    ADD COLUMN IF NOT EXISTS refund_message_id UUID;

CREATE INDEX IF NOT EXISTS idx_bot_payment_receipts_refund_message
    ON bot_payment_receipts (refund_message_id);
