package com.roomify.service.recommendation;

import com.roomify.dto.recommendation.GenerationMetadata;
import com.roomify.repository.RecommendationResultRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.roomify.service.recommendation.RecommendationContractsTest.fixture;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;

/** Real commits/rollbacks on disposable projects; no outer test transaction hides the boundaries. */
@SpringBootTest
class RecommendationRecoveryTest {
    @Autowired RecommendationResultService service;
    @Autowired RecommendationContractCodec codec;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManager entityManager;
    @MockitoSpyBean RecommendationResultRepository repository;
    private final List<UUID> projects = new ArrayList<>();
    private final GenerationMetadata metadata = new GenerationMetadata("local", "fixture", "1.0", "correlation-only");

    UUID project() {
        UUID id = UUID.randomUUID(); projects.add(id);
        jdbc.update("INSERT INTO room_projects(project_id) VALUES (?)", id);
        jdbc.update("INSERT INTO project_preferences(project_id, style, budget_amount, currency, room_purpose) VALUES (?, 'MINIMALIST', 100, 'USD', 'LIVING')", id);
        return id;
    }
    Long design(UUID id) {
        return jdbc.queryForObject("INSERT INTO design_results(project_id, plan_json, estimated_total_cost, currency, model_name) VALUES (?, '{}'::jsonb, 50, 'USD', 'fixture') RETURNING design_id", Long.class, id);
    }
    int sets(UUID id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_sets WHERE project_id = ?", Integer.class, id);
    }
    @AfterEach void removeOnlyDisposableProjects() {
        projects.forEach(id -> jdbc.update("DELETE FROM room_projects WHERE project_id = ?", id));
    }

