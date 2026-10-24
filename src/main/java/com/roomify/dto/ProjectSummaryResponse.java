package com.roomify.dto;

import com.roomify.entity.RentalStatus;
import com.roomify.entity.RoomProjectStatus;
import com.roomify.entity.RoomType;

import java.time.Instant;
import java.util.UUID;

public record ProjectSummaryResponse(
        UUID id,
        String name,
        RoomType roomType,
        RentalStatus rentalStatus,
        RoomProjectStatus status,
        boolean hasImage,
        boolean hasDesign,
        Instant createdAt,
        Instant updatedAt
) {
}
