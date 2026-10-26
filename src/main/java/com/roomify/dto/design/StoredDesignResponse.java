package com.roomify.dto.design;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.roomify.ai.DemoLibrary;
import java.time.Instant;
import java.util.UUID;

public record StoredDesignResponse(Long designId, UUID requestId, UUID projectId,
        String status, String errorCode, DesignPlan plan, String provider, String modelName,
        String requestVersion, String promptVersion, Instant createdAt, Instant completedAt,
        GeneratedAsset image) {
    /** "demo" when the plan came from the bundled demo library instead of a live AI model. */
    @JsonProperty("source")
    public String source() {
        return DemoLibrary.PROVIDER.equals(provider) ? DemoLibrary.SOURCE_DEMO : DemoLibrary.SOURCE_AI;
    }

    /** source is "demo" for a bundled demo-library photo, "ai" for a generated image. */
    public record GeneratedAsset(String status, String errorCode, String contentType,
            Long sizeBytes, Integer width, Integer height, String imageUrl, String source) {}
}
