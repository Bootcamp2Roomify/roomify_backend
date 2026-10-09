package com.roomify.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record ProjectPreferencesRequest(
    @NotBlank @Pattern(regexp = "SCANDINAVIAN|COZY_MINIMALIST|MINIMALIST|MODERN|INDUSTRIAL|NO_PREFERENCE") String style,
    @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal budgetAmount,
    @NotBlank @Pattern(regexp = "TWD|USD") String currency,
    @Size(max = 8) List<@NotBlank @Size(max = 32) String> preferredColors,
    @NotBlank @Pattern(regexp = "STUDY_AND_SLEEP|STUDY|SLEEP|LIVING|MULTIPURPOSE") String roomPurpose,
    Boolean rentalFriendly,
    @Size(max = 1000) String specialRequirements
) {}
