-- =====================================================================
-- V1: Core multi-tenant schema - businesses (tenants) and users
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto"; -- for gen_random_uuid()

-- ---------------------------------------------------------------------
-- businesses: the tenant table. Every other business-scoped table
-- carries a business_id foreign key to this table.
-- ---------------------------------------------------------------------
CREATE TABLE businesses (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_name       VARCHAR(200) NOT NULL,
    business_type       VARCHAR(100),
    owner_name          VARCHAR(150) NOT NULL,
    email               VARCHAR(150) NOT NULL,
    phone               VARCHAR(20)  NOT NULL,
    address_line        VARCHAR(255),
    city                VARCHAR(100),
    state               VARCHAR(100),
    pincode             VARCHAR(20),
    country             VARCHAR(100) DEFAULT 'India',
    gstin               VARCHAR(20),
    logo_url            VARCHAR(500),
    plan                VARCHAR(30)  NOT NULL DEFAULT 'FREE',   -- FREE, BASIC, PROFESSIONAL, BUSINESS
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, SUSPENDED, CANCELLED
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_businesses_email UNIQUE (email)
);

CREATE INDEX idx_businesses_status ON businesses(status);

-- ---------------------------------------------------------------------
-- users: login identities, always scoped to exactly one business.
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id         UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    full_name           VARCHAR(150) NOT NULL,
    email               VARCHAR(150) NOT NULL,
    phone               VARCHAR(20),
    password_hash       VARCHAR(255) NOT NULL,
    role                VARCHAR(20)  NOT NULL DEFAULT 'STAFF', -- OWNER, ADMIN, MANAGER, STAFF
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING_ACTIVATION', -- PENDING_ACTIVATION, ACTIVE, DISABLED
    activation_token     VARCHAR(255),
    password_reset_token VARCHAR(255),
    password_reset_expires_at TIMESTAMPTZ,
    failed_login_attempts SMALLINT NOT NULL DEFAULT 0,
    locked_until        TIMESTAMPTZ,
    last_login_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- email must be unique per business, not globally (two different
    -- businesses could each have an owner with the same email in theory,
    -- but in practice we also keep it globally unique for login simplicity)
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE INDEX idx_users_business_id ON users(business_id);
CREATE INDEX idx_users_business_role ON users(business_id, role);
CREATE INDEX idx_users_status ON users(status);

-- ---------------------------------------------------------------------
-- refresh_tokens: server-side tracking so tokens can be revoked
-- (logout / password change / admin disable).
-- ---------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash          VARCHAR(255) NOT NULL,
    expires_at          TIMESTAMPTZ NOT NULL,
    revoked             BOOLEAN NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expiry ON refresh_tokens(expires_at);

-- ---------------------------------------------------------------------
-- business_settings: 1:1 configuration for invoice numbering, currency,
-- tax mode etc. Split from `businesses` so the profile table stays lean.
-- ---------------------------------------------------------------------
CREATE TABLE business_settings (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id             UUID NOT NULL UNIQUE REFERENCES businesses(id) ON DELETE CASCADE,
    invoice_prefix          VARCHAR(20) NOT NULL DEFAULT 'INV',
    invoice_number_format   VARCHAR(50) NOT NULL DEFAULT '{PREFIX}-{YEAR}-{SEQ:00000}',
    invoice_next_sequence   BIGINT NOT NULL DEFAULT 1,
    financial_year_start_month SMALLINT NOT NULL DEFAULT 4, -- April, for Indian FY
    default_due_days        INT NOT NULL DEFAULT 7,
    currency                VARCHAR(10) NOT NULL DEFAULT 'INR',
    tax_mode                VARCHAR(20) NOT NULL DEFAULT 'EXCLUSIVE', -- EXCLUSIVE, INCLUSIVE
    invoice_notes           TEXT,
    invoice_terms           TEXT,
    theme_preference        VARCHAR(10) NOT NULL DEFAULT 'SYSTEM',   -- LIGHT, DARK, SYSTEM
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- one settings row is created automatically per business at registration time (service layer)
