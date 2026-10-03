package com.roomify.service;

import com.roomify.client.VisionServiceClient;
import com.roomify.dto.analysis.AnalyzeProjectResponse;
import com.roomify.entity.RoomImage;
import com.roomify.entity.RoomProject;
import com.roomify.entity.RoomProjectStatus;
import com.roomify.exception.VisionServiceException;
import com.roomify.repository.DetectedObjectRepository;
import com.roomify.repository.RoomImageRepository;
import com.roomify.repository.RoomProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomProjectServiceTest {

    @Mock
    private RoomProjectRepository projectRepository;

    @Mock
    private RoomImageRepository imageRepository;

    @Mock
    private DetectedObjectRepository detectedObjectRepository;

    @Mock
    private VisionServiceClient visionServiceClient;

    @InjectMocks
    private RoomProjectService service;

    @Test
    void analyzeProject_marksFailureWithoutDeletingExistingDetections() {
        UUID projectId = UUID.randomUUID();

        RoomProject project = new RoomProject();
        project.setStatus(RoomProjectStatus.IMAGE_UPLOADED);

        RoomImage image = new RoomImage(
                projectId,
                "test-local",
                "room-images/example.jpg",
                "example.jpg",
                "image/jpeg",
                2000L
        );

        VisionServiceException visionError = new VisionServiceException(
                "Vision service is unavailable. Please retry the analysis."
        );

        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(project));
        when(imageRepository.findTopByProjectIdOrderByCreatedAtDesc(projectId))
                .thenReturn(Optional.of(image));
        when(visionServiceClient.analyze(image)).thenThrow(visionError);

        assertThatThrownBy(() -> service.analyzeProject(projectId))
                .isSameAs(visionError);

        assertThat(project.getStatus())
                .isEqualTo(RoomProjectStatus.ANALYSIS_FAILED);

        verify(projectRepository).save(project);
        verify(detectedObjectRepository, never()).deleteByImageId(any());
        verify(detectedObjectRepository, never()).saveAll(any());
    }

    @Test
    void analyzeProject_whenAlreadyAnalyzed_returnsExistingResultsWithoutCallingVision() {
        UUID projectId = UUID.randomUUID();

        RoomProject project = new RoomProject();
        project.setStatus(RoomProjectStatus.IMAGE_UPLOADED);
        project.setStatus(RoomProjectStatus.ANALYZED);

        RoomImage image = new RoomImage(
                projectId,
                "test-local",
                "room-images/example.jpg",
                "example.jpg",
                "image/jpeg",
                2000L
        );

        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(project));
        when(imageRepository.findTopByProjectIdOrderByCreatedAtDesc(projectId))
                .thenReturn(Optional.of(image));
        when(detectedObjectRepository.findByImageIdOrderByIdAsc(image.getId()))
                .thenReturn(List.of());

        AnalyzeProjectResponse response = service.analyzeProject(projectId);

        assertThat(response.projectId()).isEqualTo(project.getId());
        assertThat(response.status()).isEqualTo(RoomProjectStatus.ANALYZED);
        assertThat(response.objects()).isEmpty();

        verify(visionServiceClient, never()).analyze(any());
        verify(detectedObjectRepository, never()).deleteByImageId(any());
        verify(detectedObjectRepository, never()).saveAll(any());
    }
}