package com.saasbilling.dto.expense;

import com.saasbilling.entity.Expense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ExpenseResponse(
        UUID id,
        String description,
        BigDecimal amount,
        LocalDate expenseDate,
        String paymentMethod,
        UUID categoryId,
        String referenceNumber,
        String notes,
        String attachmentUrl,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ExpenseResponse from(Expense e) {
        return new ExpenseResponse(
                e.getId(), e.getDescription(), e.getAmount(), e.getExpenseDate(), e.getPaymentMethod().name(),
                e.getCategoryId(), e.getReferenceNumber(), e.getNotes(), e.getAttachmentUrl(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
