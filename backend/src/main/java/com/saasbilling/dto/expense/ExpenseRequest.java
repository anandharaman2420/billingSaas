package com.saasbilling.dto.expense;

import com.saasbilling.entity.PaymentMethod;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseRequest(

        @NotBlank(message = "Description is required")
        @Size(max = 255)
        String description,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        BigDecimal amount,

        @NotNull(message = "Expense date is required")
        LocalDate expenseDate,

        @NotNull(message = "Payment method is required")
        PaymentMethod paymentMethod,

        UUID categoryId,

        @Size(max = 100)
        String referenceNumber,

        String notes,
        String attachmentUrl
) {
}
