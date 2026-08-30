package com.saasbilling.dto.service;

import com.saasbilling.entity.BillableService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ServiceResponse(
        UUID id,
        String serviceName,
        String description,
        BigDecimal price,
        BigDecimal taxRatePercent,
        UUID categoryId,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ServiceResponse from(BillableService s) {
        return new ServiceResponse(
                s.getId(), s.getServiceName(), s.getDescription(), s.getPrice(), s.getTaxRatePercent(),
                s.getCategoryId(), s.getStatus().name(), s.getCreatedAt(), s.getUpdatedAt());
    }
}
