-- =====================================================================
-- V4: Invoices and Invoice Items
-- =====================================================================

CREATE TABLE invoices (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id                 UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    customer_id                 UUID NOT NULL REFERENCES customers(id),
    invoice_number              VARCHAR(50), -- assigned when the invoice transitions DRAFT -> ISSUED, not at creation, so an abandoned draft never burns a number
    invoice_date                DATE NOT NULL,
    due_date                    DATE,
    status                      VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    -- DRAFT, ISSUED, PARTIALLY_PAID, PAID, OVERDUE, CANCELLED

    subtotal                    NUMERIC(14,2) NOT NULL DEFAULT 0, -- sum(qty * unit_price) across items, before any discount
    item_discount_total         NUMERIC(14,2) NOT NULL DEFAULT 0, -- sum of per-line discounts
    additional_discount_amount  NUMERIC(14,2) NOT NULL DEFAULT 0, -- invoice-level extra discount, applied post-tax (see InvoiceService for rationale)
    taxable_amount               NUMERIC(14,2) NOT NULL DEFAULT 0, -- subtotal - item_discount_total
    cgst_amount                  NUMERIC(14,2) NOT NULL DEFAULT 0,
    sgst_amount                  NUMERIC(14,2) NOT NULL DEFAULT 0,
    igst_amount                  NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_tax                    NUMERIC(14,2) NOT NULL DEFAULT 0,
    grand_total                  NUMERIC(14,2) NOT NULL DEFAULT 0, -- taxable_amount + total_tax - additional_discount_amount
    amount_paid                  NUMERIC(14,2) NOT NULL DEFAULT 0, -- maintained by the Payments module (Phase 4)

    notes                       TEXT,
    terms                       TEXT,

    cancelled_at                 TIMESTAMPTZ,
    cancellation_reason          VARCHAR(255),

    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_invoices_amounts_non_negative CHECK (
        subtotal >= 0 AND item_discount_total >= 0 AND additional_discount_amount >= 0
        AND taxable_amount >= 0 AND total_tax >= 0 AND grand_total >= 0 AND amount_paid >= 0
    )
);

-- Partial unique index rather than a plain UNIQUE column constraint,
-- since invoice_number is NULL for every DRAFT invoice.
CREATE UNIQUE INDEX uq_invoices_business_number ON invoices(business_id, invoice_number) WHERE invoice_number IS NOT NULL;

CREATE INDEX idx_invoices_business_id ON invoices(business_id);
CREATE INDEX idx_invoices_business_status ON invoices(business_id, status);
CREATE INDEX idx_invoices_business_customer ON invoices(business_id, customer_id);
CREATE INDEX idx_invoices_business_date ON invoices(business_id, invoice_date);

CREATE TABLE invoice_items (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id        UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    item_type         VARCHAR(20) NOT NULL, -- PRODUCT, SERVICE
    product_id        UUID REFERENCES products(id) ON DELETE SET NULL,
    service_id        UUID REFERENCES services(id) ON DELETE SET NULL,

    -- Snapshotted at billing time so historical invoices remain accurate
    -- even if the underlying product/service is later renamed or deleted.
    item_name         VARCHAR(150) NOT NULL,
    description       TEXT,

    quantity          NUMERIC(14,2) NOT NULL DEFAULT 1,
    unit_price        NUMERIC(14,2) NOT NULL DEFAULT 0,
    discount_amount   NUMERIC(14,2) NOT NULL DEFAULT 0,
    tax_rate_percent  NUMERIC(5,2)  NOT NULL DEFAULT 0,
    tax_amount        NUMERIC(14,2) NOT NULL DEFAULT 0,
    line_total        NUMERIC(14,2) NOT NULL DEFAULT 0, -- (quantity * unit_price - discount_amount) + tax_amount

    sort_order        INT NOT NULL DEFAULT 0,

    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_invoice_items_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_invoice_items_amounts_non_negative CHECK (
        unit_price >= 0 AND discount_amount >= 0 AND tax_amount >= 0 AND line_total >= 0
    ),
    CONSTRAINT chk_invoice_items_one_reference CHECK (
        (item_type = 'PRODUCT' AND product_id IS NOT NULL AND service_id IS NULL)
        OR (item_type = 'SERVICE' AND service_id IS NOT NULL AND product_id IS NULL)
    )
);

CREATE INDEX idx_invoice_items_invoice_id ON invoice_items(invoice_id);
CREATE INDEX idx_invoice_items_product_id ON invoice_items(product_id);
CREATE INDEX idx_invoice_items_service_id ON invoice_items(service_id);
