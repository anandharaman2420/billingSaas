package com.saasbilling.dto.subscription;

import com.saasbilling.entity.Subscription;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.util.UUID;

public class SubscriptionDtos {

    public record ChangePlanRequest(
            @NotBlank
            @Pattern(regexp = "FREE|BASIC|PROFESSIONAL|BUSINESS", message = "Plan must be FREE, BASIC, PROFESSIONAL, or BUSINESS")
            String plan
    ) {
    }

    /** What the frontend needs to open Razorpay Checkout for an order. */
    public record CheckoutResponse(String orderId, long amountPaise, String currency, String keyId, String plan) {
    }

    public record SubscriptionResponse(
            UUID id, String plan, String status, String provider,
            LocalDate currentPeriodStart, LocalDate currentPeriodEnd
    ) {
        public static SubscriptionResponse from(Subscription s) {
            return new SubscriptionResponse(s.getId(), s.getPlan(), s.getStatus().name(), s.getProvider().name(),
                    s.getCurrentPeriodStart(), s.getCurrentPeriodEnd());
        }
    }
}
