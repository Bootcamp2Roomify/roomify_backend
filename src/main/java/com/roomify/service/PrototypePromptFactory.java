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
    private static final int MAX_LENGTH = 4000;

    public String build(DesignPlan plan, DesignRequestSnapshot snapshot) {
        var prompt = new StringBuilder();
        prompt.append("Edit this photo of a real room into a photorealistic redesign concept. ")
                .append("Keep the same camera position, perspective, lighting direction, walls, windows, doors, ")
                .append("ceiling and floor layout. Do not add text, labels, people or watermarks.\n");
        prompt.append("Style: ").append(humanize(plan.designStyle())).append('.');
        List<String> colors = snapshot.preferences().preferredColors();
        if (!colors.isEmpty()) prompt.append(" Color palette: ").append(String.join(", ", colors)).append('.');
        prompt.append('\n');

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
        String result = prompt.toString();
        return result.length() <= MAX_LENGTH ? result : result.substring(0, MAX_LENGTH);
    }

    private static void line(StringBuilder prompt, String text) {
        prompt.append("- ").append(text.strip()).append('\n');
    }

    private static String humanize(String value) {
        return value.replace('_', ' ').toLowerCase(Locale.ROOT);
    }
}
