package com.roomify.dto.vision;

public record VisionAnalysisRequest(
        String storageKey,
        String mimeType
) {
}