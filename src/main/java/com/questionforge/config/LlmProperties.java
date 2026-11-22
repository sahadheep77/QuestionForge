package com.questionforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "question-forge.llm")
public record LlmProperties(
    String baseUrl,
    String apiKey,
    String model,
    double temperature,
    int maxTokens,
    int timeoutMs
) {
    public LlmProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.openai.com/v1";
        }
        if (model == null || model.isBlank()) {
            model = "gpt-4o-mini";
        }
        if (timeoutMs <= 0) {
            timeoutMs = 15000;
        }
    }
}
