package com.roomify.repository;

import com.roomify.entity.RoomImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoomImageRepository extends JpaRepository<RoomImage, Long> {

    Optional<RoomImage> findByProjectId(Long projectId);
}