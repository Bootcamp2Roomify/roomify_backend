package com.roomify.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "room_projects")
public class RoomProject {

    @Id
    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private RoomProjectStatus status;

    @Column(name = "user_id", updatable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public RoomProject() {
        this.id = UUID.randomUUID();
        this.status = RoomProjectStatus.CREATED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public RoomProject(long userId) {
        this();
        this.userId = userId;
    }

    public UUID getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public RoomProjectStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setStatus(RoomProjectStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }
}