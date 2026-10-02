package com.roomify.controller;

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
    public ResponseEntity<RoomProject> createProject() {
        RoomProject project = service.createProject();

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(project);
    }

    @GetMapping("/{id}")
    public RoomProject getProject(@PathVariable UUID id) {
        return service.getProject(id);
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