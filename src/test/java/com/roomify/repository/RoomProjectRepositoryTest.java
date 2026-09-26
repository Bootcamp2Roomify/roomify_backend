package com.roomify.repository;

import com.roomify.entity.RoomProject;
import com.roomify.entity.RoomProjectStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class RoomProjectRepositoryTest {

    @Autowired
    private RoomProjectRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void savesAndRetrievesRoomProjectByUuid() {
        RoomProject saved = repository.saveAndFlush(new RoomProject());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(RoomProjectStatus.CREATED);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        // Clear cached entities so the next lookup reads from PostgreSQL.
        entityManager.clear();

        RoomProject retrieved = repository
            .findById(saved.getId())
            .orElseThrow();

        assertThat(retrieved.getId()).isEqualTo(saved.getId());
        assertThat(retrieved.getStatus())
            .isEqualTo(RoomProjectStatus.CREATED);
        assertThat(retrieved.getCreatedAt()).isNotNull();
        assertThat(retrieved.getUpdatedAt()).isNotNull();
    }
}