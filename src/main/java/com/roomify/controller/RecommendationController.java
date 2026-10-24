package com.roomify.controller;

import com.roomify.dto.recommendation.StoredDIYGuide;
import com.roomify.dto.recommendation.StoredRecommendationSet;
import com.roomify.service.recommendation.RecommendationResultService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/recommendations")
public class RecommendationController {
    private final RecommendationResultService service;
    public RecommendationController(RecommendationResultService service) { this.service = service; }

    @GetMapping
    public StoredRecommendationSet latest(@PathVariable UUID projectId) {
        return service.latest(projectId);
    }

    @GetMapping("/{itemId}/diy")
    public StoredDIYGuide guide(@PathVariable UUID projectId, @PathVariable UUID itemId) {
        return service.latestGuide(projectId, itemId);
    }

    // Controller-local handlers take precedence without changing other project APIs' errors.
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ReadError> missing() {
        return ResponseEntity.status(404).body(new ReadError("RECOMMENDATION_NOT_FOUND", "Saved recommendation result not found."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ReadError> invalidId() {
        return ResponseEntity.badRequest().body(new ReadError("INVALID_ID", "Project and item IDs must be valid UUIDs."));
    }

    public record ReadError(String code, String message) {}
}
