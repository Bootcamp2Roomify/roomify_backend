package com.roomify.dto;

import java.math.BigDecimal;

public record BoundingBoxResponse(
    BigDecimal x,
    BigDecimal y,
    BigDecimal w,
    BigDecimal h
) {
}