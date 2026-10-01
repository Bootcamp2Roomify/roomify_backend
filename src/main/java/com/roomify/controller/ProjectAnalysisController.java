package com.roomify.controller;

import com.roomify.dto.ProjectAnalysisResponse;
import com.roomify.service.ProjectAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/analysis")
public class ProjectAnalysisController {

    private final ProjectAnalysisService projectAnalysisService;

    public ProjectAnalysisController(
        ProjectAnalysisService projectAnalysisService
    ) {
        this.projectAnalysisService = projectAnalysisService;
    }

    @GetMapping
    public ResponseEntity<ProjectAnalysisResponse> getAnalysis(
        @PathVariable Long projectId
    ) {
        return ResponseEntity.ok(
            projectAnalysisService.getStoredAnalysis(projectId)
        );
    }
}