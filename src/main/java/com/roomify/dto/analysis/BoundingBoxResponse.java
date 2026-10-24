package com.roomify.dto.analysis;

import java.math.BigDecimal;

public record BoundingBoxResponse(
        BigDecimal xMin,
        BigDecimal yMin,
        BigDecimal xMax,
        BigDecimal yMax
) {
}