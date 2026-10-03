package com.roomify.service;

import com.roomify.dto.BoundingBoxInput;
import com.roomify.dto.DetectionInput;
import com.roomify.entity.DetectedObject;
import com.roomify.repository.DetectedObjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class DetectionPersistenceServiceTest {

    @Autowired
    DetectionPersistenceService detectionPersistenceService;

    @Autowired
    DetectedObjectRepository detectedObjectRepository;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void reanalysisSupersedesPreviousActiveDetections() {
        UUID projectId = createProject();
        Long imageId = createRoomImage(projectId);

        createExistingDetection(projectId, imageId, "bed");
        createExistingDetection(projectId, imageId, "chair");

        List<DetectionInput> newDetections = List.of(
            new DetectionInput(
                "sofa",
                0.91,
                new BoundingBoxInput(0.10, 0.20, 0.30, 0.40)
            ),
            new DetectionInput(
                "plant",
                0.84,
                new BoundingBoxInput(0.60, 0.10, 0.20, 0.30)
            )
        );

        List<DetectedObject> saved =
            detectionPersistenceService.replaceActiveDetections(
                projectId,
                "yolo11n-test",
                newDetections
            );

        assertThat(saved).hasSize(2);
        assertThat(saved)
            .allMatch(DetectedObject::isActive);

        List<DetectedObject> active =
            detectedObjectRepository
                .findByProjectIdAndActiveTrueOrderByIdAsc(projectId);

        assertThat(active).hasSize(2);
        assertThat(active)
            .extracting(DetectedObject::getObjectClass)
            .containsExactly("sofa", "plant");

        Integer inactiveCount = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM detected_objects
            WHERE project_id = ?
              AND active = FALSE
            """,
            Integer.class,
            projectId
        );

        assertThat(inactiveCount).isEqualTo(2);
    }

    @Test
    void eachPersistedDetectionGetsUniqueStableUuid() {
        UUID projectId = createProject();
        createRoomImage(projectId);

        List<DetectedObject> saved =
            detectionPersistenceService.replaceActiveDetections(
                projectId,
                "yolo11n-test",
                List.of(
                    new DetectionInput(
                        "chair",
                        0.83,
                        new BoundingBoxInput(0.10, 0.10, 0.20, 0.20)
                    ),
                    new DetectionInput(
                        "bed",
                        0.94,
                        new BoundingBoxInput(0.40, 0.30, 0.40, 0.50)
                    )
                )
            );

        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getObjectUuid()).isNotNull();
        assertThat(saved.get(1).getObjectUuid()).isNotNull();
        assertThat(saved.get(0).getObjectUuid())
            .isNotEqualTo(saved.get(1).getObjectUuid());
    }

    private UUID createProject() {
        return jdbc.queryForObject("""
            INSERT INTO room_projects (
                project_id
            )
            VALUES (?)
            RETURNING project_id
            """,
            UUID.class,
            UUID.randomUUID()
        );
    }

    private Long createRoomImage(UUID projectId) {
        return jdbc.queryForObject("""
            INSERT INTO room_images (
                project_id,
                bucket,
                storage_key,
                original_filename,
                mime_type,
                file_size_bytes
            )
            VALUES (
                ?,
                'test-local',
                ?,
                'room.jpg',
                'image/jpeg',
                1000
            )
            RETURNING image_id
            """,
            Long.class,
            projectId,
            "rooms/" + projectId + "/" + UUID.randomUUID() + ".jpg"
        );
    }

    private void createExistingDetection(
        UUID projectId,
        Long imageId,
        String label
    ) {
        jdbc.update("""
            INSERT INTO detected_objects (
                project_id,
                image_id,
                object_class,
                confidence,
                x_min,
                y_min,
                x_max,
                y_max,
                model_version,
                active
            )
            VALUES (
                ?,
                ?,
                ?,
                0.80000,
                0.100000,
                0.100000,
                0.300000,
                0.300000,
                'old-model',
                TRUE
            )
            """,
            projectId,
            imageId,
            label
        );
    }
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollsBackEntireReanalysisWhenPersistenceFails() {
        UUID projectId = createProject();
        Long imageId = createRoomImage(projectId);

        createExistingDetection(
            projectId,
            imageId,
            "bed"
        );

        try {
            List<DetectionInput> detections = List.of(
                new DetectionInput(
                    "chair",
                    0.90,
                    new BoundingBoxInput(
                        0.10,
                        0.10,
                        0.20,
                        0.20
                    )
                ),
                new DetectionInput(
                    "x".repeat(101),
                    0.85,
                    new BoundingBoxInput(
                        0.40,
                        0.40,
                        0.20,
                        0.20
                    )
                )
            );

            assertThatThrownBy(() ->
                detectionPersistenceService.replaceActiveDetections(
                    projectId,
                    "yolo11n-test",
                    detections
                )
            ).isInstanceOf(RuntimeException.class);

            List<DetectedObject> active =
                detectedObjectRepository
                    .findByProjectIdAndActiveTrueOrderByIdAsc(projectId);

            assertThat(active).hasSize(1);
            assertThat(active.get(0).getObjectClass())
                .isEqualTo("bed");

            Integer newCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM detected_objects
                WHERE project_id = ?
                AND object_class = 'chair'
                """,
                Integer.class,
                projectId
            );

            assertThat(newCount).isZero();
        } finally {
            jdbc.update(
                "DELETE FROM room_projects WHERE project_id = ?",
                projectId
            );
        }
    }
}