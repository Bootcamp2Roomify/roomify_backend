package com.roomify.exception;

/** Only a fixed safe code crosses the API boundary. Never wraps provider messages. */
public class DesignFailureException extends RuntimeException {
    private final String code;
    private final Long designId;
    public DesignFailureException(String code, Long designId) {
        super("Design could not be completed. Please retry."); this.code=code; this.designId=designId;
    }
    public String code() { return code; }
    public Long designId() { return designId; }
}
