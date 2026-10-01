package com.saasbilling.service;

import com.saasbilling.dto.subscription.SubscriptionDtos.ChangePlanRequest;
import com.saasbilling.dto.subscription.SubscriptionDtos.CheckoutResponse;
import com.saasbilling.dto.subscription.SubscriptionDtos.SubscriptionResponse;
import com.saasbilling.entity.Business;
import com.saasbilling.entity.Subscription;
import com.saasbilling.entity.SubscriptionProvider;
import com.saasbilling.entity.SubscriptionStatus;
import com.saasbilling.exception.ForbiddenOperationException;
import com.saasbilling.exception.ResourceNotFoundException;
import com.saasbilling.payment.PlanPricing;
import com.saasbilling.payment.RazorpayClient;
import com.saasbilling.repository.BusinessRepository;
import com.saasbilling.repository.SubscriptionRepository;
import com.saasbilling.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spec section 38 foundation: subscription state lives here, separate
 * from businesses.plan (which stays as a fast-read denormalized copy
 * that PlanLimits and the rest of the app already check). Only MANUAL
 * plan changes are implemented - no payment is actually taken. When a
 * real gateway is wired in, a webhook controller would call
 * applyProviderPlanChange() here instead of change() being called
 * directly by the business owner, and this service is where that
 * webhook handler plugs in without a schema change (see V8 migration).
 */
@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final BusinessRepository businessRepository;
    private final AuditLogService auditLogService;
    private final RazorpayClient razorpayClient;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                                BusinessRepository businessRepository,
                                AuditLogService auditLogService,
                                RazorpayClient razorpayClient) {
        this.subscriptionRepository = subscriptionRepository;
        this.businessRepository = businessRepository;
        this.auditLogService = auditLogService;
        this.razorpayClient = razorpayClient;
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse get() {
        return SubscriptionResponse.from(getOrCreateOwnSubscription());
    }

    /** MVP: any OWNER/ADMIN can self-serve change their plan, no payment collected. */
    @Transactional
    public SubscriptionResponse changePlan(ChangePlanRequest request) {
        // Once a real payment gateway is configured, a paid plan must be
        // purchased through checkout - otherwise this manual endpoint would
        // let any OWNER simply grant themselves a paid tier for free.
        if (razorpayClient.isConfigured() && PlanPricing.monthlyPriceInr(request.plan()).signum() > 0) {
            throw new ForbiddenOperationException("Paid plans must be purchased through checkout");
        }
        Subscription subscription = getOrCreateOwnSubscription();
        Business business = businessRepository.findById(subscription.getBusinessId())
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));

        subscription.setPlan(request.plan());
        subscription.setProvider(SubscriptionProvider.MANUAL);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription = subscriptionRepository.save(subscription);

        // Keep the fast-read copy on Business in sync - PlanLimits and
        // everything else in the app reads business.getPlan() directly.
        business.setPlan(request.plan());
        businessRepository.save(business);

        auditLogService.record(business.getId(), TenantContext.currentUserId(), "SUBSCRIPTION_PLAN_CHANGED",
                "SUBSCRIPTION", subscription.getId(), null, request.plan());

        return SubscriptionResponse.from(subscription);
    }

    /**
     * Starts an online purchase of a paid plan: creates a Razorpay order
     * (tagged with this business + plan in its notes) and returns what the
     * frontend needs to open Razorpay Checkout. The plan is NOT changed
     * here - only when Razorpay's signed webhook confirms payment
     * (see WebhookController -> applyProviderPlanChange).
     */
    @Transactional(readOnly = true)
    public CheckoutResponse createCheckout(ChangePlanRequest request) {
        if (!razorpayClient.isConfigured()) {
            throw new IllegalArgumentException("Online payments are not configured for this deployment");
        }
        var price = PlanPricing.monthlyPriceInr(request.plan());
        if (price.signum() <= 0) {
            throw new IllegalArgumentException("The " + request.plan() + " plan is free - no checkout needed");
        }

        java.util.UUID businessId = TenantContext.currentBusinessId();
        var order = razorpayClient.createOrder(price, "sub_" + businessId.toString().substring(0, 8) + "_" + System.currentTimeMillis(),
                java.util.Map.of("businessId", businessId.toString(), "plan", request.plan()));

        return new CheckoutResponse(order.orderId(), order.amountPaise(), order.currency(), razorpayClient.getKeyId(), request.plan());
    }

    /**
     * Called by the signature-verified Razorpay webhook once payment is
     * confirmed. Runs outside any authenticated request (no TenantContext),
     * so it takes the business id explicitly. Idempotent on the payment id:
     * Razorpay may deliver the same event more than once.
     */
    @Transactional
    public void applyProviderPlanChange(java.util.UUID businessId, String plan, String providerPaymentId) {
        Subscription subscription = subscriptionRepository.findByBusinessId(businessId).orElseGet(() -> {
            Subscription s = new Subscription();
            s.setBusinessId(businessId);
            return s;
        });

        if (providerPaymentId != null && providerPaymentId.equals(subscription.getProviderSubscriptionId())) {
            return; // already applied - webhook redelivery
        }

        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));

        java.time.LocalDate today = java.time.LocalDate.now();
        subscription.setPlan(plan);
        subscription.setProvider(SubscriptionProvider.RAZORPAY);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setProviderSubscriptionId(providerPaymentId);
        subscription.setCurrentPeriodStart(today);
        subscription.setCurrentPeriodEnd(today.plusMonths(1));
        subscription = subscriptionRepository.save(subscription);

        business.setPlan(plan);
        businessRepository.save(business);

        auditLogService.record(businessId, null, "SUBSCRIPTION_PAID_PLAN_ACTIVATED",
                "SUBSCRIPTION", subscription.getId(), null, plan);
    }

    private Subscription getOrCreateOwnSubscription() {
        java.util.UUID businessId = TenantContext.currentBusinessId();
        return subscriptionRepository.findByBusinessId(businessId).orElseGet(() -> {
            // Defensive fallback for a business created before this migration's
            // backfill, or in a test context that doesn't go through AuthService.
            Business business = businessRepository.findById(businessId)
                    .orElseThrow(() -> new ResourceNotFoundException("Business not found"));
            Subscription subscription = new Subscription();
            subscription.setBusinessId(businessId);
            subscription.setPlan(business.getPlan());
            return subscriptionRepository.save(subscription);
        });
    }
}
