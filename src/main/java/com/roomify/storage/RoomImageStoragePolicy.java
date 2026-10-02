package com.roomify.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Component
public class RoomImageStoragePolicy {

    private static final Set<String> ALLOWED_TYPES = Set.of(
        "image/jpeg",
        "image/png"
    );

    private final long maxSizeBytes;

    public RoomImageStoragePolicy(
        @Value("${room-image.max-size-bytes:10485760}") long maxSizeBytes
    ) {
        this.maxSizeBytes = maxSizeBytes;
    }

    public void validate(String contentType, long size) {
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                "Only JPEG and PNG images are supported."
            );
        }

        if (size <= 0) {
            throw new IllegalArgumentException(
                "Image file must not be empty."
            );
        }

        if (size > maxSizeBytes) {
            throw new IllegalArgumentException(
                "Image exceeds the maximum allowed size."
            );
        }
    }

    public String generateObjectKey(Long projectId, String contentType) {
        if (projectId == null || projectId <= 0) {
            throw new IllegalArgumentException("Invalid project ID.");
        }

        String extension = switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            default -> throw new IllegalArgumentException(
                "Unsupported image type."
            );
        };

        return "rooms/%d/original/%s.%s".formatted(
            projectId,
            UUID.randomUUID(),
            extension
        );
    }
}