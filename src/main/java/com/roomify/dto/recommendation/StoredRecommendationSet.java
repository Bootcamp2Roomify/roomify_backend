package com.roomify.dto.recommendation;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import java.time.Instant;

public record StoredRecommendationSet(UUID setId, UUID projectId, Long designResultId, String schemaVersion, String currency,
        BigDecimal budget, BigDecimal estimatedTotal, BigDecimal remainingBudget,
        List<StoredRecommendationItem> items, GenerationMetadata metadata, Instant createdAt) {
    public StoredRecommendationSet { items = List.copyOf(items); }

}
