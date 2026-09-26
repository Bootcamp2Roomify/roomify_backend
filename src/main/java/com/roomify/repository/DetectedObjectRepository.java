package com.roomify.repository;

import com.roomify.entity.DetectedObject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DetectedObjectRepository
        extends JpaRepository<DetectedObject, Long> {

    @Query(value = """
        SELECT detected_object.*
        FROM detected_objects detected_object
        JOIN room_images room_image
          ON room_image.image_id = detected_object.image_id
        WHERE detected_object.object_id = :objectId
          AND room_image.project_id = :projectId
        """, nativeQuery = true)
    Optional<DetectedObject> findByIdAndProjectId(
        @Param("objectId") Long objectId,
        @Param("projectId") Long projectId
    );
}