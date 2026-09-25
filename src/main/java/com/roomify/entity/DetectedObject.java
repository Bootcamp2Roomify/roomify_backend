package com.roomify.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "detected_objects")
public class DetectedObject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "object_id")
    private Long id;

    @Column(name = "image_id", nullable = false)
    private Long imageId;

    @Column(name = "object_class", nullable = false, length = 100)
    private String objectClass;

    protected DetectedObject() {
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
}