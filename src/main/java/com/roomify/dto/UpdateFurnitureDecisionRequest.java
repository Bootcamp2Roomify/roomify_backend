package com.roomify.dto;

import com.roomify.entity.FurnitureDecisionType;
import jakarta.validation.constraints.NotNull;

public record UpdateFurnitureDecisionRequest(
    @NotNull(message = "Decision is required.")
    FurnitureDecisionType decision
) {
}