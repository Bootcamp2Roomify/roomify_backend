package com.roomify.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.roomify.config.AiProperties;
import com.roomify.config.ImageGenerationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Base64;
import java.util.List;

@Component
@ConditionalOnProperty(
        prefix = "ai",
        name = "provider",
        havingValue = "openai",
        matchIfMissing = true
)
public class OpenAiDesignImageProvider implements DesignImageProvider {

    private static final Logger logger =
            LoggerFactory.getLogger(OpenAiDesignImageProvider.class);

    private final RestClient restClient;
    private final AiProperties aiProperties;
    private final ImageGenerationProperties imageProperties;

    public OpenAiDesignImageProvider(
            @Qualifier("aiRestClient") RestClient restClient,
            AiProperties aiProperties,
            ImageGenerationProperties imageProperties
    ) {
        this.restClient = restClient;
        this.aiProperties = aiProperties;
        this.imageProperties = imageProperties;
    }

    @Override
    public GeneratedDesignImage generate(
            byte[] originalImage,
            String originalFilename,
            String contentType,
            String designInstructions
    ) {
        validateConfiguration();
        validateRequest(
                originalImage,
                originalFilename,
                contentType,
                designInstructions
        );

        ByteArrayResource imageResource = new ByteArrayResource(originalImage) {
            @Override
            public String getFilename() {
                return originalFilename;
            }
        };

        HttpHeaders imageHeaders = new HttpHeaders();
        imageHeaders.setContentType(MediaType.parseMediaType(contentType));

        MultiValueMap<String, Object> requestBody = new LinkedMultiValueMap<>();
        requestBody.add("model", imageProperties.getModel());
        requestBody.add("prompt", designInstructions);
        requestBody.add("image", new HttpEntity<>(imageResource, imageHeaders));
        requestBody.add("n", "1");
        requestBody.add("size", imageProperties.getSize());
        requestBody.add("quality", imageProperties.getQuality());
        requestBody.add("output_format", "png");

        long started = System.nanoTime();
        try {
            OpenAiImageResponse response = restClient.post()
                    .uri("/images/edits")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .headers(headers ->
                            headers.setBearerAuth(aiProperties.getApiKey()))
                    .body(requestBody)
                    .retrieve()
                    .body(OpenAiImageResponse.class);

            ImageUsage usage = response == null ? null : response.usage();
            logger.info("AI usage kind=image model={} inputTokens={} outputTokens={} latencyMs={}",
                    imageProperties.getModel(),
                    usage == null ? null : usage.inputTokens(),
                    usage == null ? null : usage.outputTokens(),
                    (System.nanoTime() - started) / 1_000_000);
            return toGeneratedImage(response);

        } catch (RestClientResponseException exception) {
            int statusCode = exception.getStatusCode().value();

            logger.warn(
                    "OpenAI image-edit request received HTTP status {} ({}).",
                    statusCode,
                    OpenAiErrors.describe(exception.getResponseBodyAsString())
            );

            // A rejected key is a configuration problem, not a transient failure.
            if (statusCode == 401 || statusCode == 403) {
                throw new AiProviderConfigurationException("OpenAI rejected the configured API key.");
            }

            if (statusCode == 408 || statusCode == 429 || statusCode >= 500) {
                throw new AiProviderRetryableException(
                        "Image generation is temporarily unavailable. Please try again.",
                        exception
                );
            }

            throw new IllegalStateException(
                    "OpenAI image-edit request failed with HTTP status "
                            + statusCode
                            + "."
            );

        } catch (RestClientException exception) {
            throw new AiProviderRetryableException(
                    "Image generation timed out or could not reach the provider.",
                    exception
            );
        }
    }

    @Override
    public String getModelName() {
        return imageProperties.getModel();
    }

    private void validateConfiguration() {
        if (!aiProperties.hasUsableApiKey()) {
            throw new AiProviderConfigurationException(
                    "AI_API_KEY is required when AI_PROVIDER=openai."
            );
        }

        if (!StringUtils.hasText(imageProperties.getModel())) {
            throw new AiProviderConfigurationException(
                    "AI_IMAGE_MODEL is required when AI_PROVIDER=openai."
            );
        }

        if (!StringUtils.hasText(imageProperties.getSize())) {
            throw new AiProviderConfigurationException(
                    "AI_IMAGE_SIZE is required when AI_PROVIDER=openai."
            );
        }

        if (!StringUtils.hasText(imageProperties.getQuality())) {
            throw new AiProviderConfigurationException(
                    "AI_IMAGE_QUALITY is required when AI_PROVIDER=openai."
            );
        }
    }

    private void validateRequest(
            byte[] originalImage,
            String originalFilename,
            String contentType,
            String designInstructions
    ) {
        if (originalImage == null || originalImage.length == 0) {
            throw new IllegalArgumentException(
                    "An original room image is required."
            );
        }

        if (!StringUtils.hasText(originalFilename)) {
            throw new IllegalArgumentException(
                    "The original image filename is required."
            );
        }

        if (!isSupportedImageContentType(contentType)) {
            throw new IllegalArgumentException(
                    "The original image must be a PNG, JPEG, or WebP file."
            );
        }

        if (!StringUtils.hasText(designInstructions)) {
            throw new IllegalArgumentException(
                    "Design instructions are required."
            );
        }
    }

    private boolean isSupportedImageContentType(String contentType) {
        return MediaType.IMAGE_PNG_VALUE.equals(contentType)
                || MediaType.IMAGE_JPEG_VALUE.equals(contentType)
                || "image/webp".equals(contentType);
    }

    private GeneratedDesignImage toGeneratedImage(
            OpenAiImageResponse response
    ) {
        if (response == null
                || response.data() == null
                || response.data().isEmpty()
                || !StringUtils.hasText(response.data().get(0).base64Image())) {
            throw new IllegalStateException(
                    "OpenAI returned an empty generated image response."
            );
        }

        try {
            byte[] imageBytes = Base64.getDecoder()
                    .decode(response.data().get(0).base64Image());

            return new GeneratedDesignImage(
                    imageBytes,
                    MediaType.IMAGE_PNG_VALUE
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "OpenAI returned invalid generated image data.",
                    exception
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OpenAiImageResponse(List<ImageData> data, ImageUsage usage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImageUsage(
            @JsonProperty("input_tokens") Integer inputTokens,
            @JsonProperty("output_tokens") Integer outputTokens
    ) {
    }

    public record ImageData(
            @JsonProperty("b64_json") String base64Image
    ) {
    }
}