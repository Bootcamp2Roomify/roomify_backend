package com.roomify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private String provider = "openai";
    private String baseUrl = "https://api.openai.com/v1";
    private String model;
    private String apiKey;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(60);
    private int maxOutputTokens = 800;
    private int maxRetries = 1;
    /** Omitted from requests when null (reasoning models reject it). */
    private Double temperature = 0.1;
    /** Sent only when set, e.g. "low" for reasoning models such as gpt-6-luna. */
    private String reasoningEffort;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    /** False for a blank key or an unedited .env.example placeholder such as "your_openai_api_key". */
    public boolean hasUsableApiKey() {
        if (apiKey == null || apiKey.isBlank()) return false;
        String key = apiKey.trim().toLowerCase(java.util.Locale.ROOT);
        return !(key.startsWith("your_") || key.startsWith("your-") || key.startsWith("replace_with") || key.equals("changeme"));
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
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

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public String getReasoningEffort() {
        return reasoningEffort;
    }

    public void setReasoningEffort(String reasoningEffort) {
        this.reasoningEffort = reasoningEffort == null || reasoningEffort.isBlank() ? null : reasoningEffort;
    }
}