package com.saasbilling.dto.payment;

import com.saasbilling.entity.Payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID invoiceId,
        UUID customerId,
        BigDecimal amount,
        LocalDate paymentDate,
        String paymentMethod,
        String referenceNumber,
        String notes,
        UUID recordedByUserId,
        boolean voided,
        OffsetDateTime voidedAt,
        String voidedReason,
        OffsetDateTime createdAt
) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(
                p.getId(), p.getInvoiceId(), p.getCustomerId(), p.getAmount(), p.getPaymentDate(),
                p.getPaymentMethod().name(), p.getReferenceNumber(), p.getNotes(), p.getRecordedByUserId(),
                p.isVoided(), p.getVoidedAt(), p.getVoidedReason(), p.getCreatedAt());
    }
}
