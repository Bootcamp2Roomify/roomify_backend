package com.roomify.dto.auth;

public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
}
