package com.roomify.client;

import com.roomify.config.VisionServiceProperties;
import com.roomify.dto.vision.VisionAnalysisRequest;
import com.roomify.dto.vision.VisionAnalysisResponse;
import com.roomify.entity.RoomImage;
import com.roomify.exception.VisionServiceException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class VisionServiceClient {

    private final RestClient restClient;
    private final VisionServiceProperties properties;

    public VisionServiceClient(
            @Qualifier("visionRestClient") RestClient restClient,
            VisionServiceProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public VisionAnalysisResponse analyze(RoomImage image) {
        try {
            VisionAnalysisResponse response = restClient.post()
                    .uri(properties.getAnalysisPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new VisionAnalysisRequest(
                            image.getStorageKey(),
                            image.getMimeType()
                    ))
                    .retrieve()
                    .body(VisionAnalysisResponse.class);

            if (response == null || !"completed".equalsIgnoreCase(response.status())) {
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