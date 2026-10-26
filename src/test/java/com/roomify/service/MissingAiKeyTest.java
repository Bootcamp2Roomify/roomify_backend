package com.roomify.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomify.dto.design.DesignPlan;
import com.roomify.service.recommendation.RecommendationContractCodec;
import com.roomify.service.recommendation.RecommendationResultService;
import com.roomify.dto.recommendation.GenerationMetadata;
import com.roomify.storage.StorageService;
import com.roomify.support.TestUserSecurity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uses the real OpenAI provider beans with no usable key: every AI endpoint must answer with a clear
 * 503 AI_CONFIGURATION_ERROR, leave no half-written result behind and keep the project usable.
 */
@SpringBootTest(properties = {"ai.api-key=", "OPENAI_API_KEY=", "ai.demo-fallback=false"})
@AutoConfigureMockMvc
class MissingAiKeyTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired DesignPersistenceService designs;
    @Autowired RecommendationResultService recommendations;
    @Autowired RecommendationContractCodec codec;
    @Autowired com.roomify.config.AiProperties aiProperties;
    @MockitoBean StorageService storage;

    final List<UUID> projects = new ArrayList<>();
    UUID project;
    UUID bed;

    @BeforeEach
    void setUp() throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(32, 24, BufferedImage.TYPE_INT_RGB), "png", out);
        when(storage.download(anyString())).thenReturn(out.toByteArray());
        project = UUID.randomUUID();
        projects.add(project);
        jdbc.update("INSERT INTO room_projects(project_id, status, user_id) VALUES (?, 'PREFERENCES_READY', ?)",
                project, TestUserSecurity.USER_ID);
        jdbc.update("""
                INSERT INTO project_preferences(project_id, style, budget_amount, currency, room_purpose, rental_friendly)
                VALUES (?, 'MINIMALIST', 300, 'USD', 'STUDY', FALSE)
                """, project);
        Long image = jdbc.queryForObject("""
                INSERT INTO room_images(project_id, bucket, storage_key, original_filename, mime_type, file_size_bytes, width, height)
                VALUES (?, 'test', 'rooms/nokey.png', 'room.png', 'image/png', 100, 32, 24) RETURNING image_id
                """, Long.class, project);
        Long object = jdbc.queryForObject("""
                INSERT INTO detected_objects(project_id, image_id, object_class, confidence, x_min, y_min, x_max, y_max, model_version)
                VALUES (?, ?, 'bed', 0.9, 0.1, 0.1, 0.5, 0.5, 'cv-test') RETURNING object_id
                """, Long.class, project, image);
        jdbc.update("INSERT INTO furniture_decisions(object_id, decision) VALUES (?, 'KEEP')", object);
        bed = jdbc.queryForObject("SELECT object_uuid FROM detected_objects WHERE object_id = ?", UUID.class, object);
    }

    @AfterEach
    void cleanUp() {
        projects.forEach(id -> jdbc.update("DELETE FROM room_projects WHERE project_id = ?", id));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "your_openai_api_key", "  "})
    void blankOrPlaceholderKeysCountAsNotConfigured(String key) {
        var properties = new com.roomify.config.AiProperties();
        properties.setApiKey(key);
        assertThat(properties.hasUsableApiKey()).isFalse();
        properties.setApiKey("sk-proj-realistic-looking-key");
        assertThat(properties.hasUsableApiKey()).isTrue();
    }

    @Test
    void planGenerationExplainsTheMissingKeyAndCanBeRetried() throws Exception {
        assertThat(aiProperties.hasUsableApiKey()).isFalse();
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/api/projects/{p}/designs", project))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("AI_CONFIGURATION_ERROR"));
        }
        mvc.perform(get("/api/projects/{p}/designs/latest", project))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("AI_CONFIGURATION_ERROR"))
                .andExpect(jsonPath("$.plan").doesNotExist());
        // Nothing left pending, project still at its previous step.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM design_requests WHERE project_id = ? AND status = 'PENDING'", Integer.class, project)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM room_projects WHERE project_id = ?", String.class, project)).isEqualTo("PREFERENCES_READY");
        mvc.perform(get("/api/projects/{p}/preferences", project)).andExpect(status().isOk());
    }

    @Test
    void conceptSuggestionsAndDiyExplainTheMissingKey() throws Exception {
        Long designId = succeededDesign();

        mvc.perform(post("/api/projects/{p}/designs/{d}/prototype", project, designId))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_CONFIGURATION_ERROR"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM design_assets WHERE design_result_id = ?", Integer.class, designId)).isZero();

        mvc.perform(post("/api/projects/{p}/recommendations", project))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_CONFIGURATION_ERROR"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_sets WHERE project_id = ?", Integer.class, project)).isZero();

        var set = recommendations.storeSet(project, designId,
                codec.readSet(Files.readString(Path.of("contracts/recommendations/v1/examples/recommendation-set.json"))),
                new GenerationMetadata("fixture", "fixture", "1.0", null));
        UUID diyItem = set.items().stream().filter(i -> Boolean.TRUE.equals(i.diyEligible())).findFirst().orElseThrow().itemId();
        mvc.perform(post("/api/projects/{p}/recommendations/{i}/diy", project, diyItem))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_CONFIGURATION_ERROR"));

        // The saved plan is still readable: the page keeps working without AI.
        JsonNode latest = mapper.readTree(mvc.perform(get("/api/projects/{p}/designs/latest", project))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(latest.at("/status").asText()).isEqualTo("SUCCEEDED");
    }

    private Long succeededDesign() throws Exception {
        var started = designs.begin(project, "fixture", "fixture");
        DesignPlan plan = mapper.readValue("""
                {"planVersion":"1","summary":"Calm study","designStyle":"MINIMALIST",
                 "actions":[{"objectId":"%s","objectClass":"bed","decision":"KEEP","type":"KEEP",
                 "instruction":"Keep the bed","rationale":"Good condition"}],
                 "layoutNotes":[],"budgetAllocation":[{"category":"lighting","item":"Lamp","purpose":"Light","estimatedCost":40}],
                 "estimatedTotalCost":40,"currency":"USD","warnings":[],"itemsNeeded":[],
                 "rentalFriendlyWarnings":[],"constraints":[]}
                """.formatted(bed), DesignPlan.class);
        designs.succeed(started, plan);
        return started.designId();
    }
}
