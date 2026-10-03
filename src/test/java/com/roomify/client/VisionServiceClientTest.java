package com.roomify.client;

import com.roomify.config.VisionServiceProperties;
import com.roomify.dto.vision.VisionAnalysisResponse;
import com.roomify.entity.RoomImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class VisionServiceClientTest {

    private MockRestServiceServer mockServer;
    private VisionServiceClient client;

    @BeforeEach
    void setUp() {
        VisionServiceProperties properties = new VisionServiceProperties();
        properties.setBaseUrl("http://vision-service.test");
        properties.setAnalysisPath("/api/v1/analysis");

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getBaseUrl());

        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new VisionServiceClient(builder.build(), properties);
    }

    @Test
    void analyze_mapsStubbedVisionResponse() {
        mockServer.expect(
                        requestTo("http://vision-service.test/api/v1/analysis")
                )
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "storageKey": "room-images/example.jpg",
                          "mimeType": "image/jpeg"
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "status": "completed",
                          "detections": [
                            {
                              "label": "chair",
                              "confidence": 0.91,
                              "bounding_box": {
                                "x": 120,
                                "y": 80,
                                "width": 240,
                                "height": 360
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        RoomImage image = new RoomImage(
                UUID.randomUUID(),
                "room-images/example.jpg",
                "example.jpg",
                "image/jpeg",
                2000L,
                1000,
                800
        );

        VisionAnalysisResponse response = client.analyze(image);

        assertThat(response.status()).isEqualTo("completed");
        assertThat(response.detections()).hasSize(1);
        assertThat(response.detections().getFirst().label())
                .isEqualTo("chair");

        mockServer.verify();
    }
}