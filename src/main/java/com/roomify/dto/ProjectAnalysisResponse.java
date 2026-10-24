package com.roomify.dto;

import java.util.List;
import java.util.UUID;

public record ProjectAnalysisResponse(
    UUID projectId,
    List<DetectedObjectResponse> objects
) {
}