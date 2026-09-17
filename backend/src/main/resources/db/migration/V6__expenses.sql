-- =====================================================================
-- V6: Expenses
-- =====================================================================

CREATE TABLE expenses (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id       UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    category_id       UUID REFERENCES categories(id) ON DELETE SET NULL, -- categories.type = 'EXPENSE'

    description       VARCHAR(255) NOT NULL,
    amount            NUMERIC(14,2) NOT NULL,
    expense_date      DATE NOT NULL,
    payment_method    VARCHAR(20) NOT NULL, -- reuses the same set as payments: CASH, UPI, BANK_TRANSFER, CARD, CHEQUE, OTHER
    reference_number  VARCHAR(100),
    notes             TEXT,
    attachment_url    VARCHAR(500), -- optional receipt/bill image or PDF, uploaded via a future file-storage integration

    recorded_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,

    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_expenses_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_expenses_business_id ON expenses(business_id);
CREATE INDEX idx_expenses_business_date ON expenses(business_id, expense_date);
CREATE INDEX idx_expenses_business_category ON expenses(business_id, category_id);

-- Seed the common expense categories from spec section 17 for every
-- existing business, so the dropdown isn't empty on first use. New
-- businesses get these via BusinessRegistration in AuthService instead
-- (see the comment there) so this INSERT only backfills pre-existing tenants.
INSERT INTO categories (business_id, name, type)
SELECT b.id, cat.name, 'EXPENSE'
FROM businesses b
CROSS JOIN (VALUES ('Rent'), ('Electricity'), ('Internet'), ('Salary'), ('Purchase'), ('Transport'), ('Maintenance'), ('Other')) AS cat(name)
ON CONFLICT (business_id, name, type) DO NOTHING;
