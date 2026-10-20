package com.saasbilling.service;

import com.saasbilling.dto.subscription.SubscriptionDtos.ChangePlanRequest;
import com.saasbilling.dto.subscription.SubscriptionDtos.CheckoutResponse;
import com.saasbilling.dto.subscription.SubscriptionDtos.SubscriptionResponse;
import com.saasbilling.dto.subscription.SubscriptionDtos.GatewayOptions;
import com.saasbilling.entity.Business;
import com.saasbilling.entity.Subscription;
import com.saasbilling.entity.SubscriptionStatus;
import com.saasbilling.exception.ForbiddenOperationException;
import com.saasbilling.exception.ResourceNotFoundException;
import com.saasbilling.payment.PlanPricing;
import com.saasbilling.payment.RazorpayClient;
import com.saasbilling.payment.StripeClient;
import com.saasbilling.entity.SubscriptionProvider;
import com.saasbilling.repository.BusinessRepository;
import com.saasbilling.repository.SubscriptionRepository;
import com.saasbilling.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Subscription state lives here, separate
 * from businesses.plan (which stays as a fast-read denormalized copy
 * that PlanLimits and the rest of the app already check). Paid plans
 * activate only from a verified payment-provider webhook; the MANUAL
 * path remains available for free plans and deployments without gateways.
 */
@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final BusinessRepository businessRepository;
    private final AuditLogService auditLogService;
    private final RazorpayClient razorpayClient;
    private final StripeClient stripeClient;
    private final PlanPricing planPricing;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                                BusinessRepository businessRepository,
                                AuditLogService auditLogService,
                                RazorpayClient razorpayClient,
                                StripeClient stripeClient,
                                PlanPricing planPricing) {
        this.subscriptionRepository = subscriptionRepository;
        this.businessRepository = businessRepository;
        this.auditLogService = auditLogService;
        this.razorpayClient = razorpayClient;
        this.stripeClient = stripeClient;
        this.planPricing = planPricing;
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse get() {
        return SubscriptionResponse.from(getOrCreateOwnSubscription());
    }

    /** Allows free plan changes and manual plan selection when gateways are disabled. */
    @Transactional
    public SubscriptionResponse changePlan(ChangePlanRequest request) {
        // A paid plan must be activated by a signed provider webhook whenever a gateway is enabled.
        if ((razorpayClient.isConfigured() || stripeClient.isConfigured())
                && planPricing.monthlyPriceInr(request.plan()).signum() > 0) {
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
     * Starts provider checkout. The plan changes only after the provider's
     * signed webhook confirms payment or subscription activation.
     */
    @Transactional(readOnly = true)
    public CheckoutResponse createCheckout(ChangePlanRequest request) {
        String provider = request.provider();
        if (provider == null) {
            provider = razorpayClient.isConfigured() ? "RAZORPAY" : "STRIPE";
        }
        if (!"RAZORPAY".equals(provider) && !"STRIPE".equals(provider)) {
            throw new IllegalArgumentException("Unsupported payment provider");
        }
        if ("RAZORPAY".equals(provider) && !razorpayClient.isConfigured()
                || "STRIPE".equals(provider) && !stripeClient.isConfigured()) {
            throw new IllegalArgumentException("Online payments are not configured for this deployment");
        }
        var price = planPricing.monthlyPriceInr(request.plan());
        if (price.signum() <= 0) {
            throw new IllegalArgumentException("The " + request.plan() + " plan is free - no checkout needed");
        }

        java.util.UUID businessId = TenantContext.currentBusinessId();
        if ("STRIPE".equals(provider)) {
            var session = stripeClient.createSubscriptionSession(request.plan(), price, businessId.toString());
            return new CheckoutResponse(provider, session.sessionId(), session.amountMinor(),
                    session.currency(), null, session.checkoutUrl(), request.plan());
        }
        var order = razorpayClient.createOrder(price,
                "sub_" + businessId.toString().substring(0, 8) + "_" + System.currentTimeMillis(),
                java.util.Map.of("businessId", businessId.toString(), "plan", request.plan()));

        return new CheckoutResponse(provider, order.orderId(), order.amountPaise(), order.currency(),
                razorpayClient.getKeyId(), null, request.plan());
    }

    @Transactional(readOnly = true)
    public GatewayOptions availableGateways() {
        java.util.List<String> providers = new java.util.ArrayList<>();
        if (razorpayClient.isConfigured()) providers.add("RAZORPAY");
        if (stripeClient.isConfigured()) providers.add("STRIPE");
        return new GatewayOptions(providers);
    }

    /**
     * Called by a signature-verified provider webhook. It runs outside
     * authenticated requests, so the business id is explicit and updates
     * are idempotent on the provider payment/subscription identifier.
     */
    @Transactional
    public void applyProviderPlanChange(java.util.UUID businessId, String plan, String providerPaymentId) {
        applyProviderPlanChange(businessId, plan, providerPaymentId, SubscriptionProvider.RAZORPAY);
    }

    @Transactional
    public void applyProviderPlanChange(java.util.UUID businessId, String plan, String providerPaymentId,
                                       SubscriptionProvider provider) {
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
        subscription.setProvider(provider);
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
