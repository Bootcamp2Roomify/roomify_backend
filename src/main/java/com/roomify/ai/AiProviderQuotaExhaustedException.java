package com.roomify.ai;

/** The provider account has no quota or credit left. Retrying cannot succeed until the owner tops it up. */
public class AiProviderQuotaExhaustedException extends RuntimeException {

    public AiProviderQuotaExhaustedException(String message) {
        super(message);
    }
}
