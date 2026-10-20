package com.roomify.ai;

public class AiProviderRetryableException extends RuntimeException {

    public AiProviderRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}