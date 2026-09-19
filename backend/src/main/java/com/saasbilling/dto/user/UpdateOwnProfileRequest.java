package com.saasbilling.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateOwnProfileRequest(

        @NotBlank(message = "Full name is required")
        @Size(max = 150)
        String fullName,

        String phone
) {
}
