package com.roomify.dto.vision;

public record VisionBoundingBox(
        int x,
        int y,
        int width,
        int height
) {
}