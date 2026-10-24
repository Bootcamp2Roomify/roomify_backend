package com.roomify.dto.design;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record DesignPlan(@NotNull @Pattern(regexp="1") String planVersion,
        @NotBlank @Size(max=2000) String summary, @NotBlank @Size(max=50) String designStyle,
        @NotNull @Size(max=100) List<@NotNull @Valid LayoutChange> actions,
        @NotNull @Size(max=20) List<@NotBlank @Size(max=1000) String> layoutNotes,
        @NotNull @Size(max=30) List<@NotNull @Valid BudgetAllocation> budgetAllocation,
        @NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal estimatedTotalCost,
        @NotNull @Pattern(regexp="TWD|USD") String currency,
        @NotNull @Size(max=20) List<@NotBlank @Size(max=1000) String> warnings,
        @NotNull @Size(max=30) List<@NotNull @Valid ItemNeeded> itemsNeeded,
        @NotNull @Size(max=20) List<@NotBlank @Size(max=1000) String> rentalFriendlyWarnings,
        @NotNull @Size(max=20) List<@NotBlank @Size(max=1000) String> constraints) {
    public record ItemNeeded(UUID objectId, @NotBlank @Size(max=200) String name,
            @Min(1) @Max(100) int quantity) {}
}
