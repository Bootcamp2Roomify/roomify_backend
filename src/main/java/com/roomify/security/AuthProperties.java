package com.roomify.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@ConfigurationProperties(prefix = "auth.jwt")
public record AuthProperties(String secret, Duration ttl, String issuer) {

    public AuthProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must be set to at least 32 bytes.");
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalStateException("JWT_TTL must be a positive duration.");
        }
    }
}
