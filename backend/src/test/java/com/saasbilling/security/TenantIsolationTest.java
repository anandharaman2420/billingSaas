package com.saasbilling.security;

import com.saasbilling.entity.Business;
import com.saasbilling.entity.BusinessStatus;
import com.saasbilling.entity.User;
import com.saasbilling.entity.UserRole;
import com.saasbilling.entity.UserStatus;
import com.saasbilling.repository.BusinessRepository;
import com.saasbilling.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Core SaaS requirement (spec section 35): "Business A cannot access
 * Business B data." This proves the tenant-scoped repository lookup
 * (findByIdAndBusinessId) is the mechanism that guarantees it - the
 * pattern every future domain repository (customers, invoices, ...)
 * must follow.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TenantIsolationTest {

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void userFromBusinessA_cannotBeFetchedUsingBusinessBId() {
        Business businessA = createBusiness("Business A", "owner-a@example.com");
        Business businessB = createBusiness("Business B", "owner-b@example.com");

        User userInBusinessA = createUser(businessA, "owner-a@example.com");

        // Attempting to fetch a Business-A user while scoped to Business B must return empty,
        // even though the raw user id is correct. This is the check every controller/service
        // must rely on instead of a bare findById(id).
        Optional<User> crossTenantLookup =
                userRepository.findByIdAndBusinessId(userInBusinessA.getId(), businessB.getId());

        assertThat(crossTenantLookup).isEmpty();

        // Sanity check: the same lookup scoped to the *correct* business succeeds.
        Optional<User> correctTenantLookup =
                userRepository.findByIdAndBusinessId(userInBusinessA.getId(), businessA.getId());

        assertThat(correctTenantLookup).isPresent();
    }

    @Test
    void tenantContext_throwsWhenAccessedOutsideAuthenticatedRequest() {
        TenantContext.clear();
        assertThat(catchThrowable()).isInstanceOf(IllegalStateException.class);
    }

    private Throwable catchThrowable() {
        try {
            TenantContext.get();
            return null;
        } catch (Throwable t) {
            return t;
        }
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

    private User createUser(Business business, String email) {
        User user = new User();
        user.setBusiness(business);
        user.setFullName("Test Owner");
        user.setEmail(email);
        user.setPasswordHash("{bcrypt}$2a$12$abcdefghijklmnopqrstuv"); // not a real hash, unused in this test
        user.setRole(UserRole.OWNER);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }
}
