package com.saasbilling.payment;

import com.saasbilling.dto.subscription.SubscriptionDtos.ChangePlanRequest;
import com.saasbilling.entity.Business;
import com.saasbilling.entity.BusinessStatus;
import com.saasbilling.entity.Subscription;
import com.saasbilling.entity.SubscriptionProvider;
import com.saasbilling.repository.BusinessRepository;
import com.saasbilling.repository.SubscriptionRepository;
import com.saasbilling.security.TenantContext;
import com.saasbilling.service.SubscriptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SubscriptionPaymentFlowTest {

    @Autowired private BusinessRepository businessRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private SubscriptionService subscriptionService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void providerPlanChange_upgradesBusinessAndSubscription() {
        Business business = createBusiness("Pay Shop", "payshop@example.com");

        subscriptionService.applyProviderPlanChange(business.getId(), "PROFESSIONAL", "pay_001");

        Subscription sub = subscriptionRepository.findByBusinessId(business.getId()).orElseThrow();
        assertThat(sub.getPlan()).isEqualTo("PROFESSIONAL");
        assertThat(sub.getProvider()).isEqualTo(SubscriptionProvider.RAZORPAY);
        assertThat(sub.getCurrentPeriodEnd()).isAfter(sub.getCurrentPeriodStart());
        assertThat(businessRepository.findById(business.getId()).orElseThrow().getPlan()).isEqualTo("PROFESSIONAL");
    }

    @Test
    void redeliveredWebhookForTheSamePayment_isIdempotent() {
        Business business = createBusiness("Repeat Shop", "repeat@example.com");

        subscriptionService.applyProviderPlanChange(business.getId(), "BASIC", "pay_dup");
        Subscription first = subscriptionRepository.findByBusinessId(business.getId()).orElseThrow();
        var firstPeriodEnd = first.getCurrentPeriodEnd();

        // Same payment id redelivered - must be a no-op, not a second application.
        subscriptionService.applyProviderPlanChange(business.getId(), "BASIC", "pay_dup");

        Subscription second = subscriptionRepository.findByBusinessId(business.getId()).orElseThrow();
        assertThat(second.getCurrentPeriodEnd()).isEqualTo(firstPeriodEnd);
        assertThat(second.getProviderSubscriptionId()).isEqualTo("pay_dup");
    }

    @Test
    void checkoutIsRejectedWhenNoGatewayIsConfigured() {
        Business business = createBusiness("No Gateway", "nogateway@example.com");
        TenantContext.set(new TenantContext.AuthenticatedPrincipal(UUID.randomUUID(), business.getId(), "o@example.com", "OWNER"));

        // Test profile leaves RAZORPAY_* blank, so checkout must refuse rather than pretend to charge.
        assertThatThrownBy(() -> subscriptionService.createCheckout(new ChangePlanRequest("BASIC")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void manualPlanChange_stillWorksWhenNoGatewayIsConfigured() {
        Business business = createBusiness("Manual Shop", "manual@example.com");
        TenantContext.set(new TenantContext.AuthenticatedPrincipal(UUID.randomUUID(), business.getId(), "o@example.com", "OWNER"));

        var result = subscriptionService.changePlan(new ChangePlanRequest("BASIC"));

        assertThat(result.plan()).isEqualTo("BASIC");
        assertThat(result.provider()).isEqualTo("MANUAL");
    }

    private Business createBusiness(String name, String email) {
        Business business = new Business();
        business.setBusinessName(name);
        business.setOwnerName(name + " Owner");
        business.setEmail(email);
        business.setPhone("9999999999");
        business.setStatus(BusinessStatus.ACTIVE);
        return businessRepository.save(business);
    }
}
