package com.roomify.service;

import com.roomify.dto.design.DesignPlan;
import com.roomify.dto.design.DesignRequestSnapshot;
import com.roomify.dto.design.LayoutChange;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * ROOM-93: turns a validated design plan into an image-edit instruction for the person's own photo.
 * Only plan fields are used; free-text user requirements already shaped the plan and are not repeated.
 */
@Component
public class PrototypePromptFactory {

    public static final String VERSION = "prototype-v1";

    public String build(DesignPlan plan, DesignRequestSnapshot snapshot) {
        var prompt = new StringBuilder();
        prompt.append("Edit this original room photo into one photorealistic redesign concept. ")
            .append("Preserve the existing room architecture, doors, windows, ")
            .append("camera viewpoint, perspective, and major room dimensions as much as possible. ")
            .append("Keep the original walls, ceiling, floor layout, and natural lighting direction. ")
            .append("Do not invent structural changes, new doors, windows, or walls. ")
            .append("This is a conceptual visualization, not an exact architectural rendering. ")
            .append("Do not add people, text, labels, or watermarks.\n");
        prompt.append("Style: ").append(humanize(plan.designStyle())).append('.');
        List<String> colors = snapshot.preferences().preferredColors();
        if (!colors.isEmpty()) prompt.append(" Color palette: ").append(String.join(", ", colors)).append('.');
        prompt.append('\n');
        line(prompt, "Lighting: preserve the existing natural light direction; "
        + "use balanced, soft ambient lighting appropriate to the selected style.");

        for (LayoutChange action : plan.actions()) {
            String item = action.objectClass();
            switch (action.type()) {
                case KEEP -> line(prompt, "Keep the existing " + item + " exactly where it is; restyle only with soft furnishings.");
                case KEEP_AND_REPOSITION -> line(prompt, "Keep the existing " + item + " but move it: " + action.instruction());
                case REPLACE -> line(prompt, "Replace the " + item + ": " + action.instruction());
                case REMOVE -> line(prompt, "Remove the " + item + " from the room and fill the space naturally.");
                case UNSURE -> line(prompt, "Leave the " + item + " as it is.");
                case ADD -> line(prompt, "Add: " + action.instruction());
            }
        }
        for (DesignPlan.ItemNeeded item : plan.itemsNeeded()) {
            if (item.objectId() == null) line(prompt, "Include " + item.quantity() + " x " + item.name() + ".");
        }
        if (snapshot.preferences().rentalFriendly()) {
            line(prompt, "This is a rented room: no repainted walls, built-ins, new flooring or wall-mounted fixtures; "
                    + "use freestanding, removable pieces only.");
        }
        for (String constraint : plan.constraints()) line(prompt, "Constraint: " + constraint);
        return prompt.toString();
    }

    private static void line(StringBuilder prompt, String text) {
        prompt.append("- ").append(text.strip()).append('\n');
    }

    private static String humanize(String value) {
        return value.replace('_', ' ').toLowerCase(Locale.ROOT);
    }
}
