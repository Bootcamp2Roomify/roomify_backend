package com.roomify.service;

import com.roomify.dto.DetectedObjectResponse;
import com.roomify.dto.ProjectAnalysisResponse;
import com.roomify.repository.DetectedObjectRepository;
import com.roomify.repository.FurnitureDecisionRepository;
import com.roomify.entity.FurnitureDecisionType;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProjectAnalysisService {

    private final DetectedObjectRepository detectedObjectRepository;
    private final FurnitureDecisionRepository furnitureDecisionRepository;

    public ProjectAnalysisService(
        DetectedObjectRepository detectedObjectRepository,
        FurnitureDecisionRepository furnitureDecisionRepository
    ) {
        this.detectedObjectRepository = detectedObjectRepository;
        this.furnitureDecisionRepository = furnitureDecisionRepository;
    }

    @Transactional(readOnly = true)
    public ProjectAnalysisResponse getStoredAnalysis(UUID projectId) {
        Map<Long, FurnitureDecisionType> decisions = new HashMap<>();
        furnitureDecisionRepository.findByDetectedObject_ProjectIdOrderByIdAsc(projectId)
            .forEach(choice -> decisions.put(choice.getDetectedObject().getId(), choice.getDecision()));
        var objects = detectedObjectRepository
            .findByProjectIdAndActiveTrueOrderByIdAsc(projectId)
            .stream()
            .map(object -> DetectedObjectResponse.from(object,
                decisions.getOrDefault(object.getId(), FurnitureDecisionType.UNSURE)))
            .toList();

        return new ProjectAnalysisResponse(projectId, objects);
    }
}