package com.roomify.service;

import com.roomify.dto.ProjectPreferencesResponse;
import com.roomify.dto.design.BudgetAllocation;
import com.roomify.dto.design.DesignPlan;
import com.roomify.dto.design.LayoutChange;
import com.roomify.entity.DetectedObject;
import com.roomify.entity.FurnitureDecision;
import com.roomify.entity.FurnitureDecisionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesignPlanValidatorTest {

    private final DesignPlanValidator validator = new DesignPlanValidator();

    @Test
    void acceptsValidRentalFriendlyPlanThatKeepsObject() {
        FurnitureDecision furnitureDecision = furnitureDecision(
                FurnitureDecisionType.KEEP,
                new BigDecimal("0.95")
        );

        DesignPlan plan = plan(
                new LayoutChange(
                        furnitureDecision.getDetectedObject().getObjectUuid(),
                        "bed",
                        FurnitureDecisionType.KEEP,
                        "Keep the bed in its current position.",
                        "Keeping the bed avoids unnecessary replacement costs."
                ),
                List.of(
                        new BudgetAllocation(
                                "Lighting",
                                "Plug-in floor lamp",
                                "Adds removable task lighting.",
                                new BigDecimal("45.00")
                        )
                ),
                true
        );

        assertDoesNotThrow(() -> validator.validate(
                plan,
                preferences(true),
                List.of(furnitureDecision)
        ));
    }

    @Test
    void rejectsPlanThatRemovesKeepObject() {
        FurnitureDecision furnitureDecision = furnitureDecision(
                FurnitureDecisionType.KEEP,
                new BigDecimal("0.95")
        );

        DesignPlan plan = plan(
                new LayoutChange(
                        furnitureDecision.getDetectedObject().getObjectUuid(),
                        "bed",
                        FurnitureDecisionType.KEEP,
                        "Remove the bed to create more floor space.",
                        "A larger open area may feel more spacious."
                ),
                standardAllocation(),
                false
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(
                        plan,
                        preferences(false),
                        List.of(furnitureDecision)
                )
        );

        assertTrue(exception.getMessage().contains("KEEP"));
    }

    @Test
    void rejectsPlanThatRetainsRemoveObject() {
        FurnitureDecision furnitureDecision = furnitureDecision(
                FurnitureDecisionType.REMOVE,
                new BigDecimal("0.95")
        );

        DesignPlan plan = plan(
                new LayoutChange(
                        furnitureDecision.getDetectedObject().getObjectUuid(),
                        "bed",
                        FurnitureDecisionType.REMOVE,
                        "Keep and style the bed with new pillows.",
                        "The bed can become a focal point."
                ),
                standardAllocation(),
                false
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(
                        plan,
                        preferences(false),
                        List.of(furnitureDecision)
                )
        );

        assertTrue(exception.getMessage().contains("REMOVE"));
    }

    @Test
    void rejectsPermanentModificationForRentalFriendlyPlan() {
        FurnitureDecision furnitureDecision = furnitureDecision(
                FurnitureDecisionType.KEEP,
                new BigDecimal("0.95")
        );

        DesignPlan plan = plan(
                new LayoutChange(
                        furnitureDecision.getDetectedObject().getObjectUuid(),
                        "bed",
                        FurnitureDecisionType.KEEP,
                        "Keep the bed in its current position.",
                        "This keeps the layout functional."
                ),
                List.of(
                        new BudgetAllocation(
                                "Walls",
                                "Interior paint",
                                "Repaint the walls in a neutral color.",
                                new BigDecimal("80.00")
                        )
                ),
                true
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(
                        plan,
                        preferences(true),
                        List.of(furnitureDecision)
                )
        );

        assertTrue(exception.getMessage().contains("permanent modifications"));
    }

    @Test
    void rejectsLowConfidenceObjectWithoutUncertaintyWording() {
        FurnitureDecision furnitureDecision = furnitureDecision(
                FurnitureDecisionType.KEEP,
                new BigDecimal("0.40")
        );

        DesignPlan plan = plan(
                new LayoutChange(
                        furnitureDecision.getDetectedObject().getObjectUuid(),
                        "bed",
                        FurnitureDecisionType.KEEP,
                        "Keep the bed in its current position.",
                        "This preserves the existing layout."
                ),
                standardAllocation(),
                false
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(
                        plan,
                        preferences(false),
                        List.of(furnitureDecision)
                )
        );

        assertTrue(exception.getMessage().contains("Low-confidence"));
    }

    @Test
    void rejectsLayoutChangeWithNullDecision() {
        FurnitureDecision furnitureDecision = furnitureDecision(
                FurnitureDecisionType.KEEP,
                new BigDecimal("0.95")
        );

        DesignPlan plan = plan(
                new LayoutChange(
                        furnitureDecision.getDetectedObject().getObjectUuid(),
                        "bed",
                        null,
                        "Keep the bed in its current position.",
                        "The bed is in good condition."
                ),
                standardAllocation(),
                false
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(
                        plan,
                        preferences(false),
                        List.of(furnitureDecision)
                )
        );

        assertTrue(
                exception.getMessage().contains(
                        "layoutChanges.decision is required"
                )
        );
        }

    @Test
    void rejectsPlanThatExceedsUserBudget() {
        FurnitureDecision furnitureDecision = furnitureDecision(
                FurnitureDecisionType.KEEP,
                new BigDecimal("0.95")
        );

        ProjectPreferencesResponse preferences = new ProjectPreferencesResponse(
                UUID.randomUUID(),
                "Modern",
                new BigDecimal("9000"),
                "TWD",
                List.of("white", "blue"),
                "Bedroom",
                false,
                "Keep the room affordable.",
                new BigDecimal("1000000.00")
        );

        DesignPlan plan = new DesignPlan(
                "A modern bedroom refresh.",
                List.of(new LayoutChange(
                        furnitureDecision.getDetectedObject().getObjectUuid(),
                        "bed",
                        FurnitureDecisionType.KEEP,
                        "Keep the bed in its current position.",
                        "The bed is in good condition."
                )),
                List.of(
                        new BudgetAllocation(
                                "Furniture",
                                "Desk",
                                "Adds workspace.",
                                new BigDecimal("5000")
                        ),
                        new BudgetAllocation(
                                "Lighting",
                                "Lamp",
                                "Improves lighting.",
                                new BigDecimal("4600")
                        )
                ),
                new BigDecimal("9600"),
                "TWD",
                List.of(),
                List.of()
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(
                        plan,
                        preferences,
                        List.of(furnitureDecision)
                )
        );

        assertTrue(
                exception.getMessage().contains("exceeds the user's budget")
        );
        }

    @Test
    void rejectsPlanWithIncorrectAllocationTotal() {
        FurnitureDecision furnitureDecision = furnitureDecision(
            FurnitureDecisionType.KEEP,
            new BigDecimal("0.95")
    );

        DesignPlan validPlan = plan(
            new LayoutChange(
                    furnitureDecision.getDetectedObject().getObjectUuid(),
                    "bed",
                    FurnitureDecisionType.KEEP,
                    "Keep the bed in its current position.",
                    "The bed is in good condition."
            ),
            standardAllocation(),
            false
    );

        // Allocation is 35.00, but AI declares 30.00.
         DesignPlan invalidPlan = new DesignPlan(
            validPlan.designRationale(),
            validPlan.layoutChanges(),
            validPlan.budgetAllocation(),
            new BigDecimal("30.00"),
            validPlan.currency(),
            validPlan.rentalFriendlyNotes(),
            validPlan.warnings()
    );

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> validator.validate(
                    invalidPlan,
                    preferences(false),
                    List.of(furnitureDecision)
            )
    );

        assertTrue(
            exception.getMessage().contains(
                    "estimatedTotalCost must equal the sum"
            )
    );
}
private FurnitureDecision furnitureDecision(
            FurnitureDecisionType decision,
            BigDecimal confidence
    ) {
        DetectedObject detectedObject = new DetectedObject(
                UUID.randomUUID(),
                1L,
                "bed",
                confidence,
                null,
                null,
                null,
                null,
                "test-model"
        );

        return new FurnitureDecision(detectedObject, decision);
    }

    private ProjectPreferencesResponse preferences(boolean rentalFriendly) {
        return new ProjectPreferencesResponse(
                UUID.randomUUID(),
                "Modern",
                new BigDecimal("500.00"),
                "USD",
                List.of("white", "blue"),
                "Bedroom",
                rentalFriendly,
                "Keep the room affordable.",
                new BigDecimal("1000000.00")
        );
    }

    private DesignPlan plan(
            LayoutChange layoutChange,
            List<BudgetAllocation> budgetAllocation,
            boolean rentalFriendly
    ) {
        BigDecimal total = budgetAllocation.stream()
                .map(BudgetAllocation::estimatedCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new DesignPlan(
                "A practical and budget-aware bedroom refresh.",
                List.of(layoutChange),
                budgetAllocation,
                total,
                "USD",
                rentalFriendly
                        ? List.of("Use removable and non-damaging upgrades only.")
                        : List.of(),
                List.of()
        );
    }

    private List<BudgetAllocation> standardAllocation() {
        return List.of(
                new BudgetAllocation(
                        "Decor",
                        "Removable curtains",
                        "Adds color without permanent changes.",
                        new BigDecimal("35.00")
                )
        );
    }
}