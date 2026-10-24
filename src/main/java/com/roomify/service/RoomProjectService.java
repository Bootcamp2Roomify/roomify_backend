package com.roomify.service;

import com.roomify.client.VisionServiceClient;
import com.roomify.dto.CreateProjectRequest;
import com.roomify.dto.ProjectListResponse;
import com.roomify.dto.ProjectSummaryResponse;
import com.roomify.repository.ProjectSummaryRepository;
import com.roomify.dto.analysis.AnalyzeProjectResponse;
import com.roomify.dto.analysis.BoundingBoxResponse;
import com.roomify.dto.analysis.DetectedObjectResponse;
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

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class RoomProjectService {

    private final RoomProjectRepository repository;
    private final RoomImageRepository roomImageRepository;
    private final DetectedObjectRepository detectedObjectRepository;
    private final VisionServiceClient visionServiceClient;
    private final DetectionPersistenceService detectionPersistenceService;
    private final ProjectSummaryRepository summaries;

    public RoomProjectService(
            RoomProjectRepository repository,
            RoomImageRepository roomImageRepository,
            DetectedObjectRepository detectedObjectRepository,
            VisionServiceClient visionServiceClient,
            DetectionPersistenceService detectionPersistenceService,
            ProjectSummaryRepository summaries
    ) {
        this.repository = repository;
        this.roomImageRepository = roomImageRepository;
        this.detectedObjectRepository = detectedObjectRepository;
        this.visionServiceClient = visionServiceClient;
        this.detectionPersistenceService = detectionPersistenceService;
        this.summaries = summaries;
    }

    public static final int MAX_PAGE_SIZE = 50;

    @Transactional(readOnly = true)
    public ProjectListResponse listProjects(long userId, int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page must be at least 1 and pageSize between 1 and " + MAX_PAGE_SIZE + ".");
        }
        List<ProjectSummaryResponse> rows = summaries.findForUser(userId, pageSize + 1, (page - 1) * pageSize);
        boolean hasNext = rows.size() > pageSize;
        return new ProjectListResponse(hasNext ? rows.subList(0, pageSize) : rows, page, pageSize, hasNext);
    }

    @Transactional
    public RoomProject createProject(long userId, CreateProjectRequest request) {
        return repository.save(new RoomProject(userId, request.name().trim(), request.roomType(),
                request.rentalStatus()));
    }

    @Transactional(readOnly = true)
    public RoomProject getProject(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException(
                        "Room project not found: " + id
                ));
    }

    @Transactional
    public RoomProject transitionTo(UUID id, RoomProjectStatus nextStatus) {
        if (nextStatus == RoomProjectStatus.DESIGN_READY) {
            throw new InvalidStateException("Only a validated saved design can mark a project ready.");
        }
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
                            + " to " + nextStatus
            );
        }

        project.setStatus(nextStatus);
        return repository.save(project);
    }

    @Transactional(noRollbackFor = VisionServiceException.class)
    public AnalyzeProjectResponse analyzeProject(UUID id) {
        RoomProject project = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new NoSuchElementException("Room project not found: " + id));

        RoomImage activeImage = roomImageRepository
                .findTopByProjectIdOrderByCreatedAtDesc(id)
                .orElseThrow(() -> new NoActiveImageException(
                        "Project has no room image to analyze."
                ));

        // A repeat request returns the already saved result.
        if (project.getStatus() == RoomProjectStatus.ANALYZED) {
            List<DetectedObject> existingObjects =
                    detectedObjectRepository.findByProjectIdAndActiveTrueOrderByIdAsc(
                            id
                    );

            return toAnalyzeProjectResponse(project, existingObjects);
        }

        validateAnalysisStatus(project);

        try {
            var analysis = visionServiceClient.analyze(activeImage);

            List<DetectedObject> savedObjects =
                    detectionPersistenceService.replaceActiveDetections(id, analysis);

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
}
