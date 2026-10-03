package com.roomify.repository;

import com.roomify.entity.DetectedObject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DetectedObjectRepository
        extends JpaRepository<DetectedObject, Long> {

    void deleteByImageId(Long imageId);

    List<DetectedObject> findByImageIdOrderByIdAsc(Long imageId);

    Optional<DetectedObject> findByIdAndProjectId(
            Long objectId,
            UUID projectId
    );

    Optional<DetectedObject> findByObjectUuidAndProjectId(
            UUID objectUuid,
            UUID projectId
    );

    List<DetectedObject> findByProjectIdAndActiveTrueOrderByIdAsc(
            UUID projectId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE DetectedObject detectedObject
            SET detectedObject.active = false
            WHERE detectedObject.projectId = :projectId
              AND detectedObject.active = true
            """)
    int deactivateActiveByProjectId(
            @Param("projectId") UUID projectId
    );
}