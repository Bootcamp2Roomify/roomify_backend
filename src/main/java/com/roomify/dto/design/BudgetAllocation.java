package com.roomify.dto.design;

import java.math.BigDecimal;

public record BudgetAllocation(
        String category,
        String item,
        String purpose,
        BigDecimal estimatedCost
) {
}