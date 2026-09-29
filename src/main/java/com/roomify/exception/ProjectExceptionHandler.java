package com.roomify.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.NoSuchElementException;

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

    public record ApiError(String code, String message) {
    }
}