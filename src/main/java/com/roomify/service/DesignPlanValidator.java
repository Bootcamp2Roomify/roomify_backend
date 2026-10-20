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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class DesignPlanValidator {

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
        validateRentalFriendlyNotes(plan, preferences);
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
        }
    }

    private void validateRentalFriendlyNotes(
            DesignPlan plan,
            ProjectPreferencesResponse preferences
    ) {
        if (preferences.rentalFriendly()
                && (plan.rentalFriendlyNotes() == null
                || plan.rentalFriendlyNotes().isEmpty())) {
            throw new IllegalArgumentException(
                    "Rental-friendly plans must include rentalFriendlyNotes."
            );
        }
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
    }
}