package com.saasbilling.dto.invoice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InvoiceRequest(

        @NotNull(message = "Customer is required")
        UUID customerId,

        @NotNull(message = "Invoice date is required")
        LocalDate invoiceDate,

        // Optional - if omitted, InvoiceService computes it from
        // business_settings.default_due_days.
        LocalDate dueDate,

        @NotEmpty(message = "At least one item is required")
        @Valid
        List<InvoiceItemRequest> items,

        @DecimalMin(value = "0.0", message = "Discount cannot be negative")
        BigDecimal additionalDiscountAmount,

        String notes,
        String terms
) {
}
