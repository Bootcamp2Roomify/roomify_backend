package com.roomify.controller;

import com.roomify.dto.analysis.AnalyzeProjectResponse;
import com.roomify.dto.analysis.BoundingBoxResponse;
import com.roomify.dto.analysis.DetectedObjectResponse;
import com.roomify.entity.FurnitureDecisionType;
import com.roomify.entity.RoomProject;
import com.roomify.entity.RoomProjectStatus;
import com.roomify.exception.NoActiveImageException;
import com.roomify.exception.ProjectExceptionHandler;
import com.roomify.service.RoomProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
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
                        "Room project not found: " + unknownId
                ));

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

    @Test
    void analyzeProject_whenSuccessful_returnsSavedObjects() throws Exception {
        UUID projectId = UUID.randomUUID();

        DetectedObjectResponse detectedObject = new DetectedObjectResponse(
                42L,
                "chair",
                new BigDecimal("0.91"),
                new BoundingBoxResponse(
                        new BigDecimal("0.12"),
                        new BigDecimal("0.08"),
                        new BigDecimal("0.36"),
                        new BigDecimal("0.44")
                ),
                FurnitureDecisionType.UNSURE
        );

        AnalyzeProjectResponse response = new AnalyzeProjectResponse(
                projectId,
                RoomProjectStatus.ANALYZED,
                List.of(detectedObject)
        );

        given(service.analyzeProject(projectId)).willReturn(response);

        mockMvc.perform(post("/api/projects/{id}/analysis", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.status").value("ANALYZED"))
                .andExpect(jsonPath("$.objects[0].id").value(42))
                .andExpect(jsonPath("$.objects[0].label").value("chair"))
                .andExpect(jsonPath("$.objects[0].confidence").value(0.91))
                .andExpect(jsonPath("$.objects[0].bbox.xMin").value(0.12))
                .andExpect(jsonPath("$.objects[0].bbox.yMin").value(0.08))
                .andExpect(jsonPath("$.objects[0].bbox.xMax").value(0.36))
                .andExpect(jsonPath("$.objects[0].bbox.yMax").value(0.44))
                .andExpect(jsonPath("$.objects[0].decision").value("UNSURE"));
    }

    @Test
    void analyzeProject_withoutImage_returns409AndClearMessage()
            throws Exception {
        UUID projectId = UUID.randomUUID();

        given(service.analyzeProject(projectId))
                .willThrow(new NoActiveImageException(
                        "Project has no room image to analyze."
                ));

        mockMvc.perform(post("/api/projects/{id}/analysis", projectId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_ACTIVE_IMAGE"))
                .andExpect(jsonPath("$.message")
                        .value("Project has no room image to analyze."));
    }
}