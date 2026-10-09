package com.roomify.controller;

import com.roomify.dto.ProjectPreferencesRequest;
import com.roomify.dto.ProjectPreferencesResponse;
import com.roomify.service.ProjectPreferencesService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/preferences")
public class ProjectPreferencesController {
    private final ProjectPreferencesService service;
    public ProjectPreferencesController(ProjectPreferencesService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<ProjectPreferencesResponse> get(@PathVariable UUID projectId) {
        var preferences = service.get(projectId);
        return preferences == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(preferences);
    }

    @GetMapping("/limits")
    public Map<String, BigDecimal> limits(@PathVariable UUID projectId) {
        service.get(projectId);
        return Map.of("maxBudgetAmount", service.maxBudget());
    }

    @PutMapping
    public ProjectPreferencesResponse save(@PathVariable UUID projectId,
            @Valid @RequestBody ProjectPreferencesRequest request) {
        return service.save(projectId, request);
    }
}
