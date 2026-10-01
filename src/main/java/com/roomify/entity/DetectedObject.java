package com.roomify.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "detected_objects")
public class DetectedObject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "object_id")
    private Long id;

    @Column(name = "object_uuid", nullable = false, unique = true, updatable = false)
    private UUID objectUuid;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "image_id", nullable = false)
    private Long imageId;

    @Column(name = "object_class", nullable = false, length = 100)
    private String objectClass;

    @Column(name = "confidence", precision = 6, scale = 5)
    private BigDecimal confidence;

    @Column(name = "x_min", precision = 8, scale = 6)
    private BigDecimal xMin;

    @Column(name = "y_min", precision = 8, scale = 6)
    private BigDecimal yMin;

    @Column(name = "x_max", precision = 8, scale = 6)
    private BigDecimal xMax;

    @Column(name = "y_max", precision = 8, scale = 6)
    private BigDecimal yMax;

    @Column(name = "model_version", nullable = false, length = 100)
    private String modelVersion;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "source", nullable = false, length = 20)
    private String source = "CV";

    protected DetectedObject() {
    }

    public DetectedObject(
        Long projectId,
        Long imageId,
        String objectClass,
        BigDecimal confidence,
        BigDecimal xMin,
        BigDecimal yMin,
        BigDecimal xMax,
        BigDecimal yMax,
        String modelVersion
    ) {
        this.objectUuid = UUID.randomUUID();
        this.projectId = projectId;
        this.imageId = imageId;
        this.objectClass = objectClass;
        this.confidence = confidence;
        this.xMin = xMin;
        this.yMin = yMin;
        this.xMax = xMax;
        this.yMax = yMax;
        this.modelVersion = modelVersion;
        this.active = true;
        this.source = "CV";
    }

    @PrePersist
    void ensureUuid() {
        if (objectUuid == null) {
            objectUuid = UUID.randomUUID();
        }
    }

    public void deactivate() {
        this.active = false;
    }

    public Long getId() {
        return id;
    }

    public UUID getObjectUuid() {
        return objectUuid;
    }

    public Long getProjectId() {
        return projectId;
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

    public String getModelVersion() {
        return modelVersion;
    }

    public boolean isActive() {
        return active;
    }

    public String getSource() {
        return source;
    }
}