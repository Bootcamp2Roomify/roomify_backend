package com.roomify.controller;

import com.roomify.dto.CreateProjectResponse;
import com.roomify.dto.ProjectSnapshotResponse;
import com.roomify.dto.vision.VisionAnalysisResponse;
import com.roomify.entity.RoomProject;
import com.roomify.entity.RoomProjectStatus;
import com.roomify.service.RoomProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class RoomProjectController {

    private final RoomProjectService service;

    public RoomProjectController(RoomProjectService service) {
        this.service = service;
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
                project.getUpdatedAt()
        );
    }

    @PostMapping("/{id}/analysis")
    public VisionAnalysisResponse analyzeProject(@PathVariable UUID id) {
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