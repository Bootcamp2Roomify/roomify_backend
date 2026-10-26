package com.roomify.dto.recommendation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.roomify.ai.DemoLibrary;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import java.time.Instant;

public record StoredRecommendationSet(UUID setId, UUID projectId, Long designResultId, String schemaVersion, String currency,
        BigDecimal budget, BigDecimal estimatedTotal, BigDecimal remainingBudget,
        List<StoredRecommendationItem> items, GenerationMetadata metadata, Instant createdAt) {
    public StoredRecommendationSet { items = List.copyOf(items); }

    /** "demo" when this came from the bundled demo library instead of a live AI model. */
    @JsonProperty("source")
    public String source() {
        return metadata != null && DemoLibrary.PROVIDER.equals(metadata.provider()) ? DemoLibrary.SOURCE_DEMO : DemoLibrary.SOURCE_AI;
    }
}
