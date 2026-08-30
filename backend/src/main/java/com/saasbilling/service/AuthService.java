package com.saasbilling.service;

import com.saasbilling.dto.auth.*;
import com.saasbilling.entity.*;
import com.saasbilling.exception.AuthenticationFailedException;
import com.saasbilling.exception.DuplicateResourceException;
import com.saasbilling.exception.ResourceNotFoundException;
import com.saasbilling.repository.BusinessRepository;
import com.saasbilling.repository.BusinessSettingsRepository;
import com.saasbilling.repository.RefreshTokenRepository;
import com.saasbilling.repository.UserRepository;
import com.saasbilling.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCK_DURATION_MINUTES = 15;
    private static final long PASSWORD_RESET_VALIDITY_MINUTES = 30;

    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final BusinessSettingsRepository businessSettingsRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditLogService auditLogService;

    public AuthService(BusinessRepository businessRepository,
                        UserRepository userRepository,
                        BusinessSettingsRepository businessSettingsRepository,
                        RefreshTokenRepository refreshTokenRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        AuditLogService auditLogService) {
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
        this.businessSettingsRepository = businessSettingsRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditLogService = auditLogService;
    }

    // -----------------------------------------------------------------
    // Registration: creates the Business (tenant), its owner User, and
    // a default BusinessSettings row, all in a single transaction.
    // -----------------------------------------------------------------
    @Transactional
    public AuthResponse registerBusiness(RegisterBusinessRequest request) {
        if (businessRepository.existsByEmailIgnoreCase(request.email())
                || userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("An account with this email already exists");
        }

        Business business = new Business();
        business.setBusinessName(request.businessName());
        business.setBusinessType(request.businessType());
        business.setOwnerName(request.ownerName());
        business.setEmail(request.email());
        business.setPhone(request.phone());
        business.setAddressLine(request.addressLine());
        business.setCity(request.city());
        business.setState(request.state());
        business.setPincode(request.pincode());
        if (request.country() != null && !request.country().isBlank()) {
            business.setCountry(request.country());
        }
        business.setGstin(request.gstin());
        business = businessRepository.save(business);

        User owner = new User();
        owner.setBusiness(business);
        owner.setFullName(request.ownerName());
        owner.setEmail(request.email());
        owner.setPhone(request.phone());
        owner.setPasswordHash(passwordEncoder.encode(request.password()));
        owner.setRole(UserRole.OWNER);
        // MVP: activate immediately so the owner can log in right away.
        // Swap to PENDING_ACTIVATION + email verification link once email sending is wired up.
        owner.setStatus(UserStatus.ACTIVE);
        owner = userRepository.save(owner);

        BusinessSettings settings = new BusinessSettings();
        settings.setBusiness(business);
        businessSettingsRepository.save(settings);

        auditLogService.record(business.getId(), owner.getId(), "BUSINESS_REGISTERED", "BUSINESS", business.getId(), null, null);

        return buildAuthResponse(owner, business);
    }

    // -----------------------------------------------------------------
    // Login
    // -----------------------------------------------------------------
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new AuthenticationFailedException("Invalid email or password"));

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(OffsetDateTime.now())) {
            throw new AuthenticationFailedException("Account temporarily locked due to repeated failed attempts");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AuthenticationFailedException("Account is not active");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            registerFailedLogin(user);
            throw new AuthenticationFailedException("Invalid email or password");
        }

        user.setFailedLoginAttempts((short) 0);
        user.setLockedUntil(null);
        user.setLastLoginAt(OffsetDateTime.now());
        userRepository.save(user);

        auditLogService.record(user.getBusiness().getId(), user.getId(), "USER_LOGIN", "USER", user.getId(), null, null);

        return buildAuthResponse(user, user.getBusiness());
    }

    private void registerFailedLogin(User user) {
        short attempts = (short) (user.getFailedLoginAttempts() + 1);
        user.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(OffsetDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
        }
        userRepository.save(user);
    }

    // -----------------------------------------------------------------
    // Refresh access token
    // -----------------------------------------------------------------
    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String hash = sha256(request.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new AuthenticationFailedException("Invalid refresh token"));

        if (stored.isRevoked() || stored.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new AuthenticationFailedException("Refresh token expired or revoked");
        }

        User user = stored.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AuthenticationFailedException("Account is not active");
        }

        // Rotate: revoke the used refresh token and issue a new pair.
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        return buildAuthResponse(user, user.getBusiness());
    }

    @Transactional
    public void logout(UUID userId, String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenRepository.findByTokenHash(sha256(refreshToken))
                    .ifPresent(rt -> {
                        rt.setRevoked(true);
                        refreshTokenRepository.save(rt);
                    });
        } else {
            refreshTokenRepository.revokeAllForUser(userId);
        }
    }

    // -----------------------------------------------------------------
    // Forgot / reset / change password
    // -----------------------------------------------------------------
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmailIgnoreCase(request.email()).ifPresent(user -> {
            String token = generateSecureToken();
            user.setPasswordResetToken(sha256(token));
            user.setPasswordResetExpiresAt(OffsetDateTime.now().plusMinutes(PASSWORD_RESET_VALIDITY_MINUTES));
            userRepository.save(user);
            // TODO (next phase): send the raw `token` via email. Never log or return it directly.
            log.info("Password reset requested for user {}", user.getId());
        });
        // Always respond the same way whether or not the email exists, to avoid user enumeration.
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String hashedToken = sha256(request.token());
        User user = userRepository.findByPasswordResetToken(hashedToken)
                .orElseThrow(() -> new AuthenticationFailedException("Invalid or expired reset token"));

        if (user.getPasswordResetExpiresAt() == null
                || user.getPasswordResetExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new AuthenticationFailedException("Invalid or expired reset token");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordResetToken(null);
        user.setPasswordResetExpiresAt(null);
        user.setFailedLoginAttempts((short) 0);
        user.setLockedUntil(null);
        userRepository.save(user);

        refreshTokenRepository.revokeAllForUser(user.getId());

        auditLogService.record(user.getBusiness().getId(), user.getId(), "PASSWORD_RESET", "USER", user.getId(), null, null);
    }

    @Transactional
    public void changePassword(UUID userId, UUID businessId, ChangePasswordRequest request) {
        User user = userRepository.findByIdAndBusinessId(userId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new AuthenticationFailedException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        refreshTokenRepository.revokeAllForUser(user.getId());

        auditLogService.record(businessId, userId, "PASSWORD_CHANGED", "USER", userId, null, null);
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------
    private AuthResponse buildAuthResponse(User user, Business business) {
        String accessToken = jwtService.generateAccessToken(
                user.getId(), business.getId(), user.getEmail(), user.getRole().name());

        String rawRefreshToken = jwtService.generateOpaqueRefreshToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(sha256(rawRefreshToken));
        refreshToken.setExpiresAt(OffsetDateTime.now().plus(
                java.time.Duration.ofMillis(jwtService.getRefreshTokenExpirationMs())));
        refreshTokenRepository.save(refreshToken);

        AuthResponse.UserSummary summary = new AuthResponse.UserSummary(
                user.getId(), business.getId(), business.getBusinessName(),
                user.getFullName(), user.getEmail(), user.getRole().name());

        return new AuthResponse(accessToken, rawRefreshToken, jwtService.getRefreshTokenExpirationMs(), summary);
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
