package com.roomify.controller;

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
import static org.mockito.Mockito.when;
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

    private UUID createProject() {
        return jdbc.queryForObject(
            "INSERT INTO room_projects (project_id) VALUES (?) RETURNING project_id",
            UUID.class,
            UUID.randomUUID()
        );
    }

    private byte[] png(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
}
