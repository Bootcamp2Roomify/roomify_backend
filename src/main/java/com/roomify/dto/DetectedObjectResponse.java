package com.roomify.dto;

import com.roomify.entity.DetectedObject;

import java.math.BigDecimal;
import java.util.UUID;

public record DetectedObjectResponse(
    UUID objectId,
    Long imageId,
    String label,
    BigDecimal confidence,
    BoundingBoxResponse bbox,
    String modelVersion
) {
    public static DetectedObjectResponse from(DetectedObject object) {
        return new DetectedObjectResponse(
            object.getObjectUuid(),
            object.getImageId(),
            object.getObjectClass(),
            object.getConfidence(),
            new BoundingBoxResponse(
                object.getXMin(),
                object.getYMin(),
                object.getXMax().subtract(object.getXMin()),
                object.getYMax().subtract(object.getYMin())
            ),
            object.getModelVersion()
        );
    }
}