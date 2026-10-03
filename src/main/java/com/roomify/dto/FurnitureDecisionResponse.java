package com.roomify.dto;

import com.roomify.entity.FurnitureDecisionType;

import java.util.UUID;

public record FurnitureDecisionResponse(
        UUID objectId,
        FurnitureDecisionType decision
) {
}