-- =====================================================================
-- V8: Subscriptions (spec section 38 foundation)
-- =====================================================================

CREATE TABLE subscriptions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id                 UUID NOT NULL UNIQUE REFERENCES businesses(id) ON DELETE CASCADE,
    plan                        VARCHAR(30) NOT NULL DEFAULT 'FREE', -- FREE, BASIC, PROFESSIONAL, BUSINESS
    status                      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, CANCELLED, PAST_DUE

    -- 'MANUAL' means the plan was set directly (by the business itself,
    -- or an admin) with no real payment gateway involved - this is the
    -- only provider implemented right now. RAZORPAY/STRIPE are reserved
    -- for when a real gateway integration (spec section 38) is wired in;
    -- the webhook handler that would flip status/plan based on gateway
    -- events plugs in next to SubscriptionService without needing a
    -- schema change.
    provider                    VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    provider_subscription_id    VARCHAR(255),

    current_period_start        DATE,
    current_period_end          DATE,

    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_subscriptions_business_id ON subscriptions(business_id);

-- Every existing business gets a FREE/MANUAL subscription row backfilled,
-- matching its current businesses.plan value where possible.
INSERT INTO subscriptions (business_id, plan)
SELECT id, plan FROM businesses
ON CONFLICT (business_id) DO NOTHING;
