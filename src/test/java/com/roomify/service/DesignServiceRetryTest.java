package com.roomify.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomify.dto.ProjectPreferencesResponse;
import com.roomify.ai.AiDesignProvider;
import com.roomify.exception.DesignGenerationException;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;

class DesignServiceRetryTest {

    @Test
    void throwsControlledFailureAfterTwoInvalidAiResponses() {

        // Arrange: Mock AI and prompt generation.
        AiDesignProvider aiProvider = mock(AiDesignProvider.class);
        DesignPromptFactory promptFactory = mock(DesignPromptFactory.class);
        DesignPlanValidator validator = mock(DesignPlanValidator.class);

        ProjectPreferencesResponse preferences =
                mock(ProjectPreferencesResponse.class);

        when(promptFactory.systemPrompt())
                .thenReturn("system prompt");

        when(promptFactory.userPrompt(
                any(),
                anyList(),
                nullable(String.class)
        )).thenReturn("user prompt");

        // AI always returns malformed JSON.
        when(aiProvider.generateJson(
                "system prompt",
                "user prompt"
        )).thenReturn("{invalid json");

        DesignService service = new DesignService(
                null,
                null,
                null,
                null,
                promptFactory,
                validator,
                aiProvider,
                new ObjectMapper()
        );

        // Act & Assert: Two failed attempts cause controlled failure.
        assertThrows(
                DesignGenerationException.class,
                () -> ReflectionTestUtils.invokeMethod(
                        service,
                        "generateValidPlan",
                        preferences,
                        List.of()
                )
        );

        // Initial generation + exactly one retry.
        verify(aiProvider, times(2))
                .generateJson("system prompt", "user prompt");

        // Invalid JSON must never reach plan validation.
        verifyNoInteractions(validator);
    }
}