package com.roomify.dto;

import com.roomify.entity.RoomImage;

public record RoomImageResponse(
    Long imageId,
    Long projectId,
    String bucket,
    String storageKey,
    String originalFilename,
    String contentType,
    Long size
) {
    public static RoomImageResponse from(RoomImage image) {
        return new RoomImageResponse(
            image.getId(),
            image.getProjectId(),
            image.getBucket(),
            image.getStorageKey(),
            image.getOriginalFilename(),
            image.getMimeType(),
            image.getFileSizeBytes()
        );
    }
}