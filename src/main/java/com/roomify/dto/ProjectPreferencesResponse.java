package com.roomify.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProjectPreferencesResponse(
    UUID projectId, String style, BigDecimal budgetAmount, String currency,
    List<String> preferredColors, String roomPurpose, boolean rentalFriendly,
    String specialRequirements, BigDecimal maxBudgetAmount
) {}
