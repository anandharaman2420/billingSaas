-- =====================================================================
-- V10: Separation of Standard Invoices and POS Billing Sequences
-- =====================================================================

ALTER TABLE business_settings
    ADD COLUMN IF NOT EXISTS pos_prefix VARCHAR(20) NOT NULL DEFAULT 'BILL',
    ADD COLUMN IF NOT EXISTS pos_number_format VARCHAR(50) NOT NULL DEFAULT '{PREFIX}-{YEAR}-{SEQ:00000}',
    ADD COLUMN IF NOT EXISTS pos_next_sequence BIGINT NOT NULL DEFAULT 1;

ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS invoice_type VARCHAR(20) NOT NULL DEFAULT 'STANDARD';

CREATE INDEX IF NOT EXISTS idx_invoices_business_type ON invoices(business_id, invoice_type);
