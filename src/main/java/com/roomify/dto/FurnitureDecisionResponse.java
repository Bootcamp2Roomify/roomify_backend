package com.roomify.dto;

import com.roomify.entity.FurnitureDecisionType;

public record FurnitureDecisionResponse(
    Long objectId,
    FurnitureDecisionType decision
) {
}