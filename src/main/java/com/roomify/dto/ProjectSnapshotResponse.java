package com.roomify.dto;

import com.roomify.entity.RoomProjectStatus;
import java.time.Instant;
import java.util.UUID;

public record ProjectSnapshotResponse(
        UUID id,
        RoomProjectStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}