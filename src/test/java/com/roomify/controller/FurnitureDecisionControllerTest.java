package com.roomify.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FurnitureDecisionControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void createsAndUpdatesFurnitureDecision() throws Exception {
        Long projectId = createProject();
        Long objectId = createDetectedObject(projectId);

        mockMvc.perform(patch(
                "/api/projects/{projectId}/objects/{objectId}",
                projectId,
                objectId
            )
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"decision":"KEEP"}
                """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.objectId").value(objectId))
            .andExpect(jsonPath("$.decision").value("KEEP"));

        mockMvc.perform(patch(
                "/api/projects/{projectId}/objects/{objectId}",
                projectId,
                objectId
            )
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"decision":"REPLACE"}
                """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.decision").value("REPLACE"));

        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM furniture_decisions WHERE object_id = ?",
            Integer.class,
            objectId
        );

        String decision = jdbc.queryForObject(
            "SELECT decision FROM furniture_decisions WHERE object_id = ?",
            String.class,
            objectId
        );

        assertThat(count).isEqualTo(1);
        assertThat(decision).isEqualTo("REPLACE");
    }

    @Test
    void returns404WhenObjectBelongsToAnotherProject() throws Exception {
        Long correctProjectId = createProject();
        Long wrongProjectId = createProject();
        Long objectId = createDetectedObject(correctProjectId);

        mockMvc.perform(patch(
                "/api/projects/{projectId}/objects/{objectId}",
                wrongProjectId,
                objectId
            )
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"decision":"KEEP"}
                """))
            .andExpect(status().isNotFound());
    }

    @Test
    void returns400ForInvalidDecision() throws Exception {
        Long projectId = createProject();
        Long objectId = createDetectedObject(projectId);

        mockMvc.perform(patch(
                "/api/projects/{projectId}/objects/{objectId}",
                projectId,
                objectId
            )
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"decision":"SELL"}
                """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message")
                .value("Decision must be one of the allowed values."))
            .andExpect(jsonPath("$.allowedValues[0]").value("KEEP"))
            .andExpect(jsonPath("$.allowedValues[1]").value("REPLACE"))
            .andExpect(jsonPath("$.allowedValues[2]").value("REMOVE"))
            .andExpect(jsonPath("$.allowedValues[3]").value("UNSURE"));
    }

    private Long createProject() {
        Long userId = jdbc.queryForObject("""
            INSERT INTO users (email, password_hash)
            VALUES (?, 'test-password')
            RETURNING user_id
            """, Long.class, UUID.randomUUID() + "@example.com");

        return jdbc.queryForObject("""
            INSERT INTO room_projects (user_id, name)
            VALUES (?, 'Test room')
            RETURNING project_id
            """, Long.class, userId);
    }

    private Long createDetectedObject(Long projectId) {
        Long imageId = jdbc.queryForObject("""
            INSERT INTO room_images (
                project_id,
                storage_key,
                original_filename,
                mime_type,
                file_size_bytes
            )
            VALUES (?, ?, 'room.jpg', 'image/jpeg', 1000)
            RETURNING image_id
            """, Long.class, projectId, UUID.randomUUID().toString());

        return jdbc.queryForObject("""
            INSERT INTO detected_objects (image_id, object_class)
            VALUES (?, 'chair')
            RETURNING object_id
            """, Long.class, imageId);
    }
}