package com.roomify.service;

import com.roomify.dto.ProjectPreferencesResponse;
import com.roomify.entity.FurnitureDecision;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class DesignPromptFactory {

    private static final BigDecimal LOW_CONFIDENCE_THRESHOLD = new BigDecimal("0.75");

    public String systemPrompt() {
        return """
                You are Roomify's constraint-aware, budget-aware interior redesign planner.

                Return ONLY one valid JSON object.
                Do not use Markdown, code fences, or extra text.

                The JSON must have exactly these fields:
                {
                  "designRationale": "short explanation of the overall design",
                  "layoutChanges": [
                    {
                      "objectUuid": "UUID from the input",
                      "objectClass": "furniture type from the input",
                      "decision": "KEEP, REPLACE, REMOVE, or UNSURE",
                      "action": "specific practical action",
                      "rationale": "why this action supports the redesign"
                    }
                  ],
                  "budgetAllocation": [
                    {
                      "category": "category name",
                      "item": "item or material",
                      "purpose": "why it is needed",
                      "estimatedCost": 0.00
                    }
                  ],
                  "estimatedTotalCost": 0.00,
                  "currency": "USD",
                  "rentalFriendlyNotes": ["practical rental-safe note"],
                  "warnings": ["optional limitation or caution"]
                }

                HARD CONSTRAINTS:
                - Respect every furniture decision exactly.
                - KEEP: preserve the object. Its action must begin with "Keep ".
                  Never replace, remove, store, discard, or sell a KEEP object.
                - REPLACE: recommend a practical replacement within budget.
                  Its action must begin with "Replace ".
                - REMOVE: recommend removal or storage only.
                  Its action must begin with "Remove or store ".
                  Never recommend retaining, keeping, styling, or spending money on a REMOVE object.
                - UNSURE: do not invent certainty. State what cannot be confirmed and recommend
                  a safe next step, such as measuring, confirming the object, or reviewing another photo.
                - Keep estimatedTotalCost at or below the user's budget.
                - estimatedTotalCost must equal the sum of budgetAllocation estimatedCost values.
                - Give practical, low-cost suggestions rather than professional construction advice.

                RENTAL-FRIENDLY RULES:
                - If rentalFriendly is true, recommend removable and non-damaging solutions only.
                - Do not recommend repainting, built-ins, major drilling, rewiring,
                  permanent flooring, demolition, or other permanent modifications.
                - Put rental limitations or cautions in rentalFriendlyNotes or warnings.

                UNCERTAINTY RULE:
                - When an object's confidence is low or missing, clearly use uncertain wording
                  such as "possibly", "may be", "cannot confirm from the image", or
                  "verify measurements before purchase".
                - Never claim exact room dimensions, object placement, materials, or spatial
                  clearance when the input does not provide that information.
                """;
    }

    public String userPrompt(
            ProjectPreferencesResponse preferences,
            List<FurnitureDecision> decisions,
            String retryInstruction
    ) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("""
                
                Create a redesign plan using the following normalized room data.

                USER PREFERENCES
                """);

        prompt.append("\nStyle: ").append(preferences.style());
        prompt.append("\nBudget: ").append(preferences.budgetAmount())
                .append(" ").append(preferences.currency());
        prompt.append("\nPreferred colors: ").append(preferences.preferredColors());
        prompt.append("\nRoom purpose: ").append(preferences.roomPurpose());
        prompt.append("\nRental friendly: ").append(preferences.rentalFriendly());
        prompt.append("\nSpecial requirements: ").append(preferences.specialRequirements());

        prompt.append("\n\nFURNITURE DECISIONS (must all be included in layoutChanges):");

        for (FurnitureDecision furnitureDecision : decisions) {
            BigDecimal confidence = furnitureDecision.getDetectedObject().getConfidence();
            boolean lowConfidence = confidence == null
                    || confidence.compareTo(LOW_CONFIDENCE_THRESHOLD) < 0;

            prompt.append("\n- objectUuid: ")
                    .append(furnitureDecision.getDetectedObject().getObjectUuid())
                    .append(", objectClass: ")
                    .append(furnitureDecision.getDetectedObject().getObjectClass())
                    .append(", decision: ")
                    .append(furnitureDecision.getDecision())
                    .append(", confidence: ")
                    .append(confidence == null ? "unknown" : confidence)
                    .append(", uncertaintyRequired: ")
                    .append(lowConfidence);
        }

        prompt.append("\n\nMANDATORY OUTPUT CHECK:");
        prompt.append("\n- layoutChanges must contain EXACTLY ")
                .append(decisions.size())
                .append(" item(s).");
        prompt.append("\n- Each input objectUuid must appear ONCE AND ONLY ONCE.");
        prompt.append("\n- Do not add extra furniture objects.");
        prompt.append("\n- Do not repeat any objectUuid.");
        prompt.append("\n- For every item with uncertaintyRequired: true, use uncertainty wording");
        prompt.append(" in its action or rationale.");
        prompt.append("\n- If rentalFriendly is true, include at least one rentalFriendlyNotes item.");
        prompt.append("\n- If rentalFriendly is true, do not include permanent modifications");
        prompt.append(" in layoutChanges or budgetAllocation.");

        prompt.append("\n\nThe only allowed objectUuid values are:");
        for (FurnitureDecision furnitureDecision : decisions) {
            prompt.append("\n- ")
                    .append(furnitureDecision.getDetectedObject().getObjectUuid());
        }

        if (retryInstruction != null && !retryInstruction.isBlank()) {
            prompt.append("\n\nPREVIOUS RESPONSE WAS INVALID. Fix this issue: ")
                    .append(retryInstruction);
        }

        return prompt.toString();
    }
}