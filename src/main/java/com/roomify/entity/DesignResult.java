package com.roomify.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "design_results")
public class DesignResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "design_id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "plan_json", columnDefinition = "jsonb")
    private String planJson;

    @Column(
            name = "estimated_total_cost",
            precision = 12,
            scale = 2
    )
    private BigDecimal estimatedTotalCost;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DesignResult() {
    }

    public DesignResult(
            UUID projectId,
            String planJson,
            BigDecimal estimatedTotalCost,
            String currency,
            String modelName
    ) {
        this.projectId = projectId;
        this.planJson = planJson;
        this.estimatedTotalCost = estimatedTotalCost;
        this.currency = currency;
        this.modelName = modelName;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getPlanJson() {
        return planJson;
    }

    public BigDecimal getEstimatedTotalCost() {
        return estimatedTotalCost;
    }

    public String getCurrency() {
        return currency;
    }

    public String getModelName() {
        return modelName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}