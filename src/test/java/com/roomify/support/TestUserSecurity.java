package com.roomify.support;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Integration tests act as one seeded user unless they build their own MockMvc.
 * Fixtures that insert projects directly must set {@code user_id = TestUserSecurity.USER_ID}.
 */
@Configuration
public class TestUserSecurity {

    public static final long USER_ID = 900_001L;

    @Bean
    MockMvcBuilderCustomizer authenticateAsTestUser() {
        return builder -> builder.defaultRequest(get("/")
                .with(jwt().jwt(token -> token.subject(Long.toString(USER_ID)).issuer("roomify"))));
    }

    @Bean
    ApplicationRunner seedTestUser(JdbcTemplate jdbc) {
        return args -> jdbc.update("""
                INSERT INTO users(user_id, email, password_hash, display_name)
                VALUES (?, 'test-user@roomify.test', 'not-a-login-hash', 'Test User')
                ON CONFLICT DO NOTHING
                """, USER_ID);
    }
}
