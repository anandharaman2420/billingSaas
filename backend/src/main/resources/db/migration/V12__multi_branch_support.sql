CREATE TABLE branches (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id  UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    branch_name  VARCHAR(150) NOT NULL,
    address_line VARCHAR(255),
    city         VARCHAR(100),
    state        VARCHAR(100),
    pincode      VARCHAR(20),
    country      VARCHAR(100) NOT NULL DEFAULT 'India',
    phone        VARCHAR(20),
    email        VARCHAR(150),
    gstin        VARCHAR(20),
    is_default   BOOLEAN NOT NULL DEFAULT false,
    status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_branches_business_name UNIQUE (business_id, branch_name),
    CONSTRAINT uq_branches_id_business UNIQUE (id, business_id)
);

CREATE UNIQUE INDEX uq_branches_business_default ON branches(business_id) WHERE is_default = true;

INSERT INTO branches (business_id, branch_name, address_line, city, state, pincode, country, phone, email, gstin, is_default)
SELECT id, 'Main Branch', address_line, city, state, pincode, country, phone, email, gstin, true
FROM businesses;

ALTER TABLE customers ADD COLUMN branch_id UUID;
ALTER TABLE products ADD COLUMN branch_id UUID;
ALTER TABLE services ADD COLUMN branch_id UUID;
ALTER TABLE expenses ADD COLUMN branch_id UUID;
ALTER TABLE invoices ADD COLUMN branch_id UUID;
ALTER TABLE business_settings ADD COLUMN branch_id UUID;

UPDATE customers c SET branch_id = b.id FROM branches b WHERE b.business_id = c.business_id AND b.is_default;
UPDATE products p SET branch_id = b.id FROM branches b WHERE b.business_id = p.business_id AND b.is_default;
UPDATE services s SET branch_id = b.id FROM branches b WHERE b.business_id = s.business_id AND b.is_default;
UPDATE expenses e SET branch_id = b.id FROM branches b WHERE b.business_id = e.business_id AND b.is_default;
UPDATE invoices i SET branch_id = b.id FROM branches b WHERE b.business_id = i.business_id AND b.is_default;
UPDATE business_settings s SET branch_id = b.id FROM branches b WHERE b.business_id = s.business_id AND b.is_default;

ALTER TABLE customers ALTER COLUMN branch_id SET NOT NULL;
ALTER TABLE products ALTER COLUMN branch_id SET NOT NULL;
ALTER TABLE services ALTER COLUMN branch_id SET NOT NULL;
ALTER TABLE expenses ALTER COLUMN branch_id SET NOT NULL;
ALTER TABLE invoices ALTER COLUMN branch_id SET NOT NULL;
ALTER TABLE business_settings ALTER COLUMN branch_id SET NOT NULL;

ALTER TABLE customers ADD CONSTRAINT fk_customers_branch FOREIGN KEY (branch_id, business_id) REFERENCES branches(id, business_id);
ALTER TABLE products ADD CONSTRAINT fk_products_branch FOREIGN KEY (branch_id, business_id) REFERENCES branches(id, business_id);
ALTER TABLE services ADD CONSTRAINT fk_services_branch FOREIGN KEY (branch_id, business_id) REFERENCES branches(id, business_id);
ALTER TABLE expenses ADD CONSTRAINT fk_expenses_branch FOREIGN KEY (branch_id, business_id) REFERENCES branches(id, business_id);
ALTER TABLE invoices ADD CONSTRAINT fk_invoices_branch FOREIGN KEY (branch_id, business_id) REFERENCES branches(id, business_id);
ALTER TABLE business_settings ADD CONSTRAINT fk_business_settings_branch FOREIGN KEY (branch_id, business_id) REFERENCES branches(id, business_id);

ALTER TABLE business_settings DROP CONSTRAINT business_settings_business_id_key;
ALTER TABLE business_settings ADD CONSTRAINT uq_business_settings_branch UNIQUE (branch_id);

DROP INDEX uq_invoices_business_number;
CREATE UNIQUE INDEX uq_invoices_branch_number ON invoices(business_id, branch_id, invoice_number)
    WHERE invoice_number IS NOT NULL;

DROP INDEX uq_products_business_sku;
CREATE UNIQUE INDEX uq_products_branch_sku ON products(branch_id, sku) WHERE sku IS NOT NULL AND sku <> '';

ALTER TABLE invoices ADD COLUMN last_reminder_sent_at TIMESTAMPTZ;

CREATE INDEX idx_customers_branch_status ON customers(branch_id, status);
CREATE INDEX idx_products_branch_status ON products(branch_id, status);
CREATE INDEX idx_services_branch_status ON services(branch_id, status);
CREATE INDEX idx_expenses_branch_date ON expenses(branch_id, expense_date);
CREATE INDEX idx_invoices_branch_status ON invoices(branch_id, status);
