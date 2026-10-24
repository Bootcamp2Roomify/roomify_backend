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

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false, length = 30)
    private RoomType roomType;

    @Enumerated(EnumType.STRING)
    @Column(name = "rental_status", nullable = false, length = 20)
    private RentalStatus rentalStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public RoomProject() {
        this.id = UUID.randomUUID();
        this.name = "My room";
        this.roomType = RoomType.OTHER;
        this.rentalStatus = RentalStatus.OWNED;
        this.status = RoomProjectStatus.CREATED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public RoomProject(long userId, String name, RoomType roomType, RentalStatus rentalStatus) {
        this();
        this.userId = userId;
        this.name = name;
        this.roomType = roomType;
        this.rentalStatus = rentalStatus;
    }

    public UUID getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public RoomType getRoomType() {
        return roomType;
    }

    public RentalStatus getRentalStatus() {
        return rentalStatus;
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