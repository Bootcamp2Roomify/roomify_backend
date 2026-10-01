package com.roomify.dto;

public record DetectionInput (
    String label,
    double confidence,
    BoundingBoxInput bbox
){}
