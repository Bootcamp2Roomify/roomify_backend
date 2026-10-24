package com.roomify.dto;

import com.roomify.entity.RoomImage;

import java.util.UUID;

public record ActiveRoomImageResponse(
    Long imageId,
    UUID projectId,
    String contentType,
    Integer width,
    Integer height,
    String imageUrl
) {
    public static ActiveRoomImageResponse from(RoomImage image, String imageUrl) {
        return new ActiveRoomImageResponse(
            image.getId(),
            image.getProjectId(),
            image.getMimeType(),
            image.getWidth(),
            image.getHeight(),
            imageUrl
        );
    }
}
