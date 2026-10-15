package com.roomify.controller;

import com.roomify.dto.CreateProjectResponse;
import com.roomify.dto.ProjectSnapshotResponse;
import com.roomify.dto.analysis.AnalyzeProjectResponse;
import com.roomify.entity.RoomProject;
import com.roomify.entity.RoomProjectStatus;
import com.roomify.service.RoomProjectService;
import com.roomify.service.ProjectPreferencesService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class RoomProjectController {

    private final RoomProjectService service;
    private final ProjectPreferencesService preferences;

    public RoomProjectController(RoomProjectService service, ProjectPreferencesService preferences) {
        this.service = service;
        this.preferences = preferences;
    }

    @PostMapping
    public ResponseEntity<CreateProjectResponse> createProject() {
        RoomProject project = service.createProject();

        CreateProjectResponse response = new CreateProjectResponse(
                project.getId(),
                project.getStatus()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ProjectSnapshotResponse getProject(@PathVariable UUID id) {
        RoomProject project = service.getProject(id);

        return new ProjectSnapshotResponse(
                project.getId(),
                project.getStatus(),
                project.getCreatedAt(),
                project.getUpdatedAt(),
                preferences.find(id)
        );
    }

    @PostMapping("/{id}/analysis")
    public AnalyzeProjectResponse analyzeProject(@PathVariable UUID id) {
        return service.analyzeProject(id);
    }

    @PatchMapping("/{id}/status")
    public RoomProject transitionStatus(
            @PathVariable UUID id,
            @RequestBody StatusTransitionRequest request
    ) {
        return service.transitionTo(id, request.status());
    }

    public record StatusTransitionRequest(RoomProjectStatus status) {
    }
}
