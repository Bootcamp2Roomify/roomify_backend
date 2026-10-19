package com.roomify.client;

import com.roomify.config.OllamaProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Component
public class OllamaClient {

    private final RestClient restClient;
    private final OllamaProperties properties;

    public OllamaClient(
            @Qualifier("ollamaRestClient") RestClient restClient,
            OllamaProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public String generateJson(String systemPrompt, String userPrompt) {
        ChatRequest request = new ChatRequest(
                properties.getModel(),
                List.of(
                        new Message("system", systemPrompt),
                        new Message("user", userPrompt)
                ),
                false,
                designPlanSchema(),
                Map.of("temperature", 0.1)
        );

        try {
            ChatResponse response = restClient.post()
                    .uri(properties.getChatPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);

            if (response == null
                    || response.message() == null
                    || response.message().content() == null
                    || response.message().content().isBlank()) {
                throw new IllegalStateException(
                        "Ollama returned an empty design response."
                );
            }

            return response.message().content();
        } catch (RestClientException exception) {
            throw new IllegalStateException(
                    "Ollama is unavailable. Make sure the Ollama app is running.",
                    exception
            );
        }
    }

    private Map<String, Object> designPlanSchema() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of(
                        "designRationale",
                        "layoutChanges",
                        "budgetAllocation",
                        "estimatedTotalCost",
                        "currency",
                        "rentalFriendlyNotes",
                        "warnings"
                ),
                "properties", Map.of(
                        "designRationale", Map.of(
                                "type", "string"
                        ),
                        "layoutChanges", Map.of(
                                "type", "array",
                                "items", Map.of(
                                        "type", "object",
                                        "additionalProperties", false,
                                        "required", List.of(
                                                "objectUuid",
                                                "objectClass",
                                                "decision",
                                                "action",
                                                "rationale"
                                        ),
                                        "properties", Map.of(
                                                "objectUuid", Map.of(
                                                        "type", "string",
                                                        "format", "uuid"
                                                ),
                                                "objectClass", Map.of(
                                                        "type", "string"
                                                ),
                                                "decision", Map.of(
                                                        "type", "string",
                                                        "enum", List.of(
                                                                "KEEP",
                                                                "REPLACE",
                                                                "REMOVE",
                                                                "UNSURE"
                                                        )
                                                ),
                                                "action", Map.of(
                                                        "type", "string"
                                                ),
                                                "rationale", Map.of(
                                                        "type", "string"
                                                )
                                        )
                                )
                        ),
                        "budgetAllocation", Map.of(
                                "type", "array",
                                "items", Map.of(
                                        "type", "object",
                                        "additionalProperties", false,
                                        "required", List.of(
                                                "category",
                                                "item",
                                                "purpose",
                                                "estimatedCost"
                                        ),
                                        "properties", Map.of(
                                                "category", Map.of(
                                                        "type", "string"
                                                ),
                                                "item", Map.of(
                                                        "type", "string"
                                                ),
                                                "purpose", Map.of(
                                                        "type", "string"
                                                ),
                                                "estimatedCost", Map.of(
                                                        "type", "number",
                                                        "minimum", 0
                                                )
                                        )
                                )
                        ),
                        "estimatedTotalCost", Map.of(
                                "type", "number",
                                "minimum", 0
                        ),
                        "currency", Map.of(
                                "type", "string"
                        ),
                        "rentalFriendlyNotes", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string")
                        ),
                        "warnings", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string")
                        )
                )
        );
    }

    public record ChatRequest(
            String model,
            List<Message> messages,
            boolean stream,
            Object format,
            Map<String, Object> options
    ) {
    }

    public record Message(String role, String content) {
    }

    public record ChatResponse(Message message) {
    }
}