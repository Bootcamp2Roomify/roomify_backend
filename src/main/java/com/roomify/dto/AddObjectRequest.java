package com.roomify.dto;

import com.roomify.entity.FurnitureDecisionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** decision defaults to UNSURE when omitted. */
public record AddObjectRequest(@NotBlank @Size(max = 100) String label, FurnitureDecisionType decision) {
}
