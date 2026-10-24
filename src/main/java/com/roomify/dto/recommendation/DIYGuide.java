package com.roomify.dto.recommendation;

import java.math.BigDecimal;
import java.util.List;

public record DIYGuide(String schemaVersion, String currency, List<String> materials,
        List<String> tools, BigDecimal estimatedCost, String difficulty, String estimatedTime,
        List<String> steps, List<String> safetyNotes) {
    public DIYGuide {
        if (materials != null) materials = List.copyOf(materials);
        if (tools != null) tools = List.copyOf(tools);
        if (steps != null) steps = List.copyOf(steps);
        if (safetyNotes != null) safetyNotes = List.copyOf(safetyNotes);
    }

}
