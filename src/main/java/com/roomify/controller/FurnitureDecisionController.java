package com.roomify.controller;

import com.roomify.dto.AddObjectRequest;
import com.roomify.dto.ApiErrorResponse;
import com.roomify.dto.DetectedObjectResponse;
import com.roomify.dto.FurnitureDecisionResponse;
import com.roomify.dto.ObjectLabelRequest;
import com.roomify.dto.UpdateFurnitureDecisionRequest;
import com.roomify.entity.FurnitureDecision;
import com.roomify.service.FurnitureDecisionService;
import com.roomify.service.ObjectEditService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/objects")
public class FurnitureDecisionController {

    private final FurnitureDecisionService service;
    private final ObjectEditService edits;

    public FurnitureDecisionController(FurnitureDecisionService service, ObjectEditService edits) {
        this.service = service;
        this.edits = edits;
    }

    @PostMapping
    public ResponseEntity<DetectedObjectResponse> addObject(
            @PathVariable UUID projectId,
            @Valid @RequestBody AddObjectRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(edits.add(projectId, request.label(), request.decision()));
    }

    @PutMapping("/{objectId}/label")
    public DetectedObjectResponse relabel(
            @PathVariable UUID projectId,
            @PathVariable UUID objectId,
            @Valid @RequestBody ObjectLabelRequest request
    ) {
        return edits.relabel(projectId, objectId, request.label());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> invalidObject(MethodArgumentNotValidException exception) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse("VALIDATION_ERROR",
                "Item names must be 1 to 100 characters; decisions must be one of the allowed values.",
                List.of("KEEP", "REPLACE", "REMOVE", "UNSURE")));
    }

    @DeleteMapping("/{objectId}")
    public ResponseEntity<Void> removeObject(@PathVariable UUID projectId, @PathVariable UUID objectId) {
        edits.remove(projectId, objectId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{objectId}")
    public ResponseEntity<FurnitureDecisionResponse> updateDecision(
            @PathVariable UUID projectId,
            @PathVariable UUID objectId,
            @Valid @RequestBody UpdateFurnitureDecisionRequest request
    ) {
        FurnitureDecision saved = service.setDecision(
                projectId,
                objectId,
                request.decision()
        );

        return ResponseEntity.ok(
                new FurnitureDecisionResponse(
                        saved.getDetectedObject().getObjectUuid(),
                        saved.getDecision()
                )
        );
    }
}