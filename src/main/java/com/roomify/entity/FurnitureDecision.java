package com.roomify.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "furniture_decisions")
public class FurnitureDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "decision_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "object_id", nullable = false, unique = true)
    private DetectedObject detectedObject;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 20)
    private FurnitureDecisionType decision;

    protected FurnitureDecision() {
    }

    public FurnitureDecision(
            DetectedObject detectedObject,
            FurnitureDecisionType decision) {
        this.detectedObject = detectedObject;
        this.decision = decision;
    }

    public void updateDecision(FurnitureDecisionType decision) {
        this.decision = decision;
    }

    public Long getId() {
        return id;
    }

    public DetectedObject getDetectedObject() {
        return detectedObject;
    }

    public FurnitureDecisionType getDecision() {
        return decision;
    }
}