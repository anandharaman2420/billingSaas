package com.saasbilling.dto.pos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record PosItemRequest(
        UUID productId,
        UUID serviceId,

        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be positive")
        BigDecimal quantity,

        @DecimalMin(value = "0.0", message = "Discount cannot be negative")
        BigDecimal discountAmount,

        String description
) {
}
