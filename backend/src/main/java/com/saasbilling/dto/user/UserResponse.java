package com.saasbilling.dto.user;

import com.saasbilling.entity.User;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String fullName,
        String email,
        String phone,
        String role,
        String status,
        OffsetDateTime lastLoginAt,
        OffsetDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(), user.getFullName(), user.getEmail(), user.getPhone(),
                user.getRole().name(), user.getStatus().name(), user.getLastLoginAt(), user.getCreatedAt());
    }
}
