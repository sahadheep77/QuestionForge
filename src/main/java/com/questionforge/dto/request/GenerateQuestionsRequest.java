package com.questionforge.dto.request;

import com.questionforge.dto.enums.Difficulty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GenerateQuestionsRequest(
    @NotBlank(message = "targetId must not be blank")
    String targetId,

    @Min(value = 0, message = "depth must be >= 0")
    @Max(value = 5, message = "depth must be <= 5")
    int depth,

    @Min(value = 1, message = "count must be >= 1")
    @Max(value = 10, message = "count must be <= 10")
    int count,

    @NotNull(message = "difficulty must not be null")
    Difficulty difficulty
) {
    public GenerateQuestionsRequest {
        if (count == 0) {
            count = 3; // Default 3 questions per request
        }
    }
}
