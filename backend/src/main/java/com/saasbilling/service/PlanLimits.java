package com.saasbilling.service;

import java.util.Map;

/**
 * Foundation for spec section 37's Free/Basic/Professional/Business
 * tiers. Deliberately simple for now: a static map of limits per plan
 * string, checked at the point of creating a user/customer/invoice.
 * When subscription billing (spec section 38) is built, this becomes
 * the natural place to plug in "read the business's actual subscribed
 * plan" instead of the `businesses.plan` column being hand-set.
 *
 * -1 means unlimited (the BUSINESS tier).
 */
public record PlanLimits(int maxUsers, int maxCustomers, int maxInvoicesPerMonth) {

    private static final Map<String, PlanLimits> LIMITS = Map.of(
            "FREE", new PlanLimits(2, 50, 20),
            "BASIC", new PlanLimits(5, 500, 200),
            "PROFESSIONAL", new PlanLimits(15, 5000, 2000),
            "BUSINESS", new PlanLimits(-1, -1, -1)
    );

    public static PlanLimits forPlan(String plan) {
        return LIMITS.getOrDefault(plan, LIMITS.get("FREE"));
    }

    public boolean isWithinUserLimit(long currentActiveUsers) {
        return maxUsers < 0 || currentActiveUsers < maxUsers;
    }

    public boolean isWithinCustomerLimit(long currentActiveCustomers) {
        return maxCustomers < 0 || currentActiveCustomers < maxCustomers;
    }

    public boolean isWithinMonthlyInvoiceLimit(long invoicesIssuedThisMonth) {
        return maxInvoicesPerMonth < 0 || invoicesIssuedThisMonth < maxInvoicesPerMonth;
    }
}
