package com.roomify.service;

import com.roomify.dto.design.BudgetAllocation;
import com.roomify.dto.design.DesignPlan;
import com.roomify.dto.design.DesignRequestSnapshot;
import com.roomify.dto.design.LayoutChange;
import com.roomify.entity.FurnitureDecisionType;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesignPlanValidatorTest {

    private final DesignPlanValidator validator =
            new DesignPlanValidator(Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    void acceptsValidRentalFriendlyPlanThatKeepsObject() {
        DesignRequestSnapshot.Furniture bed = bed(FurnitureDecisionType.KEEP, new BigDecimal("0.95"));

        DesignPlan plan = plan(
                action(bed, LayoutChange.Type.KEEP,
                        "Keep the bed in its current position.",
                        "Keeping the bed avoids unnecessary replacement costs."),
                List.of(new BudgetAllocation("Lighting", "Plug-in floor lamp",
                        "Adds removable task lighting.", new BigDecimal("45.00"))),
                true
        );

        assertDoesNotThrow(() -> validator.validate(plan, snapshot(bed, true)));
    }

    @Test
    void rejectsPlanThatRemovesKeepObject() {
        DesignRequestSnapshot.Furniture bed = bed(FurnitureDecisionType.KEEP, new BigDecimal("0.95"));

        DesignPlan plan = plan(
                action(bed, LayoutChange.Type.KEEP,
                        "Remove the bed to create more floor space.",
                        "A larger open area may feel more spacious."),
                standardAllocation(),
                false
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> validator.validate(plan, snapshot(bed, false)));
        assertTrue(exception.getMessage().contains("KEEP"));
    }

    @Test
    void rejectsPlanThatRetainsRemoveObject() {
        DesignRequestSnapshot.Furniture bed = bed(FurnitureDecisionType.REMOVE, new BigDecimal("0.95"));

        DesignPlan plan = plan(
                action(bed, LayoutChange.Type.REMOVE,
                        "Remove the bed but keep the frame as a styled daybed.",
                        "This creates a lounge corner."),
                standardAllocation(),
                false
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> validator.validate(plan, snapshot(bed, false)));
        assertTrue(exception.getMessage().contains("REMOVE"));
    }

    @Test
    void rejectsPermanentModificationForRentalFriendlyPlan() {
        DesignRequestSnapshot.Furniture bed = bed(FurnitureDecisionType.KEEP, new BigDecimal("0.95"));

        DesignPlan plan = plan(
                action(bed, LayoutChange.Type.KEEP,
                        "Keep the bed and repaint the wall behind it.",
                        "A painted accent wall adds color."),
                standardAllocation(),
                true
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> validator.validate(plan, snapshot(bed, true)));
        assertTrue(exception.getMessage().contains("permanent modifications"));
    }

    @Test
    void rejectsLowConfidenceObjectWithoutUncertaintyWording() {
        DesignRequestSnapshot.Furniture bed = bed(FurnitureDecisionType.KEEP, new BigDecimal("0.40"));

        DesignPlan plan = plan(
                action(bed, LayoutChange.Type.KEEP,
                        "Keep the bed against the wall.",
                        "The bed anchors the room."),
                standardAllocation(),
                false
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> validator.validate(plan, snapshot(bed, false)));
        assertTrue(exception.getMessage().contains("Low-confidence"));
    }

    @Test
    void rejectsPlanOverBudget() {
        DesignRequestSnapshot.Furniture bed = bed(FurnitureDecisionType.KEEP, new BigDecimal("0.95"));

        DesignPlan plan = plan(
                action(bed, LayoutChange.Type.KEEP, "Keep the bed.", "It is in good condition."),
                List.of(new BudgetAllocation("Furniture", "Wardrobe", "Adds storage.", new BigDecimal("650.00"))),
                false
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> validator.validate(plan, snapshot(bed, false)));
        assertTrue(exception.getMessage().contains("budget"));
    }

    @Test
    void rejectsPermanentModificationInRentalBudget() {
        DesignRequestSnapshot.Furniture bed = bed(FurnitureDecisionType.KEEP, new BigDecimal("0.95"));

        DesignPlan plan = plan(
                action(bed, LayoutChange.Type.KEEP, "Keep the bed.", "It is in good condition."),
                List.of(new BudgetAllocation("Storage", "Built-in shelving", "Adds storage.", new BigDecimal("120.00"))),
                true
        );

        assertThrows(IllegalArgumentException.class, () -> validator.validate(plan, snapshot(bed, true)));
    }

    private DesignRequestSnapshot.Furniture bed(FurnitureDecisionType decision, BigDecimal confidence) {
        return new DesignRequestSnapshot.Furniture(UUID.randomUUID(), "bed", confidence, null, decision);
    }

    private LayoutChange action(DesignRequestSnapshot.Furniture object, LayoutChange.Type type,
            String instruction, String rationale) {
        return new LayoutChange(object.objectId(), object.label(), object.decision(), type, instruction, rationale);
    }

    private DesignRequestSnapshot snapshot(DesignRequestSnapshot.Furniture object, boolean rentalFriendly) {
        return new DesignRequestSnapshot("1", UUID.randomUUID(), null, List.of(object),
                new DesignRequestSnapshot.Preferences("Modern", List.of("white", "blue"), "Bedroom",
                        rentalFriendly, "Keep the room affordable."),
                new DesignRequestSnapshot.Budget(new BigDecimal("500.00"), "USD"));
    }

    private DesignPlan plan(LayoutChange action, List<BudgetAllocation> budgetAllocation, boolean rentalFriendly) {
        BigDecimal total = budgetAllocation.stream()
                .map(BudgetAllocation::estimatedCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new DesignPlan("1", "A practical and budget-aware bedroom refresh.", "Modern",
                List.of(action), List.of(), budgetAllocation, total, "USD", List.of(), List.of(),
                rentalFriendly ? List.of("Use removable and non-damaging upgrades only.") : List.of(),
                rentalFriendly ? List.of("No drilling or repainting.") : List.of());
    }

    private List<BudgetAllocation> standardAllocation() {
        return List.of(new BudgetAllocation("Decor", "Removable curtains",
                "Adds color without permanent changes.", new BigDecimal("35.00")));
    }
}
