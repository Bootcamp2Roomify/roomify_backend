package com.roomify.repository;

import com.roomify.entity.DetectedObject;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DetectedObjectRepository
        extends JpaRepository<DetectedObject, Long> {

    void deleteByImageId(Long imageId);
}