package com.roomify.dto.vision;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record VisionDetection(
        String label,
        BigDecimal confidence,
        @JsonProperty("bounding_box") VisionBoundingBox boundingBox
) {
}