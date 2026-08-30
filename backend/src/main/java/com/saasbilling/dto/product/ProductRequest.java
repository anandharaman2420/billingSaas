package com.saasbilling.dto.product;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductRequest(

        @NotBlank(message = "Product name is required")
        @Size(max = 150)
        String productName,

        @Size(max = 50)
        String sku,

        UUID categoryId,

        String description,

        @NotBlank(message = "Unit is required")
        @Size(max = 20)
        String unit,

        @NotNull(message = "Purchase price is required")
        @DecimalMin(value = "0.0", message = "Purchase price cannot be negative")
        BigDecimal purchasePrice,

        @NotNull(message = "Selling price is required")
        @DecimalMin(value = "0.0", message = "Selling price cannot be negative")
        BigDecimal sellingPrice,

        @NotNull(message = "Tax rate is required")
        @DecimalMin(value = "0.0", message = "Tax rate cannot be negative")
        @DecimalMax(value = "100.0", message = "Tax rate cannot exceed 100%")
        BigDecimal taxRatePercent,

        @NotNull(message = "Stock quantity is required")
        @DecimalMin(value = "0.0", message = "Stock quantity cannot be negative")
        BigDecimal stockQuantity,

        @NotNull(message = "Minimum stock level is required")
        @DecimalMin(value = "0.0", message = "Minimum stock level cannot be negative")
        BigDecimal minimumStockLevel
) {
}
