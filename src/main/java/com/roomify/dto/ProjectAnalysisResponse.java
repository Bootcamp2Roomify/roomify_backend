package com.roomify.dto;

import java.util.List;

public record ProjectAnalysisResponse(
    Long projectId,
    List<DetectedObjectResponse> objects
) {
}