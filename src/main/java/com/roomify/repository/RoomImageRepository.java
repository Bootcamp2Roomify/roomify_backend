package com.roomify.repository;

import com.roomify.entity.RoomImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoomImageRepository extends JpaRepository<RoomImage, Long> {

    Optional<RoomImage> findTopByProjectIdOrderByCreatedAtDesc(UUID projectId);
}