package com.roomify.dto;

import java.util.List;

public record ApiErrorResponse(
    String code,
    String message,
    List<String> allowedValues
) {
}
