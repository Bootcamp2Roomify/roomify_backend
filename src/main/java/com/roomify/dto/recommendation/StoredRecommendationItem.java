package com.roomify.dto.recommendation;

import java.math.BigDecimal;
import java.util.UUID;

public record StoredRecommendationItem(UUID itemId, String name, String category, String reason, BigDecimal estimatedPrice,
        CheaperAlternative cheaperAlternative, String searchQuery, Boolean diyEligible) {
}
