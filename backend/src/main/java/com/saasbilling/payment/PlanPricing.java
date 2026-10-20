package com.saasbilling.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class PlanPricing {

    private final Map<String, BigDecimal> monthlyPrices;

    public PlanPricing(@Value("${app.subscription.pricing.free:0}") BigDecimal free,
                       @Value("${app.subscription.pricing.basic:499}") BigDecimal basic,
                       @Value("${app.subscription.pricing.professional:1499}") BigDecimal professional,
                       @Value("${app.subscription.pricing.business:3999}") BigDecimal business) {
        this.monthlyPrices = Map.of(
                "FREE", free,
                "BASIC", basic,
                "PROFESSIONAL", professional,
                "BUSINESS", business);
    }

    public BigDecimal monthlyPriceInr(String plan) {
        BigDecimal price = monthlyPrices.get(plan);
        if (price == null) {
            throw new IllegalArgumentException("Unknown plan: " + plan);
        }
        if (price.signum() < 0) {
            throw new IllegalStateException("Configured price for " + plan + " cannot be negative");
        }
        return price;
    }
}
