package com.roomify.dto;

import com.roomify.entity.RentalStatus;
import com.roomify.entity.RoomProjectStatus;
import com.roomify.entity.RoomType;
import java.time.Instant;
import java.util.UUID;

public record ProjectSnapshotResponse(
        UUID id,
        String name,
        RoomType roomType,
        RentalStatus rentalStatus,
        RoomProjectStatus status,
        Instant createdAt,
        Instant updatedAt,
        ProjectPreferencesResponse preferences
) {
}
