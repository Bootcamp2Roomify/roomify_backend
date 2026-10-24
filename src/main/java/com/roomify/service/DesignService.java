package com.roomify.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomify.ai.AiDesignProvider;
import com.roomify.dto.ProjectPreferencesResponse;
import com.roomify.dto.design.DesignPlan;
import com.roomify.dto.design.GenerateDesignResponse;
import com.roomify.entity.DesignResult;
import com.roomify.entity.FurnitureDecision;
import com.roomify.entity.RoomProject;
import com.roomify.entity.RoomProjectStatus;
import com.roomify.repository.DesignResultRepository;
import com.roomify.repository.FurnitureDecisionRepository;
import com.roomify.repository.RoomProjectRepository;
import com.roomify.exception.DesignGenerationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class DesignService {
    private static final Logger log =LoggerFactory.getLogger(DesignService.class);
    private static final int MAX_GENERATION_ATTEMPTS = 2;

    private final RoomProjectRepository roomProjectRepository;
    private final FurnitureDecisionRepository furnitureDecisionRepository;
    private final DesignResultRepository designResultRepository;
    private final ProjectPreferencesService preferencesService;
    private final DesignPromptFactory promptFactory;
    private final DesignPlanValidator planValidator;
    private final AiDesignProvider aiDesignProvider;
    private final ObjectMapper objectMapper;

    public DesignService(
            RoomProjectRepository roomProjectRepository,
            FurnitureDecisionRepository furnitureDecisionRepository,
            DesignResultRepository designResultRepository,
            ProjectPreferencesService preferencesService,
            DesignPromptFactory promptFactory,
            DesignPlanValidator planValidator,
            AiDesignProvider aiDesignProvider,
            ObjectMapper objectMapper
    ) {
        this.roomProjectRepository = roomProjectRepository;
        this.furnitureDecisionRepository = furnitureDecisionRepository;
        this.designResultRepository = designResultRepository;
        this.preferencesService = preferencesService;
        this.promptFactory = promptFactory;
        this.planValidator = planValidator;
        this.aiDesignProvider = aiDesignProvider;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public GenerateDesignResponse generate(UUID projectId) {
        RoomProject project = roomProjectRepository.findById(projectId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Room project not found: " + projectId
                ));

        if (project.getStatus() != RoomProjectStatus.PREFERENCES_READY) {
            throw new IllegalStateException(
                    "Project must have saved preferences before generating a design."
            );
        }

        if (designResultRepository.existsByProjectId(projectId)) {
            throw new IllegalStateException(
                    "A design has already been generated for this project."
            );
        }

        ProjectPreferencesResponse preferences = preferencesService.find(projectId);

        if (preferences == null) {
            throw new IllegalStateException(
                    "Project preferences are required before generating a design."
            );
        }

        List<FurnitureDecision> furnitureDecisions =
                furnitureDecisionRepository
                        .findByDetectedObject_ProjectIdOrderByIdAsc(projectId);

        if (furnitureDecisions.isEmpty()) {
            throw new IllegalStateException(
                    "Furniture decisions are required before generating a design."
            );
        }

        DesignPlan plan = generateValidPlan(preferences, furnitureDecisions);

        try {
            String planJson = objectMapper.writeValueAsString(plan);

            DesignResult savedResult = designResultRepository.save(
                    new DesignResult(
                            projectId,
                            planJson,
                            plan.estimatedTotalCost(),
                            plan.currency().toUpperCase(),
                            aiDesignProvider.getModelName()
                    )
            );

            project.setStatus(RoomProjectStatus.DESIGN_READY);
            roomProjectRepository.save(project);

            return toResponse(savedResult, plan);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to store the generated design plan.",
                    exception
            );
        }
    }

private DesignPlan generateValidPlan(
        ProjectPreferencesResponse preferences,
        List<FurnitureDecision> furnitureDecisions
) {
    String retryInstruction = null;

    for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {

        String rawResponse = aiDesignProvider.generateJson(
                promptFactory.systemPrompt(),
                promptFactory.userPrompt(
                        preferences,
                        furnitureDecisions,
                        retryInstruction
                )
        );

        try {
            DesignPlan plan = objectMapper.readValue(
                    rawResponse,
                    DesignPlan.class
            );

            planValidator.validate(
                    plan,
                    preferences,
                    furnitureDecisions
            );

            return plan;

        } catch (JsonProcessingException | IllegalArgumentException exception) {

            // Log only error category and attempt number.
            // Never log raw AI output or sensitive user data.
            log.warn(
                    "AI design validation failed: attempt={}, errorType={}",
                    attempt,
                    exception instanceof JsonProcessingException
                            ? "JSON_PARSING_ERROR"
                            : "PLAN_VALIDATION_ERROR"
            );

            // Provide feedback for one corrective retry.
            if (attempt < MAX_GENERATION_ATTEMPTS) {
                retryInstruction = exception instanceof JsonProcessingException
                        ? "Previous response had invalid JSON or schema values. "
                          + "Return a valid DesignPlan JSON object with correct fields and enum values."
                        : exception.getMessage();
            }
        }
    }

    // Never expose raw validation details to the frontend.
    throw new DesignGenerationException(
            "Unable to generate a valid design plan."
    );
}
    private GenerateDesignResponse toResponse(
            DesignResult result,
            DesignPlan plan
    ) {
        return new GenerateDesignResponse(
                result.getId(),
                result.getProjectId(),
                plan,
                result.getEstimatedTotalCost(),
                result.getCurrency(),
                result.getModelName(),
                result.getCreatedAt()
        );
    }
}