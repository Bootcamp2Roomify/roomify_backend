package com.roomify.dto.analysis;

import com.roomify.entity.FurnitureDecisionType;

import java.math.BigDecimal;

public record DetectedObjectResponse(
        Long id,
        String label,
        BigDecimal confidence,
        BoundingBoxResponse bbox,
        FurnitureDecisionType decision
) {
}