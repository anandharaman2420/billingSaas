package com.saasbilling.dto.service;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record ServiceRequest(

        @NotBlank(message = "Service name is required")
        @Size(max = 150)
        String serviceName,

        String description,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.0", message = "Price cannot be negative")
        BigDecimal price,

        @NotNull(message = "Tax rate is required")
        @DecimalMin(value = "0.0", message = "Tax rate cannot be negative")
        @DecimalMax(value = "100.0", message = "Tax rate cannot exceed 100%")
        BigDecimal taxRatePercent,

        UUID categoryId
) {
}
