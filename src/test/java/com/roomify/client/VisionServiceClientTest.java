package com.roomify.client;

import com.roomify.config.VisionServiceProperties;
import com.roomify.dto.AnalysisInput;
import com.roomify.entity.RoomImage;
import com.roomify.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class VisionServiceClientTest {

    private MockRestServiceServer mockServer;
    private VisionServiceClient client;
    private StorageService storageService;

    @BeforeEach
    void setUp() {
        VisionServiceProperties properties = new VisionServiceProperties();
        properties.setBaseUrl("http://vision-service.test");
        properties.setAnalysisPath("/v1/analyze");

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getBaseUrl());

        storageService = mock(StorageService.class);
        when(storageService.download("room-images/example.jpg"))
                .thenReturn(new byte[] {1, 2, 3});

        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new VisionServiceClient(builder.build(), properties, storageService);
    }

    @Test
    void analyze_mapsStubbedVisionResponse() {
        mockServer.expect(
                        requestTo("http://vision-service.test/v1/analyze")
                )
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andRespond(withSuccess("""
                        {
                          "modelVersion": "yolo11n",
                          "processingTimeMs": 25.5,
                          "objects": [
                            {
                              "label": "chair",
                              "confidence": 0.91,
                              "bbox": {
                                "x": 0.12,
                                "y": 0.1,
                                "w": 0.24,
                                "h": 0.45
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        RoomImage image = new RoomImage(
                UUID.randomUUID(),
                "local",
                "room-images/example.jpg",
                "example.jpg",
                "image/jpeg",
                2000L
        );

        AnalysisInput response = client.analyze(image);

        assertThat(response.modelVersion()).isEqualTo("yolo11n");
        assertThat(response.objects()).hasSize(1);
        assertThat(response.objects().getFirst().label())
                .isEqualTo("chair");
        assertThat(response.objects().getFirst().bbox().w())
                .isEqualTo(0.24);

        mockServer.verify();
    }
}