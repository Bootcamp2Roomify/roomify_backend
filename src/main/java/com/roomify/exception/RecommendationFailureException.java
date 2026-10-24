package com.roomify.exception;

/** Generation could not produce a valid, storable result; the code is safe to show to clients. */
public class RecommendationFailureException extends RuntimeException {
    private final String code;

    public RecommendationFailureException(String code) {
        super("Recommendations could not be generated. Please retry.");
        this.code = code;
    }

    public String code() {
        return code;
    }
}
