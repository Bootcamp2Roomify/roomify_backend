package com.roomify.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomify.dto.ProjectPreferencesRequest;
import com.roomify.dto.ProjectPreferencesResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ProjectPreferencesService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final BigDecimal maxBudget;

    public ProjectPreferencesService(JdbcTemplate jdbc, ObjectMapper mapper,
            @Value("${preferences.max-budget-amount:1000000}") BigDecimal maxBudget) {
        if (maxBudget.signum() <= 0 || maxBudget.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new IllegalArgumentException("Preference budget maximum is outside the supported range.");
        }
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.maxBudget = maxBudget;
    }

    public BigDecimal maxBudget() { return maxBudget; }

    @Transactional(readOnly = true)
    public ProjectPreferencesResponse get(UUID projectId) {
        requireProject(projectId);
        return find(projectId);
    }

    @Transactional(readOnly = true)
    public ProjectPreferencesResponse find(UUID projectId) {
        var values = jdbc.query("SELECT * FROM project_preferences WHERE project_id = ?",
            (rs, row) -> new ProjectPreferencesResponse(projectId, rs.getString("style"),
                rs.getBigDecimal("budget_amount"), rs.getString("currency"),
                readColors(rs.getString("preferred_colors")), rs.getString("room_purpose"),
                rs.getBoolean("rental_friendly"), rs.getString("special_requirements"), maxBudget),
            projectId);
        return values.isEmpty() ? null : values.getFirst();
    }

    @Transactional
    public ProjectPreferencesResponse save(UUID projectId, ProjectPreferencesRequest request) {
        requireProject(projectId);
        if (request.budgetAmount().compareTo(maxBudget) > 0) {
            throw new BudgetLimitException("Budget must be at most " + maxBudget.toPlainString() + ".");
        }
        jdbc.update("""
            INSERT INTO project_preferences
                (project_id, style, budget_amount, currency, preferred_colors,
                 room_purpose, rental_friendly, special_requirements)
            VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?)
            ON CONFLICT (project_id) DO UPDATE SET
                style = EXCLUDED.style, budget_amount = EXCLUDED.budget_amount,
                currency = EXCLUDED.currency, preferred_colors = EXCLUDED.preferred_colors,
                room_purpose = EXCLUDED.room_purpose, rental_friendly = EXCLUDED.rental_friendly,
                special_requirements = EXCLUDED.special_requirements, updated_at = CURRENT_TIMESTAMP
            """, projectId, request.style(), request.budgetAmount(), request.currency(),
            writeColors(request.preferredColors() == null ? List.of() : request.preferredColors()),
            request.roomPurpose(), Boolean.TRUE.equals(request.rentalFriendly()),
            request.specialRequirements() == null ? "" : request.specialRequirements().strip());
        return find(projectId);
    }

    private void requireProject(UUID projectId) {
        if (Boolean.FALSE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM room_projects WHERE project_id = ?)", Boolean.class, projectId))) {
            throw new NoSuchElementException("Room project not found: " + projectId);
        }
    }

    private String writeColors(List<String> colors) {
        try { return mapper.writeValueAsString(colors.stream().map(String::strip).distinct().toList()); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot encode preferred colors.", e); }
    }

    private List<String> readColors(String json) {
        try { return mapper.readValue(json, new TypeReference<List<String>>() {}); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot read preferred colors.", e); }
    }

    public static class BudgetLimitException extends IllegalArgumentException {
        public BudgetLimitException(String message) { super(message); }
    }
}
