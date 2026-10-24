package com.roomify.service;

import com.roomify.entity.RoomImage;
import com.roomify.entity.RoomProject;
import com.roomify.entity.RoomProjectStatus;
import com.roomify.repository.RoomImageRepository;
import com.roomify.repository.DetectedObjectRepository;
import com.roomify.repository.RoomProjectRepository;
import com.roomify.storage.RoomImageStoragePolicy;
import com.roomify.storage.StorageService;
import com.roomify.storage.StoredObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomImageServiceTest {
    @Mock RoomProjectRepository projects;
    @Mock RoomImageRepository images;
    @Mock DetectedObjectRepository detections;
    @Mock StorageService storage;
    @Mock PlatformTransactionManager transactionManager;
    @Spy RoomImageStoragePolicy policy = new RoomImageStoragePolicy(10485760);
    @InjectMocks RoomImageService service;

    @Test
    void replacementRetainsBothFilesEvenWhenDeletionWouldFail() throws Exception {
        RoomProject project = new RoomProject();
        project.setStatus(RoomProjectStatus.ANALYZED);
        UUID id = project.getId();
        RoomImage old = new RoomImage(id, "test", "old.png", "old.png", "image/png", 100);
        when(projects.existsById(id)).thenReturn(true);
        when(projects.findByIdForUpdate(id)).thenReturn(Optional.of(project));
        when(images.findByProjectId(id)).thenReturn(Optional.of(old));
        when(storage.upload(anyString(), anyString(), anyLong(), any()))
            .thenReturn(new StoredObject("test", "new.png"));
        when(images.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        lenient().doThrow(new IllegalStateException("cleanup failed")).when(storage).delete(anyString());

        RoomImage saved = service.upload(id, png());

        assertThat(saved.getStorageKey()).isEqualTo("new.png");
        assertThat(saved.getWidth()).isEqualTo(40);
        assertThat(project.getStatus()).isEqualTo(RoomProjectStatus.IMAGE_UPLOADED);
        var order = inOrder(projects, images, detections);
        order.verify(projects).findByIdForUpdate(id);
        order.verify(images).findByProjectId(id);
        order.verify(images).saveAndFlush(any());
        order.verify(detections).deactivateActiveByProjectId(id);
        order.verify(projects).save(project);
        verify(storage, never()).delete(anyString());
    }

    @Test
    void storageFailureDoesNotWriteMetadataOrStatus() throws Exception {
        RoomProject project = new RoomProject();
        project.setStatus(RoomProjectStatus.ANALYZED);
        when(projects.existsById(project.getId())).thenReturn(true);
        when(storage.upload(anyString(), anyString(), anyLong(), any()))
            .thenThrow(new IllegalStateException("upload failed"));

        assertThatThrownBy(() -> service.upload(project.getId(), png()))
            .hasMessage("upload failed");

        assertThat(project.getStatus()).isEqualTo(RoomProjectStatus.ANALYZED);
        verify(images, never()).saveAndFlush(any());
        verify(projects, never()).save(any());
        verify(storage, never()).delete(anyString());
    }

    private MockMultipartFile png() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "png", out);
        return new MockMultipartFile("file", "room.png", "image/png", out.toByteArray());
    }
}
