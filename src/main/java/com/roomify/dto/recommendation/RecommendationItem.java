package com.roomify.dto.recommendation;

import java.math.BigDecimal;

/** placement is a required key whose value may be null (e.g. for a DIY refresh of an owned item). */
public record RecommendationItem(String name, String category, String reason, String placement, BigDecimal estimatedPrice,
        CheaperAlternative cheaperAlternative, String searchQuery, Boolean diyEligible) {
}
