package com.roomify.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "room_projects")
public class RoomProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "project_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 50)
    private String status = "DRAFT";

    protected RoomProject() {
    }

    public RoomProject(Long userId, String name) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("A valid user ID is required.");
        }
        if (name == null || name.isBlank() || name.strip().length() > 100) {
            throw new IllegalArgumentException(
                "Room name must contain 1–100 characters."
            );
        }
        this.userId = userId;
        this.name = name.strip();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }
}