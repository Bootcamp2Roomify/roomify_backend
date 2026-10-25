package com.roomify.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.roomify.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

@Component
@ConditionalOnProperty(
        prefix = "ai",
        name = "provider",
        havingValue = "openai",
        matchIfMissing = true
)
public class OpenAiDesignProvider implements AiDesignProvider {

    private static final Logger logger =
            LoggerFactory.getLogger(OpenAiDesignProvider.class);

    private final RestClient restClient;
    private final AiProperties properties;

    public OpenAiDesignProvider(
            @Qualifier("aiRestClient") RestClient restClient,
            AiProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }


    @Override
    public String generateJson(String systemPrompt, String userPrompt) {
        validateConfiguration();
        OpenAiChatRequest request = new OpenAiChatRequest(
                properties.getModel(),
                List.of(
                        new ChatMessage("system", systemPrompt),
                        new ChatMessage("user", userPrompt)
                ),
                new ResponseFormat("json_object"),
                properties.getMaxOutputTokens(),
                properties.getTemperature(),
                properties.getReasoningEffort()
        );

        int totalAttempts = properties.getMaxRetries() + 1;

        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                return sendRequest(request);
            } catch (AiProviderRetryableException exception) {
                if (attempt == totalAttempts) {
                    throw exception;
                }

                logger.warn(
                        "OpenAI design request failed with a retryable error. "
                                + "Attempt {}/{}.",
                        attempt,
                        totalAttempts
                );
            }
        }

        throw new AiProviderRetryableException(
                "OpenAI design request could not be completed.",
                null
        );
    }

    @Override
    public String getModelName() {
        return properties.getModel();
    }

        private void validateConfiguration() {
        if (!properties.hasUsableApiKey()) {
            throw new AiProviderConfigurationException(
                    "AI_API_KEY is required when AI_PROVIDER=openai."
            );
        }

        if (!StringUtils.hasText(properties.getModel())) {
            throw new AiProviderConfigurationException(
                    "AI_MODEL is required when AI_PROVIDER=openai."
            );
        }
    }
    private String sendRequest(OpenAiChatRequest request) {
        long started = System.nanoTime();
        try {
            OpenAiChatResponse response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers ->
                            headers.setBearerAuth(properties.getApiKey()))
                    .body(request)
                    .retrieve()
                    .body(OpenAiChatResponse.class);

            if (response == null
                    || response.choices() == null
                    || response.choices().isEmpty()
                    || response.choices().get(0).message() == null
                    || !StringUtils.hasText(
                            response.choices().get(0).message().content())) {
                throw new IllegalStateException(
                        "OpenAI returned an empty design response."
                );
            }

            // Content-free usage line for cost and latency measurement.
            Usage usage = response.usage();
            logger.info("AI usage kind=text model={} promptTokens={} completionTokens={} reasoningTokens={} latencyMs={}",
                    request.model(),
                    usage == null ? null : usage.promptTokens(),
                    usage == null ? null : usage.completionTokens(),
                    usage == null || usage.completionTokensDetails() == null ? null : usage.completionTokensDetails().reasoningTokens(),
                    (System.nanoTime() - started) / 1_000_000);
            return response.choices().get(0).message().content();
        } catch (RestClientResponseException exception) {
            int statusCode = exception.getStatusCode().value();

            logger.warn(
                    "OpenAI design request received HTTP status {} ({}).",
                    statusCode,
                    OpenAiErrors.describe(exception.getResponseBodyAsString())
            );

            // A rejected key is a configuration problem, not a transient failure.
            if (statusCode == 401 || statusCode == 403) {
                throw new AiProviderConfigurationException("OpenAI rejected the configured API key.");
            }

            // No credit left: retrying cannot succeed until the owner tops up the account.
            if (statusCode == 429 && OpenAiErrors.isQuotaExhausted(exception.getResponseBodyAsString())) {
                throw new AiProviderQuotaExhaustedException("AI quota exhausted.");
            }

            if (statusCode == 408 || statusCode == 429 || statusCode >= 500) {
                throw new AiProviderRetryableException(
                        "OpenAI is temporarily unavailable. Please try again.",
                        exception
                );
            }

            throw new IllegalStateException(
                    "OpenAI design request failed with HTTP status "
                            + statusCode
                            + "."
            );
        } catch (RestClientException exception) {
            throw new AiProviderRetryableException(
                    "OpenAI request timed out or could not reach the provider.",
                    exception
            );
        }
    }

    public record OpenAiChatRequest(
            String model,
            List<ChatMessage> messages,
            @JsonProperty("response_format") ResponseFormat responseFormat,
            // max_completion_tokens is accepted by current and older chat models; max_tokens is not.
            @JsonProperty("max_completion_tokens") int maxCompletionTokens,
            @JsonInclude(JsonInclude.Include.NON_NULL) Double temperature,
            @JsonInclude(JsonInclude.Include.NON_NULL) @JsonProperty("reasoning_effort") String reasoningEffort
    ) {
    }

    public record ChatMessage(String role, String content) {
    }

    public record ResponseFormat(String type) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OpenAiChatResponse(List<Choice> choices, Usage usage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens,
            @JsonProperty("completion_tokens_details") CompletionDetails completionTokensDetails
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CompletionDetails(@JsonProperty("reasoning_tokens") Integer reasoningTokens) {
    }

    public record Choice(ChatMessage message) {
    }
}