package com.roomify.ai;

import com.roomify.config.AiProperties;
import com.roomify.config.ImageGenerationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}