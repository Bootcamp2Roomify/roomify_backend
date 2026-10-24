package com.roomify.dto.design;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record BudgetAllocation(@NotBlank @Size(max=100) String category,
        @NotBlank @Size(max=200) String item, @NotBlank @Size(max=1000) String purpose,
        @NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal estimatedCost) {}
