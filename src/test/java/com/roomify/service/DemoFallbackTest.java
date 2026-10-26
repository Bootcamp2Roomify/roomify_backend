package com.roomify.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomify.ai.AiDesignProvider;
import com.roomify.ai.AiProviderConfigurationException;
import com.roomify.ai.AiProviderQuotaExhaustedException;
import com.roomify.ai.AiProviderRetryableException;
import com.roomify.ai.DesignImageProvider;
import com.roomify.config.AiProperties;
import com.roomify.storage.StorageService;
import com.roomify.storage.StoredObject;
import com.roomify.support.TestUserSecurity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI_DEMO_MODE answers every AI feature from the demo library without calling the provider; with the default
 * AI_DEMO_FALLBACK a failing provider (bad key, no credit, timeout, any other error) gets the same demo answer.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DemoFallbackTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired AiProperties properties;
    @MockitoBean AiDesignProvider textProvider;
    @MockitoBean DesignImageProvider imageProvider;
    @MockitoBean StorageService storage;

    final List<UUID> projects = new ArrayList<>();
    final java.util.Map<String, byte[]> stored = new java.util.HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(32, 24, BufferedImage.TYPE_INT_RGB), "png", out);
        when(storage.upload(anyString(), anyString(), anyLong(), any())).thenAnswer(call -> {
            stored.put(call.getArgument(0), ((java.io.InputStream) call.getArgument(3)).readAllBytes());
            return new StoredObject("test", call.getArgument(0));
        });
        when(storage.download(anyString())).thenAnswer(call -> stored.getOrDefault(call.getArgument(0), out.toByteArray()));
    }

    @AfterEach
    void cleanUp() {
        properties.setDemoMode(false);
        projects.forEach(id -> jdbc.update("DELETE FROM room_projects WHERE project_id = ?", id));
    }

    @ParameterizedTest
    @CsvSource({"LIVING,living-room", "SLEEP,bedroom", "STUDY,home-office", "MULTIPURPOSE,student-room"})
    void demoModeServesEveryFeatureWithoutCallingTheProvider(String purpose, String key) throws Exception {
        properties.setDemoMode(true);
        UUID project = project(purpose, false);

        JsonNode design = json(mvc.perform(post("/api/projects/{p}/designs", project)).andExpect(status().is2xxSuccessful()));
        assertThat(design.at("/status").asText()).isEqualTo("SUCCEEDED");
        assertThat(design.at("/source").asText()).isEqualTo("demo");
        assertThat(design.at("/provider").asText()).isEqualTo("demo");
        assertThat(design.at("/modelName").asText()).isEqualTo("roomify-demo-" + key);

        long designId = design.at("/designId").asLong();
        JsonNode image = json(mvc.perform(post("/api/projects/{p}/designs/{d}/prototype", project, designId))
                .andExpect(status().is2xxSuccessful()));
        assertThat(image.at("/status").asText()).isEqualTo("SUCCEEDED");
        assertThat(image.at("/source").asText()).isEqualTo("demo");
        byte[] content = mvc.perform(get("/api/projects/{p}/designs/{d}/image/content", project, designId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(content).isEqualTo(new ClassPathResource("demo/" + key + ".jpg").getContentAsByteArray());

        JsonNode set = json(mvc.perform(post("/api/projects/{p}/recommendations", project)).andExpect(status().is2xxSuccessful()));
        assertThat(set.at("/source").asText()).isEqualTo("demo");
        assertThat(set.at("/items").size()).isBetween(3, 6);

        String diyItem = null;
        for (JsonNode item : set.at("/items")) if (item.at("/diyEligible").asBoolean()) diyItem = item.at("/itemId").asText();
        JsonNode guide = json(mvc.perform(post("/api/projects/{p}/recommendations/{i}/diy", project, diyItem))
                .andExpect(status().is2xxSuccessful()));
        assertThat(guide.at("/source").asText()).isEqualTo("demo");

        verifyNoInteractions(textProvider, imageProvider);
    }

    @Test
    void failingProviderFallsBackForEveryFeature() throws Exception {
        UUID project = project("SLEEP", true);
        when(textProvider.generateJson(anyString(), anyString())).thenThrow(new AiProviderQuotaExhaustedException("AI quota exhausted."));
        when(imageProvider.generate(any(), anyString(), anyString(), anyString()))
                .thenThrow(new AiProviderConfigurationException("OpenAI rejected the configured API key."));

        JsonNode design = json(mvc.perform(post("/api/projects/{p}/designs", project)).andExpect(status().is2xxSuccessful()));
        assertThat(design.at("/source").asText()).isEqualTo("demo");
        assertThat(design.at("/plan/rentalFriendlyWarnings").size()).isPositive();
        assertThat(jdbc.queryForObject("SELECT status FROM room_projects WHERE project_id = ?", String.class, project))
                .isEqualTo("DESIGN_READY");

        JsonNode image = json(mvc.perform(post("/api/projects/{p}/designs/{d}/prototype", project, design.at("/designId").asLong()))
                .andExpect(status().is2xxSuccessful()));
        assertThat(image.at("/source").asText()).isEqualTo("demo");
        assertThat(jdbc.queryForObject("SELECT source FROM design_assets WHERE design_result_id = ?", String.class,
                design.at("/designId").asLong())).isEqualTo("demo");

        JsonNode set = json(mvc.perform(post("/api/projects/{p}/recommendations", project)).andExpect(status().is2xxSuccessful()));
        assertThat(set.at("/source").asText()).isEqualTo("demo");
        assertThat(set.at("/metadata/model").asText()).isEqualTo("roomify-demo-bedroom");
    }

    @Test
    void timeoutsAndUnexpectedErrorsAlsoFallBack() throws Exception {
        UUID project = project("STUDY", false);
        when(textProvider.generateJson(anyString(), anyString()))
                .thenThrow(new AiProviderRetryableException("OpenAI request timed out or could not reach the provider.", null));
        JsonNode design = json(mvc.perform(post("/api/projects/{p}/designs", project)).andExpect(status().is2xxSuccessful()));
        assertThat(design.at("/source").asText()).isEqualTo("demo");

        when(imageProvider.generate(any(), anyString(), anyString(), anyString())).thenThrow(new IllegalStateException("boom"));
        JsonNode image = json(mvc.perform(post("/api/projects/{p}/designs/{d}/prototype", project, design.at("/designId").asLong()))
                .andExpect(status().is2xxSuccessful()));
        assertThat(image.at("/source").asText()).isEqualTo("demo");
    }

    @Test
    void invalidModelOutputFallsBackAfterTheCorrectiveRetry() throws Exception {
        UUID project = project("LIVING", false);
        when(textProvider.generateJson(anyString(), anyString())).thenReturn("{\"not\":\"a plan\"}");
        JsonNode design = json(mvc.perform(post("/api/projects/{p}/designs", project)).andExpect(status().is2xxSuccessful()));
        assertThat(design.at("/source").asText()).isEqualTo("demo");
        org.mockito.Mockito.verify(textProvider, org.mockito.Mockito.times(2)).generateJson(anyString(), anyString());
    }

    private UUID project(String purpose, boolean rental) {
        UUID project = UUID.randomUUID();
        projects.add(project);
        jdbc.update("INSERT INTO room_projects(project_id, status, user_id) VALUES (?, 'PREFERENCES_READY', ?)",
                project, TestUserSecurity.USER_ID);
        jdbc.update("""
                INSERT INTO project_preferences(project_id, style, budget_amount, currency, room_purpose, rental_friendly)
                VALUES (?, 'SCANDINAVIAN', 300, 'USD', ?, ?)
                """, project, purpose, rental);
        Long image = jdbc.queryForObject("""
                INSERT INTO room_images(project_id, bucket, storage_key, original_filename, mime_type, file_size_bytes, width, height)
                VALUES (?, 'test', 'rooms/demo.png', 'room.png', 'image/png', 100, 32, 24) RETURNING image_id
                """, Long.class, project);
        Long bed = jdbc.queryForObject("""
                INSERT INTO detected_objects(project_id, image_id, object_class, confidence, x_min, y_min, x_max, y_max, model_version)
                VALUES (?, ?, 'bed', 0.9, 0.1, 0.1, 0.5, 0.5, 'cv-test') RETURNING object_id
                """, Long.class, project, image);
        Long chair = jdbc.queryForObject("""
                INSERT INTO detected_objects(project_id, image_id, object_class, confidence, x_min, y_min, x_max, y_max, model_version)
                VALUES (?, ?, 'chair', 0.5, 0.6, 0.6, 0.8, 0.9, 'cv-test') RETURNING object_id
                """, Long.class, project, image);
        jdbc.update("INSERT INTO furniture_decisions(object_id, decision) VALUES (?, 'KEEP')", bed);
        jdbc.update("INSERT INTO furniture_decisions(object_id, decision) VALUES (?, 'REPLACE')", chair);
        return project;
    }

    private JsonNode json(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
