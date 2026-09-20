package com.saasbilling.dto.search;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class SearchDtos {

    public record CustomerHit(UUID id, String name, String phone) {
    }

    public record ProductHit(UUID id, String name, String sku) {
    }

    public record ServiceHit(UUID id, String name) {
    }

    public record InvoiceHit(UUID id, String invoiceNumber, String status, BigDecimal grandTotal) {
    }

    public record GlobalSearchResponse(
            List<CustomerHit> customers,
            List<ProductHit> products,
            List<ServiceHit> services,
            List<InvoiceHit> invoices
    ) {
    }
}
