package com.roomify.controller;

import com.roomify.dto.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRequest(
            HttpMessageNotReadableException exception) {

        ApiErrorResponse error = new ApiErrorResponse(
            "VALIDATION_ERROR",
            "Decision must be one of the allowed values.",
            List.of("KEEP", "REPLACE", "REMOVE", "UNSURE")
        );

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException exception) {

        ApiErrorResponse error = new ApiErrorResponse(
            "VALIDATION_ERROR",
            exception.getMessage(),
            List.of()
        );

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(error);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(
            MaxUploadSizeExceededException exception) {

        ApiErrorResponse error = new ApiErrorResponse(
            "VALIDATION_ERROR",
            "Image exceeds the maximum allowed size.",
            List.of()
        );

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(error);
    }
}

