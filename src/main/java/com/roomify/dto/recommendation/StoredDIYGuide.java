package com.roomify.dto.recommendation;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import java.time.Instant;

public record StoredDIYGuide(UUID guideId, UUID itemId, UUID projectId, String schemaVersion, String currency,
        List<String> materials, List<String> tools, BigDecimal estimatedCost, String difficulty,
        String estimatedTime, List<String> steps, List<String> safetyNotes,
        GenerationMetadata metadata, Instant createdAt) {
    public StoredDIYGuide {
        materials = List.copyOf(materials); tools = List.copyOf(tools);
        steps = List.copyOf(steps); safetyNotes = List.copyOf(safetyNotes);
    }

}
