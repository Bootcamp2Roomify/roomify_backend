package com.roomify.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectAnalysisControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void returnsStoredActiveDetectionsWithStableUuid() throws Exception {
        UUID projectId = createProject();
        Long imageId = createRoomImage(projectId);

        UUID activeObjectUuid = createDetectedObject(
            projectId,
            imageId,
            "chair",
            true
        );

        createDetectedObject(
            projectId,
            imageId,
            "bed",
            false
        );

        mockMvc.perform(get(
                "/api/projects/{projectId}/analysis",
                projectId
            ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectId").value(projectId.toString()))
            .andExpect(jsonPath("$.objects.length()").value(1))
            .andExpect(jsonPath("$.objects[0].objectId")
                .value(activeObjectUuid.toString()))
            .andExpect(jsonPath("$.objects[0].imageId").value(imageId))
            .andExpect(jsonPath("$.objects[0].label").value("chair"))
            .andExpect(jsonPath("$.objects[0].confidence").value(0.83))
            .andExpect(jsonPath("$.objects[0].bbox.x").value(0.42))
            .andExpect(jsonPath("$.objects[0].bbox.y").value(0.31))
            .andExpect(jsonPath("$.objects[0].bbox.w").value(0.18))
            .andExpect(jsonPath("$.objects[0].bbox.h").value(0.35))
            .andExpect(jsonPath("$.objects[0].modelVersion")
                .value("test-yolo"));
    }

    @Test
    void returnsSameStableUuidAcrossMultipleReads() throws Exception {
        UUID projectId = createProject();
        Long imageId = createRoomImage(projectId);

        UUID objectUuid = createDetectedObject(
            projectId,
            imageId,
            "chair",
            true
        );

        mockMvc.perform(get(
                "/api/projects/{projectId}/analysis",
                projectId
            ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.objects[0].objectId")
                .value(objectUuid.toString()));

        mockMvc.perform(get(
                "/api/projects/{projectId}/analysis",
                projectId
            ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.objects[0].objectId")
                .value(objectUuid.toString()));
    }

    @Test
    void returnsProjectDecisionsAndDefaultsOnlyMissingChoices() throws Exception {
        UUID project = createProject();
        Long image = createRoomImage(project);
        for (String decision : new String[]{"KEEP", "REPLACE", "REMOVE"}) {
            UUID object = createDetectedObject(project, image, decision.toLowerCase(), true);
            saveDecision(object, decision);
        }
        createDetectedObject(project, image, "missing choice", true);
        UUID inactive = createDetectedObject(project, image, "inactive", false);
        saveDecision(inactive, "REMOVE");
        UUID otherProject = createProject();
        UUID other = createDetectedObject(otherProject, createRoomImage(otherProject), "other", true);
        saveDecision(other, "REMOVE");

        mockMvc.perform(get("/api/projects/{projectId}/analysis", project))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.objects.length()").value(4))
            .andExpect(jsonPath("$.objects[0].decision").value("KEEP"))
            .andExpect(jsonPath("$.objects[1].decision").value("REPLACE"))
            .andExpect(jsonPath("$.objects[2].decision").value("REMOVE"))
            .andExpect(jsonPath("$.objects[3].decision").value("UNSURE"));
        mockMvc.perform(get("/api/projects/{projectId}/analysis", otherProject))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.objects.length()").value(1))
            .andExpect(jsonPath("$.objects[0].objectId").value(other.toString()))
            .andExpect(jsonPath("$.objects[0].decision").value("REMOVE"));
    }

    private void saveDecision(UUID object, String decision) {
        jdbc.update("""
            INSERT INTO furniture_decisions(object_id, decision)
            SELECT object_id, ? FROM detected_objects WHERE object_uuid = ?
            """, decision, object);
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

    private UUID createDetectedObject(
        UUID projectId,
        Long imageId,
        String label,
        boolean active
    ) {
        return jdbc.queryForObject("""
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
                0.83000,
                0.420000,
                0.310000,
                0.600000,
                0.660000,
                'test-yolo',
                ?
            )
            RETURNING object_uuid
            """,
            UUID.class,
            projectId,
            imageId,
            label,
            active
        );
    }
}