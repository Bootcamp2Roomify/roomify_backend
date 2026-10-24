package com.roomify.dto;

import com.roomify.entity.RentalStatus;
import com.roomify.entity.RoomProjectStatus;
import com.roomify.entity.RoomType;

import java.util.UUID;

public record CreateProjectResponse(
        UUID id,
        String name,
        RoomType roomType,
        RentalStatus rentalStatus,
        RoomProjectStatus status
) {
}