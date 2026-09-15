-- =====================================================================
-- V5: Payments
-- =====================================================================

CREATE TABLE payments (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id       UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    invoice_id        UUID NOT NULL REFERENCES invoices(id),
    customer_id       UUID NOT NULL REFERENCES customers(id), -- denormalized from the invoice, for fast filtering

    amount            NUMERIC(14,2) NOT NULL,
    payment_date      DATE NOT NULL,
    payment_method    VARCHAR(20) NOT NULL, -- CASH, UPI, BANK_TRANSFER, CARD, CHEQUE, OTHER
    reference_number  VARCHAR(100),
    notes             TEXT,

    recorded_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,

    -- Payments are never hard-deleted (spec section 42) - a mistaken
    -- entry is voided instead, which excludes it from invoice total
    -- recalculation while preserving the audit trail.
    voided            BOOLEAN NOT NULL DEFAULT false,
    voided_at         TIMESTAMPTZ,
    voided_reason     VARCHAR(255),

    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_payments_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_payments_business_id ON payments(business_id);
CREATE INDEX idx_payments_business_invoice ON payments(business_id, invoice_id);
CREATE INDEX idx_payments_business_customer ON payments(business_id, customer_id);
CREATE INDEX idx_payments_business_date ON payments(business_id, payment_date);
CREATE INDEX idx_payments_business_method ON payments(business_id, payment_method);
