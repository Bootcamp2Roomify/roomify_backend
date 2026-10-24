package com.roomify.service;

import com.roomify.dto.auth.AuthResponse;
import com.roomify.dto.auth.LoginRequest;
import com.roomify.dto.auth.RegisterRequest;
import com.roomify.dto.auth.UserResponse;
import com.roomify.exception.AuthenticationFailedException;
import com.roomify.exception.EmailAlreadyRegisteredException;
import com.roomify.repository.UserAccountRepository;
import com.roomify.repository.UserAccountRepository.UserAccount;
import com.roomify.security.JwtTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.NoSuchElementException;

@Service
public class AuthService {

    private final UserAccountRepository users;
    private final PasswordEncoder passwords;
    private final JwtTokenService tokens;
    // Compared against when the email is unknown so both login failures cost the same time.
    private final String unknownUserHash;

    public AuthService(UserAccountRepository users, PasswordEncoder passwords, JwtTokenService tokens) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.unknownUserHash = passwords.encode("roomify-unknown-user");
    }

    public UserResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String displayName = request.displayName() == null || request.displayName().isBlank()
                ? null : request.displayName().trim();
        UserAccount account = users.create(email, passwords.encode(request.password()), displayName)
                .orElseThrow(EmailAlreadyRegisteredException::new);
        return toResponse(account);
    }

    public AuthResponse login(LoginRequest request) {
        var account = users.findByEmail(request.email().trim());
        String hash = account.map(UserAccount::passwordHash).orElse(unknownUserHash);
        if (!passwords.matches(request.password(), hash) || account.isEmpty()) {
            throw new AuthenticationFailedException();
        }
        var token = tokens.issue(account.get().id());
        long expiresIn = Duration.between(Instant.now(), token.expiresAt()).toSeconds();
        return new AuthResponse(token.value(), "Bearer", expiresIn, toResponse(account.get()));
    }

    public UserResponse me(long userId) {
        return users.findById(userId).map(AuthService::toResponse)
                .orElseThrow(() -> new NoSuchElementException("User not found."));
    }

    private static UserResponse toResponse(UserAccount account) {
        return new UserResponse(account.id(), account.email(), account.displayName());
    }
}
