package com.roomify.dto;

import com.roomify.entity.RoomProjectStatus;

import java.util.UUID;

public record CreateProjectResponse(
        UUID id,
        RoomProjectStatus status
) {
}