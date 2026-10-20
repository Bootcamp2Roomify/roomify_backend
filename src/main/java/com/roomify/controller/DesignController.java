package com.roomify.controller;

import com.roomify.dto.design.GenerateDesignResponse;
import com.roomify.service.DesignService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class DesignController {

    private final DesignService designService;

    public DesignController(DesignService designService) {
        this.designService = designService;
    }

    @PostMapping("/{projectId}/designs")
    public ResponseEntity<GenerateDesignResponse> generateDesign(
            @PathVariable UUID projectId
    ) {
        GenerateDesignResponse response = designService.generate(projectId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}