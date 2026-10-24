package com.roomify.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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

/** Exercises the real filter chain with real tokens; no default test user is applied here. */
@SpringBootTest
class AuthSecurityTest {

    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void registerLoginAndMeWithHashedPassword() throws Exception {
        String email = uniqueEmail();
        register(email, "correct horse");

        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, email);
        assertThat(hash).startsWith("$2").doesNotContain("correct horse");

        String loginToken = token(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email.toUpperCase(), "correct horse")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + loginToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.displayName").value("Tester"));
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
        String email = uniqueEmail();
        register(email, "password123");

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email.toUpperCase(), "password123")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void wrongPasswordAndUnknownEmailFailIdentically() throws Exception {
        String email = uniqueEmail();
        register(email, "password123");

        String wrongPassword = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, "password124")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(uniqueEmail(), "password123")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(wrongPassword).isEqualTo(unknownEmail).contains("INVALID_CREDENTIALS");
    }

    @Test
    void rejectsInvalidRegistration() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("not-an-email", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void protectedEndpointsRequireAValidToken() throws Exception {
        mvc.perform(post("/api/projects")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/projects/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test
    void otherUsersProjectsLookMissing() throws Exception {
        String owner = register(uniqueEmail(), "password123");
        String stranger = register(uniqueEmail(), "password123");

        String created = mvc.perform(post("/api/projects").header("Authorization", "Bearer " + owner))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String projectId = mapper.readTree(created).get("id").asText();

        mvc.perform(get("/api/projects/{id}", projectId).header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk());
        for (String path : new String[]{"", "/preferences", "/image/content", "/analysis", "/designs/latest"}) {
            mvc.perform(get("/api/projects/" + projectId + path).header("Authorization", "Bearer " + stranger))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
        }
        mvc.perform(get("/api/projects/not-a-uuid/recommendations").header("Authorization", "Bearer " + owner))
                .andExpect(status().isBadRequest());
    }

    /** Registers, then logs in, returning the access token. */
    private String register(String email, String password) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, password).replace("}", ",\"displayName\":\"Tester\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
        return token(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString());
    }

    private String token(String body) throws Exception {
        return mapper.readTree(body).get("accessToken").asText();
    }

    private String credentials(String email, String password) throws Exception {
        return mapper.writeValueAsString(new java.util.LinkedHashMap<>(java.util.Map.of(
                "email", email, "password", password)));
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@roomify.test";
    }
}
