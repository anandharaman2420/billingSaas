package com.saasbilling.dto.pos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PosReceiptResponse(
        UUID invoiceId,
        String invoiceNumber,
        String billNo,
        LocalDate invoiceDate,
        OffsetDateTime createdAt,
        String cashierName,

        // Business details
        String businessName,
        String branchName,
        String businessAddress,
        String businessPhone,
        String businessEmail,
        String businessGstin,

        // Customer details
        String customerName,
        String customerPhone,

        // Line items
        List<Item> items,

        // Financial totals
        BigDecimal subtotal,
        BigDecimal itemDiscountTotal,
        BigDecimal additionalDiscountAmount,
        BigDecimal taxableAmount,
        BigDecimal cgstAmount,
        BigDecimal sgstAmount,
        BigDecimal igstAmount,
        BigDecimal totalTax,
        BigDecimal grandTotal,

        // Payment details
        String paymentMethod,
        BigDecimal amountPaid,
        BigDecimal amountTendered,
        BigDecimal changeAmount,
        String paymentReference,

        String currency,
        String notes,
        String terms
) {
    public record Item(
            String itemName,
            String itemType,
            String hsnSacCode,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal discountAmount,
            BigDecimal taxRatePercent,
            BigDecimal taxAmount,
            BigDecimal lineTotal
    ) {}
}
