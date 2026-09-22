package com.saasbilling.service;

import com.saasbilling.dto.common.PageResponse;
import com.saasbilling.dto.user.CreateUserRequest;
import com.saasbilling.dto.user.UpdateOwnProfileRequest;
import com.saasbilling.dto.user.UpdateUserRequest;
import com.saasbilling.dto.user.UserResponse;
import com.saasbilling.entity.Business;
import com.saasbilling.entity.User;
import com.saasbilling.entity.UserRole;
import com.saasbilling.entity.UserStatus;
import com.saasbilling.exception.DuplicateResourceException;
import com.saasbilling.exception.ForbiddenOperationException;
import com.saasbilling.exception.ResourceNotFoundException;
import com.saasbilling.repository.BusinessRepository;
import com.saasbilling.repository.UserRepository;
import com.saasbilling.security.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Team member management (spec section 5/6). Two guardrails matter
 * more here than in any other module:
 *   1. Privilege escalation: an ADMIN can manage STAFF/MANAGER/ADMIN
 *      users, but only an OWNER can create, edit, or touch the status
 *      of another OWNER - otherwise an ADMIN could grant themselves
 *      (or an ally) OWNER access.
 *   2. Lockout prevention: a business can never end up with zero
 *      active OWNERs, or nobody could manage the account again.
 */
@Service
public class UserManagementService {

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final com.saasbilling.notification.EmailService emailService;

    public UserManagementService(UserRepository userRepository,
                                  BusinessRepository businessRepository,
                                  PasswordEncoder passwordEncoder,
                                  AuditLogService auditLogService,
                                  com.saasbilling.notification.EmailService emailService) {
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.emailService = emailService;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(Pageable pageable) {
        Page<User> page = userRepository.findByBusinessId(TenantContext.currentBusinessId(), pageable);
        return PageResponse.from(page.map(UserResponse::from));
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID id) {
        return UserResponse.from(getOwnedOrThrow(id, TenantContext.currentBusinessId()));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        UUID businessId = TenantContext.currentBusinessId();
        assertActorCanManageRole(request.role());

        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("A user with this email already exists");
        }

        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));

        long activeUsers = userRepository.countByBusinessIdAndStatus(businessId, UserStatus.ACTIVE);
        if (!com.saasbilling.service.PlanLimits.forPlan(business.getPlan()).isWithinUserLimit(activeUsers)) {
            throw new ForbiddenOperationException(
                    "This business's " + business.getPlan() + " plan allows a limited number of users. Upgrade your plan to add more team members.");
        }

        User user = new User();
        user.setBusiness(business);
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        // MVP: activated immediately, same rationale as business registration
        // (AuthService) - swap for PENDING_ACTIVATION + email invite later.
        user.setStatus(UserStatus.ACTIVE);
        user = userRepository.save(user);

        emailService.sendWelcomeEmail(user.getEmail(), user.getFullName(), business.getBusinessName());

        auditLogService.record(businessId, TenantContext.currentUserId(), "USER_CREATED", "USER", user.getId(), null, null);

        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        UUID businessId = TenantContext.currentBusinessId();
        User user = getOwnedOrThrow(id, businessId);

        assertActorCanManageRole(user.getRole()); // guard against touching an OWNER as a non-OWNER
        assertActorCanManageRole(request.role());  // guard against granting OWNER as a non-OWNER

        if (user.getRole() == UserRole.OWNER && request.role() != UserRole.OWNER) {
            assertNotLastActiveOwner(businessId, "demote");
        }

        user.setFullName(request.fullName());
        user.setPhone(request.phone());
        user.setRole(request.role());
        user = userRepository.save(user);

        auditLogService.record(businessId, TenantContext.currentUserId(), "USER_UPDATED", "USER", user.getId(), null, null);

        return UserResponse.from(user);
    }

    @Transactional
    public void disable(UUID id) {
        UUID businessId = TenantContext.currentBusinessId();
        User user = getOwnedOrThrow(id, businessId);
        assertActorCanManageRole(user.getRole());

        if (user.getId().equals(TenantContext.currentUserId())) {
            throw new ForbiddenOperationException("You cannot disable your own account");
        }
        if (user.getRole() == UserRole.OWNER) {
            assertNotLastActiveOwner(businessId, "disable");
        }

        user.setStatus(UserStatus.DISABLED);
        userRepository.save(user);

        auditLogService.record(businessId, TenantContext.currentUserId(), "USER_DISABLED", "USER", user.getId(), null, null);
    }

    @Transactional
    public void enable(UUID id) {
        UUID businessId = TenantContext.currentBusinessId();
        User user = getOwnedOrThrow(id, businessId);
        assertActorCanManageRole(user.getRole());

        user.setStatus(UserStatus.ACTIVE);
        user.setFailedLoginAttempts((short) 0);
        user.setLockedUntil(null);
        userRepository.save(user);

        auditLogService.record(businessId, TenantContext.currentUserId(), "USER_ENABLED", "USER", user.getId(), null, null);
    }

    @Transactional
    public UserResponse updateOwnProfile(UpdateOwnProfileRequest request) {
        UUID businessId = TenantContext.currentBusinessId();
        UUID userId = TenantContext.currentUserId();
        User user = getOwnedOrThrow(userId, businessId);

        user.setFullName(request.fullName());
        user.setPhone(request.phone());
        user = userRepository.save(user);

        auditLogService.record(businessId, userId, "USER_PROFILE_UPDATED", "USER", user.getId(), null, null);

        return UserResponse.from(user);
    }

    // -----------------------------------------------------------------
    private void assertActorCanManageRole(UserRole role) {
        if (role == UserRole.OWNER && !"OWNER".equals(TenantContext.get().role())) {
            throw new ForbiddenOperationException("Only an OWNER can manage another OWNER's account or grant the OWNER role");
        }
    }

    /**
     * Called BEFORE the target user's role/status is actually changed, so
     * the current count still includes them - exactly one active owner at
     * that point means "the only one left is the person being changed".
     */
    private void assertNotLastActiveOwner(UUID businessId, String action) {
        long activeOwners = userRepository.countByBusinessIdAndRoleAndStatus(businessId, UserRole.OWNER, UserStatus.ACTIVE);
        if (activeOwners <= 1) {
            throw new ForbiddenOperationException("Cannot " + action + " the last active owner of the business");
        }
    }

    private User getOwnedOrThrow(UUID id, UUID businessId) {
        return userRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
