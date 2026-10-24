package com.roomify.service;

import com.roomify.dto.ProjectPreferencesResponse;
import com.roomify.dto.design.BudgetAllocation;
import com.roomify.dto.design.DesignPlan;
import com.roomify.dto.design.LayoutChange;
import com.roomify.entity.FurnitureDecision;
import com.roomify.entity.FurnitureDecisionType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class DesignPlanValidator {

    private static final BigDecimal LOW_CONFIDENCE_THRESHOLD = new BigDecimal("0.75");

    private static final List<String> UNCERTAINTY_PHRASES = List.of(
            "possibly",
            "may be",
            "cannot confirm",
            "can't confirm",
            "uncertain",
            "verify",
            "confirm",
            "measure"
    );

    private static final List<String> PERMANENT_MODIFICATION_TERMS = List.of(
            "repaint",
            "built-in",
            "builtin",
            "major drilling",
            "drill into",
            "rewiring",
            "re-wire",
            "permanent flooring",
            "install hardwood",
            "demolition"
    );

    public void validate(
            DesignPlan plan,
            ProjectPreferencesResponse preferences,
            List<FurnitureDecision> furnitureDecisions
    ) {
        if (plan == null) {
            throw new IllegalArgumentException("Design plan is missing.");
        }

        requireText(plan.designRationale(), "designRationale");
        requireText(plan.currency(), "currency");

        if (!plan.currency().equalsIgnoreCase(preferences.currency())) {
            throw new IllegalArgumentException(
                    "Design plan currency must be " + preferences.currency() + "."
            );
        }

        if (plan.estimatedTotalCost() == null
                || plan.estimatedTotalCost().signum() < 0) {
            throw new IllegalArgumentException(
                    "estimatedTotalCost must be zero or greater."
            );
        }

        if (plan.estimatedTotalCost().compareTo(preferences.budgetAmount()) > 0) {
            throw new IllegalArgumentException(
                    "Design plan exceeds the user's budget of "
                            + preferences.budgetAmount() + "."
            );
        }

        validateBudgetAllocation(plan);
        validateFurnitureDecisions(plan, furnitureDecisions);
        validateRentalFriendlyPlan(plan, preferences);
    }

    private void validateBudgetAllocation(DesignPlan plan) {
        if (plan.budgetAllocation() == null || plan.budgetAllocation().isEmpty()) {
            throw new IllegalArgumentException(
                    "budgetAllocation must contain at least one item."
            );
        }

        BigDecimal allocatedTotal = BigDecimal.ZERO;

        for (BudgetAllocation allocation : plan.budgetAllocation()) {
            if (allocation == null) {
                throw new IllegalArgumentException(
                        "budgetAllocation cannot contain null items."
                );
            }

            requireText(allocation.category(), "budgetAllocation.category");
            requireText(allocation.item(), "budgetAllocation.item");
            requireText(allocation.purpose(), "budgetAllocation.purpose");

            if (allocation.estimatedCost() == null
                    || allocation.estimatedCost().signum() < 0) {
                throw new IllegalArgumentException(
                        "Each budgetAllocation estimatedCost must be zero or greater."
                );
            }

            allocatedTotal = allocatedTotal.add(allocation.estimatedCost());
        }

        if (allocatedTotal.compareTo(plan.estimatedTotalCost()) != 0) {
            throw new IllegalArgumentException(
                    "estimatedTotalCost must equal the sum of budgetAllocation costs."
            );
        }
    }

    private void validateFurnitureDecisions(
            DesignPlan plan,
            List<FurnitureDecision> furnitureDecisions
    ) {
        if (plan.layoutChanges() == null) {
            throw new IllegalArgumentException("layoutChanges is required.");
        }
    for (LayoutChange change : plan.layoutChanges()) {
        if (change == null) {
            throw new IllegalArgumentException(
                    "layoutChanges cannot contain null items."
            );
        }

        if (change.objectUuid() == null) {
            throw new IllegalArgumentException(
                    "layoutChanges.objectUuid is required."
            );
        }

        if (change.decision() == null) {
            throw new IllegalArgumentException(
                    "layoutChanges.decision is required."
            );
        }
}

        Map<UUID, FurnitureDecision> inputByObjectUuid =
                furnitureDecisions.stream().collect(Collectors.toMap(
                        decision -> decision.getDetectedObject().getObjectUuid(),
                        decision -> decision
                ));

        Map<UUID, FurnitureDecisionType> expectedDecisions =
                furnitureDecisions.stream().collect(Collectors.toMap(
                        decision -> decision.getDetectedObject().getObjectUuid(),
                        FurnitureDecision::getDecision
                ));

        Map<UUID, FurnitureDecisionType> actualDecisions =
                plan.layoutChanges().stream().collect(Collectors.toMap(
                        LayoutChange::objectUuid,
                        LayoutChange::decision,
                        (first, second) -> {
                            throw new IllegalArgumentException(
                                    "layoutChanges contains a duplicate objectUuid."
                            );
                        }
                ));

        if (!actualDecisions.equals(expectedDecisions)) {
            throw new IllegalArgumentException(
                    "layoutChanges must include every furniture object with its original decision."
            );
        }

        for (LayoutChange change : plan.layoutChanges()) {
            requireText(change.objectClass(), "layoutChanges.objectClass");
            requireText(change.action(), "layoutChanges.action");
            requireText(change.rationale(), "layoutChanges.rationale");

            FurnitureDecision inputDecision = inputByObjectUuid.get(change.objectUuid());

            if (inputDecision == null) {
                throw new IllegalArgumentException(
                        "layoutChanges contains an unknown objectUuid."
                );
            }

            validateActionMatchesDecision(change, inputDecision.getDecision());
            validateLowConfidenceUncertainty(change, inputDecision);
        }
    }

    private void validateActionMatchesDecision(
            LayoutChange change,
            FurnitureDecisionType decision
    ) {
        String action = change.action().trim().toLowerCase(Locale.ROOT);

        switch (decision) {
            case KEEP -> {
                if (!action.startsWith("keep ")) {
                    throw new IllegalArgumentException(
                            "KEEP object actions must begin with 'Keep '."
                    );
                }

                if (containsAny(action, List.of(
                        "replace", "remove", "store", "discard", "sell"
                ))) {
                    throw new IllegalArgumentException(
                            "KEEP objects cannot be replaced, removed, stored, discarded, or sold."
                    );
                }
            }

            case REPLACE -> {
                if (!action.startsWith("replace ")) {
                    throw new IllegalArgumentException(
                            "REPLACE object actions must begin with 'Replace '."
                    );
                }
            }

            case REMOVE -> {
                if (!(action.startsWith("remove ") || action.startsWith("store "))) {
                    throw new IllegalArgumentException(
                            "REMOVE object actions must begin with 'Remove ' or 'Store '."
                    );
                }

                if (containsAny(action, List.of("keep", "retain", "style"))) {
                    throw new IllegalArgumentException(
                            "REMOVE objects cannot be recommended as retained or styled."
                    );
                }
            }

            case UNSURE -> {
                String combinedText = (change.action() + " " + change.rationale())
                        .toLowerCase(Locale.ROOT);

                if (!containsAny(combinedText, UNCERTAINTY_PHRASES)) {
                    throw new IllegalArgumentException(
                            "UNSURE objects must include uncertainty wording or a safe verification step."
                    );
                }
            }
        }
    }

    private void validateLowConfidenceUncertainty(
            LayoutChange change,
            FurnitureDecision furnitureDecision
    ) {
        BigDecimal confidence = furnitureDecision.getDetectedObject().getConfidence();

        if (confidence == null || confidence.compareTo(LOW_CONFIDENCE_THRESHOLD) < 0) {
            String combinedText = (change.action() + " " + change.rationale())
                    .toLowerCase(Locale.ROOT);

            if (!containsAny(combinedText, UNCERTAINTY_PHRASES)) {
                throw new IllegalArgumentException(
                        "Low-confidence detected objects must include uncertainty wording."
                );
            }
        }
    }

    private void validateRentalFriendlyPlan(
            DesignPlan plan,
            ProjectPreferencesResponse preferences
    ) {
        if (!preferences.rentalFriendly()) {
            return;
        }

        if (plan.rentalFriendlyNotes() == null || plan.rentalFriendlyNotes().isEmpty()) {
            throw new IllegalArgumentException(
                    "Rental-friendly plans must include rentalFriendlyNotes."
            );
        }

        for (LayoutChange change : plan.layoutChanges()) {
            if (containsAny(change.action(), PERMANENT_MODIFICATION_TERMS)) {
                throw new IllegalArgumentException(
                        "Rental-friendly plans cannot recommend permanent modifications."
                );
            }
        }

        for (BudgetAllocation allocation : plan.budgetAllocation()) {
            String allocationText = allocation.item() + " " + allocation.purpose();

            if (containsAny(allocationText, PERMANENT_MODIFICATION_TERMS)) {
                throw new IllegalArgumentException(
                        "Rental-friendly plans cannot budget for permanent modifications."
                );
            }
        }
    }

    private boolean containsAny(String value, List<String> terms) {
        String normalizedValue = value.toLowerCase(Locale.ROOT);

        return terms.stream().anyMatch(normalizedValue::contains);
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
    }
}