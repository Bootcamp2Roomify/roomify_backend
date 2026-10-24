package com.roomify.security;

import org.springframework.security.oauth2.jwt.Jwt;

/** The authenticated user id is the JWT subject issued by {@link JwtTokenService}. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static long id(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
