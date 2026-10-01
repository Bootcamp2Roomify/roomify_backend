package com.roomify.dto.analysis;

import com.roomify.entity.RoomProjectStatus;

import java.util.List;
import java.util.UUID;

public record AnalyzeProjectResponse(
        UUID projectId,
        RoomProjectStatus status,
        List<DetectedObjectResponse> objects
) {
}