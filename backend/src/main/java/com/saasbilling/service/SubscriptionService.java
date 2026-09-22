package com.saasbilling.service;

import com.saasbilling.dto.subscription.SubscriptionDtos.ChangePlanRequest;
import com.saasbilling.dto.subscription.SubscriptionDtos.SubscriptionResponse;
import com.saasbilling.entity.Business;
import com.saasbilling.entity.Subscription;
import com.saasbilling.entity.SubscriptionProvider;
import com.saasbilling.entity.SubscriptionStatus;
import com.saasbilling.exception.ResourceNotFoundException;
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

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                                BusinessRepository businessRepository,
                                AuditLogService auditLogService) {
        this.subscriptionRepository = subscriptionRepository;
        this.businessRepository = businessRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse get() {
        return SubscriptionResponse.from(getOrCreateOwnSubscription());
    }

    /** MVP: any OWNER/ADMIN can self-serve change their plan, no payment collected. */
    @Transactional
    public SubscriptionResponse changePlan(ChangePlanRequest request) {
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
