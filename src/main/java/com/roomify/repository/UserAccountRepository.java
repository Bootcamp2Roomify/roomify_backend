package com.roomify.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserAccountRepository {

    private static final RowMapper<UserAccount> MAPPER = (rs, row) -> new UserAccount(
            rs.getLong("user_id"), rs.getString("email"), rs.getString("password_hash"),
            rs.getString("display_name"));

    private final JdbcTemplate jdbc;

    public UserAccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Inserts the account, or returns empty when the email is already registered. */
    public Optional<UserAccount> create(String email, String passwordHash, String displayName) {
        return jdbc.query("""
                INSERT INTO users(email, password_hash, display_name) VALUES (?, ?, ?)
                ON CONFLICT DO NOTHING
                RETURNING user_id, email, password_hash, display_name
                """, MAPPER, email, passwordHash, displayName).stream().findFirst();
    }

    public Optional<UserAccount> findByEmail(String email) {
        return jdbc.query("SELECT user_id, email, password_hash, display_name FROM users WHERE LOWER(email) = LOWER(?)",
                MAPPER, email).stream().findFirst();
    }

    public Optional<UserAccount> findById(long userId) {
        return jdbc.query("SELECT user_id, email, password_hash, display_name FROM users WHERE user_id = ?",
                MAPPER, userId).stream().findFirst();
    }

    public record UserAccount(long id, String email, String passwordHash, String displayName) {
    }
}
