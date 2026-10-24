package com.roomify.dto.design;

import com.roomify.entity.FurnitureDecisionType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Immutable data used for one generation, without signed URLs or provider configuration. */
public record DesignRequestSnapshot(String requestVersion, UUID projectId, ImageReference image,
        List<Furniture> objects, Preferences preferences, Budget budget) {
    public DesignRequestSnapshot { objects = List.copyOf(objects); }
    public record ImageReference(Long imageId, String bucket, String storageKey,
            String contentType, Integer width, Integer height) {}
    public record Furniture(UUID objectId, String label, BigDecimal confidence,
            Bounds bbox, FurnitureDecisionType decision) {}
    public record Bounds(BigDecimal x, BigDecimal y, BigDecimal w, BigDecimal h) {}
    public record Preferences(String style, List<String> preferredColors, String roomPurpose,
            boolean rentalFriendly, String specialRequirements) {
        public Preferences { preferredColors = List.copyOf(preferredColors); }
    }
    public record Budget(BigDecimal amount, String currency) {}
}
