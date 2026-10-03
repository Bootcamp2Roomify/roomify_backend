package com.roomify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "vision-service")
public class VisionServiceProperties {

    private String baseUrl;
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(10);
    private String analysisPath = "/v1/analyze";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public String getAnalysisPath() {
        return analysisPath;
    }

    public void setAnalysisPath(String analysisPath) {
        this.analysisPath = analysisPath;
    }
}