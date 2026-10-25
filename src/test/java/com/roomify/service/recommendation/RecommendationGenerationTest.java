package com.roomify.service.recommendation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomify.ai.AiDesignProvider;
import com.roomify.ai.AiProviderQuotaExhaustedException;
import com.roomify.service.DesignService;
import com.roomify.support.TestUserSecurity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ROOM-97/99/100: shopping recommendations and DIY guides generated from the saved design. */
@SpringBootTest
@AutoConfigureMockMvc
class RecommendationGenerationTest {

    static final String PLAN_PROMPT = "You are Roomify's constraint-aware";
    static final String ITEMS_PROMPT = "You are Roomify's budget-aware furniture";
    static final String DIY_PROMPT = "You are Roomify's DIY assistant";

    @Autowired JdbcTemplate jdbc;
    @Autowired DesignService designs;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean AiDesignProvider provider;

    final List<UUID> projects = new ArrayList<>();
    UUID project;

    @BeforeEach
    void setUp() {
        when(provider.getModelName()).thenReturn("test-model");
        project = seed(false);
    }

    @AfterEach
    void cleanUp() {
        projects.forEach(id -> jdbc.update("DELETE FROM room_projects WHERE project_id = ?", id));
    }

    @Test
    void generatesSetWithServerComputedTotalsAndPlacement() throws Exception {
        items(item("Desk lamp", "39.999", "true", "On the desk, left of the monitor", alt("Clip lamp", "19.50")),
                item("Storage basket", "25", "false", null, "null"));

        mvc.perform(post("/api/projects/{p}/recommendations", project))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estimatedTotal").value(65.00))
                .andExpect(jsonPath("$.remainingBudget").value(235.00))
                .andExpect(jsonPath("$.items[0].estimatedPrice").value(40.00))
                .andExpect(jsonPath("$.items[0].placement").value("On the desk, left of the monitor"))
                .andExpect(jsonPath("$.items[0].cheaperAlternative.estimatedPrice").value(19.5))
                .andExpect(jsonPath("$.items[1].placement").doesNotExist());
        mvc.perform(get("/api/projects/{p}/recommendations", project))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void retriesWithBudgetFeedbackThenStores() throws Exception {
        when(provider.generateJson(startsWith(ITEMS_PROMPT), anyString()))
                .thenReturn(itemsJson(item("Sofa", "900", "false", "Under the window", "null")))
                .thenReturn(itemsJson(item("Floor cushion", "60", "false", "Under the window", "null")));

        mvc.perform(post("/api/projects/{p}/recommendations", project))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].name").value("Floor cushion"));
        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        verify(provider, times(2)).generateJson(startsWith(ITEMS_PROMPT), prompts.capture());
        assertThat(prompts.getAllValues().get(1)).contains("exceeds the budget of 300.00");
    }

