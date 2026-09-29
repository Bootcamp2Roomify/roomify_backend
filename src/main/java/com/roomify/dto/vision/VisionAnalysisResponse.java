package com.roomify.dto.vision;

import java.util.List;

public record VisionAnalysisResponse(
        String status,
        List<VisionDetection> detections
) {
}