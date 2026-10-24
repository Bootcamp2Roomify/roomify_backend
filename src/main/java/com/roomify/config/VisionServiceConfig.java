package com.roomify.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(VisionServiceProperties.class)
public class VisionServiceConfig {

    @Bean
    public RestClient visionRestClient(VisionServiceProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                // Uvicorn rejects the h2c upgrade the JDK client sends by default.
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.getConnectTimeout())
                .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());

        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}