package com.roomify.dto.design;

import com.roomify.entity.FurnitureDecisionType;
import jakarta.validation.constraints.*;
import java.util.UUID;

public record LayoutChange(UUID objectId, @NotBlank @Size(max=100) String objectClass,
        FurnitureDecisionType decision, @NotNull Type type,
        @NotBlank @Size(max=1000) String instruction, @NotBlank @Size(max=1000) String rationale) {
    public enum Type { KEEP, KEEP_AND_REPOSITION, REPLACE, REMOVE, UNSURE, ADD }
}
