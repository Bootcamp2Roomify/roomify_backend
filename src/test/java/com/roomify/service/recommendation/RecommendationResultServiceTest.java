package com.roomify.service.recommendation;

import com.roomify.dto.recommendation.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.UUID;

import static com.roomify.service.recommendation.RecommendationContractsTest.fixture;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class RecommendationResultServiceTest {
    @Autowired RecommendationResultService service;
    @Autowired RecommendationContractCodec codec;
    @Autowired JdbcTemplate jdbc;
    private final GenerationMetadata metadata = new GenerationMetadata("local", "fixture", "1.0", null);

    UUID project() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO room_projects(project_id) VALUES (?)", id);
        jdbc.update("""
            INSERT INTO project_preferences(project_id, style, budget_amount, currency, room_purpose)
            VALUES (?, 'MINIMALIST', 100, 'USD', 'LIVING')
            """, id);
        return id;
    }

    Long design(UUID project) {
        return jdbc.queryForObject("""
            INSERT INTO design_results(project_id, plan_json, estimated_total_cost, currency, model_name)
            VALUES (?, '{}'::jsonb, 50, 'USD', 'fixture') RETURNING design_id
            """, Long.class, project);
    }

    @Test void persistsCanonicalSetAndStableItemsThenAppendsEligibleGuide() throws Exception {
        UUID project = project(); Long design = design(project);
        var saved = service.storeSet(project, design, codec.readSet(fixture("recommendation-set")), metadata);
        assertThat(saved.setId()).isNotNull();
        assertThat(saved.projectId()).isEqualTo(project);
        assertThat(saved.designResultId()).isEqualTo(design);
        assertThat(saved.items()).extracting(StoredRecommendationItem::name).containsExactly("Desk lamp", "Storage basket");
        assertThat(saved.items()).extracting(StoredRecommendationItem::itemId).doesNotHaveDuplicates().doesNotContainNull();
        assertThat(saved.estimatedTotal()).isEqualByComparingTo("29.99");
        assertThat(service.latest(project)).isEqualTo(saved);
        UUID item = saved.items().get(1).itemId();
        var guide = service.storeGuide(project, item, codec.readGuide(fixture("diy-guide")), metadata);
        assertThat(guide.itemId()).isEqualTo(item);
        assertThat(guide.projectId()).isEqualTo(project);
        assertThat(service.latestGuide(project, item)).isEqualTo(guide);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_items WHERE recommendation_set_id = (SELECT id FROM recommendation_sets WHERE public_id = ?)", Integer.class, saved.setId())).isEqualTo(2);
    }

    @Test void rejectsDesignFromAnotherProjectAndMissingPreferences() throws Exception {
        UUID first = project(); UUID other = project(); Long design = design(other);
        var payload = codec.readSet(fixture("recommendation-set"));
        assertThatThrownBy(() -> service.storeSet(first, design, payload, metadata)).isInstanceOf(NoSuchElementException.class);
        jdbc.update("DELETE FROM project_preferences WHERE project_id = ?", other);
        assertThatThrownBy(() -> service.storeSet(other, design, payload, metadata)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.latest(first)).isInstanceOf(NoSuchElementException.class);
    }

    @Test void rejectsWrongCurrencyAndExcessBudgetWithoutLosingPreviousSet() throws Exception {
        UUID project = project(); Long design = design(project);
        var payload = codec.readSet(fixture("recommendation-set"));
        var saved = service.storeSet(project, design, payload, metadata);
        var wrongCurrency = new RecommendationSet("1.0", "TWD", payload.budget(), payload.estimatedTotal(), payload.remainingBudget(), payload.items());
        var tooLarge = new RecommendationSet("1.0", "USD", new BigDecimal("101"), payload.estimatedTotal(), new BigDecimal("71.01"), payload.items());
        assertThatThrownBy(() -> service.storeSet(project, design, wrongCurrency, metadata)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.storeSet(project, design, tooLarge, metadata)).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.latest(project).setId()).isEqualTo(saved.setId());
    }

    @Test void checksGuideOwnershipEligibilityAndCurrency() throws Exception {
        UUID project = project(); UUID other = project();
        var saved = service.storeSet(project, design(project), codec.readSet(fixture("recommendation-set")), metadata);
        var payload = codec.readGuide(fixture("diy-guide"));
        UUID eligible = saved.items().get(1).itemId();
        assertThatThrownBy(() -> service.storeGuide(other, eligible, payload, metadata)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> service.latestGuide(other, eligible)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> service.storeGuide(project, saved.items().getFirst().itemId(), payload, metadata)).isInstanceOf(IllegalArgumentException.class);
        var wrongCurrency = new DIYGuide("1.0", "TWD", payload.materials(), payload.tools(), payload.estimatedCost(), payload.difficulty(), payload.estimatedTime(), payload.steps(), payload.safetyNotes());
        assertThatThrownBy(() -> service.storeGuide(project, eligible, wrongCurrency, metadata)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.latestGuide(project, eligible)).isInstanceOf(NoSuchElementException.class);
    }
}
