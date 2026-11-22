package com.questionforge.dto.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatCompletionResponse(
    String id,
    List<Choice> choices
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
        int index,
        Message message,
        @com.fasterxml.jackson.annotation.JsonProperty("finish_reason")
        String finishReason
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
        String role,
        String content
    ) {}
}
