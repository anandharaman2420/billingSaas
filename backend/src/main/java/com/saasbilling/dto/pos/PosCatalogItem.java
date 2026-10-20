package com.saasbilling.dto.pos;

import java.math.BigDecimal;
import java.util.UUID;

public record PosCatalogItem(
        UUID id,
        String name,
        String type, // "PRODUCT" or "SERVICE"
        String sku,
        String hsnSacCode,
        String unit,
        BigDecimal price,
        BigDecimal taxRatePercent,
        BigDecimal stockQuantity,
        UUID categoryId,
        String categoryName
) {
}
