package com.saasbilling.dto.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerRequest(

        @NotBlank(message = "Customer name is required")
        @Size(max = 150)
        String customerName,

        @Pattern(regexp = "^$|^[0-9+\\-\\s]{7,20}$", message = "Phone number is invalid")
        String phone,

        @Email(message = "Email must be a valid email address")
        String email,

        String addressLine,
        String city,
        String state,

        @Pattern(regexp = "^$|^[0-9]{4,10}$", message = "Pincode is invalid")
        String pincode,

        @Pattern(regexp = "^$|^[0-9A-Z]{15}$", message = "GSTIN must be 15 characters")
        String gstin,

        String notes
) {
}
