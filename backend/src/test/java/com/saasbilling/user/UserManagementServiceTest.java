package com.saasbilling.user;

import com.saasbilling.dto.user.CreateUserRequest;
import com.saasbilling.dto.user.UpdateUserRequest;
import com.saasbilling.dto.user.UserResponse;
import com.saasbilling.entity.*;
import com.saasbilling.exception.ForbiddenOperationException;
import com.saasbilling.repository.BusinessRepository;
import com.saasbilling.repository.UserRepository;
import com.saasbilling.security.TenantContext;
import com.saasbilling.service.UserManagementService;
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
class UserManagementServiceTest {

    @Autowired private BusinessRepository businessRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserManagementService userManagementService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void cannotDisableTheLastActiveOwner() {
        Business business = createBusiness("Sole Owner Shop", "sole@example.com");
        User owner = createUser(business, "owner@example.com", UserRole.OWNER);

        setTenantContext(business.getId(), owner.getId(), "OWNER");

        assertThatThrownBy(() -> userManagementService.disable(owner.getId()))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("last active owner");
    }

    @Test
    void canDisableAnOwnerWhenAnotherActiveOwnerExists() {
        Business business = createBusiness("Two Owner Shop", "twoowner@example.com");
        User ownerA = createUser(business, "ownerA@example.com", UserRole.OWNER);
        User ownerB = createUser(business, "ownerB@example.com", UserRole.OWNER);

        setTenantContext(business.getId(), ownerA.getId(), "OWNER");

        userManagementService.disable(ownerB.getId());

        User reloaded = userRepository.findById(ownerB.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.DISABLED);
    }

    @Test
    void adminCannotCreateAnotherOwnerAccount() {
        Business business = createBusiness("Admin Shop", "adminshop@example.com");
        User admin = createUser(business, "admin@example.com", UserRole.ADMIN);

        setTenantContext(business.getId(), admin.getId(), "ADMIN");

        CreateUserRequest request = new CreateUserRequest("New Owner", "newowner@example.com", null, "password123", UserRole.OWNER);

        assertThatThrownBy(() -> userManagementService.create(request))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("Only an OWNER");
    }

    @Test
    void adminCannotEditAnExistingOwnersAccount() {
        Business business = createBusiness("Admin Shop 2", "adminshop2@example.com");
        User owner = createUser(business, "owner2@example.com", UserRole.OWNER);
        User admin = createUser(business, "admin2@example.com", UserRole.ADMIN);

        setTenantContext(business.getId(), admin.getId(), "ADMIN");

        UpdateUserRequest request = new UpdateUserRequest("Renamed", null, UserRole.OWNER);

        assertThatThrownBy(() -> userManagementService.update(owner.getId(), request))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void ownerCanCreateAnAdminUser() {
        Business business = createBusiness("Owner Shop", "ownershop@example.com");
        User owner = createUser(business, "owner3@example.com", UserRole.OWNER);

        setTenantContext(business.getId(), owner.getId(), "OWNER");

        UserResponse created = userManagementService.create(
                new CreateUserRequest("New Admin", "newadmin@example.com", "9876543210", "password123", UserRole.ADMIN));

        assertThat(created.role()).isEqualTo("ADMIN");
        assertThat(created.status()).isEqualTo("ACTIVE");
    }

    // -----------------------------------------------------------------
    private void setTenantContext(UUID businessId, UUID userId, String role) {
        TenantContext.set(new TenantContext.AuthenticatedPrincipal(userId, businessId, "test@example.com", role));
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

    private User createUser(Business business, String email, UserRole role) {
        User user = new User();
        user.setBusiness(business);
        user.setFullName("Test User");
        user.setEmail(email);
        user.setPasswordHash("{bcrypt}$2a$12$abcdefghijklmnopqrstuv");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }
}
