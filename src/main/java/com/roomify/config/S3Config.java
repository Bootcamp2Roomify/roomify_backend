package com.roomify.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
public class S3Config {

    @Bean
    public S3Client s3Client(
        @Value("${storage.s3.region}") String region
    ) {
        return S3Client.builder()
            .region(Region.of(region))
            .build();
    }
}