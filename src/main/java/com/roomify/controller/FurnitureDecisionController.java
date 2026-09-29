package com.roomify.controller;

import com.roomify.dto.FurnitureDecisionResponse;
import com.roomify.dto.UpdateFurnitureDecisionRequest;
import com.roomify.entity.FurnitureDecision;
import com.roomify.service.FurnitureDecisionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/objects")
public class FurnitureDecisionController {

    private final FurnitureDecisionService service;

    public FurnitureDecisionController(FurnitureDecisionService service) {
        this.service = service;
    }

    @PatchMapping("/{objectId}")
    public ResponseEntity<FurnitureDecisionResponse> updateDecision(
            @PathVariable Long projectId,
            @PathVariable Long objectId,
            @Valid @RequestBody UpdateFurnitureDecisionRequest request) {

        FurnitureDecision saved = service.setDecision(
            projectId,
            objectId,
            request.decision()
        );

        return ResponseEntity.ok(
            new FurnitureDecisionResponse(
                saved.getDetectedObject().getId(),
                saved.getDecision()
            )
        );
    }
}