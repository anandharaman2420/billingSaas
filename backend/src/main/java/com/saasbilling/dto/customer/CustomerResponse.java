package com.saasbilling.dto.customer;

import com.saasbilling.entity.Customer;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String customerName,
        String phone,
        String email,
        String addressLine,
        String city,
        String state,
        String pincode,
        String gstin,
        String notes,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(
                c.getId(), c.getCustomerName(), c.getPhone(), c.getEmail(),
                c.getAddressLine(), c.getCity(), c.getState(), c.getPincode(),
                c.getGstin(), c.getNotes(), c.getStatus().name(),
                c.getCreatedAt(), c.getUpdatedAt());
    }
}
