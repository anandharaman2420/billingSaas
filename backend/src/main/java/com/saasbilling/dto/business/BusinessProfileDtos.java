package com.saasbilling.dto.business;

import com.saasbilling.entity.Business;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class BusinessProfileDtos {

    public record BusinessProfileRequest(
            @NotBlank(message = "Business name is required")
            @Size(max = 200)
            String businessName,

            String businessType,
            String addressLine,
            String city,
            String state,

            @Pattern(regexp = "^$|^[0-9]{4,10}$", message = "Pincode is invalid")
            String pincode,

            String country,
            String phone,
            String email,
            String website,
            String logoUrl,

            @Pattern(regexp = "^$|^[0-9A-Z]{15}$", message = "GSTIN must be 15 characters")
            String gstin
    ) {
    }

    public record BusinessProfileResponse(
            UUID id, String businessName, String businessType, String ownerName,
            String addressLine, String city, String state, String pincode, String country,
            String phone, String email, String website, String logoUrl, String gstin, String plan, String status
    ) {
        public static BusinessProfileResponse from(Business b) {
            return new BusinessProfileResponse(
                    b.getId(), b.getBusinessName(), b.getBusinessType(), b.getOwnerName(),
                    b.getAddressLine(), b.getCity(), b.getState(), b.getPincode(), b.getCountry(),
                    b.getPhone(), b.getEmail(), b.getWebsite(), b.getLogoUrl(), b.getGstin(),
                    b.getPlan(), b.getStatus().name());
        }
    }
}
