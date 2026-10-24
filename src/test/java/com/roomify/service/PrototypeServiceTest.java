package com.roomify.service;

import com.roomify.ai.AiDesignProvider;
import com.roomify.ai.AiProviderRetryableException;
import com.roomify.ai.DesignImageProvider;
import com.roomify.ai.GeneratedDesignImage;
import com.roomify.storage.StorageService;
import com.roomify.storage.StoredObject;
import com.roomify.support.TestUserSecurity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** ROOM-93/95: generate, store and serve the visual prototype for a saved design. */
@SpringBootTest
@AutoConfigureMockMvc
class PrototypeServiceTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired DesignService designs;
    @Autowired MockMvc mvc;
    @MockitoBean AiDesignProvider textProvider;
    @MockitoBean DesignImageProvider imageProvider;
    @MockitoBean StorageService storage;

    final List<UUID> projects = new ArrayList<>();
    UUID project;
    UUID bed;
    byte[] original;
    byte[] rendered;

    @BeforeEach
    void setUp() throws Exception {
        original = png(64, 48);
        rendered = png(32, 32);
        when(textProvider.getModelName()).thenReturn("test-model");
        when(imageProvider.getModelName()).thenReturn("test-image-model");
        when(storage.download("rooms/original/proto.png")).thenReturn(original);
        when(storage.upload(anyString(), eq("image/png"), anyLong(), any()))
                .thenAnswer(call -> new StoredObject("bucket", call.getArgument(0)));
        project = UUID.randomUUID();
        projects.add(project);
        jdbc.update("INSERT INTO room_projects(project_id, status, user_id) VALUES (?, 'PREFERENCES_READY', ?)",
                project, TestUserSecurity.USER_ID);
        jdbc.update("""
                INSERT INTO project_preferences(project_id, style, budget_amount, currency, room_purpose, rental_friendly,
                    preferred_colors)
                VALUES (?, 'SCANDINAVIAN', 300, 'USD', 'STUDY_AND_SLEEP', TRUE, '["sage","cream"]'::jsonb)
                """, project);
        Long image = jdbc.queryForObject("""
                INSERT INTO room_images(project_id, bucket, storage_key, original_filename, mime_type, file_size_bytes, width, height)
                VALUES (?, 'test', 'rooms/original/proto.png', 'room.png', 'image/png', 100, 64, 48) RETURNING image_id
                """, Long.class, project);
        Long objectId = jdbc.queryForObject("""
                INSERT INTO detected_objects(project_id, image_id, object_class, confidence, x_min, y_min, x_max, y_max, model_version)
                VALUES (?, ?, 'bed', 0.9, 0.1, 0.1, 0.5, 0.5, 'cv-test') RETURNING object_id
                """, Long.class, project, image);
        jdbc.update("INSERT INTO furniture_decisions(object_id, decision) VALUES (?, 'KEEP')", objectId);
        bed = jdbc.queryForObject("SELECT object_uuid FROM detected_objects WHERE object_id = ?", UUID.class, objectId);
    }

    @AfterEach
    void cleanUp() {
        projects.forEach(id -> jdbc.update("DELETE FROM room_projects WHERE project_id = ?", id));
    }

    @Test
    void generatesStoresAndServesPrototypeOnceFromTheOriginalPhoto() throws Exception {
        Long design = design();
        when(imageProvider.generate(any(), anyString(), anyString(), anyString()))
                .thenReturn(new GeneratedDesignImage(rendered, "image/png"));

        mvc.perform(post("/api/projects/{p}/designs/{d}/prototype", project, design))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.width").value(32));

        ArgumentCaptor<byte[]> sent = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(imageProvider).generate(sent.capture(), eq("room.png"), eq("image/png"), prompt.capture());
        assertThat(sent.getValue()).isEqualTo(original);
        assertThat(prompt.getValue())
                .contains("Keep the same camera position")
                .contains("Keep the existing bed exactly where it is")
                .contains("sage, cream")
                .contains("rented room")
                .contains("Freestanding shelf");

        // A second request reuses the stored prototype instead of paying for another image.
        mvc.perform(post("/api/projects/{p}/designs/{d}/prototype", project, design)).andExpect(status().isCreated());
        verify(imageProvider, times(1)).generate(any(), anyString(), anyString(), anyString());

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).upload(key.capture(), eq("image/png"), eq((long) rendered.length), any());
        when(storage.download(key.getValue())).thenReturn(rendered);
        mvc.perform(get("/api/projects/{p}/designs/{d}/image/content", project, design))
                .andExpect(status().isOk())
                .andExpect(content().bytes(rendered));
    }

    @Test
    void providerOutageLeavesNoAssetAndCanBeRetried() throws Exception {
        Long design = design();
        when(imageProvider.generate(any(), anyString(), anyString(), anyString()))
                .thenThrow(new AiProviderRetryableException("down", null))
                .thenReturn(new GeneratedDesignImage(rendered, "image/png"));

        mvc.perform(post("/api/projects/{p}/designs/{d}/prototype", project, design))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_PROVIDER_UNAVAILABLE"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM design_assets WHERE design_result_id = ?", Integer.class, design))
                .isZero();

        mvc.perform(post("/api/projects/{p}/designs/{d}/prototype", project, design))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }

    @Test
    void rejectsMissingAndFailedDesigns() throws Exception {
        mvc.perform(post("/api/projects/{p}/designs/{d}/prototype", project, 987654321L))
                .andExpect(status().isNotFound());

        when(textProvider.generateJson(anyString(), anyString())).thenReturn("not json");
        Long failed = null;
        try {
            designs.generate(project);
        } catch (RuntimeException expected) {
            failed = jdbc.queryForObject("SELECT design_id FROM design_results WHERE project_id = ? AND status = 'FAILED'",
                    Long.class, project);
        }
        mvc.perform(post("/api/projects/{p}/designs/{d}/prototype", project, failed))
                .andExpect(status().isConflict());
        verifyNoInteractions(imageProvider);
    }

    private Long design() {
        when(textProvider.generateJson(anyString(), anyString())).thenReturn("""
                {"planVersion":"1","summary":"A calm study bedroom","designStyle":"SCANDINAVIAN",
                 "actions":[{"objectId":"%s","objectClass":"bed","decision":"KEEP","type":"KEEP",
                 "instruction":"Keep the bed against the wall","rationale":"It is in good condition"},
                 {"objectId":null,"objectClass":"shelf","decision":null,"type":"ADD",
                 "instruction":"a freestanding oak shelf beside the bed","rationale":"Adds storage"}],
                 "layoutNotes":["Verify measurements before buying"],
                 "budgetAllocation":[{"category":"storage","item":"Freestanding shelf","purpose":"Storage","estimatedCost":120}],
                 "estimatedTotalCost":120,"currency":"USD","warnings":[],
                 "itemsNeeded":[{"objectId":null,"name":"Freestanding shelf","quantity":1}],
                 "rentalFriendlyWarnings":["Check your lease"],"constraints":["No drilling"]}
                """.formatted(bed));
        return designs.generate(project).designId();
    }

    private static byte[] png(int width, int height) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
}
