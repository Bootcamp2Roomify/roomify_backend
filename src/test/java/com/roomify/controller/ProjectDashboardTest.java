package com.roomify.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MVP Features 2 and 11 through the real filter chain with two real accounts. */
@SpringBootTest
class ProjectDashboardTest {

    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper mapper;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void createsProjectWithDetailsAndShowsItInSnapshot() throws Exception {
        String token = token();
        JsonNode created = create(token, "  Study nook  ", "STUDY", "RENTAL");
        assertThat(created.get("name").asText()).isEqualTo("Study nook");
        assertThat(created.get("status").asText()).isEqualTo("CREATED");

        mvc.perform(get("/api/projects/{id}", created.get("id").asText()).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Study nook"))
                .andExpect(jsonPath("$.roomType").value("STUDY"))
                .andExpect(jsonPath("$.rentalStatus").value("RENTAL"));
    }

    @Test
    void rejectsInvalidProjectsWithoutCreatingThem() throws Exception {
        String token = token();
        for (String body : new String[]{"", "{}", "{\"name\":\"   \",\"roomType\":\"BEDROOM\",\"rentalStatus\":\"OWNED\"}",
                "{\"name\":\"x\",\"roomType\":\"KITCHEN\",\"rentalStatus\":\"OWNED\"}",
                "{\"name\":\"" + "x".repeat(101) + "\",\"roomType\":\"BEDROOM\",\"rentalStatus\":\"OWNED\"}"}) {
            mvc.perform(post("/api/projects").header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/projects").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projects.length()").value(0))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void listsOnlyOwnProjectsNewestFirstWithPaging() throws Exception {
        String owner = token();
        String stranger = token();
        create(owner, "First", "BEDROOM", "OWNED");
        create(owner, "Second", "LIVING_ROOM", "RENTAL");
        create(owner, "Third", "DORM_ROOM", "DORMITORY");
        create(stranger, "Not yours", "STUDIO", "RENTAL");

        mvc.perform(get("/api/projects").param("pageSize", "2").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projects.length()").value(2))
                .andExpect(jsonPath("$.projects[0].name").value("Third"))
                .andExpect(jsonPath("$.projects[0].hasImage").value(false))
                .andExpect(jsonPath("$.projects[0].hasDesign").value(false))
                .andExpect(jsonPath("$.projects[1].name").value("Second"))
                .andExpect(jsonPath("$.hasNext").value(true));
        mvc.perform(get("/api/projects").param("page", "2").param("pageSize", "2").header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$.projects.length()").value(1))
                .andExpect(jsonPath("$.projects[0].name").value("First"))
                .andExpect(jsonPath("$.hasNext").value(false));
        mvc.perform(get("/api/projects").header("Authorization", bearer(stranger)))
                .andExpect(jsonPath("$.projects.length()").value(1))
                .andExpect(jsonPath("$.projects[0].name").value("Not yours"));
        mvc.perform(get("/api/projects").param("pageSize", "51").header("Authorization", bearer(owner)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
    }

    private JsonNode create(String token, String name, String roomType, String rentalStatus) throws Exception {
        String body = mapper.writeValueAsString(java.util.Map.of("name", name, "roomType", roomType, "rentalStatus", rentalStatus));
        return mapper.readTree(mvc.perform(post("/api/projects").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private String token() throws Exception {
        String email = "dash-" + UUID.randomUUID() + "@roomify.test";
        String credentials = "{\"email\":\"" + email + "\",\"password\":\"password123\"}";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isCreated());
        return mapper.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andReturn().getResponse().getContentAsString()).get("accessToken").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
