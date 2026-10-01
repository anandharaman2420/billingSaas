package com.saasbilling.payment;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Placeholder pricing per plan tier (INR, per month). Kept as a simple
 * static map for now - when real plans/pricing get decided these
 * should move to configuration or a DB table so they can change
 * without a redeploy.
 */
public final class PlanPricing {

    private static final Map<String, BigDecimal> MONTHLY_PRICE_INR = Map.of(
            "FREE", BigDecimal.ZERO,
            "BASIC", new BigDecimal("499"),
            "PROFESSIONAL", new BigDecimal("1499"),
            "BUSINESS", new BigDecimal("3999")
    );

    private PlanPricing() {
    }

    public static BigDecimal monthlyPriceInr(String plan) {
        BigDecimal price = MONTHLY_PRICE_INR.get(plan);
        if (price == null) {
            throw new IllegalArgumentException("Unknown plan: " + plan);
        }
        return price;
    }
}
