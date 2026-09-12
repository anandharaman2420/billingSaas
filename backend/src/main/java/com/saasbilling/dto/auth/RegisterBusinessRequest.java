package com.saasbilling.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterBusinessRequest(

        @NotBlank(message = "Business name is required")
        @Size(max = 200)
        String businessName,

        @Size(max = 100)
        String businessType,

        @NotBlank(message = "Owner name is required")
        @Size(max = 150)
        String ownerName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        @Size(max = 150)
        String email,

        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^[0-9+\\-\\s]{7,20}$", message = "Phone number is invalid")
        String phone,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
        String password,

        String addressLine,
        String city,
        String state,

        @Pattern(regexp = "^[0-9]{4,10}$", message = "Pincode is invalid")
        String pincode,

        String country,

        // GSTIN is optional; when present it is validated for format in AuthService
        // (kept out of bean validation here so an empty string doesn't fail @Pattern).
        String gstin
) {
}
