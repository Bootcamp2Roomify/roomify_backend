package com.roomify.exception;

import com.roomify.ai.AiProviderConfigurationException;
import com.roomify.ai.AiProviderQuotaExhaustedException;
import com.roomify.ai.AiProviderRetryableException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.NoSuchElementException;

// Runs before ApiExceptionHandler, whose IllegalArgumentException handler would
// otherwise match the cause of an invalid UUID path variable.
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class ProjectExceptionHandler {

    @ExceptionHandler(InvalidStateException.class)
    public ResponseEntity<ApiError> handleInvalidState(
            InvalidStateException exception
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(
                        "INVALID_STATE_TRANSITION",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiError> handleNotFound(
            NoSuchElementException exception
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(
                        "PROJECT_NOT_FOUND",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleInvalidPathVariable(
            MethodArgumentTypeMismatchException exception
    ) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(
                        "INVALID_PROJECT_ID",
                        "Project id must be a valid UUID."
                ));
    }

    @ExceptionHandler(NoActiveImageException.class)
    public ResponseEntity<ApiError> handleNoActiveImage(
            NoActiveImageException exception
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(
                        "NO_ACTIVE_IMAGE",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(VisionServiceException.class)
    public ResponseEntity<ApiError> handleVisionService(
            VisionServiceException exception
    ) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError(
                        "ANALYSIS_FAILED",
                        "Analysis could not be completed. Please retry."
                ));
    }

    @ExceptionHandler(AiProviderConfigurationException.class)
    public ResponseEntity<ApiError> handleAiProviderConfiguration(
            AiProviderConfigurationException exception
    ) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError(
                        "AI_PROVIDER_CONFIGURATION_ERROR",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(AiProviderQuotaExhaustedException.class)
    public ResponseEntity<ApiError> handleAiProviderQuotaExhausted(
            AiProviderQuotaExhaustedException exception
    ) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError(
                        "AI_QUOTA_EXHAUSTED",
                        "AI quota exhausted. Generation is paused until the AI account is topped up."
                ));
    }

    @ExceptionHandler(AiProviderRetryableException.class)
    public ResponseEntity<ApiError> handleAiProviderUnavailable(
            AiProviderRetryableException exception
    ) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError(
                        "AI_PROVIDER_UNAVAILABLE",
                        "Design generation is temporarily unavailable. Please retry."
                ));
    }

    public record ApiError(String code, String message) {
    }
}