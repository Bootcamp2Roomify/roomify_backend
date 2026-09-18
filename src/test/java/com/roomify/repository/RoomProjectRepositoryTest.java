package com.roomify.repository;

import com.roomify.entity.RoomProject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class RoomProjectRepositoryTest {

    @Autowired
    RoomProjectRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager entityManager;

    @Test
    void savesAndRetrievesRoomForItsOwner() {
        Long ownerId = createTestUser();
        Long otherUserId = createTestUser();

        RoomProject saved = repository.saveAndFlush(
            new RoomProject(ownerId, "My bedroom")
        );

        assertThat(saved.getId()).isNotNull();

        // Clear Java's cached entities so the lookup reads the database.
        entityManager.clear();

        RoomProject retrieved = repository
            .findByIdAndUserId(saved.getId(), ownerId)
            .orElseThrow();

        assertThat(retrieved.getName()).isEqualTo("My bedroom");
        assertThat(retrieved.getUserId()).isEqualTo(ownerId);
        assertThat(retrieved.getStatus()).isEqualTo("DRAFT");

        assertThat(repository.findByIdAndUserId(
            saved.getId(), otherUserId
        )).isEmpty();
    }

    private Long createTestUser() {
        return jdbc.queryForObject(
            """
            INSERT INTO users (email, password_hash)
            VALUES (?, ?)
            RETURNING user_id
            """,
            Long.class,
            UUID.randomUUID() + "@example.com",
            "test-only-placeholder"
        );
    }
}