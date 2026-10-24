package com.roomify.controller;

import com.roomify.support.TestUserSecurity;
import com.roomify.entity.RoomProject;
import com.roomify.entity.RoomProjectStatus;
import com.roomify.client.VisionServiceClient;
import com.roomify.dto.AnalysisInput;
import com.roomify.dto.DetectionInput;
import com.roomify.dto.BoundingBoxInput;
import com.roomify.service.RoomProjectService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import com.roomify.repository.RoomProjectRepository;
import com.roomify.service.RoomImageService;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import com.roomify.storage.StorageService;
import com.roomify.storage.StoredObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoomImageControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    StorageService storageService;

    @Autowired
    RoomImageService imageService;
    @Autowired
    RoomProjectService projectService;
    @Autowired
    EntityManager entityManager;
    @Autowired
    PlatformTransactionManager transactionManager;
    @MockitoBean
    VisionServiceClient visionServiceClient;

    @MockitoSpyBean
    RoomProjectRepository projects;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failedStatusWriteRollsBackImageMetadataAndKeepsOriginalDownload() throws Exception {
        UUID projectId = createProject();
        jdbc.update("UPDATE room_projects SET status = 'ANALYZED' WHERE project_id = ?", projectId);
        jdbc.update("""
            INSERT INTO room_images (project_id, bucket, storage_key, original_filename,
                mime_type, file_size_bytes, width, height)
            VALUES (?, 'test', 'original.png', 'original.png', 'image/png', 100, 40, 30)
            """, projectId);
        Long oldObject = createDetection(projectId);
        byte[] original = png(40, 30);
        when(storageService.download("original.png")).thenReturn(original);
        when(storageService.upload(anyString(), anyString(), anyLong(), any()))
            .thenReturn(new StoredObject("test", "replacement.png"));
        doThrow(new IllegalStateException("status write failed"))
            .when(projects).save(org.mockito.ArgumentMatchers.<RoomProject>argThat(p -> p.getId().equals(projectId)));

        assertThatThrownBy(() -> imageService.upload(projectId,
            new MockMultipartFile("file", "new.png", "image/png", png(80, 60))))
            .hasMessage("status write failed");

        assertThat(jdbc.queryForObject("SELECT storage_key FROM room_images WHERE project_id = ?",
            String.class, projectId)).isEqualTo("original.png");
        assertThat(jdbc.queryForObject("SELECT status FROM room_projects WHERE project_id = ?",
            String.class, projectId)).isEqualTo("ANALYZED");
        assertThat(jdbc.queryForObject("SELECT active FROM detected_objects WHERE object_id = ?",
            Boolean.class, oldObject)).isTrue();
        mockMvc.perform(get("/api/projects/{id}/image/content", projectId))
            .andExpect(status().isOk()).andExpect(content().bytes(original));
        verify(storageService, never()).delete(anyString());
    }

    @Test
    void returnsActiveImageWithDimensionsAndContentUrl() throws Exception {
        UUID projectId = createProject();
        byte[] png = png(40, 30);
        when(storageService.upload(anyString(), anyString(), anyLong(), any()))
            .thenAnswer(invocation -> new StoredObject("test-bucket", invocation.getArgument(0)));
        when(storageService.download(anyString())).thenReturn(png);

        mockMvc.perform(multipart("/api/projects/{id}/image", projectId)
                .file(new MockMultipartFile("file", "room.png", "image/png", png)))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/projects/{id}/image", projectId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectId").value(projectId.toString()))
            .andExpect(jsonPath("$.imageId").isNumber())
            .andExpect(jsonPath("$.contentType").value("image/png"))
            .andExpect(jsonPath("$.width").value(40))
            .andExpect(jsonPath("$.height").value(30))
            .andExpect(jsonPath("$.imageUrl")
                .value("http://localhost/api/projects/" + projectId + "/image/content"));

        mockMvc.perform(get("/api/projects/{id}/image/content", projectId))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/png"))
            .andExpect(content().bytes(png));
    }

    @Test
    void backfillsDimensionsForImagesStoredWithoutThem() throws Exception {
        UUID projectId = createProject();
        jdbc.update("""
            INSERT INTO room_images (
                project_id, bucket, storage_key, original_filename, mime_type, file_size_bytes
            )
            VALUES (?, 'test-bucket', 'rooms/legacy.png', 'legacy.png', 'image/png', 100)
            """,
            projectId
        );
        when(storageService.download("rooms/legacy.png")).thenReturn(png(64, 48));

        mockMvc.perform(get("/api/projects/{id}/image", projectId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.width").value(64))
            .andExpect(jsonPath("$.height").value(48));
    }

    @Test
    void rejectsUploadThatIsNotADecodableImage() throws Exception {
        UUID projectId = createProject();

        mockMvc.perform(multipart("/api/projects/{id}/image", projectId)
                .file(new MockMultipartFile("file", "room.jpg", "image/jpeg", "not an image".getBytes())))
            .andExpect(status().isBadRequest());
    }

    @Test
    void returnsConflictWhenProjectHasNoImage() throws Exception {
        UUID projectId = createProject();

        mockMvc.perform(get("/api/projects/{id}/image", projectId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("NO_ACTIVE_IMAGE"));
    }

    @Test
    void returnsNotFoundForUnknownProject() throws Exception {
        mockMvc.perform(get("/api/projects/{id}/image", UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }

    @ParameterizedTest
    @EnumSource(value = RoomProjectStatus.class, names = {
        "ANALYZED", "ANALYSIS_FAILED", "PREFERENCES_READY", "DESIGN_READY"
    })
    void replacementResetsStateAndHidesOldDetectionsWithoutRemovingHistory(RoomProjectStatus previous) throws Exception {
        UUID project = seedImageProject(previous);
        Long oldObject = createDetection(project);
        jdbc.update("INSERT INTO furniture_decisions(object_id, decision) VALUES (?, 'KEEP')", oldObject);
        jdbc.update("""
            INSERT INTO project_preferences(project_id, style, budget_amount, currency, preferred_colors,
                room_purpose, rental_friendly, special_requirements)
            VALUES (?, 'MODERN', 100, 'USD', '[]'::jsonb, 'Bedroom', false, '')
            """, project);
        stubUpload();

        mockMvc.perform(multipart("/api/projects/{id}/image", project)
                .file(new MockMultipartFile("file", "new.png", "image/png", png(80, 60))))
            .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();

        assertThat(jdbc.queryForObject("SELECT status FROM room_projects WHERE project_id = ?",
            String.class, project)).isEqualTo("IMAGE_UPLOADED");
        mockMvc.perform(get("/api/projects/{id}/analysis", project))
            .andExpect(status().isOk()).andExpect(jsonPath("$.objects").isEmpty());
        assertThat(jdbc.queryForObject("SELECT active FROM detected_objects WHERE object_id = ?",
            Boolean.class, oldObject)).isFalse();
        assertThat(jdbc.queryForObject("SELECT decision FROM furniture_decisions WHERE object_id = ?",
            String.class, oldObject)).isEqualTo("KEEP");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project_preferences WHERE project_id = ?",
            Integer.class, project)).isEqualTo(1);
        verify(storageService, never()).delete(anyString());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failedStorageUploadLeavesPersistedImageStateAndDetectionsUntouched() throws Exception {
        UUID project = seedImageProject(RoomProjectStatus.DESIGN_READY);
        Long object = createDetection(project);
        when(storageService.upload(anyString(), anyString(), anyLong(), any()))
            .thenThrow(new IllegalStateException("storage unavailable"));
        assertThatThrownBy(() -> imageService.upload(project,
            new MockMultipartFile("file", "new.png", "image/png", png(80, 60))))
            .hasMessage("storage unavailable");
        assertThat(jdbc.queryForObject("SELECT storage_key FROM room_images WHERE project_id = ?",
            String.class, project)).isEqualTo("original.png");
        assertThat(jdbc.queryForObject("SELECT status FROM room_projects WHERE project_id = ?",
            String.class, project)).isEqualTo("DESIGN_READY");
        assertThat(jdbc.queryForObject("SELECT active FROM detected_objects WHERE object_id = ?",
            Boolean.class, object)).isTrue();
        verify(storageService, never()).delete(anyString());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void analysisAndReplacementSerializeForBothOrderings(boolean analysisFirst) throws Exception {
        UUID project = seedImageProject(RoomProjectStatus.IMAGE_UPLOADED);
        stubUpload();
        when(visionServiceClient.analyze(any())).thenAnswer(call -> {
            com.roomify.entity.RoomImage image = call.getArgument(0);
            return new AnalysisInput(image.getStorageKey(), List.of(
                new DetectionInput("chair", .9, new BoundingBoxInput(.1, .1, .2, .2))));
        });
        var firstReady = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        String session = "room-workflow-" + UUID.randomUUID();
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> new TransactionTemplate(transactionManager).execute(tx -> {
                runOperation(project, analysisFirst);
                entityManager.flush();
                firstReady.countDown();
                await(releaseFirst);
                return null;
            }));
            assertThat(firstReady.await(10, TimeUnit.SECONDS)).as("first transaction ready").isTrue();
            Future<?> second = executor.submit(() -> new TransactionTemplate(transactionManager).execute(tx -> {
                jdbc.queryForObject("SELECT set_config('application_name', ?, true)", String.class, session);
                secondStarted.countDown();
                runOperation(project, !analysisFirst);
                return null;
            }));
            assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
            try {
                assertThat(awaitDatabaseBlock(session, second)).as("second transaction waits for first").isTrue();
            } finally {
                releaseFirst.countDown();
            }
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT storage_key FROM room_images WHERE project_id = ?",
                String.class, project)).isEqualTo("replacement.png");
            assertThat(jdbc.queryForObject("SELECT status FROM room_projects WHERE project_id = ?",
                String.class, project)).isEqualTo(analysisFirst ? "IMAGE_UPLOADED" : "ANALYZED");
            List<String> models = jdbc.queryForList(
                "SELECT model_version FROM detected_objects WHERE project_id = ? AND active = true",
                String.class, project);
            if (analysisFirst) {
                assertThat(models).isEmpty();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM detected_objects WHERE project_id = ?",
                    Integer.class, project)).isEqualTo(1);
            } else {
                assertThat(models).containsExactly("replacement.png");
            }
            verify(storageService, never()).delete(anyString());
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private boolean awaitDatabaseBlock(String session, Future<?> second) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline && !second.isDone()) {
            Boolean blocked = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pg_stat_activity
                    WHERE application_name = ? AND cardinality(pg_blocking_pids(pid)) > 0)
                """, Boolean.class, session);
            if (Boolean.TRUE.equals(blocked)) return true;
            Thread.yield();
        }
        return false;
    }

    private void runOperation(UUID project, boolean analyze) {
        if (analyze) {
            projectService.analyzeProject(project);
        } else {
            try {
                imageService.upload(project,
                    new MockMultipartFile("file", "new.png", "image/png", png(80, 60)));
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("latch timeout");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private void stubUpload() {
        when(storageService.upload(anyString(), anyString(), anyLong(), any()))
            .thenReturn(new StoredObject("test", "replacement.png"));
    }

    private UUID seedImageProject(RoomProjectStatus status) {
        UUID project = createProject();
        jdbc.update("UPDATE room_projects SET status = ? WHERE project_id = ?", status.name(), project);
        jdbc.update("""
            INSERT INTO room_images (project_id, bucket, storage_key, original_filename,
                mime_type, file_size_bytes, width, height)
            VALUES (?, 'test', 'original.png', 'original.png', 'image/png', 100, 40, 30)
            """, project);
        return project;
    }

    private Long createDetection(UUID project) {
        return jdbc.queryForObject("""
            INSERT INTO detected_objects(project_id, image_id, object_class, confidence,
                x_min, y_min, x_max, y_max, model_version, active)
            SELECT ?, image_id, 'old chair', .9, .1, .1, .3, .3, 'old-model', true
            FROM room_images WHERE project_id = ? RETURNING object_id
            """, Long.class, project, project);
    }

    private UUID createProject() {
        return jdbc.queryForObject(
            "INSERT INTO room_projects (project_id, user_id) VALUES (?, ?) RETURNING project_id",
            UUID.class,
            UUID.randomUUID(),
            TestUserSecurity.USER_ID
        );
    }

    private byte[] png(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
}
