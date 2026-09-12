package com.saasbilling.dto.product;

import com.saasbilling.entity.Product;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String productName,
        String sku,
        UUID categoryId,
        String description,
        String unit,
        BigDecimal purchasePrice,
        BigDecimal sellingPrice,
        BigDecimal taxRatePercent,
        BigDecimal stockQuantity,
        BigDecimal minimumStockLevel,
        boolean lowStock,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ProductResponse from(Product p) {
        return new ProductResponse(
                p.getId(), p.getProductName(), p.getSku(), p.getCategoryId(), p.getDescription(),
                p.getUnit(), p.getPurchasePrice(), p.getSellingPrice(), p.getTaxRatePercent(),
                p.getStockQuantity(), p.getMinimumStockLevel(),
                p.getStockQuantity().compareTo(p.getMinimumStockLevel()) <= 0,
                p.getStatus().name(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