    @Test
    void invalidOutputTwiceReturns503AndStoresNothing() throws Exception {
        when(provider.generateJson(startsWith(ITEMS_PROMPT), anyString())).thenReturn("{\"items\":[]}");
        mvc.perform(post("/api/projects/{p}/recommendations", project))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("INVALID_RECOMMENDATIONS"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_sets WHERE project_id = ?", Integer.class, project))
                .isZero();
    }

    @Test
    void exhaustedQuotaReturns503WithoutRetryingAndStoresNothing() throws Exception {
        when(provider.generateJson(startsWith(ITEMS_PROMPT), anyString()))
                .thenThrow(new AiProviderQuotaExhaustedException("AI quota exhausted."));
        mvc.perform(post("/api/projects/{p}/recommendations", project))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_QUOTA_EXHAUSTED"));
        verify(provider, times(1)).generateJson(startsWith(ITEMS_PROMPT), anyString());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_sets WHERE project_id = ?", Integer.class, project))
                .isZero();
    }

    @Test
    void rejectsRentalDamagingItems() throws Exception {
        UUID rental = seed(true);
        when(provider.generateJson(startsWith(ITEMS_PROMPT), anyString()))
                .thenReturn(itemsJson(item("Shelves that drill into the wall", "50", "false", "Wall", "null")));
        mvc.perform(post("/api/projects/{p}/recommendations", rental))
                .andExpect(status().isServiceUnavailable());
        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        verify(provider, times(2)).generateJson(startsWith(ITEMS_PROMPT), prompts.capture());
        assertThat(prompts.getAllValues().get(1)).contains("removable and damage-free");
    }

    @Test
    void requiresASucceededDesign() throws Exception {
        UUID bare = UUID.randomUUID();
        projects.add(bare);
        jdbc.update("INSERT INTO room_projects(project_id, user_id) VALUES (?, ?)", bare, TestUserSecurity.USER_ID);
        mvc.perform(post("/api/projects/{p}/recommendations", bare)).andExpect(status().isConflict());
        verify(provider, never()).generateJson(startsWith(ITEMS_PROMPT), anyString());
    }

    @Test
    void generatesSafeDiyGuideOnceAndRejectsUnsafeOrIneligible() throws Exception {
        items(item("Desk lamp", "40", "true", "On the desk", "null"), item("Rug", "80", "false", "Floor", "null"));
        JsonNode set = mapper.readTree(mvc.perform(post("/api/projects/{p}/recommendations", project))
                .andReturn().getResponse().getContentAsString());
        String lamp = set.at("/items/0/itemId").asText();
        String rug = set.at("/items/1/itemId").asText();

        when(provider.generateJson(startsWith(DIY_PROMPT), anyString()))
                .thenReturn(guide("Easy", "55", "Rewire the lamp base"))
                .thenReturn(guide("Beginner", "15", "Wrap the shade in fabric"));
        mvc.perform(post("/api/projects/{p}/recommendations/{i}/diy", project, lamp))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estimatedCost").value(15.00))
                .andExpect(jsonPath("$.steps[0]").value("Wrap the shade in fabric"));
        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        verify(provider, times(2)).generateJson(startsWith(DIY_PROMPT), prompts.capture());
        assertThat(prompts.getAllValues().get(1)).contains("must not exceed the purchase price");

        // The saved guide is returned without another provider call.
        mvc.perform(post("/api/projects/{p}/recommendations/{i}/diy", project, lamp)).andExpect(status().isCreated());
        verify(provider, times(2)).generateJson(startsWith(DIY_PROMPT), anyString());

        mvc.perform(post("/api/projects/{p}/recommendations/{i}/diy", project, rug)).andExpect(status().isConflict());
    }

    @Test
    void unsafeDiyGuideIsNeverStored() throws Exception {
        items(item("Pendant light", "60", "true", "Above the desk", "null"));
        String light = mapper.readTree(mvc.perform(post("/api/projects/{p}/recommendations", project))
                .andReturn().getResponse().getContentAsString()).at("/items/0/itemId").asText();
        when(provider.generateJson(startsWith(DIY_PROMPT), anyString()))
                .thenReturn(guide("Beginner", "20", "Connect the wiring to the ceiling junction box"));
        mvc.perform(post("/api/projects/{p}/recommendations/{i}/diy", project, light))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("INVALID_DIY_GUIDE"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_diy_guides", Integer.class)).isZero();
    }

    private UUID seed(boolean rental) {
        UUID id = UUID.randomUUID();
        projects.add(id);
        jdbc.update("INSERT INTO room_projects(project_id, status, user_id) VALUES (?, 'PREFERENCES_READY', ?)",
                id, TestUserSecurity.USER_ID);
        jdbc.update("""
                INSERT INTO project_preferences(project_id, style, budget_amount, currency, room_purpose, rental_friendly)
                VALUES (?, 'MINIMALIST', 300, 'USD', 'STUDY', ?)
                """, id, rental);
        Long image = jdbc.queryForObject("""
                INSERT INTO room_images(project_id, bucket, storage_key, original_filename, mime_type, file_size_bytes)
                VALUES (?, 'test', 'rooms/x.png', 'room.png', 'image/png', 100) RETURNING image_id
                """, Long.class, id);
        Long object = jdbc.queryForObject("""
                INSERT INTO detected_objects(project_id, image_id, object_class, confidence, x_min, y_min, x_max, y_max, model_version)
                VALUES (?, ?, 'desk', 0.9, 0.1, 0.1, 0.5, 0.5, 'cv-test') RETURNING object_id
                """, Long.class, id, image);
        jdbc.update("INSERT INTO furniture_decisions(object_id, decision) VALUES (?, 'KEEP')", object);
        UUID desk = jdbc.queryForObject("SELECT object_uuid FROM detected_objects WHERE object_id = ?", UUID.class, object);
        when(provider.generateJson(startsWith(PLAN_PROMPT), anyString())).thenReturn("""
                {"planVersion":"1","summary":"A focused study corner","designStyle":"MINIMALIST",
                 "actions":[{"objectId":"%s","objectClass":"desk","decision":"KEEP","type":"KEEP",
                 "instruction":"Keep the desk by the window","rationale":"Good natural light"}],
                 "layoutNotes":[],"budgetAllocation":[{"category":"lighting","item":"Desk lamp","purpose":"Task light","estimatedCost":40}],
                 "estimatedTotalCost":40,"currency":"USD","warnings":[],
                 "itemsNeeded":[{"objectId":null,"name":"Desk lamp","quantity":1}],
                 "rentalFriendlyWarnings":%s,"constraints":%s}
                """.formatted(desk, rental ? "[\"Check your lease\"]" : "[]", rental ? "[\"No drilling\"]" : "[]"));
        designs.generate(id);
        return id;
    }

    private void items(String... items) {
        when(provider.generateJson(startsWith(ITEMS_PROMPT), anyString())).thenReturn(itemsJson(items));
    }

    private static String itemsJson(String... items) {
        return "{\"items\":[" + String.join(",", items) + "]}";
    }

    private static String item(String name, String price, String diy, String placement, String alternative) {
        return """
                {"name":"%s","category":"decor","reason":"Fits the plan","placement":%s,"estimatedPrice":%s,
                 "cheaperAlternative":%s,"searchQuery":"%s","diyEligible":%s}
                """.formatted(name, placement == null ? "null" : "\"" + placement + "\"", price, alternative,
                name.toLowerCase(), diy);
    }

    private static String alt(String name, String price) {
        return "{\"name\":\"%s\",\"reason\":\"Cheaper\",\"searchQuery\":\"%s\",\"estimatedPrice\":%s}"
                .formatted(name, name.toLowerCase(), price);
    }

    private static String guide(String difficulty, String cost, String step) {
        return """
                {"schemaVersion":"1.0","currency":"USD","materials":["fabric"],"tools":["scissors"],
                 "estimatedCost":%s,"difficulty":"%s","estimatedTime":"1 hour",
                 "steps":["%s","Let it dry"],"safetyNotes":["Unplug the lamp first"]}
                """.formatted(cost, difficulty, step);
    }
}
