package com.roomify.dto;

import java.util.List;

public record AnalysisInput(
    String modelVersion,
    List<DetectionInput> objects
) {
}