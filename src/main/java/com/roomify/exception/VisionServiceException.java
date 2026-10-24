package com.roomify.exception;

public class VisionServiceException extends RuntimeException {

    public VisionServiceException(String message) {
        super(message);
    }

    public VisionServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}