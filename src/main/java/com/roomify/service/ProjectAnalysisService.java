package com.roomify.service;

import com.roomify.dto.DetectedObjectResponse;
import com.roomify.dto.ProjectAnalysisResponse;
import com.roomify.repository.DetectedObjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectAnalysisService {

    private final DetectedObjectRepository detectedObjectRepository;

    public ProjectAnalysisService(
        DetectedObjectRepository detectedObjectRepository
    ) {
        this.detectedObjectRepository = detectedObjectRepository;
    }

    @Transactional(readOnly = true)
    public ProjectAnalysisResponse getStoredAnalysis(Long projectId) {
        var objects = detectedObjectRepository
            .findByProjectIdAndActiveTrueOrderByIdAsc(projectId)
            .stream()
            .map(DetectedObjectResponse::from)
            .toList();

        return new ProjectAnalysisResponse(projectId, objects);
    }
}