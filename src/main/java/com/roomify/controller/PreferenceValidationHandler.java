package com.roomify.controller;

import com.roomify.service.ProjectPreferencesService.BudgetLimitException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.Map;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ProjectPreferencesController.class)
public class PreferenceValidationHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationError> invalidFields(MethodArgumentNotValidException exception) {
        var errors = new LinkedHashMap<String, String>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
            errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return invalid(errors);
    }
    @ExceptionHandler(BudgetLimitException.class)
    public ResponseEntity<ValidationError> budgetLimit(BudgetLimitException exception) {
        return invalid(Map.of("budgetAmount", exception.getMessage()));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ValidationError> unreadable(HttpMessageNotReadableException exception) {
        return invalid(Map.of("request", "Provide a valid preferences payload."));
    }
    private ResponseEntity<ValidationError> invalid(Map<String, String> fields) {
        return ResponseEntity.badRequest().body(new ValidationError(
            "VALIDATION_ERROR", "Check the highlighted preference fields.", fields));
    }
    public record ValidationError(String code, String message, Map<String, String> fieldErrors) {}
}
