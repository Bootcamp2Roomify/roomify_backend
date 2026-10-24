package com.roomify.dto.recommendation;

import java.math.BigDecimal;
import java.util.List;

public record RecommendationSet(String schemaVersion, String currency, BigDecimal budget,
        BigDecimal estimatedTotal, BigDecimal remainingBudget, List<RecommendationItem> items) {
    public RecommendationSet {
        if (items != null) items = List.copyOf(items);
    }

}
