package com.saasbilling.dto.pos;

import com.saasbilling.entity.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PosCheckoutRequest(
        UUID customerId,
        String customerName,
        String customerPhone,

        @NotEmpty(message = "At least one item is required in the cart")
        @Valid
        List<PosItemRequest> items,

        @DecimalMin(value = "0.0", message = "Additional discount cannot be negative")
        BigDecimal additionalDiscountAmount,

        @NotNull(message = "Payment method is required")
        PaymentMethod paymentMethod,

        @DecimalMin(value = "0.0", message = "Amount tendered cannot be negative")
        BigDecimal amountTendered,

        @DecimalMin(value = "0.0", message = "Amount paid cannot be negative")
        BigDecimal amountPaid,

        String referenceNumber,
        String notes
) {
}
