package com.roomify.dto.design;

import java.time.Instant;
import java.util.UUID;

public record StoredDesignResponse(Long designId, UUID requestId, UUID projectId,
        String status, String errorCode, DesignPlan plan, String provider, String modelName,
        String requestVersion, String promptVersion, Instant createdAt, Instant completedAt,
        GeneratedAsset image) {
    public record GeneratedAsset(String status, String errorCode, String contentType,
            Long sizeBytes, Integer width, Integer height, String imageUrl) {}
}
