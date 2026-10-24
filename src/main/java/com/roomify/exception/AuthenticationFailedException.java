package com.roomify.exception;

public class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException() {
        super("Email or password is incorrect.");
    }
}
