package com.saasbilling.dto.auth;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresInMs,
        UserSummary user
) {
    public record UserSummary(
            UUID id,
            UUID businessId,
            String businessName,
            String fullName,
            String email,
            String role
    ) {
    }
}
