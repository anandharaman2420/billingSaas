package com.saasbilling.dto.invoice;

import com.saasbilling.entity.Invoice;
import com.saasbilling.entity.InvoiceItem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record InvoiceResponse(
        UUID id,
        String invoiceNumber,
        LocalDate invoiceDate,
        LocalDate dueDate,
        String status,
        UUID customerId,
        List<Item> items,
        BigDecimal subtotal,
        BigDecimal itemDiscountTotal,
        BigDecimal additionalDiscountAmount,
        BigDecimal taxableAmount,
        BigDecimal cgstAmount,
        BigDecimal sgstAmount,
        BigDecimal igstAmount,
        BigDecimal totalTax,
        BigDecimal grandTotal,
        BigDecimal amountPaid,
        BigDecimal balanceDue,
        String notes,
        String terms,
        OffsetDateTime cancelledAt,
        String cancellationReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public record Item(
            UUID id,
            String itemType,
            UUID productId,
            UUID serviceId,
            String itemName,
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal discountAmount,
            BigDecimal taxRatePercent,
            BigDecimal taxAmount,
            BigDecimal lineTotal
    ) {
        public static Item from(InvoiceItem item) {
            return new Item(
                    item.getId(), item.getItemType().name(), item.getProductId(), item.getServiceId(),
                    item.getItemName(), item.getDescription(), item.getQuantity(), item.getUnitPrice(),
                    item.getDiscountAmount(), item.getTaxRatePercent(), item.getTaxAmount(), item.getLineTotal());
        }
    }

    public static InvoiceResponse from(Invoice invoice) {
        return new InvoiceResponse(
                invoice.getId(), invoice.getInvoiceNumber(), invoice.getInvoiceDate(), invoice.getDueDate(),
                invoice.getStatus().name(), invoice.getCustomerId(),
                invoice.getItems().stream().map(Item::from).toList(),
                invoice.getSubtotal(), invoice.getItemDiscountTotal(), invoice.getAdditionalDiscountAmount(),
                invoice.getTaxableAmount(), invoice.getCgstAmount(), invoice.getSgstAmount(), invoice.getIgstAmount(),
                invoice.getTotalTax(), invoice.getGrandTotal(), invoice.getAmountPaid(), invoice.getBalanceDue(),
                invoice.getNotes(), invoice.getTerms(), invoice.getCancelledAt(), invoice.getCancellationReason(),
                invoice.getCreatedAt(), invoice.getUpdatedAt());
    }

    /** Lightweight variant for list views - no line items. */
    public record Summary(
            UUID id,
            String invoiceNumber,
            LocalDate invoiceDate,
            LocalDate dueDate,
            String status,
            UUID customerId,
            BigDecimal grandTotal,
            BigDecimal amountPaid,
            BigDecimal balanceDue
    ) {
        public static Summary from(Invoice invoice) {
            return new Summary(
                    invoice.getId(), invoice.getInvoiceNumber(), invoice.getInvoiceDate(), invoice.getDueDate(),
                    invoice.getStatus().name(), invoice.getCustomerId(), invoice.getGrandTotal(),
                    invoice.getAmountPaid(), invoice.getBalanceDue());
        }
    }
}
