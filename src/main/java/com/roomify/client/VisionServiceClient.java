package com.roomify.client;

import com.roomify.config.VisionServiceProperties;
import com.roomify.dto.AnalysisInput;
import com.roomify.entity.RoomImage;
import com.roomify.exception.VisionServiceException;
import com.roomify.storage.StorageService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class VisionServiceClient {

    private final RestClient restClient;
    private final VisionServiceProperties properties;
    private final StorageService storageService;

    public VisionServiceClient(
            @Qualifier("visionRestClient") RestClient restClient,
            VisionServiceProperties properties,
            StorageService storageService
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.storageService = storageService;
    }

    public AnalysisInput analyze(RoomImage image) {
        byte[] imageBytes = storageService.download(image.getStorageKey());

        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", new ByteArrayResource(imageBytes) {
                    @Override
                    public String getFilename() {
                        return image.getOriginalFilename();
                    }
                })
                .contentType(MediaType.parseMediaType(image.getMimeType()));

        try {
            AnalysisInput response = restClient.post()
                    .uri(properties.getAnalysisPath())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body.build())
                    .retrieve()
                    .body(AnalysisInput.class);

            if (response == null || response.modelVersion() == null) {
                throw new VisionServiceException(
                        "Vision service returned an incomplete analysis response."
                );
            }

            return response;
        } catch (RestClientException exception) {
            throw new VisionServiceException(
                    "Vision service is unavailable. Please retry the analysis.",
                    exception
            );
        }
    }
}
