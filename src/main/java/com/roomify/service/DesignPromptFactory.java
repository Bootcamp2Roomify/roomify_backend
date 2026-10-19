package com.roomify.service;

import com.roomify.dto.ProjectPreferencesResponse;
import com.roomify.entity.FurnitureDecision;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DesignPromptFactory {

    public String systemPrompt() {
        return """
                You are Roomify's budget-aware interior redesign planner.

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

                Rules:
                - Respect every furniture decision exactly.
                - KEEP means do not replace or remove it.
                - REPLACE means propose a replacement within budget.
                - REMOVE means propose removal or storage; do not spend money to keep it.
                - Keep estimatedTotalCost at or below the user's budget.
                - estimatedTotalCost must equal the sum of budgetAllocation estimatedCost values.
                - Give practical, low-cost suggestions rather than professional construction advice.
                - If rentalFriendly is true, use removable, non-damaging solutions only.
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
            prompt.append("\n- objectUuid: ")
                    .append(furnitureDecision.getDetectedObject().getObjectUuid())
                    .append(", objectClass: ")
                    .append(furnitureDecision.getDetectedObject().getObjectClass())
                    .append(", decision: ")
                    .append(furnitureDecision.getDecision());
        }

        prompt.append("\n\nMANDATORY OUTPUT CHECK:");
        prompt.append("\n- layoutChanges must contain EXACTLY ")
                .append(decisions.size())
                .append(" item(s).");
        prompt.append("\n- Each input objectUuid must appear ONCE AND ONLY ONCE.");
        prompt.append("\n- Do not add extra furniture objects.");
        prompt.append("\n- Do not repeat any objectUuid.");

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