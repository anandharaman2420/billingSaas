package com.saasbilling.dto.invoice;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The client sends WHAT to bill (product/service id, quantity, an
 * optional per-line discount) and nothing else. Unit price and tax
 * rate are never accepted from the client - InvoiceService always
 * looks them up from the tenant's own Product/Service record at the
 * moment of billing (see spec section 11: "Do not trust totals sent
 * from frontend").
 */
public record InvoiceItemRequest(

        // Exactly one of these two must be set - validated in InvoiceService.
        UUID productId,
        UUID serviceId,

        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be greater than zero")
        BigDecimal quantity,

        @DecimalMin(value = "0.0", message = "Discount cannot be negative")
        BigDecimal discountAmount,

        String description
) {
}
