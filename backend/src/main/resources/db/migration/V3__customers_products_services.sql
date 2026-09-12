-- =====================================================================
-- V3: Customers, Categories, Products, Services
-- =====================================================================

-- ---------------------------------------------------------------------
-- customers
-- ---------------------------------------------------------------------
CREATE TABLE customers (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id  UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    customer_name VARCHAR(150) NOT NULL,
    phone        VARCHAR(20),
    email        VARCHAR(150),
    address_line VARCHAR(255),
    city         VARCHAR(100),
    state        VARCHAR(100),
    pincode      VARCHAR(20),
    gstin        VARCHAR(20),
    notes        TEXT,
    status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, INACTIVE
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_customers_business_id ON customers(business_id);
CREATE INDEX idx_customers_business_status ON customers(business_id, status);
-- supports ILIKE search on name/phone/email scoped to a business
CREATE INDEX idx_customers_business_name ON customers(business_id, customer_name);

-- ---------------------------------------------------------------------
-- categories: shared by products and services, per business
-- ---------------------------------------------------------------------
CREATE TABLE categories (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id  UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    name         VARCHAR(100) NOT NULL,
    type         VARCHAR(20) NOT NULL, -- PRODUCT, SERVICE, EXPENSE (expense categories reuse this table from Phase 4 onward)
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_categories_business_name_type UNIQUE (business_id, name, type)
);

CREATE INDEX idx_categories_business_id ON categories(business_id);

-- ---------------------------------------------------------------------
-- products
-- ---------------------------------------------------------------------
CREATE TABLE products (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id         UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    product_name        VARCHAR(150) NOT NULL,
    sku                 VARCHAR(50),
    category_id         UUID REFERENCES categories(id) ON DELETE SET NULL,
    description         TEXT,
    unit                VARCHAR(20) NOT NULL DEFAULT 'PCS', -- PCS, KG, LTR, HOUR, etc. - kept free-text for flexibility
    purchase_price      NUMERIC(14,2) NOT NULL DEFAULT 0,
    selling_price       NUMERIC(14,2) NOT NULL DEFAULT 0,
    tax_rate_percent    NUMERIC(5,2)  NOT NULL DEFAULT 0,
    stock_quantity      NUMERIC(14,2) NOT NULL DEFAULT 0,
    minimum_stock_level NUMERIC(14,2) NOT NULL DEFAULT 0,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, INACTIVE
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- SKU must be unique within a business, but is optional (many small
    -- shops won't use SKUs at first), so this is a partial unique index
    -- rather than a plain UNIQUE column constraint.
    CONSTRAINT chk_products_prices_non_negative CHECK (purchase_price >= 0 AND selling_price >= 0),
    CONSTRAINT chk_products_stock_non_negative CHECK (stock_quantity >= 0 AND minimum_stock_level >= 0)
);

CREATE UNIQUE INDEX uq_products_business_sku ON products(business_id, sku) WHERE sku IS NOT NULL AND sku <> '';
CREATE INDEX idx_products_business_id ON products(business_id);
CREATE INDEX idx_products_business_status ON products(business_id, status);
CREATE INDEX idx_products_business_name ON products(business_id, product_name);
CREATE INDEX idx_products_category ON products(category_id);

-- ---------------------------------------------------------------------
-- services (billable services, e.g. labour charges)
-- ---------------------------------------------------------------------
CREATE TABLE services (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id       UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    service_name      VARCHAR(150) NOT NULL,
    description       TEXT,
    price             NUMERIC(14,2) NOT NULL DEFAULT 0,
    tax_rate_percent  NUMERIC(5,2)  NOT NULL DEFAULT 0,
    category_id       UUID REFERENCES categories(id) ON DELETE SET NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, INACTIVE
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_services_price_non_negative CHECK (price >= 0)
);

CREATE INDEX idx_services_business_id ON services(business_id);
CREATE INDEX idx_services_business_status ON services(business_id, status);
CREATE INDEX idx_services_business_name ON services(business_id, service_name);
CREATE INDEX idx_services_category ON services(category_id);
