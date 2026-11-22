package com.questionforge.dto.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ChatCompletionRequest(
    String model,
    List<ChatMessage> messages,
    double temperature,
    @JsonProperty("max_tokens")
    int maxTokens,
    @JsonProperty("response_format")
    ResponseFormat responseFormat
) {
    public record ChatMessage(
        String role,
        String content
    ) {}

    public record ResponseFormat(
        String type
    ) {}
}
