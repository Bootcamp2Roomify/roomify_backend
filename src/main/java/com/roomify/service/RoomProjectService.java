package com.roomify.service;

import com.roomify.client.VisionServiceClient;
import com.roomify.dto.analysis.AnalyzeProjectResponse;
import com.roomify.dto.analysis.BoundingBoxResponse;
import com.roomify.dto.analysis.DetectedObjectResponse;
import com.roomify.dto.vision.VisionBoundingBox;
import com.roomify.dto.vision.VisionDetection;
import com.roomify.entity.DetectedObject;
import com.roomify.entity.FurnitureDecisionType;
import com.roomify.entity.RoomImage;
import com.roomify.entity.RoomProject;
import com.roomify.entity.RoomProjectStatus;
import com.roomify.exception.InvalidStateException;
import com.roomify.exception.NoActiveImageException;
import com.roomify.exception.VisionServiceException;
import com.roomify.repository.DetectedObjectRepository;
import com.roomify.repository.RoomImageRepository;
import com.roomify.repository.RoomProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class RoomProjectService {

    private final RoomProjectRepository repository;
    private final RoomImageRepository roomImageRepository;
    private final DetectedObjectRepository detectedObjectRepository;
    private final VisionServiceClient visionServiceClient;

    public RoomProjectService(
            RoomProjectRepository repository,
            RoomImageRepository roomImageRepository,
            DetectedObjectRepository detectedObjectRepository,
            VisionServiceClient visionServiceClient
    ) {
        this.repository = repository;
        this.roomImageRepository = roomImageRepository;
        this.detectedObjectRepository = detectedObjectRepository;
        this.visionServiceClient = visionServiceClient;
    }

    @Transactional
    public RoomProject createProject() {
        return repository.save(new RoomProject());
    }

    @Transactional(readOnly = true)
    public RoomProject getProject(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException(
                        "Room project not found: " + id));
    }

    @Transactional
    public RoomProject transitionTo(UUID id, RoomProjectStatus nextStatus) {
        RoomProject project = getProject(id);
        RoomProjectStatus currentStatus = project.getStatus();

        boolean allowed = switch (currentStatus) {
            case CREATED -> nextStatus == RoomProjectStatus.IMAGE_UPLOADED;
            case IMAGE_UPLOADED -> nextStatus == RoomProjectStatus.ANALYZED
                    || nextStatus == RoomProjectStatus.ANALYSIS_FAILED;
            case ANALYSIS_FAILED -> nextStatus == RoomProjectStatus.ANALYZED;
            case ANALYZED -> nextStatus == RoomProjectStatus.PREFERENCES_READY;
            case PREFERENCES_READY -> nextStatus == RoomProjectStatus.DESIGN_READY;
            case DESIGN_READY -> false;
        };

        if (!allowed) {
            throw new InvalidStateException(
                    "Cannot change project from " + currentStatus
                            + " to " + nextStatus);
        }

        project.setStatus(nextStatus);
        return repository.save(project);
    }

    @Transactional(noRollbackFor = VisionServiceException.class)
    public AnalyzeProjectResponse analyzeProject(UUID id) {
        RoomProject project = getProject(id);

        RoomImage activeImage = roomImageRepository
                .findTopByProjectIdOrderByCreatedAtDesc(id)
                .orElseThrow(() -> new NoActiveImageException(
                        "Project has no room image to analyze."
                ));

        // Repeat calls return existing data; vision-service is not called again.
        if (project.getStatus() == RoomProjectStatus.ANALYZED) {
            List<DetectedObject> existingObjects =
                    detectedObjectRepository.findByImageIdOrderByIdAsc(
                            activeImage.getId()
                    );

            return toAnalyzeProjectResponse(project, existingObjects);
        }

        validateAnalysisStatus(project);

        try {
            List<DetectedObject> detectedObjects = toDetectedObjects(
                    activeImage,
                    visionServiceClient.analyze(activeImage).detections()
            );

            List<DetectedObject> savedObjects =
                    detectedObjectRepository.saveAll(detectedObjects);

            project.setStatus(RoomProjectStatus.ANALYZED);
            repository.save(project);

            return toAnalyzeProjectResponse(project, savedObjects);
        } catch (VisionServiceException exception) {
            project.setStatus(RoomProjectStatus.ANALYSIS_FAILED);
            repository.save(project);
            throw exception;
        }
    }

    private void validateAnalysisStatus(RoomProject project) {
        RoomProjectStatus status = project.getStatus();

        if (status != RoomProjectStatus.IMAGE_UPLOADED
                && status != RoomProjectStatus.ANALYSIS_FAILED) {
            throw new InvalidStateException(
                    "Project must have an uploaded image before analysis."
            );
        }
    }

    private AnalyzeProjectResponse toAnalyzeProjectResponse(
            RoomProject project,
            List<DetectedObject> detectedObjects
    ) {
        List<DetectedObjectResponse> objects = detectedObjects.stream()
                .map(this::toDetectedObjectResponse)
                .toList();

        return new AnalyzeProjectResponse(
                project.getId(),
                project.getStatus(),
                objects
        );
    }

    private DetectedObjectResponse toDetectedObjectResponse(
            DetectedObject detectedObject
    ) {
        return new DetectedObjectResponse(
                detectedObject.getId(),
                detectedObject.getObjectClass(),
                detectedObject.getConfidence(),
                new BoundingBoxResponse(
                        detectedObject.getXMin(),
                        detectedObject.getYMin(),
                        detectedObject.getXMax(),
                        detectedObject.getYMax()
                ),
                FurnitureDecisionType.UNSURE
        );
    }

    private List<DetectedObject> toDetectedObjects(
            RoomImage image,
            List<VisionDetection> detections
    ) {
        if (detections == null) {
            return List.of();
        }

        return detections.stream()
                .map(detection -> toDetectedObject(image, detection))
                .toList();
    }

    private DetectedObject toDetectedObject(
            RoomImage image,
            VisionDetection detection
    ) {
        VisionBoundingBox boundingBox = detection.boundingBox();

        Integer xMin = boundingBox == null ? null : boundingBox.x();
        Integer yMin = boundingBox == null ? null : boundingBox.y();
        Integer xMax = boundingBox == null
                ? null
                : boundingBox.x() + boundingBox.width();
        Integer yMax = boundingBox == null
                ? null
                : boundingBox.y() + boundingBox.height();

        return new DetectedObject(
                image.getId(),
                detection.label(),
                detection.confidence(),
                normalizeCoordinate(xMin, image.getWidth()),
                normalizeCoordinate(yMin, image.getHeight()),
                normalizeCoordinate(xMax, image.getWidth()),
                normalizeCoordinate(yMax, image.getHeight())
        );
    }

    private BigDecimal normalizeCoordinate(Integer value, Integer dimension) {
        if (value == null || dimension == null || dimension <= 0) {
            return null;
        }

        BigDecimal normalized = BigDecimal.valueOf(value)
                .divide(BigDecimal.valueOf(dimension), 6, RoundingMode.HALF_UP);

        return normalized.max(BigDecimal.ZERO).min(BigDecimal.ONE);
    }
}