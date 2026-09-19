package com.saasbilling.dto.user;

import com.saasbilling.entity.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(

        @NotBlank(message = "Full name is required")
        @Size(max = 150)
        String fullName,

        String phone,

        @NotNull(message = "Role is required")
        UserRole role
) {
}
