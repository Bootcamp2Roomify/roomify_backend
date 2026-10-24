package com.roomify.controller;

import com.roomify.support.TestUserSecurity;
import com.roomify.dto.recommendation.GenerationMetadata;
import com.roomify.service.recommendation.RecommendationContractCodec;
import com.roomify.service.recommendation.RecommendationResultService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RecommendationControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired RecommendationResultService service;
    @Autowired RecommendationContractCodec codec;
    final GenerationMetadata metadata = new GenerationMetadata("local", "fixture", "1.0", null);
    UUID project() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO room_projects(project_id, user_id) VALUES (?, ?)", id, TestUserSecurity.USER_ID);
        jdbc.update("INSERT INTO project_preferences(project_id, style, budget_amount, currency, room_purpose) VALUES (?, 'MINIMALIST', 100, 'USD', 'LIVING')", id);
        return id;
    }
    Long design(UUID id) {
        return jdbc.queryForObject("INSERT INTO design_results(project_id, plan_json, estimated_total_cost, currency, model_name, completed_at) VALUES (?, '{}'::jsonb, 50, 'USD', 'fixture', CURRENT_TIMESTAMP) RETURNING design_id", Long.class, id);
    }
    String fixture(String name) throws Exception {
        return Files.readString(Path.of("contracts/recommendations/v1/examples/" + name + ".json"));
    }

    @Test void returnsSavedSetAndGuideWithStableIdsAndNumericMoney() throws Exception {
        UUID project = project();
        var set = service.storeSet(project, design(project), codec.readSet(fixture("recommendation-set")), metadata);
        UUID item = set.items().get(1).itemId();
        var guide = service.storeGuide(project, item, codec.readGuide(fixture("diy-guide")), metadata);
        mvc.perform(get("/api/projects/{project}/recommendations", project))
                .andExpect(status().isOk()).andExpect(jsonPath("$.setId").value(set.setId().toString()))
                .andExpect(jsonPath("$.projectId").value(project.toString()))
                .andExpect(jsonPath("$.items[0].itemId").value(set.items().getFirst().itemId().toString()))
                .andExpect(jsonPath("$.items[1].name").value("Storage basket"))
                .andExpect(jsonPath("$.estimatedTotal").isNumber()).andExpect(jsonPath("$.estimatedTotal").value(29.99))
                .andExpect(jsonPath("$.metadata.provider").value("local"));
        mvc.perform(get("/api/projects/{project}/recommendations/{item}/diy", project, item))
                .andExpect(status().isOk()).andExpect(jsonPath("$.guideId").value(guide.guideId().toString()))
                .andExpect(jsonPath("$.itemId").value(item.toString()))
                .andExpect(jsonPath("$.steps[0]").value("Measure the shelf."));
        assertThat(jdbc.queryForObject("SELECT status FROM room_projects WHERE project_id = ?", String.class, project)).isEqualTo("CREATED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_sets WHERE project_id = ?", Integer.class, project)).isEqualTo(1);
    }

    @Test void returnsSafe404ForMissingResultsAndCrossProjectGuide() throws Exception {
        UUID project = project(); UUID other = project();
        mvc.perform(get("/api/projects/{project}/recommendations", project))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RECOMMENDATION_NOT_FOUND"));
        // Unknown or foreign projects are rejected by the ownership check before the controller.
        mvc.perform(get("/api/projects/{project}/recommendations", UUID.randomUUID()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
        var set = service.storeSet(project, design(project), codec.readSet(fixture("recommendation-set")), metadata);
        UUID item = set.items().get(1).itemId();
        mvc.perform(get("/api/projects/{project}/recommendations/{item}/diy", project, item)).andExpect(status().isNotFound());
        service.storeGuide(project, item, codec.readGuide(fixture("diy-guide")), metadata);
        mvc.perform(get("/api/projects/{project}/recommendations/{item}/diy", other, item))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RECOMMENDATION_NOT_FOUND"));
    }

    @Test void malformedProjectAndItemIdentifiersReturn400() throws Exception {
        mvc.perform(get("/api/projects/bad-id/recommendations")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ID"));
        mvc.perform(get("/api/projects/{project}/recommendations/bad-item/diy", project()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ID"));
    }
}
