package com.roomify.controller;

import com.roomify.exception.DesignFailureException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes=DesignController.class)
public class DesignExceptionHandler {
    @ExceptionHandler(DesignFailureException.class)
    public ResponseEntity<Error> failure(DesignFailureException e) {
        return ResponseEntity.status(503).body(new Error(e.code(),e.getMessage(),e.designId()));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Error> invalidBody() {
        return ResponseEntity.badRequest().body(new Error("VALIDATION_ERROR","Invalid design request.",null));
    }
    public record Error(String code,String message,Long designId) {}
}
