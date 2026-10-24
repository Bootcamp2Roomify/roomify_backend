package com.roomify.dto.recommendation;


public record GenerationMetadata(String provider, String model, String schemaVersion, String requestId) {
}
