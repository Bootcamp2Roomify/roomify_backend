package com.roomify.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomify.support.TestUserSecurity;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MVP Feature 4: add missed items, correct labels, remove wrong detections. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ObjectEditControllerTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @Test
    void addsManualItemAsUserConfirmedWithoutBoxAndContinuesAfterFailedAnalysis() throws Exception {
        UUID project = project("ANALYSIS_FAILED");
        image(project);

        String body = mvc.perform(post("/api/projects/{p}/objects", project).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"  bookshelf \",\"decision\":\"KEEP\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("bookshelf"))
                .andExpect(jsonPath("$.decision").value("KEEP"))
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.confidence").value(1))
                .andExpect(jsonPath("$.bbox").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String objectId = mapper.readTree(body).get("objectId").asText();

        assertThat(projectStatus(project)).isEqualTo("ANALYZED");
        mvc.perform(get("/api/projects/{p}/analysis", project))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objects[0].objectId").value(objectId))
                .andExpect(jsonPath("$.objects[0].decision").value("KEEP"));
    }

    @Test
    void defaultsDecisionToUnsureAndRequiresAnImage() throws Exception {
        UUID withoutImage = project("CREATED");
        mvc.perform(post("/api/projects/{p}/objects", withoutImage).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"desk\"}"))
                .andExpect(status().isConflict());

        UUID project = project("ANALYZED");
        image(project);
        mvc.perform(post("/api/projects/{p}/objects", project).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"desk\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decision").value("UNSURE"));
        for (String invalid : new String[]{"{\"label\":\"  \"}", "{\"label\":\"" + "x".repeat(101) + "\"}", "{}"}) {
            mvc.perform(post("/api/projects/{p}/objects", project).contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void relabelsDetectedItemKeepingItsBoxAndDecision() throws Exception {
        UUID project = project("ANALYZED");
        UUID object = detected(project, image(project), "chair");
        jdbc.update("INSERT INTO furniture_decisions(object_id, decision) SELECT object_id, 'REPLACE' FROM detected_objects WHERE object_uuid = ?", object);

        mvc.perform(put("/api/projects/{p}/objects/{o}/label", project, object).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"armchair\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("armchair"))
                .andExpect(jsonPath("$.decision").value("REPLACE"))
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.bbox.x").value(0.1));
    }

    @Test
    void removesItemFromAnalysisAndDecisions() throws Exception {
        UUID project = project("ANALYZED");
        long image = image(project);
        UUID keep = detected(project, image, "bed");
        UUID wrong = detected(project, image, "toilet");

        mvc.perform(delete("/api/projects/{p}/objects/{o}", project, wrong)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/projects/{p}/objects/{o}", project, wrong)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/projects/{p}/objects/{o}", project, wrong).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"KEEP\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/projects/{p}/analysis", project))
                .andExpect(jsonPath("$.objects.length()").value(1))
                .andExpect(jsonPath("$.objects[0].objectId").value(keep.toString()));
    }

    @Test
    void cannotEditAnotherProjectsItem() throws Exception {
        UUID project = project("ANALYZED");
        UUID other = project("ANALYZED");
        UUID object = detected(other, image(other), "lamp");
        mvc.perform(put("/api/projects/{p}/objects/{o}/label", project, object).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"x\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/projects/{p}/objects/{o}", project, object)).andExpect(status().isNotFound());
    }

    private UUID project(String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO room_projects(project_id, user_id, status) VALUES (?, ?, ?)", id, TestUserSecurity.USER_ID, status);
        return id;
    }

    private long image(UUID project) {
        return jdbc.queryForObject("""
                INSERT INTO room_images(project_id, bucket, storage_key, original_filename, mime_type, file_size_bytes)
                VALUES (?, 'test-local', ?, 'room.jpg', 'image/jpeg', 1000) RETURNING image_id
                """, Long.class, project, UUID.randomUUID().toString());
    }

    private UUID detected(UUID project, long image, String label) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO detected_objects(object_uuid, project_id, image_id, object_class, confidence,
                    x_min, y_min, x_max, y_max, model_version)
                VALUES (?, ?, ?, ?, 0.9, 0.1, 0.1, 0.5, 0.5, 'test-model')
                """, id, project, image, label);
        return id;
    }

    private String projectStatus(UUID project) {
        return jdbc.queryForObject("SELECT status FROM room_projects WHERE project_id = ?", String.class, project);
    }
}
