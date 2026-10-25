package com.roomify.ai;

import com.roomify.config.AiProperties;
import com.roomify.config.ImageGenerationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class OpenAiDesignImageProviderTest {

    private AiProperties aiProperties;
    private ImageGenerationProperties imageProperties;
    private OpenAiDesignImageProvider provider;

    @BeforeEach
    void setUp() {
        aiProperties = new AiProperties();
        aiProperties.setApiKey("test-api-key");

        imageProperties = new ImageGenerationProperties();
        imageProperties.setModel("gpt-image-1");
        imageProperties.setSize("1024x1024");
        imageProperties.setQuality("low");

        provider = new OpenAiDesignImageProvider(
                null,
                aiProperties,
                imageProperties
        );
    }

    @Test
    void returnsConfiguredImageModelName() {
        assertThat(provider.getModelName()).isEqualTo("gpt-image-1");
    }

    @Test
    void rejectsMissingOriginalImage() {
        assertThatThrownBy(() -> provider.generate(
                null,
                "room.png",
                "image/png",
                "Keep the bed."
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("An original room image is required.");
    }

    @Test
    void rejectsMissingDesignInstructions() {
        assertThatThrownBy(() -> provider.generate(
                new byte[]{1, 2, 3},
                "room.png",
                "image/png",
                ""
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Design instructions are required.");
    }

    @Test
    void failsClearlyWhenApiKeyIsMissing() {
        aiProperties.setApiKey("");

        assertThatThrownBy(() -> provider.generate(
                new byte[]{1, 2, 3},
                "room.png",
                "image/png",
                "Keep the bed."
        ))
                .isInstanceOf(AiProviderConfigurationException.class)
                .hasMessage("AI_API_KEY is required when AI_PROVIDER=openai.");
    }

    @Test
    void rejectsUnsupportedImageContentType() {
        assertThatThrownBy(() -> provider.generate(
                new byte[]{1, 2, 3},
                "room.gif",
                "image/gif",
                "Keep the bed."
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The original image must be a PNG, JPEG, or WebP file.");
    }

    @Test
    void failsClearlyWhenImageQualityIsMissing() {
        imageProperties.setQuality("");

        assertThatThrownBy(() -> provider.generate(
                new byte[]{1, 2, 3},
                "room.png",
                "image/png",
                "Keep the bed."
        ))
                .isInstanceOf(AiProviderConfigurationException.class)
                .hasMessage("AI_IMAGE_QUALITY is required when AI_PROVIDER=openai.");
    }

    @Test
    void exhaustedQuotaFailsImmediatelyWithAClearError() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.test/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var http = new OpenAiDesignImageProvider(builder.build(), aiProperties, imageProperties);
        server.expect(requestTo("https://api.test/v1/images/edits"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"type\":\"insufficient_quota\",\"code\":\"insufficient_quota\"}}"));
        assertThatThrownBy(() -> http.generate(new byte[]{1, 2, 3}, "room.png", "image/png", "Keep the bed."))
                .isInstanceOf(AiProviderQuotaExhaustedException.class)
                .hasMessage("AI quota exhausted.");
        server.verify();
    }

    @Test
    void rateLimitStaysRetryable() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.test/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var http = new OpenAiDesignImageProvider(builder.build(), aiProperties, imageProperties);
        server.expect(requestTo("https://api.test/v1/images/edits"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"type\":\"requests\",\"code\":\"rate_limit_exceeded\"}}"));
        assertThatThrownBy(() -> http.generate(new byte[]{1, 2, 3}, "room.png", "image/png", "Keep the bed."))
                .isInstanceOf(AiProviderRetryableException.class);
        server.verify();
    }
}
