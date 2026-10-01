-- =====================================================================
-- V9: HSN/SAC codes (advanced GST, spec section 38)
-- =====================================================================

-- HSN codes classify goods, SAC codes classify services, for GST
-- reporting/e-invoicing. Optional free-text here rather than validated
-- against the official (large, periodically-changing) code list.
ALTER TABLE products ADD COLUMN hsn_code VARCHAR(20);
ALTER TABLE services ADD COLUMN sac_code VARCHAR(20);

-- Snapshotted onto each invoice line at billing time, same rationale as
-- item_name/unit_price/tax_rate_percent already being snapshotted (V4):
-- a historical invoice keeps the code that applied when it was billed.
ALTER TABLE invoice_items ADD COLUMN hsn_sac_code VARCHAR(20);
