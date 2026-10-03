package com.roomify.service;

import com.roomify.dto.BoundingBoxInput;
import com.roomify.dto.DetectionInput;
import com.roomify.entity.DetectedObject;
import com.roomify.repository.DetectedObjectRepository;
import com.roomify.repository.RoomImageRepository;
import com.roomify.dto.AnalysisInput;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class DetectionPersistenceService {

    private final DetectedObjectRepository detectedObjectRepository;
    private final RoomImageRepository roomImageRepository;

    public DetectionPersistenceService(
        DetectedObjectRepository detectedObjectRepository,
        RoomImageRepository roomImageRepository
    ) {
        this.detectedObjectRepository = detectedObjectRepository;
        this.roomImageRepository = roomImageRepository;
    }

    @Transactional
    public List<DetectedObject> replaceActiveDetections(
        Long projectId,
        String modelVersion,
        List<DetectionInput> detections
    ) {
        var roomImage = roomImageRepository.findByProjectId(projectId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Room image not found for project."
            ));

        detectedObjectRepository.deactivateActiveByProjectId(projectId);

        List<DetectedObject> newObjects = new ArrayList<>();

        for (DetectionInput detection : detections) {
            validateDetection(detection);

            BoundingBoxInput bbox = detection.bbox();

            DetectedObject object = new DetectedObject(
                projectId,
                roomImage.getId(),
                detection.label(),
                decimal(detection.confidence()),
                decimal(bbox.x()),
                decimal(bbox.y()),
                decimal(bbox.x() + bbox.w()),
                decimal(bbox.y() + bbox.h()),
                modelVersion
            );

            newObjects.add(object);
        }

        return detectedObjectRepository.saveAll(newObjects);
    }

    private void validateDetection(DetectionInput detection) {
        if (detection == null) {
            throw new IllegalArgumentException("Detection must not be null.");
        }

        if (detection.label() == null || detection.label().isBlank()) {
            throw new IllegalArgumentException("Detection label is required.");
        }

        if (detection.confidence() < 0 || detection.confidence() > 1) {
            throw new IllegalArgumentException(
                "Detection confidence must be between 0 and 1."
            );
        }

        BoundingBoxInput bbox = detection.bbox();

        if (bbox == null) {
            throw new IllegalArgumentException(
                "Detection bounding box is required."
            );
        }

        if (bbox.x() < 0 || bbox.y() < 0 ||
            bbox.w() <= 0 || bbox.h() <= 0 ||
            bbox.x() + bbox.w() > 1 ||
            bbox.y() + bbox.h() > 1) {
            throw new IllegalArgumentException(
                "Detection bounding box must be normalized between 0 and 1."
            );
        }
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value);
    }

    @Transactional
    public List<DetectedObject> replaceActiveDetections(
        Long projectId,
        AnalysisInput analysis
    ) {
        if (analysis == null) {
            throw new IllegalArgumentException("Analysis result is required.");
        }

        if (analysis.modelVersion() == null ||
            analysis.modelVersion().isBlank()) {
            throw new IllegalArgumentException(
                "Model version is required."
            );
        }

        List<DetectionInput> detections =
            analysis.objects() == null
                ? List.of()
                : analysis.objects();

        return replaceActiveDetections(
            projectId,
            analysis.modelVersion(),
            detections
        );
    }

}
