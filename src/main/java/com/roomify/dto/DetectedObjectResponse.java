package com.roomify.dto;

import com.roomify.entity.DetectedObject;
import com.roomify.entity.FurnitureDecisionType;

import java.math.BigDecimal;
import java.util.UUID;

public record DetectedObjectResponse(
    UUID objectId,
    Long imageId,
    String label,
    BigDecimal confidence,
    BoundingBoxResponse bbox,
    String modelVersion,
    FurnitureDecisionType decision,
    String source
) {
    public static DetectedObjectResponse from(DetectedObject object, FurnitureDecisionType decision) {
        return new DetectedObjectResponse(
            object.getObjectUuid(),
            object.getImageId(),
            object.getObjectClass(),
            object.getConfidence(),
            // Items added by hand have no box.
            object.getXMin() == null ? null : new BoundingBoxResponse(
                object.getXMin(),
                object.getYMin(),
                object.getXMax().subtract(object.getXMin()),
                object.getYMax().subtract(object.getYMin())
            ),
            object.getModelVersion(),
            decision,
            object.getSource()
        );
    }
}