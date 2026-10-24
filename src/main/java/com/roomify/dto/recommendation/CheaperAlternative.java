package com.roomify.dto.recommendation;

import java.math.BigDecimal;

public record CheaperAlternative(String name, String reason, String searchQuery, BigDecimal estimatedPrice) {
}
