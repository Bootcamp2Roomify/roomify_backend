package com.roomify.dto;

import com.roomify.entity.RentalStatus;
import com.roomify.entity.RoomType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull RoomType roomType,
        @NotNull RentalStatus rentalStatus
) {
}
