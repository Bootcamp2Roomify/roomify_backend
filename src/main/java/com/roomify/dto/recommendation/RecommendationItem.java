package com.roomify.dto.recommendation;

import java.math.BigDecimal;

public record RecommendationItem(String name, String category, String reason, BigDecimal estimatedPrice,
        CheaperAlternative cheaperAlternative, String searchQuery, Boolean diyEligible) {
}
