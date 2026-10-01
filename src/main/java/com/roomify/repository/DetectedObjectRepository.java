package com.roomify.repository;

import com.roomify.entity.DetectedObject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DetectedObjectRepository
        extends JpaRepository<DetectedObject, Long> {

    void deleteByImageId(Long imageId);

    List<DetectedObject> findByImageIdOrderByIdAsc(Long imageId);

    @Query("""
            select detectedObject
            from DetectedObject detectedObject
            join RoomImage roomImage on roomImage.id = detectedObject.imageId
            where detectedObject.id = :objectId
              and roomImage.projectId = :projectId
            """)
    Optional<DetectedObject> findByIdAndProjectId(
            @Param("objectId") Long objectId,
            @Param("projectId") UUID projectId
    );
}