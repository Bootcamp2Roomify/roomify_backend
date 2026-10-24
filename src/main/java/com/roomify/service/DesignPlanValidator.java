package com.roomify.service;

import com.roomify.dto.design.*;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;
import static com.roomify.dto.design.LayoutChange.Type.*;

/**
 * Structural checks (ROOM-88) plus constraint guardrails (ROOM-90). Messages are safe to send back
 * to the provider as a corrective-retry hint; they are never shown to end users.
 */
@Component
public class DesignPlanValidator {
    static final BigDecimal LOW_CONFIDENCE_THRESHOLD = new BigDecimal("0.75");
    private static final List<String> UNCERTAINTY_PHRASES = List.of("possibly", "may be", "cannot confirm",
            "can't confirm", "uncertain", "verify", "confirm", "measure");
    private static final List<String> PERMANENT_MODIFICATION_TERMS = List.of("repaint", "built-in", "builtin",
            "major drilling", "drill into", "rewiring", "re-wire", "permanent flooring", "install hardwood",
            "demolition");

    private final Validator validator;
    public DesignPlanValidator(Validator validator) { this.validator=validator; }

    public void validate(DesignPlan plan, DesignRequestSnapshot snapshot) {
        if(plan==null) invalid("Design plan is missing.");
        if(!validator.validate(plan).isEmpty()) invalid("Design plan does not match the schema.");
        if(!plan.currency().equals(snapshot.budget().currency()))
            invalid("Design plan currency must be "+snapshot.budget().currency()+".");
        BigDecimal sum=plan.budgetAllocation().stream().map(BudgetAllocation::estimatedCost)
            .reduce(BigDecimal.ZERO,BigDecimal::add);
        if(sum.compareTo(plan.estimatedTotalCost())!=0)
            invalid("estimatedTotalCost must equal the sum of budgetAllocation costs.");
        if(sum.compareTo(snapshot.budget().amount())>0)
            invalid("Design plan exceeds the user's budget of "+snapshot.budget().amount()+".");
        var expected=new HashMap<UUID,DesignRequestSnapshot.Furniture>();
        snapshot.objects().forEach(o->expected.put(o.objectId(),o));
        var seen=new HashSet<UUID>();
        for(var a:plan.actions()) {
            if(a.type()==ADD) {
                if(a.objectId()!=null || a.decision()!=null) invalid("ADD actions must not reference a detected object.");
                continue;
            }
            var original=expected.get(a.objectId());
            if(original==null) invalid("actions contains an unknown objectId.");
            if(!seen.add(a.objectId())) invalid("actions contains a duplicate objectId.");
            if(a.decision()!=original.decision() || !a.objectClass().equals(original.label()))
                invalid("actions must include every furniture object with its original label and decision.");
            boolean matches=switch(original.decision()) {
                case KEEP -> a.type()==KEEP || a.type()==KEEP_AND_REPOSITION;
                case REMOVE -> a.type()==REMOVE;
                case REPLACE -> a.type()==REPLACE;
                case UNSURE -> a.type()==UNSURE;
            };
            if(!matches) invalid(original.decision()+" objects cannot use action type "+a.type()+".");
            validateInstructionMatchesDecision(a);
            validateLowConfidenceUncertainty(a,original);
        }
        if(!seen.equals(expected.keySet()))
            invalid("actions must include every furniture object with its original decision.");
        for(var item:plan.itemsNeeded())
            if(item.objectId()!=null && !expected.containsKey(item.objectId())) invalid("itemsNeeded references an unknown objectId.");
        if(snapshot.preferences().rentalFriendly()) validateRentalFriendlyPlan(plan);
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
                if(!instruction.startsWith("keep ")) invalid("KEEP object instructions must begin with 'Keep '.");
                if(containsAny(instruction,List.of("replace","remove","store","discard","sell")))
                    invalid("KEEP objects cannot be replaced, removed, stored, discarded, or sold.");
            }
            case REPLACE -> {
                if(!instruction.startsWith("replace ")) invalid("REPLACE object instructions must begin with 'Replace '.");
            }
            case REMOVE -> {
                if(!(instruction.startsWith("remove ") || instruction.startsWith("store ")))
                    invalid("REMOVE object instructions must begin with 'Remove ' or 'Store '.");
                if(containsAny(instruction,List.of("keep","retain","style")))
                    invalid("REMOVE objects cannot be recommended as retained or styled.");
            }
            case UNSURE -> {
                if(!containsAny(change.instruction()+" "+change.rationale(),UNCERTAINTY_PHRASES))
                    invalid("UNSURE objects must include uncertainty wording or a safe verification step.");
            }
        }
    }

    private void validateLowConfidenceUncertainty(LayoutChange change, DesignRequestSnapshot.Furniture original) {
        BigDecimal confidence=original.confidence();
        if((confidence==null || confidence.compareTo(LOW_CONFIDENCE_THRESHOLD)<0)
                && !containsAny(change.instruction()+" "+change.rationale(),UNCERTAINTY_PHRASES))
            invalid("Low-confidence detected objects must include uncertainty wording.");
    }

    private void validateRentalFriendlyPlan(DesignPlan plan) {
        if(plan.constraints().isEmpty() || plan.rentalFriendlyWarnings().isEmpty())
            invalid("Rental-friendly plans must include constraints and rentalFriendlyWarnings.");
        for(var a:plan.actions()) if(containsAny(a.instruction(),PERMANENT_MODIFICATION_TERMS))
            invalid("Rental-friendly plans cannot recommend permanent modifications.");
        for(var allocation:plan.budgetAllocation()) if(containsAny(allocation.item()+" "+allocation.purpose(),PERMANENT_MODIFICATION_TERMS))
            invalid("Rental-friendly plans cannot budget for permanent modifications.");
        for(var item:plan.itemsNeeded()) if(containsAny(item.name(),PERMANENT_MODIFICATION_TERMS))
            invalid("Rental-friendly plans cannot require permanent modifications.");
    }

    private boolean containsAny(String value, List<String> terms) {
        String normalized=value.toLowerCase(Locale.ROOT);
        return terms.stream().anyMatch(normalized::contains);
    }
    private void invalid(String reason) { throw new IllegalArgumentException(reason); }
}
