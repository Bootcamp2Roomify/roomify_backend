package com.roomify.dto.design;

import com.roomify.entity.FurnitureDecisionType;

import java.util.UUID;

public record LayoutChange(
        UUID objectUuid,
        String objectClass,
        FurnitureDecisionType decision,
        String action,
        String rationale
) {
}