    @Test void reloadsInFreshTransactionAndKeepsOldItemGuidesAcrossNewVersions() throws Exception {
        UUID project = project(); Long design = design(project);
        var payload = codec.readSet(fixture("recommendation-set"));
        var first = service.storeSet(project, design, payload, metadata);
        UUID oldItem = first.items().get(1).itemId();
        var guide = service.storeGuide(project, oldItem, codec.readGuide(fixture("diy-guide")), metadata);
        var second = service.storeSet(project, design, payload, metadata);
        assertThat(second.setId()).isNotEqualTo(first.setId());
        assertThat(second.items().get(1).itemId()).isNotEqualTo(oldItem);
        assertThat(sets(project)).isEqualTo(2);
        // Equal timestamps cannot make the older sequence win.
        jdbc.update("UPDATE recommendation_sets SET created_at = TIMESTAMPTZ '2026-10-20 00:00:00Z' WHERE project_id = ?", project);
        var reloaded = new TransactionTemplate(transactions).execute(status -> {
            entityManager.clear();
            return service.latest(project);
        });
        assertThat(reloaded.setId()).isEqualTo(second.setId());
        assertThat(reloaded.items()).isEqualTo(second.items());
        assertThat(service.latestGuide(project, oldItem)).isEqualTo(guide);
        assertThatThrownBy(() -> service.latestGuide(project, second.items().get(1).itemId()))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test void actualConstraintFailureRollsBackParentAndPartialItemsLeavingPreviousSet() throws Exception {
        UUID project = project(); Long design = design(project);
        var payload = codec.readSet(fixture("recommendation-set"));
        var previous = service.storeSet(project, design, payload, metadata);
        doAnswer(call -> {
            if ((int) call.getArgument(2) == 1) {
                // First item already exists. PostgreSQL rejects this duplicate set position.
                jdbc.update("INSERT INTO recommendation_items(public_id, recommendation_set_id, position, payload) VALUES (?, ?, 0, '{}'::jsonb)",
                        UUID.randomUUID(), (long) call.getArgument(1));
            }
            return call.callRealMethod();
        }).when(repository).insertItem(any(UUID.class), anyLong(), anyInt(), anyString());
        assertThatThrownBy(() -> service.storeSet(project, design, payload, metadata)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(sets(project)).isEqualTo(1);
        assertThat(service.latest(project)).isEqualTo(previous);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_items i JOIN recommendation_sets s ON s.id = i.recommendation_set_id WHERE s.project_id = ?", Integer.class, project)).isEqualTo(2);
    }

    @Test void actualGuideConstraintFailurePreservesLastSuccessfulGuide() throws Exception {
        UUID project = project();
        var set = service.storeSet(project, design(project), codec.readSet(fixture("recommendation-set")), metadata);
        UUID item = set.items().get(1).itemId(); var payload = codec.readGuide(fixture("diy-guide"));
        var previous = service.storeGuide(project, item, payload, metadata);
        doAnswer(call -> {
            // A non-object payload violates the database's JSONB shape constraint.
            jdbc.update("INSERT INTO recommendation_diy_guides(public_id, recommendation_item_id, payload, provider_metadata) VALUES (?, ?, '[]'::jsonb, '{}'::jsonb)",
                    UUID.randomUUID(), (long) call.getArgument(1));
            return null;
        }).when(repository).insertGuide(any(UUID.class), anyLong(), anyString(), anyString());
        assertThatThrownBy(() -> service.storeGuide(project, item, payload, metadata)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(service.latestGuide(project, item)).isEqualTo(previous);
    }

    @Test void storeGuideReturnsItsOwnWriteEvenWhenAnotherGuideBecomesVisible() throws Exception {
        UUID project = project();
        var set = service.storeSet(project, design(project), codec.readSet(fixture("recommendation-set")), metadata);
        UUID item = set.items().get(1).itemId();
        AtomicReference<UUID> insertedId = new AtomicReference<>();
        doAnswer(call -> {
            insertedId.set(call.getArgument(0));
            call.callRealMethod();
            // Deterministically model another visible guide between this INSERT and response lookup.
            jdbc.update("INSERT INTO recommendation_diy_guides(public_id, recommendation_item_id, payload, provider_metadata) VALUES (?, ?, CAST(? AS jsonb), CAST(? AS jsonb))",
                    UUID.randomUUID(), (long) call.getArgument(1), (String) call.getArgument(2), (String) call.getArgument(3));
            return null;
        }).when(repository).insertGuide(any(UUID.class), anyLong(), anyString(), anyString());
        var own = service.storeGuide(project, item, codec.readGuide(fixture("diy-guide")), metadata);
        assertThat(own.guideId()).isEqualTo(insertedId.get());
    }

    @Test void readsRejectASetLinkedToAnotherProjectsDesignEvenAfterDirectDatabaseWrite() throws Exception {
        UUID owner = project(); UUID other = project();
        var set = service.storeSet(owner, design(owner), codec.readSet(fixture("recommendation-set")), metadata);
        UUID item = set.items().get(1).itemId();
        service.storeGuide(owner, item, codec.readGuide(fixture("diy-guide")), metadata);
        // Model an inconsistent row from an out-of-band writer; service writes forbid this.
        jdbc.update("UPDATE recommendation_sets SET project_id = ? WHERE public_id = ?", other, set.setId());
        assertThatThrownBy(() -> service.latest(other)).isInstanceOf(java.util.NoSuchElementException.class);
        assertThatThrownBy(() -> service.latestGuide(other, item)).isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test void indexesAndParentCascadesCoverPersistedResults() throws Exception {
        var indexes = jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE tablename IN ('recommendation_sets', 'recommendation_diy_guides')", String.class);
        assertThat(indexes).contains("idx_recommendation_sets_project_latest", "idx_recommendation_diy_item_latest");
        for (boolean deleteDesign : new boolean[]{true, false}) {
            UUID project = project(); Long design = design(project);
            var set = service.storeSet(project, design, codec.readSet(fixture("recommendation-set")), metadata);
            var guide = service.storeGuide(project, set.items().get(1).itemId(), codec.readGuide(fixture("diy-guide")), metadata);
            if (deleteDesign) jdbc.update("DELETE FROM design_results WHERE design_id = ?", design);
            else jdbc.update("DELETE FROM room_projects WHERE project_id = ?", project);
            assertThat(sets(project)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_items WHERE public_id = ?", Integer.class, set.items().getFirst().itemId())).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_diy_guides WHERE public_id = ?", Integer.class, guide.guideId())).isZero();
        }
    }
}
