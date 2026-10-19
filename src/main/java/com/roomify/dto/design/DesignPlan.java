package com.roomify.dto.design;

import java.math.BigDecimal;
import java.util.List;

public record DesignPlan(
        String designRationale,
        List<LayoutChange> layoutChanges,
        List<BudgetAllocation> budgetAllocation,
        BigDecimal estimatedTotalCost,
        String currency,
        List<String> rentalFriendlyNotes,
        List<String> warnings
) {
}