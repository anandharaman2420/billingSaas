-- =====================================================================
-- V2: Audit logging
-- =====================================================================

CREATE TABLE audit_logs (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id   UUID REFERENCES businesses(id) ON DELETE SET NULL,
    user_id       UUID REFERENCES users(id) ON DELETE SET NULL,
    action        VARCHAR(100) NOT NULL,      -- e.g. INVOICE_CREATED, USER_LOGIN, SETTINGS_CHANGED
    entity_type   VARCHAR(100),                -- e.g. INVOICE, CUSTOMER, USER
    entity_id     UUID,
    ip_address    VARCHAR(64),
    before_value  JSONB,
    after_value   JSONB,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_logs_business_id ON audit_logs(business_id);
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);
