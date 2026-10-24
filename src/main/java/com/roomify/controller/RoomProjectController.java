package com.roomify.controller;

import com.roomify.dto.CreateProjectResponse;
import com.roomify.dto.ProjectSnapshotResponse;
import com.roomify.dto.analysis.AnalyzeProjectResponse;
import com.roomify.entity.RoomProject;
import com.roomify.entity.RoomProjectStatus;
import com.roomify.service.RoomProjectService;
import com.roomify.service.ProjectPreferencesService;
import com.roomify.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import com.roomify.dto.ApiErrorResponse;
import com.roomify.dto.CreateProjectRequest;
import com.roomify.dto.ProjectListResponse;
import com.roomify.entity.RentalStatus;
import com.roomify.entity.RoomType;
import jakarta.validation.Valid;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
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
    public ResponseEntity<CreateProjectResponse> createProject(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateProjectRequest request
    ) {
        RoomProject project = service.createProject(CurrentUser.id(jwt), request);

        CreateProjectResponse response = new CreateProjectResponse(
                project.getId(),
                project.getName(),
                project.getRoomType(),
                project.getRentalStatus(),
                project.getStatus()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ProjectListResponse listProjects(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return service.listProjects(CurrentUser.id(jwt), page, pageSize);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> invalidProject(MethodArgumentNotValidException exception) {
        List<String> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getField).distinct().sorted().toList();
        return ResponseEntity.badRequest().body(new ApiErrorResponse("VALIDATION_ERROR",
                "Project name, room type and rental status are required.", fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> unreadableProject(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse("INVALID_JSON",
                "The request body is malformed or uses an unknown value.",
                List.of("roomType: " + String.join("|", names(RoomType.values())),
                        "rentalStatus: " + String.join("|", names(RentalStatus.values())))));
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    @GetMapping("/{id}")
    public ProjectSnapshotResponse getProject(@PathVariable UUID id) {
        RoomProject project = service.getProject(id);

        return new ProjectSnapshotResponse(
                project.getId(),
                project.getName(),
                project.getRoomType(),
                project.getRentalStatus(),
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
