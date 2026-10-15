package com.roomify.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "preferences.max-budget-amount=10000")
@AutoConfigureMockMvc
@Transactional
class ProjectPreferencesControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private UUID project() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO room_projects(project_id) VALUES (?)", id);
        return id;
    }
    private String body(String budget) {
        return """
            {"style":"SCANDINAVIAN","budgetAmount":%s,"currency":"TWD",
             "preferredColors":["sage", "cream"],"roomPurpose":"STUDY_AND_SLEEP",
             "specialRequirements":"Add storage without moving the bed."}
            """.formatted(budget);
    }
    @Test void savesUpdatesAndIncludesPreferencesInSnapshot() throws Exception {
        UUID id = project();
        mvc.perform(put("/api/projects/{id}/preferences", id).contentType(MediaType.APPLICATION_JSON).content(body("9000")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.rentalFriendly").value(false));
        mvc.perform(put("/api/projects/{id}/preferences", id).contentType(MediaType.APPLICATION_JSON)
                .content(body("8000").replace("\"currency\":\"TWD\"", "\"currency\":\"USD\",\"rentalFriendly\":true")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.rentalFriendly").value(true));
        mvc.perform(get("/api/projects/{id}/preferences", id))
            .andExpect(status().isOk()).andExpect(jsonPath("$.budgetAmount").value(8000))
            .andExpect(jsonPath("$.preferredColors[0]").value("sage"));
        mvc.perform(get("/api/projects/{id}", id))
            .andExpect(status().isOk()).andExpect(jsonPath("$.preferences.budgetAmount").value(8000));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_preferences WHERE project_id = ?", Integer.class, id)).isEqualTo(1);
    }
    @Test void returnsEmptyAndConfiguredLimitBeforeFirstSave() throws Exception {
        UUID id = project();
        mvc.perform(get("/api/projects/{id}/preferences", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/projects/{id}/preferences/limits", id))
            .andExpect(status().isOk()).andExpect(jsonPath("$.maxBudgetAmount").value(10000));
    }
    @Test void rejectsZeroNegativeOverMaximumAndFractionalCents() throws Exception {
        UUID id = project();
        for (String budget : new String[]{"0", "-1", "10001", "0.001"}) {
            mvc.perform(put("/api/projects/{id}/preferences", id).contentType(MediaType.APPLICATION_JSON).content(body(budget)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.budgetAmount").exists());
        }
    }
    @Test void acceptsConfiguredMaximum() throws Exception {
        mvc.perform(put("/api/projects/{id}/preferences", project()).contentType(MediaType.APPLICATION_JSON).content(body("10000")))
            .andExpect(status().isOk());
    }
    @Test void validatesStyleCurrencyAndText() throws Exception {
        UUID id = project();
        for (String payload : new String[]{body("10").replace("SCANDINAVIAN", "UNKNOWN"),
                body("10").replace("TWD", "EUR"), body("10").replace("Add storage without moving the bed.", "x".repeat(1001))}) {
            mvc.perform(put("/api/projects/{id}/preferences", id).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }
    @Test void rejectsUnknownProject() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(get("/api/projects/{id}/preferences", id)).andExpect(status().isNotFound());
        mvc.perform(put("/api/projects/{id}/preferences", id).contentType(MediaType.APPLICATION_JSON).content(body("10")))
            .andExpect(status().isNotFound());
    }
    @Test void rejectsMalformedJsonAndUuid() throws Exception {
        mvc.perform(put("/api/projects/{id}/preferences", project()).contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/projects/bad-id/preferences")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PROJECT_ID"));
    }
}
