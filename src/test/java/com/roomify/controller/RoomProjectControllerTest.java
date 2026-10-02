package com.roomify.controller;

import com.roomify.entity.RoomProject;
import com.roomify.exception.ProjectExceptionHandler;
import com.roomify.service.RoomProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoomProjectController.class)
class RoomProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoomProjectService service;

    @Test
    void createProject_returns201AndNewProjectData() throws Exception {
        RoomProject project = new RoomProject();

        given(service.createProject()).willReturn(project);

        mockMvc.perform(post("/api/projects"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(project.getId().toString()))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void getProject_whenProjectExists_returns200AndSnapshot() throws Exception {
        RoomProject project = new RoomProject();

        given(service.getProject(project.getId())).willReturn(project);

        mockMvc.perform(get("/api/projects/{id}", project.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(project.getId().toString()))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void getProject_whenProjectDoesNotExist_returns404AndApiError()
            throws Exception {
        UUID unknownId = UUID.randomUUID();

        given(service.getProject(unknownId))
                .willThrow(new NoSuchElementException(
                        "Room project not found: " + unknownId));

        mockMvc.perform(get("/api/projects/{id}", unknownId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void getProject_withInvalidUuid_returns400AndApiError() throws Exception {
        mockMvc.perform(get("/api/projects/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PROJECT_ID"))
                .andExpect(jsonPath("$.message").exists());
    }
}