package com.roomify.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "room_images")
public class RoomImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "image_id")
    private Long id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RoomImage() {
    }

    public RoomImage(
            UUID projectId,
            String storageKey,
            String originalFilename,
            String mimeType,
            long fileSizeBytes,
            Integer width,
            Integer height
    ) {
        this.projectId = projectId;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.mimeType = mimeType;
        this.fileSizeBytes = fileSizeBytes;
        this.width = width;
        this.height = height;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public Integer getWidth() {
        return width;
    }

    public Integer getHeight() {
        return height;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}