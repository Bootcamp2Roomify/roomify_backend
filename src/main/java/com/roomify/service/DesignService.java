package com.roomify.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomify.client.OllamaClient;
import com.roomify.config.OllamaProperties;
import com.roomify.dto.ProjectPreferencesResponse;
import com.roomify.dto.design.BudgetAllocation;
import com.roomify.dto.design.DesignPlan;
import com.roomify.dto.design.GenerateDesignResponse;
import com.roomify.entity.DesignResult;
import com.roomify.entity.FurnitureDecision;
import com.roomify.entity.RoomProject;
import com.roomify.entity.RoomProjectStatus;
import com.roomify.repository.DesignResultRepository;
import com.roomify.repository.FurnitureDecisionRepository;
import com.roomify.repository.RoomProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class DesignService {

    private static final int MAX_GENERATION_ATTEMPTS = 2;

    private final RoomProjectRepository roomProjectRepository;
    private final FurnitureDecisionRepository furnitureDecisionRepository;
    private final DesignResultRepository designResultRepository;
    private final ProjectPreferencesService preferencesService;
    private final DesignPromptFactory promptFactory;
    private final DesignPlanValidator planValidator;
    private final OllamaClient ollamaClient;
    private final OllamaProperties ollamaProperties;
    private final ObjectMapper objectMapper;

    public DesignService(
            RoomProjectRepository roomProjectRepository,
            FurnitureDecisionRepository furnitureDecisionRepository,
            DesignResultRepository designResultRepository,
            ProjectPreferencesService preferencesService,
            DesignPromptFactory promptFactory,
            DesignPlanValidator planValidator,
            OllamaClient ollamaClient,
            OllamaProperties ollamaProperties,
            ObjectMapper objectMapper
    ) {
        this.roomProjectRepository = roomProjectRepository;
        this.furnitureDecisionRepository = furnitureDecisionRepository;
        this.designResultRepository = designResultRepository;
        this.preferencesService = preferencesService;
        this.promptFactory = promptFactory;
        this.planValidator = planValidator;
        this.ollamaClient = ollamaClient;
        this.ollamaProperties = ollamaProperties;
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
                            ollamaProperties.getModel()
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
        String lastValidationError = null;

        for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
            String rawResponse = ollamaClient.generateJson(
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

                plan = withCalculatedTotal(plan);

                planValidator.validate(plan, preferences, furnitureDecisions);
                return plan;
            } catch (JsonProcessingException | IllegalArgumentException exception) {
                lastValidationError = exception.getMessage();
                retryInstruction = lastValidationError;
            }
        }

        throw new IllegalStateException(
                "Unable to generate a valid design plan after "
                        + MAX_GENERATION_ATTEMPTS
                        + " attempts. Last error: "
                        + lastValidationError
        );
    }

    private DesignPlan withCalculatedTotal(DesignPlan plan) {
        if (plan == null || plan.budgetAllocation() == null) {
            return plan;
        }

        BigDecimal total = BigDecimal.ZERO;

        for (BudgetAllocation allocation : plan.budgetAllocation()) {
            if (allocation == null || allocation.estimatedCost() == null) {
                return plan;
            }

            total = total.add(allocation.estimatedCost());
        }

        return new DesignPlan(
                plan.designRationale(),
                plan.layoutChanges(),
                plan.budgetAllocation(),
                total,
                plan.currency(),
                plan.rentalFriendlyNotes(),
                plan.warnings()
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