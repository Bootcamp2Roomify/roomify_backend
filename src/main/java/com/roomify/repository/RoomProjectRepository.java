package com.roomify.repository;

import com.roomify.entity.RoomProject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RoomProjectRepository extends JpaRepository<RoomProject, UUID> {
}