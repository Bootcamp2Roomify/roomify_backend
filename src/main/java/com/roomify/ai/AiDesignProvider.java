package com.roomify.ai;

public interface AiDesignProvider {

    String generateJson(String systemPrompt, String userPrompt);

    String getModelName();
}