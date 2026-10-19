package com.roomify.dto.design;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record GenerateDesignResponse(
        Long designId,
        UUID projectId,
        DesignPlan plan,
        BigDecimal estimatedTotalCost,
        String currency,
        String modelName,
        Instant createdAt
) {
}