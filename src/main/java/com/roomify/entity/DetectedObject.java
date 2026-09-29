package com.roomify.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "detected_objects")
public class DetectedObject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "object_id")
    private Long id;

    @Column(name = "image_id", nullable = false)
    private Long imageId;

    @Column(name = "object_class", nullable = false)
    private String objectClass;

    @Column(name = "confidence")
    private BigDecimal confidence;

    @Column(name = "x_min")
    private BigDecimal xMin;

    @Column(name = "y_min")
    private BigDecimal yMin;

    @Column(name = "x_max")
    private BigDecimal xMax;

    @Column(name = "y_max")
    private BigDecimal yMax;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DetectedObject() {
    }

    public DetectedObject(
            Long imageId,
            String objectClass,
            BigDecimal confidence,
            BigDecimal xMin,
            BigDecimal yMin,
            BigDecimal xMax,
            BigDecimal yMax
    ) {
        this.imageId = imageId;
        this.objectClass = objectClass;
        this.confidence = confidence;
        this.xMin = xMin;
        this.yMin = yMin;
        this.xMax = xMax;
        this.yMax = yMax;
        this.source = "CV";
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getImageId() {
        return imageId;
    }

    public String getObjectClass() {
        return objectClass;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public BigDecimal getXMin() {
        return xMin;
    }

    public BigDecimal getYMin() {
        return yMin;
    }

    public BigDecimal getXMax() {
        return xMax;
    }

    public BigDecimal getYMax() {
        return yMax;
    }

    public String getSource() {
        return source;
    }
} 